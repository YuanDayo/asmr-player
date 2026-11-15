package com.kiite.player.domain

import android.app.Application
import com.kiite.player.core.AsmrOneTreeNode
import com.kiite.player.core.AsmrOneTree
import com.kiite.player.core.AsmrOneWork
import com.kiite.player.data.AsmrOneClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * asmr.one 原生下载：不经过网页——网页的下载按钮是在页面里用 JS 把文件拼成
 * blob 流保存的，WebView 的 DownloadListener 根本收不到（表现为「页面显示
 * Done! 但应用什么都没下」）。这里直接走它的公开 API：
 *
 *   guest 登录拿 token → /api/tree/{id} 列文件 → /api/media/{id}/{相对路径} 逐个下
 *
 * 落盘到 曲库根目录/{作品编号 标题}/ 下并保持站内目录结构，完成后自动重扫曲库。
 * 同一目录里 mp3/wav/flac 重复的只挑一种（mp3 优先），见 [AsmrOneTree.audioFiles]。
 */
class AsmrOneController(
    private val app: Application,
    private val scope: CoroutineScope,
    /** 曲库根目录（下载落盘位置）。 */
    private val rootPath: () -> String?,
    /** 下载完成后重新扫描曲库。 */
    private val rescan: () -> Unit,
    private val notify: (String) -> Unit,
) {

    /** 单个作品的下载状态（null = 空闲）。UI 用它把「下载」按钮变成进度行。 */
    data class DownloadState(
        val workId: Long,
        val title: String,
        /** 当前阶段文案，如「正在登录」「正在下载 3/24（12 MB）」；结束时是结果。 */
        val status: String,
        val running: Boolean = true,
        val failed: Boolean = false,
    )

    private val _state = MutableStateFlow<DownloadState?>(null)
    val state: StateFlow<DownloadState?> = _state.asStateFlow()

    /** token 内存缓存：guest token 有效期以年计，进程内复用即可。 */
    @Volatile
    private var token: String? = null

    private var job: Job? = null

    /** 取消标记：逐文件下载之间检查（正在下的那个会下完）。 */
    @Volatile
    private var cancelled = false

    private val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

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

    /**
     * 下载一个作品。files 传 null 时自动拉树并按 mp3>wav>flac 去重选全部音频；
     * 传具体文件（文件选择页勾选结果）则只下这些。rootName 是树根目录名，落盘时剥掉同名首段。
     */
    fun download(work: AsmrOneWork, files: List<AsmrOneTreeNode>? = null, rootName: String? = null) {
        if (job?.isActive == true) {
            notify("已有 asmr.one 下载在进行中，请等它完成")
            return
        }
        cancelled = false
        val workId = work.id
        val title = work.title ?: work.sourceId ?: ("#" + workId)
        job = scope.launch {
            _state.value = DownloadState(workId, title, "正在登录 asmr.one…")
            try {
                val root = rootPath() ?: run {
                    _state.value = _state.value?.copy(
                        status = "未设置曲库根目录，无法保存", running = false, failed = true,
                    )
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
                    _state.value = _state.value?.copy(
                        status = "没有可下载的音频文件", running = false, failed = true,
                    )
                    return@launch
                }
                // 目标目录：作品编号 + 标题（曲库扫描按文件夹名里的 RJ 号归组）
                val folder = safeFolderName((work.code ?: ("A" + workId)) + " " + title)
                val baseDir = java.io.File(root, folder)
                var ok = 0
                var skip = 0
                var bytes = 0L
                filesToFetch.forEachIndexed { i, file ->
                    if (cancelled) {
                        _state.value = _state.value?.copy(
                            status = "已取消（已保存 " + ok + " 个文件）", running = false,
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
                        return@forEachIndexed
                    }
                    _state.value = _state.value?.copy(
                        status = "正在下载 " + (i + 1) + "/" + filesToFetch.size + "（" + (bytes / 1024 / 1024) +
                            " MB）" + (out.name ?: ""),
                    )
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
                    for (cand in candidates) {
                        var attempt = withContext(Dispatchers.IO) {
                            fetchMedia(cand, freshToken, out) { bytes += it }
                        }
                        if (attempt == RETRY_AUTH) {
                            // token 失效：强刷一次重试本文件
                            freshToken = ensureToken(force = true)
                            attempt = withContext(Dispatchers.IO) {
                                fetchMedia(cand, freshToken, out) { bytes += it }
                            }
                        }
                        if (attempt == NOT_FOUND) continue
                        fetched = true
                        break
                    }
                    if (!fetched) throw java.io.IOException("媒体不存在（404）：" + candidates.first())
                    ok++
                }
                if (cancelled) return@launch
                _state.value = _state.value?.copy(
                    status = "完成：" + ok + " 个文件（" + (bytes / 1024 / 1024) + " MB）" +
                        (if (skip > 0) "，跳过已有 " + skip + " 个 " else "") + "→ " + folder,
                    running = false,
                )
                notify("asmr.one 下载完成：" + title + "，正在重新扫描曲库")
                rescan()
            } catch (e: Exception) {
                _state.value = _state.value?.copy(
                    status = "下载失败：" + (e.message ?: e.javaClass.simpleName),
                    running = false,
                    failed = true,
                )
            }
        }
    }

    /** 取消当前下载。 */
    fun cancel() {
        cancelled = true
    }

    /** 清掉结束状态，让按钮恢复可点。 */
    fun clear() {
        if (job?.isActive != true) _state.value = null
    }

    /** 返回写入字节数；[RETRY_AUTH] 表示 token 失效需要刷新重试。 */
    private fun fetchMedia(
        url: String,
        token: String,
        out: java.io.File,
        onRead: (Long) -> Unit,
    ): Long {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Authorization", "Bearer " + token)
        }
        val code = conn.responseCode
        if (code == 401) {
            conn.disconnect()
            return RETRY_AUTH
        }
        if (code == 404) {
            conn.disconnect()
            return NOT_FOUND
        }
        if (code !in 200..299) {
            conn.disconnect()
            throw java.io.IOException("媒体请求 " + code + "：" + url)
        }
        out.parentFile?.mkdirs()
        var read = 0L
        conn.inputStream.use { input ->
            java.io.FileOutputStream(out).use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    read += n
                    onRead(n.toLong())
                }
            }
        }
        conn.disconnect()
        return read
    }

    private val RETRY_AUTH: Long = -1L

    /** 404：路径口径不对，换一种路径写法再试（不算失败）。 */
    private val NOT_FOUND: Long = -2L

    private fun safeFolderName(name: String): String = name
        .replace(Regex("[\\\\/:*?\"<>|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
}
