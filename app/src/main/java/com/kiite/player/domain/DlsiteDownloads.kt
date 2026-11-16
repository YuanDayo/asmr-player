package com.kiite.player.domain

import android.app.Application
import com.kiite.player.core.SizeFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DLsite 应用内下载：带登录态抓下载直链，写到 曲库根/DLsite，单文件一个状态。
 *
 * 与 [AsmrOneController] 同一套骨架，原因也一样——以前它跑在 ViewModel 作用域里：
 *   - 退出下载页、Activity 重建，协程被取消，下到一半的文件就这么断了；
 *   - 没有通知、没有前台优先级，锁屏后被系统降频甚至回收；
 *   - 直接写目标文件，中断就留下一个「看起来正常」的半截 zip；
 *   - 只有「每 2MB 一句文字」，没有百分比、速度、剩余时间，也停不下来。
 *
 * 现在：应用级作用域（切页面/后台/锁屏都继续）、进度条数据齐全、可取消、
 * 先写 `.kiite-part` 再改名。通知栏由 [com.kiite.player.download.DownloadService] 统一挂。
 */
class DlsiteDownloader(
    private val scope: CoroutineScope,
) {

    /** 单文件下载状态（null = 空闲）。 */
    data class State(
        /** 作品编号（RJ/VJ…），下载完成后用它标记「已下载」。 */
        val code: String? = null,
        val fileName: String = "",
        /** 已下字节 / 总字节（-1 = 服务端没给 Content-Length）。 */
        val doneBytes: Long = 0L,
        val totalBytes: Long = -1L,
        val speedBps: Long = 0L,
        val running: Boolean = true,
        val completed: Boolean = false,
        val failed: Boolean = false,
        val cancelled: Boolean = false,
        /** 落盘完整路径（完成后才有值）。 */
        val savedPath: String? = null,
        /** 一句话状态，界面与通知都用它。 */
        val status: String = "",
        /** 第几次尝试（断流会重试一次）。 */
        val attempt: Int = 1,
    ) {
        val hasTotal: Boolean get() = totalBytes > 0L

        val progress: Float
            get() = when {
                completed -> 1f
                hasTotal -> (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                else -> 0f
            }

        val percent: Int get() = (progress * 100).toInt().coerceIn(0, 100)

        /** 「12.3 MB / 45.6 MB」，未知总长时只给已下多少。 */
        val sizeText: String
            get() = SizeFormat.text(doneBytes) + if (hasTotal) " / " + SizeFormat.text(totalBytes) else ""

        val speedText: String
            get() = when {
                speedBps <= 0L -> ""
                speedBps < 1024L -> speedBps.toString() + " B/s"
                speedBps < 1024L * 1024L -> (speedBps / 1024L).toString() + " KB/s"
                else -> String.format(Locale.US, "%.1f MB/s", speedBps / 1048576.0)
            }

        val etaSeconds: Long?
            get() {
                if (!running || speedBps <= 0L || !hasTotal) return null
                val left = totalBytes - doneBytes
                if (left <= 0L) return 0L
                return left / speedBps
            }

        val etaText: String
            get() = etaSeconds?.let { s ->
                when {
                    s <= 1L -> "即将完成"
                    s < 60L -> "约 " + s + " 秒"
                    else -> "约 " + (s / 60) + " 分 " + (s % 60) + " 秒"
                }
            } ?: ""

        /** 界面一行小字：「45% · 12.3 MB / 45.6 MB · 1.8 MB/s · 约 30 秒」。 */
        val progressText: String
            get() = buildString {
                if (hasTotal) append(percent).append("% · ")
                append(sizeText)
                if (running) {
                    speedText.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    etaText.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                }
                if (attempt > 1 && running) append(" · 第 ").append(attempt).append(" 次尝试")
            }
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /** 一句句提示（完成/失败/已存在…），VM 收集后弹提示。 */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    @Volatile
    private var job: Job? = null

    @Volatile
    private var cancelled = false

    /** 正在读的连接：取消时直接 disconnect，read 立刻抛错，不必等这批数据读完。 */
    @Volatile
    private var activeConn: java.net.HttpURLConnection? = null

    /** 完成回调（标记「已下载」用）。一次性，跑完即清，避免长期持有界面层对象。 */
    @Volatile
    private var onCompleted: ((String?) -> Unit)? = null

    val running: Boolean get() = job?.isActive == true

    /**
     * 开始下载一个直链。
     *
     * @param rootDir 曲库根目录（落盘到 `<rootDir>/DLsite/`）；null 时退到系统下载目录。
     * @param onCompleted 完成回调，参数是作品编号（可能为空）。
     */
    fun start(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        code: String?,
        rootDir: String?,
        cookie: String? = null,
        onCompleted: ((String?) -> Unit)? = null,
    ) {
        if (job?.isActive == true) {
            _events.tryEmit("已有下载在进行中，请等它完成")
            return
        }
        cancelled = false
        this.onCompleted = onCompleted
        val fileName = guessFileName(contentDisposition, url)
        // 先同步置为「进行中」再起协程：DownloadService 起来时读到的第一帧就是 running，
        // 否则它可能先看到上一轮的结束态而立刻 stopSelf。
        _state.value = State(code = code, fileName = fileName, status = "正在连接…")
        job = scope.launch {
            // runDownload 自己把失败/取消都写成状态，这里只兜住意外异常（协程里抛出去会崩应用）
            val saved = withContext(Dispatchers.IO) {
                runCatching { runDownload(url, userAgent, cookie, code, rootDir, fileName) }.getOrNull()
            }
            this@DlsiteDownloader.onCompleted = null
            if (saved != null) {
                val st = _state.value ?: State(code = code, fileName = fileName, running = false)
                _state.value = st.copy(
                    status = "已保存：" + saved.name + "（" + SizeFormat.text(saved.length()) + "）",
                    running = false,
                    completed = true,
                    savedPath = saved.absolutePath,
                    doneBytes = saved.length(),
                )
                runCatching { onCompleted?.invoke(code) }
                _events.tryEmit("DLsite 下载完成：" + saved.name)
            }
        }
    }

    /** 取消：立刻断开当前连接，半截文件删掉，状态停在「已暂停」。 */
    fun cancel() {
        cancelled = true
        runCatching { activeConn?.disconnect() }
        val st = _state.value
        if (st != null && st.running) {
            _state.value = st.copy(
                status = "已暂停（已下 " + SizeFormat.text(st.doneBytes) + "）",
                running = false,
                cancelled = true,
            )
        }
    }

    /** 清掉结束状态（离开下载页时调用）。 */
    fun clear() {
        if (job?.isActive != true) _state.value = null
    }

    /**
     * 真正的下载主体：`<rootDir>/DLsite/<文件名>.kiite-part` → 完成后改名。
     * 返回落盘文件；被取消时返回 null。断流会重试一次（大文件在网络抖动时很常见）。
     */
    private fun runDownload(
        url: String,
        userAgent: String?,
        cookie: String?,
        code: String?,
        rootDir: String?,
        fileName: String,
    ): java.io.File? {
        val base = rootDir?.takeIf { it.isNotBlank() }
            ?: android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
        val dir = targetDir(base).apply { mkdirs() }
        val out = java.io.File(dir, fileName)
        val part = java.io.File(out.path + PART_SUFFIX)
        runCatching { part.delete() }

        var lastError: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            if (cancelled) break
            _state.value = _state.value?.copy(attempt = attempt)
            try {
                downloadOnce(url, userAgent, cookie, code, fileName, part)
                val written = part.length()
                if (written <= 0L) throw java.io.IOException("服务器返回了空文件")
                runCatching { out.delete() }
                if (!part.renameTo(out)) throw java.io.IOException("保存失败：无法写入 " + out.name)
                _state.value = _state.value?.copy(doneBytes = written)
                return out
            } catch (e: Exception) {
                lastError = e
                if (cancelled) break
                // 还有机会就等一下重试（先清半成品，避免续写成错位的文件）
                runCatching { part.delete() }
                if (attempt < MAX_ATTEMPTS) {
                    _state.value = _state.value?.copy(
                        status = "网络中断，正在重试…",
                        doneBytes = 0L,
                        speedBps = 0L,
                    )
                    runCatching { Thread.sleep(RETRY_DELAY_MS) }
                }
            }
        }
        runCatching { part.delete() }
        if (cancelled) {
            val st = _state.value
            if (st != null) {
                _state.value = st.copy(
                    status = "已暂停（已下 " + SizeFormat.text(st.doneBytes) + "，半截文件已删除）",
                    running = false,
                    cancelled = true,
                    speedBps = 0L,
                )
            }
            return null
        }
        val message = lastError?.message ?: "未知错误"
        _state.value = _state.value?.copy(
            status = "下载失败：" + message,
            running = false,
            failed = true,
            speedBps = 0L,
        )
        _events.tryEmit("DLsite 下载失败：" + message)
        return null
    }

    /** 单次尝试：读流写 part，边写边更新进度（节流）。 */
    private fun downloadOnce(
        url: String,
        userAgent: String?,
        cookie: String?,
        code: String?,
        fileName: String,
        part: java.io.File,
    ) {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
            if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
        }
        activeConn = conn
        try {
            val response = conn.responseCode
            if (response !in 200..299) {
                throw java.io.IOException("HTTP " + response + "（下载链接可能已过期，回下载页重新点一次）")
            }
            val total = runCatching { conn.contentLengthLong }.getOrDefault(-1L)
            var read = 0L
            var lastEmit = 0L
            val startedAt = android.os.SystemClock.elapsedRealtime()
            part.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled) throw java.io.IOException("已取消")
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        read += n
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastEmit >= PROGRESS_INTERVAL_MS) {
                            lastEmit = now
                            val elapsed = (now - startedAt).coerceAtLeast(1L)
                            val inst = read * 1000L / elapsed
                            val prev = _state.value
                            // 速度平滑：新样本占 3 成，避免数字乱跳
                            val speed = if (prev != null && prev.speedBps > 0L) {
                                (prev.speedBps * 7 + inst * 3) / 10
                            } else {
                                inst
                            }
                            _state.value = prev?.copy(
                                code = code,
                                fileName = fileName,
                                doneBytes = read,
                                totalBytes = total,
                                speedBps = speed,
                                status = "正在下载 " + fileName,
                            )
                        }
                    }
                }
            }
            // 收尾校准一次：文件很小时整段不到一个上报周期（200ms），进度一次都不会发，
            // 那样 totalBytes 会一直停在 -1、完成后也算不出大小——这里补上最终值。
            _state.value = _state.value?.copy(
                code = code,
                fileName = fileName,
                doneBytes = read,
                totalBytes = total,
                speedBps = 0L,
            )
        } finally {
            activeConn = null
            runCatching { conn.disconnect() }
        }
    }

    private fun guessFileName(disposition: String?, url: String): String = guessName(disposition, url)

    companion object {
        /**
         * 从 Content-Disposition / URL 猜一个安全的文件名。
         *
         * DLsite 的下载响应常带 `filename*=UTF-8''RJ123456.zip`，前面的编码声明要剥掉；
         * 头部完全缺失时退到 URL 最后一段；文件名里的路径分隔符等换成下划线（否则会写到别处）。
         */
        internal fun guessName(disposition: String?, url: String): String {
            val fromHeader = disposition
                // 同时覆盖 filename= 与 filename*=UTF-8''（后者是 RFC 5987 的编码写法）
                ?.substringAfter("filename", "")
                ?.trimStart('*', '=', ' ')
                ?.substringAfter("''")
                ?.trim()
                ?.trim('"', '\'')
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                ?.takeIf { it.isNotBlank() && it.contains('.') }
            if (fromHeader != null) return safeName(fromHeader)
            val seg = url.substringBefore('?').substringAfterLast('/')
            return safeName(if (seg.isNotBlank() && seg.contains('.')) seg else "dlsite-download.zip")
        }

        /** 去掉路径分隔符与 Windows 非法字符，避免目录穿越 / 写失败。 */
        internal fun safeName(name: String): String = name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(120)
            .ifBlank { "dlsite-download.zip" }

        /**
         * 实际落盘目录：`<base>/DLsite`。
         * 用户把下载目录直接设成了 `…/DLsite` 时不再套一层，免得出现 `DLsite/DLsite`。
         */
        internal fun targetDir(base: String): java.io.File {
            val trimmed = base.trimEnd('/')
            val last = trimmed.substringAfterLast('/')
            return if (last.equals("DLsite", ignoreCase = true)) java.io.File(trimmed)
            else java.io.File(trimmed, "DLsite")
        }

        /** 断流重试次数（含首次）。 */
        const val MAX_ATTEMPTS = 2

        const val RETRY_DELAY_MS = 800L

        /** 进度上报节流：太密界面/通知栏刷不过来，太疏像卡住。 */
        const val PROGRESS_INTERVAL_MS = 200L

        const val PART_SUFFIX = ".kiite-part"
    }
}

/** 应用级单例：下载必须活得比 ViewModel 长，见 [DlsiteDownloader] 的类注释。 */
object DlsiteDownloads {

    @Volatile
    private var instance: DlsiteDownloader? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun get(app: Application): DlsiteDownloader {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: DlsiteDownloader(scope).also { instance = it }
        }
    }
}
