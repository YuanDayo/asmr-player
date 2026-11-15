package com.kiite.player.domain

import android.app.Application
import com.kiite.player.core.AsmrOneTreeNode
import com.kiite.player.core.AsmrOneTree
import com.kiite.player.core.AsmrOneWork
import com.kiite.player.data.AsmrOneClient
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * asmr.one 原生下载：不经过网页——网页的下载按钮是在页面里用 JS 把文件拼成
 * blob 流保存的，WebView 的 DownloadListener 根本收不到（表现为「页面显示
 * Done! 但应用什么都没下」）。这里直接走它的公开 API：
 *
 *   guest 登录拿 token → /api/tracks/{id} 拉文件树 → /api/media/download/{hash} 逐个下
 *
 * 落盘到 曲库根目录/{作品编号 标题}/ 下并保持站内目录结构，完成后自动重扫曲库。
 * 同一目录里 mp3/wav/flac 重复的只挑一种（mp3 优先），见 [AsmrOneTree.audioFiles]。
 *
 * 下载在**应用级作用域**里跑（见 [AsmrOneDownloads]），退出页面/切到后台都会继续；
 * 同时由 [com.kiite.player.download.DownloadService] 挂前台通知，通知栏能看进度也能取消。
 */
class AsmrOneController(
    private val app: Application,
    private val scope: CoroutineScope,
    /** 曲库根目录（下载落盘位置）。 */
    private val rootPath: () -> String?,
) {

    /**
     * 下载状态（null = 空闲）。UI 用它把「下载」按钮变成进度条，
     * 通知栏用同一份数据画进度。
     */
    data class DownloadState(
        val workId: Long,
        val title: String,
        /** 一句话状态，如「正在下载 3/24 · 01.mp3」「完成：12 个文件（45 MB）」。 */
        val status: String,
        val running: Boolean = true,
        val failed: Boolean = false,
        /** 正常跑完（用于把进度条画满，取消/失败不算）。 */
        val completed: Boolean = false,
        val cancelled: Boolean = false,
        /** 当前第几个文件（1 起，0 = 还在登录/拉列表）。 */
        val index: Int = 0,
        val total: Int = 0,
        val fileName: String = "",
        /** 当前文件已下字节 / 总字节（-1 = 服务端没给 Content-Length）。 */
        val fileDoneBytes: Long = 0L,
        val fileTotalBytes: Long = -1L,
        /** 已完成文件的累计字节。 */
        val doneBytes: Long = 0L,
        /** 估算速度（字节/秒），平滑过的。 */
        val speedBps: Long = 0L,
        val okCount: Int = 0,
        val skipCount: Int = 0,
    ) {
        /** 0..1 总进度：已完成文件数 + 当前文件内部进度。 */
        val progress: Float
            get() = when {
                completed -> 1f
                total <= 0 || index <= 0 -> 0f
                else -> {
                    val within = if (fileTotalBytes > 0) {
                        (fileDoneBytes.toFloat() / fileTotalBytes).coerceIn(0f, 1f)
                    } else {
                        // 没有 Content-Length 时用「文件数」估算，给一点点进度避免干等
                        0.05f
                    }
                    (((index - 1) + within) / total.toFloat()).coerceIn(0f, 1f)
                }
            }

        val percent: Int get() = (progress * 100).toInt().coerceIn(0, 100)

        /** 「1.8 MB/s」，未知时为空串。 */
        val speedText: String
            get() = when {
                speedBps <= 0L -> ""
                speedBps < 1024L -> speedBps.toString() + " B/s"
                speedBps < 1024L * 1024L -> (speedBps / 1024L).toString() + " KB/s"
                else -> String.format(Locale.US, "%.1f MB/s", speedBps / 1048576.0)
            }

        /** 当前文件预计剩余时间（秒），未知为 null。 */
        val etaSeconds: Long?
            get() {
                if (!running || speedBps <= 0L || fileTotalBytes <= 0L) return null
                val left = fileTotalBytes - fileDoneBytes
                if (left <= 0L) return 0L
                return (left / speedBps).coerceAtLeast(0L)
            }

        /** 「约 1 分 20 秒」，未知为空串。 */
        val etaText: String
            get() = etaSeconds?.let { s ->
                when {
                    s <= 1L -> "即将完成"
                    s < 60L -> "约 " + s + " 秒"
                    else -> "约 " + (s / 60) + " 分 " + (s % 60) + " 秒"
                }
            } ?: ""

        /** 已完成 / 总数，用于通知副标题与界面小字。 */
        val countText: String
            get() = if (total > 0) index.coerceAtMost(total).toString() + "/" + total else ""
    }

    private val _state = MutableStateFlow<DownloadState?>(null)
    val state: StateFlow<DownloadState?> = _state.asStateFlow()

    /** 一句句提示（完成/失败/无目录…），由界面侧收集后弹提示。 */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** token 内存缓存：guest token 有效期以年计，进程内复用即可。 */
    @Volatile
    private var token: String? = null

    @Volatile
    private var job: Job? = null

    /** 取消标记：逐文件下载之间检查；当前连接会被立刻断开。 */
    @Volatile
    private var cancelled = false

    /** 正在读的连接：取消时直接 disconnect，让 read 立刻抛错，不必等下完一个文件。 */
    @Volatile
    private var activeConn: java.net.HttpURLConnection? = null

    private val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

    val running: Boolean get() = job?.isActive == true

    /** 取一个可用 token；缓存失效（401）时 force 重新登录。 */
    private suspend fun ensureToken(force: Boolean = false): String {
        val cached = token
        if (!force && cached != null) return cached
        val (t, _) = withContext(Dispatchers.IO) {
            AsmrOneClient.login().getOrElse { throw it }
        }
        token = t
        return t
    }

    /** 拉作品的文件树（给文件选择页用；内部自动 guest 登录拿 token）。 */
    suspend fun fetchTree(workId: Long): AsmrOneTreeNode = withContext(Dispatchers.IO) {
        AsmrOneClient.tree(workId, ensureToken()).getOrThrow()
    }

    // ---------- 下载前的容量估算 ----------
    //
    // /api/tracks 的文件节点里**没有体积字段**（只有 type/title/hash/mediaDownloadUrl），
    // 所以容量只能问服务端：对每个媒体直链发一次 HEAD 读 Content-Length（个别端点不认
    // HEAD 时退回 Range: bytes=0-0 读 Content-Range 的总长）。探测结果按 source 缓存，
    // 同一作品反复进出选择页不会重复请求；失败的不缓存，下次勾选再试。

    private val _sizes = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** source → 字节数（只含探测成功的条目）。UI 直接 collect，逐个到达逐个刷新。 */
    val sizes: StateFlow<Map<String, Long>> = _sizes.asStateFlow()

    /**
     * 探测一组文件的体积（并发 [SIZE_PARALLEL] 个，已缓存的跳过）。
     * 取消协程即停止后续探测；已完成的写入缓存。
     */
    suspend fun probeSizes(workId: Long, sources: List<String>) {
        val wanted = sources.distinct().filter { it.isNotBlank() && !_sizes.value.containsKey(it) }
        if (wanted.isEmpty()) return
        val token = withContext(Dispatchers.IO) { runCatching { ensureToken() }.getOrNull() }
        wanted.chunked(SIZE_PARALLEL).forEach { chunk ->
            val got = coroutineScope {
                chunk.map { src ->
                    async(Dispatchers.IO) { src to probeOneSize(workId, src, token) }
                }.awaitAll()
            }
            val known = got.filter { it.second > 0L }
            if (known.isNotEmpty()) _sizes.value = _sizes.value + known.toMap()
        }
    }

    /** 单个体积：先 HEAD，拿不到再退 Range 0-0；都失败返回 -1。 */
    private fun probeOneSize(workId: Long, source: String, token: String?): Long {
        val head = runCatching { requestSize(workId, source, token, head = true) }.getOrDefault(-1L)
        if (head > 0L) return head
        return runCatching { requestSize(workId, source, token, head = false) }.getOrDefault(-1L)
    }

    private fun requestSize(workId: Long, source: String, token: String?, head: Boolean): Long {
        val conn = (java.net.URL(AsmrOneClient.urlFor(workId, source)).openConnection()
            as java.net.HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "*/*")
            if (token != null) setRequestProperty("Authorization", "Bearer " + token)
            if (head) {
                requestMethod = "HEAD"
            } else {
                // 不认 HEAD 的端点：只请求第 1 个字节，总长在 Content-Range 里
                setRequestProperty("Range", "bytes=0-0")
            }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) return -1L
            val len = conn.contentLengthLong
            if (head && len > 0L) return len
            if (!head) {
                val total = AsmrOneClient.totalFromContentRange(conn.getHeaderField("Content-Range"))
                if (total != null && total > 0L) return total
                // 服务端忽略了 Range（回 200）：contentLength 本身就是全长
                if (len > 0L) return len
            }
            return -1L
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * 下载一个作品。files 传 null 时自动拉树并按 mp3>wav>flac 去重选全部音频；
     * 传具体文件（文件选择页勾选结果）则只下这些。rootName 是树根目录名，落盘时剥掉同名首段。
     */
    fun download(work: AsmrOneWork, files: List<AsmrOneTreeNode>? = null, rootName: String? = null) {
        if (job?.isActive == true) {
            _events.tryEmit("已有 asmr.one 下载在进行中，请等它完成")
            return
        }
        cancelled = false
        val workId = work.id
        val title = work.title ?: work.sourceId ?: ("#" + workId)
        // 先同步置为「进行中」再起协程：DownloadService 启动时读到的第一帧就是 running，
        // 否则它可能先看到上一轮残留的结束态，立刻 stopSelf 把通知栏关掉。
        publish(DownloadState(workId, title, "正在登录 asmr.one…"))
        job = scope.launch {
            try {
                val root = rootPath() ?: run {
                    finish("未设置曲库根目录，无法保存", failed = true)
                    return@launch
                }
                val token0 = ensureToken()
                val treeName: String?
                val filesToFetch: List<AsmrOneTreeNode>
                if (files != null) {
                    filesToFetch = files
                    treeName = rootName
                } else {
                    _state.value = _state.value?.copy(status = "正在获取文件列表…")
                    val tree = withContext(Dispatchers.IO) {
                        AsmrOneClient.tree(workId, token0).getOrThrow()
                    }
                    filesToFetch = AsmrOneTree.audioFiles(tree)
                    treeName = tree.name
                }
                if (filesToFetch.isEmpty()) {
                    finish("没有可下载的音频文件", failed = true)
                    return@launch
                }
                // 目标目录：作品编号 + 标题（曲库扫描按文件夹名里的 RJ 号归组）
                val folder = safeFolderName((work.code ?: ("A" + workId)) + " " + title)
                val baseDir = java.io.File(root, folder)
                var ok = 0
                var skip = 0
                var bytes = 0L
                val total = filesToFetch.size

                filesToFetch.forEachIndexed { i, file ->
                    if (cancelled) {
                        _state.value = _state.value?.copy(
                            status = "已暂停（已保存 " + ok + " 个文件）",
                            running = false,
                            cancelled = true,
                        )
                        return@launch
                    }
                    val source = file.source ?: return@forEachIndexed
                    // 落盘路径用 relPath（如 mp3/01.mp3）；source 可能只是 @hash:作品/序号，
                    // 不能当文件名。relPath 缺失时退回文件本身的名字。
                    val relative = (file.relPath ?: file.displayName.ifEmpty { "track" })
                        .let { r ->
                            if (treeName != null && r.startsWith(treeName + "/")) r.substring(treeName.length + 1) else r
                        }
                        .split('/', '\\')
                        .filter { it.isNotBlank() && it != "." && it != ".." }
                        .joinToString(java.io.File.separator)
                        .ifEmpty { file.displayName.ifEmpty { "track" } }
                    val out = java.io.File(baseDir, relative)
                    if (out.isFile && out.length() > 0) {
                        skip++
                        bytes += out.length()
                        _state.value = _state.value?.copy(
                            index = i + 1,
                            total = total,
                            okCount = ok,
                            skipCount = skip,
                            doneBytes = bytes,
                            status = "已跳过已有文件 " + (i + 1) + "/" + total,
                        )
                        return@forEachIndexed
                    }
                    // 先写 .part，下完再改名：中断/取消不会留下「看起来完整」的半截文件，
                    // 下次重下也不会被「已存在且非空」误判成下好了。
                    val part = java.io.File(out.path + PART_SUFFIX)
                    runCatching { part.delete() }

                    var freshToken = token0
                    // 下载地址兜底：kikoeru 标准是 /api/media/download/{hash}，
                    // 个别镜像走 /api/media/stream/{hash} 或旧式 /api/media/{id}/{路径}，
                    // 逐个试，避免整批因为端点差异全失败。
                    val candidates = buildList {
                        add(AsmrOneClient.urlFor(workId, source))
                        if (source.startsWith(AsmrOneTree.HASH_TAG)) {
                            val h = source.removePrefix(AsmrOneTree.HASH_TAG)
                            add(AsmrOneClient.activeBase + "/api/media/stream/" + h)
                        }
                        file.relPath?.split('/')?.filter { it.isNotBlank() }?.joinToString("/")?.let {
                            add(AsmrOneClient.mediaUrl(workId, it))
                        }
                    }.distinct()

                    var fetched = false
                    var lastError: Exception? = null
                    for (cand in candidates) {
                        if (cancelled) break
                        var attempt = withContext(Dispatchers.IO) {
                            fetchMedia(cand, freshToken, part, i + 1, total, out.name)
                        }
                        if (attempt == RETRY_AUTH) {
                            // token 失效：强刷一次重试本文件
                            freshToken = ensureToken(force = true)
                            attempt = withContext(Dispatchers.IO) {
                                fetchMedia(cand, freshToken, part, i + 1, total, out.name)
                            }
                        }
                        if (attempt == NOT_FOUND) continue
                        if (attempt == CANCELLED) {
                            runCatching { part.delete() }
                            _state.value = _state.value?.copy(
                                status = "已暂停（已保存 " + ok + " 个文件）",
                                running = false,
                                cancelled = true,
                            )
                            return@launch
                        }
                        if (attempt < 0) {
                            lastError = java.io.IOException("下载中断：" + out.name)
                            continue
                        }
                        fetched = true
                        break
                    }
                    if (cancelled) {
                        runCatching { part.delete() }
                        _state.value = _state.value?.copy(
                            status = "已暂停（已保存 " + ok + " 个文件）",
                            running = false,
                            cancelled = true,
                        )
                        return@launch
                    }
                    if (!fetched) {
                        runCatching { part.delete() }
                        throw lastError ?: java.io.IOException("媒体不存在（404）：" + candidates.first())
                    }
                    val written = part.length()
                    runCatching { out.delete() }
                    if (!part.renameTo(out)) {
                        throw java.io.IOException("保存失败：无法写入 " + out.name)
                    }
                    ok++
                    bytes += written
                    _state.value = _state.value?.copy(
                        index = i + 1,
                        total = total,
                        okCount = ok,
                        fileName = out.name,
                        fileDoneBytes = 0,
                        fileTotalBytes = -1,
                        doneBytes = bytes,
                        status = "已下载 " + (i + 1) + "/" + total + " · " + out.name,
                    )
                }

                if (cancelled) return@launch
                val done = _state.value ?: DownloadState(workId, title, "")
                publish(
                    done.copy(
                        status = "完成：" + ok + " 个文件（" + mb(bytes) + "）" +
                            (if (skip > 0) "，跳过已有 " + skip + " 个" else "") + " → " + folder,
                        running = false,
                        completed = true,
                        index = total,
                        total = total,
                        fileDoneBytes = 1,
                        fileTotalBytes = 1,
                        speedBps = 0,
                    ),
                )
                _events.tryEmit("asmr.one 下载完成：" + title + "，曲库正在刷新")
            } catch (e: Exception) {
                if (cancelled) {
                    _state.value = _state.value?.copy(
                        status = "已暂停（已保存 " + (_state.value?.okCount ?: 0) + " 个文件）",
                        running = false,
                        cancelled = true,
                    )
                    return@launch
                }
                finish("下载失败：" + (e.message ?: e.javaClass.simpleName), failed = true)
            }
        }
    }

    /** 取消当前下载（当前文件立刻断开，半截文件会删掉）。 */
    fun cancel() {
        cancelled = true
        runCatching { activeConn?.disconnect() }
    }

    /** 清掉结束状态，让按钮恢复可点。 */
    fun clear() {
        if (job?.isActive != true) _state.value = null
    }

    private fun publish(s: DownloadState) {
        _state.value = s
    }

    private fun finish(status: String, failed: Boolean) {
        _state.value = _state.value?.copy(status = status, running = false, failed = failed)
        if (failed) _events.tryEmit("asmr.one 下载失败：" + status.removePrefix("下载失败："))
    }

    /**
     * 下载单个文件到 [part]。
     *
     * 返回写入字节数；负数是哨兵：[RETRY_AUTH] token 失效、[NOT_FOUND] 路径不对、
     * [CANCELLED] 用户取消、[FAILED] 传输中断（可换端点重试）。
     * 进度直接节流写进 [_state]，界面与通知栏都读同一份。
     */
    private fun fetchMedia(
        url: String,
        token: String,
        part: java.io.File,
        index: Int,
        total: Int,
        fileName: String,
    ): Long {
        if (cancelled) return CANCELLED
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Authorization", "Bearer " + token)
        }
        activeConn = conn
        try {
            val code = conn.responseCode
            if (code == 401) return RETRY_AUTH
            if (code == 404) return NOT_FOUND
            if (code !in 200..299) return FAILED
            val expected = conn.contentLengthLong
            part.parentFile?.mkdirs()
            val baseBytes = _state.value?.doneBytes ?: 0L
            var read = 0L
            var lastEmit = 0L
            val startedAt = android.os.SystemClock.elapsedRealtime()
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled) return CANCELLED
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        read += n
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastEmit >= PROGRESS_INTERVAL_MS) {
                            lastEmit = now
                            val elapsed = (now - startedAt).coerceAtLeast(1L)
                            val prev = _state.value
                            // 速度：新样本与旧估计 3:7 混合，避免数字乱跳
                            val inst = read * 1000L / elapsed
                            val speed = if (prev != null && prev.speedBps > 0 && prev.fileName == fileName) {
                                (prev.speedBps * 7 + inst * 3) / 10
                            } else {
                                inst
                            }
                            _state.value = prev?.copy(
                                index = index,
                                total = total,
                                fileName = fileName,
                                fileDoneBytes = read,
                                fileTotalBytes = expected,
                                doneBytes = baseBytes,
                                speedBps = speed,
                                status = "正在下载 " + index + "/" + total + " · " + fileName,
                            )
                        }
                    }
                }
            }
            return if (read > 0) read else FAILED
        } catch (e: Exception) {
            if (cancelled) return CANCELLED
            return FAILED
        } finally {
            activeConn = null
            runCatching { conn.disconnect() }
        }
    }

    private fun mb(b: Long): String = com.kiite.player.core.SizeFormat.text(b)

    private fun safeFolderName(name: String): String = name
        .replace(Regex("[\\\\/:*?\"<>|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)

    private companion object {
        const val RETRY_AUTH = -1L

        /** 404：路径口径不对，换一种路径写法再试（不算失败）。 */
        const val NOT_FOUND = -2L

        /** 用户取消。 */
        const val CANCELLED = -3L

        /** 传输中断（网络抖动/超时），可以换端点或重试。 */
        const val FAILED = -4L

        /** 进度上报间隔：太密会让界面和通知栏频繁刷新，太疏又显得卡住。 */
        const val PROGRESS_INTERVAL_MS = 220L

        const val PART_SUFFIX = ".kiite-part"

        /** 容量探测的并发数：太猛会被站点限速，太慢一个 24 文件的作品要等十几秒。 */
        const val SIZE_PARALLEL = 4
    }
}
