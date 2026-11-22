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
        /** 一次任务里下到的全部文件（分卷作品会有多个）。 */
        val savedPaths: List<String> = emptyList(),
        /** 分卷作品：当前第几个 / 一共几个。 */
        val fileIndex: Int = 1,
        val fileCount: Int = 1,
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
                // 分卷作品先说清「现在第几个」，否则进度条归零会被当成回退
                if (fileCount > 1) append("第 ").append(fileIndex).append("/").append(fileCount).append(" 个 · ")
                if (hasTotal) append(percent).append("% · ")
                append(sizeText)
                if (running) {
                    speedText.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    etaText.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                }
                if (attempt > 1 && running) append(" · 第 ").append(attempt).append(" 次尝试")
            }
    }

    /**
     * 下载器只关心「下哪个地址、带什么 Cookie、叫什么名字」。
     *
     * Cookie 必须跟着文件走：DLsite 的直链认的是入口页 302 时 `Set-Cookie` 下发的临时令牌，
     * 不是登录会话（带上会话反而 403）。
     */
    data class RemoteFile(
        val url: String,
        val cookie: String? = null,
        val fileName: String? = null,
        val contentDisposition: String? = null,
        val userAgent: String? = null,
        val referer: String? = null,
    )

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

    /**
     * 完成回调（标记「已下载」用）：作品编号 + 落盘路径。
     *
     * 路径必须一起给出去：台账只记编号的话，用户把文件删了应用还认为本机有
     * （见 [com.kiite.player.core.DownloadedLedger]）。一次性，跑完即清，
     * 避免长期持有界面层对象。
     */
    @Volatile
    private var onCompleted: ((String?, List<String>) -> Unit)? = null

    val running: Boolean get() = job?.isActive == true

    /**
     * 开始下载一个直链（WebView 兜底路径用）。
     *
     * @param rootDir 曲库根目录（落盘到 `<rootDir>/DLsite/`）；null 时退到系统下载目录。
     * @param onCompleted 完成回调：作品编号（可能为空）+ 落盘的完整路径。
     */
    fun start(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        code: String?,
        rootDir: String?,
        cookie: String? = null,
        referer: String? = null,
        /** 上层已经知道作品信息时给的落盘名（WebView 兜底路径也用「编号 + 标题」）。 */
        preferredName: String? = null,
        onCompleted: ((String?, List<String>) -> Unit)? = null,
    ) {
        startBatch(
            code = code,
            rootDir = rootDir,
            initialStatus = "正在连接…",
            onCompleted = onCompleted,
            resolver = {
                listOf(
                    RemoteFile(
                        url = url,
                        cookie = cookie,
                        fileName = preferredName,
                        contentDisposition = contentDisposition,
                        userAgent = userAgent,
                        referer = referer,
                    ),
                )
            },
        )
    }

    /**
     * 开始一次「解析 + 下载」任务。
     *
     * [resolver] 拿到的是**每个文件的直链与它自己的临时 Cookie**（DLsite 的分卷作品会有多个），
     * 解析放在 IO 线程且包在下载任务里：用户点一下就能走，不用先开网页。
     * resolver 抛出的异常（未登录 / 未购买 / 风控）直接转成界面上的可读失败原因。
     */
    fun startBatch(
        code: String?,
        rootDir: String?,
        resolver: () -> List<RemoteFile>,
        initialStatus: String = "正在向 DLsite 请求下载地址…",
        onCompleted: ((String?, List<String>) -> Unit)? = null,
    ) {
        if (job?.isActive == true) {
            _events.tryEmit("已有下载在进行中，请等它完成")
            return
        }
        cancelled = false
        this.onCompleted = onCompleted
        // 先同步置为「进行中」再起协程：DownloadService 起来时读到的第一帧就是 running，
        // 否则它可能先看到上一轮的结束态而立刻 stopSelf。
        _state.value = State(code = code, status = initialStatus)
        job = scope.launch {
            val resolved = withContext(Dispatchers.IO) { runCatching { resolver() } }
            val failure = resolved.exceptionOrNull()
            if (failure != null) {
                fail(code, failure.message ?: "拿不到下载地址")
                return@launch
            }
            val files = resolved.getOrDefault(emptyList())
            if (files.isEmpty()) {
                fail(code, "DLsite 没有给出可下载的文件")
                return@launch
            }
            _state.value = _state.value?.copy(fileCount = files.size)
            // downloadAll 在取消时返回 null（状态已经由 downloadFile 写成「已暂停」），
            // 其它失败一律抛异常，这里统一转成可读的失败态 —— 别让界面停在「进行中」。
            val result = withContext(Dispatchers.IO) {
                runCatching { downloadAll(files, code, rootDir) }
            }
            this@DlsiteDownloader.onCompleted = null
            val saved = result.getOrNull()
            if (saved == null) {
                if (!cancelled) fail(code, result.exceptionOrNull()?.message ?: "下载失败")
                return@launch
            }
            if (saved.isNotEmpty()) {
                val st = _state.value ?: State(code = code, running = false)
                val bytes = saved.sumOf { it.length() }
                _state.value = st.copy(
                    status = if (saved.size > 1) {
                        "已保存 " + saved.size + " 个文件（" + SizeFormat.text(bytes) + "）"
                    } else {
                        "已保存：" + saved.first().name + "（" + SizeFormat.text(bytes) + "）"
                    },
                    running = false,
                    completed = true,
                    savedPath = saved.first().absolutePath,
                    savedPaths = saved.map { it.absolutePath },
                    doneBytes = bytes,
                    fileIndex = saved.size.coerceAtLeast(1),
                )
                runCatching { onCompleted?.invoke(code, saved.map { it.absolutePath }) }
                _events.tryEmit(
                    if (saved.size > 1) "DLsite 下载完成：" + saved.size + " 个文件"
                    else "DLsite 下载完成：" + saved.first().name,
                )
            }
        }
    }

    /** 失败收尾：状态 + 提示一起给，界面不用再自己拼。 */
    private fun fail(code: String?, message: String) {
        this.onCompleted = null
        _state.value = (_state.value ?: State(code = code, running = false)).copy(
            status = message,
            running = false,
            failed = true,
            speedBps = 0L,
        )
        _events.tryEmit("DLsite 下载失败：" + message)
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
     * 一次任务的全部文件，按顺序下（分卷作品逐个来，进度条不会互相打架）。
     * 被取消时返回 null；中途失败直接抛给上层转成状态。
     */
    private fun downloadAll(files: List<RemoteFile>, code: String?, rootDir: String?): List<java.io.File>? {
        val base = rootDir?.takeIf { it.isNotBlank() }
            ?: android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
        val dir = targetDir(base).apply { mkdirs() }
        val saved = ArrayList<java.io.File>(files.size)
        files.forEachIndexed { i, f ->
            if (cancelled) return null
            _state.value = _state.value?.copy(
                fileIndex = i + 1,
                fileCount = files.size,
                fileName = f.fileName ?: "",
                doneBytes = 0L,
                totalBytes = -1L,
                speedBps = 0L,
                attempt = 1,
                status = if (files.size > 1) {
                    "正在下载第 " + (i + 1) + "/" + files.size + " 个文件…"
                } else {
                    "正在连接…"
                },
            )
            val out = downloadFile(f, dir, code) ?: return null
            saved.add(out)
        }
        return saved
    }

    /**
     * 单个文件：`<rootDir>/DLsite/<文件名>.kiite-part` → 完成后改名。
     * 被取消时返回 null；断流会重试一次（大文件在网络抖动时很常见）。
     */
    private fun downloadFile(f: RemoteFile, dir: java.io.File, code: String?): java.io.File? {
        val fileName = f.fileName?.takeIf { it.isNotBlank() }
            ?: guessFileName(f.contentDisposition, f.url)
        val out = java.io.File(dir, fileName)
        val part = java.io.File(out.path + PART_SUFFIX)
        runCatching { part.delete() }

        var lastError: Exception? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            if (cancelled) break
            _state.value = _state.value?.copy(attempt = attempt, fileName = fileName)
            try {
                downloadOnce(f, code, fileName, part)
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
        throw java.io.IOException(lastError?.message ?: "未知错误")
    }

    /** 单次尝试：读流写 part，边写边更新进度（节流）。 */
    private fun downloadOnce(
        f: RemoteFile,
        code: String?,
        fileName: String,
        part: java.io.File,
    ) {
        val conn = (java.net.URL(f.url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", f.userAgent ?: DEFAULT_UA)
            if (!f.cookie.isNullOrBlank()) setRequestProperty("Cookie", f.cookie)
            // DLsite 的直链会看 Referer：缺了会被 CDN 当成盗链直接 403
            if (!f.referer.isNullOrBlank()) setRequestProperty("Referer", f.referer)
        }
        activeConn = conn
        try {
            val response = conn.responseCode
            if (response !in 200..299) {
                // 403 基本只有两种：直链过期（DLsite 的签名/令牌是有时效的），或者令牌没带上。
                // 两种情况都是「重新点一次下载」能解决，所以提示必须能照着做。
                throw java.io.IOException(
                    if (response == 403) "HTTP 403（直链已过期或缺少下载令牌）：重新点一次下载会重新取地址"
                    else "HTTP " + response + "（下载链接可能已过期，回下载页重新点一次）",
                )
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

        /**
         * DLsite 下载用的落盘名：`RJ编号 作品标题.zip`（分卷再加「 第N部分」）。
         *
         * 不能信直链 URL 的最后一段：站方那一截经常是通用名，下载几部作品全是同一个文件，
         * 解压时互相覆盖；而且名字里没有编号，曲库既识别不出作品，也没法自动关联 DLsite 元数据。
         * 编号来自下载入口本身，标题优先用已缓存的作品信息，实在拿不到就退回站方文件名——
         * 至少不会把作品信息编错。
         */
        internal fun displayName(
            code: String?,
            title: String?,
            index: Int = 1,
            count: Int = 1,
            fallback: String? = null,
        ): String {
            val c = code?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            val t = title?.trim()?.takeIf { it.isNotBlank() }
                ?.let { if (c != null && it.startsWith(c, ignoreCase = true)) it.removePrefix(c).trim() else it }
            val fallbackBase = fallback?.substringBeforeLast('.')?.trim()?.ifBlank { null }
            val base = listOfNotNull(c, t ?: fallbackBase).joinToString(" ").ifBlank { "dlsite-download" }
            val part = if (count > 1) " 第" + index.coerceAtLeast(1) + "部分" else ""
            return safeName(base + part + ".zip")
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

        /** 原生下载（不经 WebView）时的 UA：与网页同一个，CDN 不会因为 UA 奇怪而拒。 */
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"

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
