package com.kiite.player.domain

import android.app.Application
import com.kiite.player.core.DlsiteLoginState
import com.kiite.player.core.DlsitePurchase
import com.kiite.player.core.DlsiteWork
import com.kiite.player.core.ProjectEntry
import com.kiite.player.data.AppLog
import com.kiite.player.data.clearDlsiteSession
import com.kiite.player.data.dlsiteCookieHeader
import com.kiite.player.data.dlsiteCookieCount
import com.kiite.player.data.DlsiteClient
import com.kiite.player.data.DlsiteStore
import com.kiite.player.data.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DLsite 相关的一切：识别作品信息、抓封面、登录态、已购同步、应用内下载。
 *
 * 抽出来的原因：这一整块有 300 行左右，且**与播放、台本、曲库结构完全无关**——
 * 它只对外部世界（网络）和几个回调（项目列表、根目录、是否只看音声）负责。
 *
 * 两条安全约定（原样保留，勿改）：
 *  - 直链可能指向第三方 CDN，**绝不把 DLsite 会话 Cookie 发过去**（只有 dlsite.com 及其子域才带）；
 *  - 登录态必须联网验证过才算已登录，绝不只看 cookie 是否存在。
 */
class DlsiteController(
    private val app: Application,
    private val repo: LibraryRepository,
    private val store: DlsiteStore,
    private val scope: CoroutineScope,
    /** 当前扫描到的项目（批量识别、已购比对用）。 */
    private val projects: () -> List<ProjectEntry>,
    /** 曲库根目录（未单独设置下载目录时的落盘位置）。 */
    private val rootPath: () -> String?,
    /** 用户自选的下载目录（设置里可改）；为空表示用曲库根目录。 */
    private val downloadDir: () -> String? = { null },
    /** 「已购列表只看音声」开关。 */
    private val purchaseAsmrOnly: () -> Boolean,
    /** 「隐藏 R18」开关（识别出 R18 作品时在通知里说明它会被曲库隐藏）。 */
    private val hideR18: () -> Boolean,
    /** 解压完成后重新扫描曲库。 */
    private val rescan: () -> Unit,
    private val notify: (String) -> Unit,
    private val setBusy: (String, String?) -> Unit,
) {
    private val _works = MutableStateFlow<Map<String, DlsiteWork>>(emptyMap())
    val works: StateFlow<Map<String, DlsiteWork>> = _works.asStateFlow()

    val count: Int get() = _works.value.size

    fun load() {
        runCatching {
            // 老版本把 DLsite 站内 banner 当成过封面（一批作品同一张图）。那些记录的
            // coverUrl 指向的不是本作品，启动时一并清掉，界面立刻回退到本地图片。
            val loaded = store.load()
            val cleaned = sanitizeCovers(loaded)
            _works.value = cleaned ?: loaded
            if (cleaned != null) runCatching { store.save(cleaned) }
            // 已购列表持久化装载：重启后主页「已购未下载」直接可用。
            // 注意：登录态仍保持 UNKNOWN，必须等 refreshLogin 联网验证（项目安全约定）。
            val saved = store.loadPurchases()
            if (saved.isNotEmpty()) {
                _purchases.value = saved
                fetchPurchaseMeta()
            }
        }
    }

    /**
     * 清掉「封面不是这部作品」的历史缓存（返回 null 表示没有改动）。
     * 判据用 coverUrl 本身：站内 banner 的地址里没有作品编号，见 [DlsiteParse.isPlausibleCover]。
     */
    private fun sanitizeCovers(map: Map<String, DlsiteWork>): Map<String, DlsiteWork>? {
        var changed = false
        val out = LinkedHashMap<String, DlsiteWork>(map.size)
        for ((code, work) in map) {
            if (work.coverLocalPath != null && !com.kiite.player.core.DlsiteParse.isPlausibleCover(work.coverUrl, code)) {
                runCatching { java.io.File(work.coverLocalPath).delete() }
                out[code] = work.copy(coverLocalPath = null)
                changed = true
                AppLog.log("清掉错封面：" + code + "（" + (work.coverUrl ?: "无地址") + "）")
            } else {
                out[code] = work
            }
        }
        return if (changed) out else null
    }

    private fun put(work: DlsiteWork) {
        val merged = _works.value + (work.code to work)
        _works.value = merged
        runCatching { store.save(merged) }
    }

    // ---------- 识别 ----------

    /**
     * 批量识别所有带 RJ 编号的项目（已识别过的跳过）。
     *
     * @param force 已识别的也重新抓一遍并重下封面——用于修复历史上被站内 banner
     *   污染成同一张头图的作品（每部作品的封面缓存按编号命名，只能重下才能覆盖）。
     */
    fun fetchAll(force: Boolean = false, codeOf: (ProjectEntry) -> String?) {
        val targets = projects().filter { codeOf(it) != null }
        if (targets.isEmpty()) {
            notify("没有找到带 RJ/VJ/BJ 编号的项目")
            return
        }
        scope.launch {
            var ok = 0
            var failed = 0
            var hiddenR18 = 0
            var firstError: String? = null
            targets.forEachIndexed { i, project ->
                val code = codeOf(project)!!.uppercase()
                if (!force && _works.value.containsKey(code)) {
                    ok++
                    return@forEachIndexed
                }
                setBusy("dlsite_batch", "DLsite 识别 " + (i + 1) + "/" + targets.size + "：" + code)
                val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
                val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    DlsiteClient.fetch(code, cookie)
                }
                outcome.onSuccess { work ->
                    val enriched = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        downloadCover(work, force = force)
                    }
                    put(enriched)
                    ok++
                    if (enriched.r18 && hideR18()) hiddenR18++
                }.onFailure {
                    failed++
                    if (firstError == null) firstError = it.message
                    AppLog.log("DLsite 识别失败：" + code + " · " + it.message)
                }
            }
            setBusy("dlsite_batch", null)
            notify(
                "DLsite 批量识别完成：成功 " + ok + "，失败 " + failed +
                    (firstError?.takeIf { failed > 0 }?.let { "（" + it + "）" } ?: "") +
                    (if (hiddenR18 > 0) "；其中 " + hiddenR18 + " 部 R18 因「隐藏 R18」设置暂不显示（设置里可关闭）" else ""),
            )
        }
    }

    /** 按编号抓一次公开信息并缓存。 */
    fun fetchOne(code: String) {
        scope.launch {
            setBusy("dlsite", "正在从 DLsite 获取 " + code + " …")
            AppLog.log("DLsite 识别开始：" + code + "（cookie " + (if (dlsiteCookieHeader().isNullOrBlank()) "无" else "有") + "）")
            val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                DlsiteClient.fetch(code, cookie)
            }
            setBusy("dlsite", null)
            outcome
                .onSuccess { work ->
                    // 用户手动点「重新识别」：封面也重下，才有机会覆盖掉历史上错的缓存
                    val withCover = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        downloadCover(work, force = true)
                    }
                    put(withCover)
                    AppLog.log("DLsite 识别成功：" + code + " · " + (withCover.title ?: "（无标题）"))
                    notify(
                        "已识别：" + (withCover.title ?: withCover.code) +
                            (if (withCover.owned) "（已购买）" else "") +
                            (if (withCover.r18 && hideR18()) "；该作品为 R18，因「隐藏 R18」设置暂不显示（设置里可关闭）" else ""),
                    )
                }
                .onFailure {
                    AppLog.log("DLsite 识别失败：" + code + " · " + it.message)
                    notify("获取失败：" + it.message)
                }
        }
    }

    /**
     * 把作品封面下载到应用内部，作为专辑封面。
     *
     * @param force true 时无视本地缓存重新下载（「重新识别」/「重抓全部头图」用）——
     *   之前把站内 banner 当成封面缓存下来的包，靠这个才能被覆盖掉。
     */
    private fun downloadCover(work: DlsiteWork, force: Boolean = false): DlsiteWork {
        val dir = java.io.File(app.filesDir, "dlsite-covers")
        val out = java.io.File(dir, work.code + ".jpg")
        val url = work.coverUrl
        if (url.isNullOrBlank()) {
            // 没有可用封面时清掉旧缓存，避免界面继续显示上一次（可能是错的）那张图
            runCatching { if (out.isFile) out.delete() }
            return work.copy(coverLocalPath = null)
        }
        val path = runCatching {
            dir.mkdirs()
            if (!force && out.isFile && out.length() > 0) return@runCatching out.absolutePath
            if (force) runCatching { out.delete() }
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
            }
            conn.inputStream.use { input -> out.outputStream().use { output -> input.copyTo(output) } }
            conn.disconnect()
            if (out.length() > 0) out.absolutePath else null
        }.getOrNull()
        return if (path == null) work.copy(coverLocalPath = null) else work.copy(coverLocalPath = path)
    }

    // ---------- 登录态 / 已购 ----------

    private val _loginState = MutableStateFlow(DlsiteLoginState.UNKNOWN)
    val loginState: StateFlow<DlsiteLoginState> = _loginState.asStateFlow()

    private val _purchases = MutableStateFlow<List<DlsitePurchase>>(emptyList())
    val purchases: StateFlow<List<DlsitePurchase>> = _purchases.asStateFlow()

    fun refreshLogin() {
        scope.launch {
            val state = withContext(kotlinx.coroutines.Dispatchers.IO) {
                DlsiteClient.verifyLogin(runCatching { dlsiteCookieHeader() }.getOrNull())
            }
            _loginState.value = state
            // 登录有效且还没有已购列表（首次使用或被清掉）时，静默同步一次，主页就能直接显示
            if (state == DlsiteLoginState.LOGGED_IN && _purchases.value.isEmpty()) {
                syncPurchases(manual = false)
            }
        }
    }

    /** @param manual 手动点「同步」时给提示；启动自动同步静默进行，失败不打扰。 */
    fun syncPurchases(manual: Boolean = true) {
        scope.launch {
            if (manual) setBusy("purchases", "正在同步已购作品…")
            val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                DlsiteClient.fetchPurchases(cookie)
            }
            if (manual) setBusy("purchases", null)
            outcome
                .onSuccess { list ->
                    _purchases.value = list
                    _loginState.value = DlsiteLoginState.LOGGED_IN
                    runCatching { store.savePurchases(list) }
                    if (manual) notify("已同步 " + list.size + " 部已购作品")
                    fetchPurchaseMeta()
                }
                .onFailure {
                    _loginState.value = DlsiteLoginState.LOGGED_OUT
                    if (manual) notify("同步失败：" + it.message)
                }
        }
    }

    fun logout() {
        runCatching { clearDlsiteSession() }
        _loginState.value = DlsiteLoginState.LOGGED_OUT
        _purchases.value = emptyList()
        runCatching { store.clearPurchases() }
        notify("已退出 DLsite")
    }

    /**
     * 给已购条目补抓元数据（标题之外的封面 / 社团 / 简介），让主页「已购未下载」
     * 直接带封面显示。串行小步跑，避免并发把 DLsite 打挂；本地已有识别结果的跳过。
     */
    private var metaFetching = false

    private fun fetchPurchaseMeta() {
        if (metaFetching) return
        metaFetching = true
        scope.launch {
            val targets = _purchases.value
                .filter { it.code.uppercase() !in _works.value.keys }
                .take(60)
            var ok = 0
            var failed = 0
            targets.forEachIndexed { i, item ->
                val code = item.code.uppercase()
                setBusy("purchase_meta", "DLsite 元数据 " + (i + 1) + "/" + targets.size + "：" + code)
                val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
                val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    DlsiteClient.fetch(code, cookie)
                }
                outcome.onSuccess { work ->
                    val enriched = withContext(kotlinx.coroutines.Dispatchers.IO) { downloadCover(work) }
                    put(enriched)
                    ok++
                }.onFailure { failed++ }
            }
            setBusy("purchase_meta", null)
            metaFetching = false
            if (targets.isNotEmpty()) {
                AppLog.log("DLsite 已购元数据补抓完成：成功 " + ok + "，失败 " + failed)
            }
        }
    }

    // ---------- 已下载标记（压缩包下了但没解压时也不能算「未下载」） ----------

    private val downloadedCodesFile = java.io.File(app.filesDir, "downloaded-codes.txt")

    private val _downloadedCodes = MutableStateFlow<Set<String>>(emptySet())
    val downloadedCodes: StateFlow<Set<String>> = _downloadedCodes.asStateFlow()

    fun loadDownloadedCodes() {
        runCatching {
            _downloadedCodes.value = if (downloadedCodesFile.isFile) {
                downloadedCodesFile.readLines().map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }
        }
    }

    private fun markDownloaded(code: String) {
        val merged = _downloadedCodes.value + code.uppercase()
        _downloadedCodes.value = merged
        runCatching { downloadedCodesFile.writeText(merged.joinToString("\n")) }
    }

    /** 已购里、本地还没有的作品。 */
    fun missingPurchases(): List<DlsitePurchase> {
        val local = projects().mapNotNull { it.code?.uppercase() }.toSet()
        val rest = _purchases.value.filter {
            val c = it.code.uppercase()
            c !in local && c !in _downloadedCodes.value
        }
        return if (purchaseAsmrOnly()) rest.filter { it.asmr } else rest
    }

    /** 已购里非音声的作品数量（用于提示被过滤掉多少）。 */
    fun skippedNonAsmr(): Int {
        val local = projects().mapNotNull { it.code?.uppercase() }.toSet()
        return _purchases.value.count { it.code.uppercase() !in local && !it.asmr }
    }

    // ---------- 站内在线搜索 ----------

    /** 在线搜索的状态：主页搜索切到「DLsite 在线」标签时使用。 */
    data class OnlineSearch(
        val loading: Boolean = false,
        val query: String = "",
        val hits: List<com.kiite.player.core.DlsiteSearchHit> = emptyList(),
        val error: String? = null,
    )

    private val _onlineSearch = MutableStateFlow(OnlineSearch())
    val onlineSearch: StateFlow<OnlineSearch> = _onlineSearch.asStateFlow()

    private var searchSeq = 0

    fun searchOnline(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            searchSeq++
            _onlineSearch.value = OnlineSearch()
            return
        }
        val seq = ++searchSeq
        _onlineSearch.value = OnlineSearch(loading = true, query = trimmed)
        scope.launch {
            val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                DlsiteClient.search(trimmed, cookie)
            }
            if (seq != searchSeq) return@launch
            _onlineSearch.value = outcome.fold(
                { hits ->
                    OnlineSearch(
                        query = trimmed,
                        hits = hits,
                        error = if (hits.isEmpty()) "DLsite 没有搜到相关作品" else null,
                    )
                },
                { OnlineSearch(query = trimmed, error = "在线搜索失败：" + it.message) },
            )
        }
    }

    // ---------- 应用内下载 ----------
    //
    // 下载本体在 [DlsiteDownloads]（应用级作用域）：退出下载页 / 切后台 / 锁屏都继续，
    // 通知栏由 [com.kiite.player.download.DownloadService] 统一挂。这里只做转发。

    private val downloads: DlsiteDownloader by lazy { DlsiteDownloads.get(app) }

    /** 当前 DLsite 下载状态（null = 空闲）。 */
    val downloadState: StateFlow<DlsiteDownloader.State?> = downloads.state

    /** 提示流（完成/失败），VM 收集后弹提示。 */
    val downloadEvents = downloads.events

    fun clearStatus() {
        downloads.clear()
    }

    /** 取消当前下载（连接立刻断开，半截文件删掉）。 */
    fun cancelDownload() = downloads.cancel()

    /** 下载的是压缩包，就地解压（复用已有的 zip 解压）。 */
    fun extractDownloaded() {
        val path = downloads.state.value?.savedPath ?: return
        scope.launch {
            // 每个下载的作品解压成曲库根目录下的独立一级文件夹，
            // 否则全落在 DLsite/ 里会被当成同一个总项目
            val root = rootPath()?.let { java.io.File(it) }
            val target = root?.let { java.io.File(it, java.io.File(path).nameWithoutExtension) }
            val outcome = runCatching { repo.extractZip(path, target) }
            notify(
                outcome.fold(
                    { n ->
                        rescan()
                        "已解压 " + n + " 个文件，正在重新扫描"
                    },
                    { "解压失败：" + it.message + "（rar/7z 需要外部解压工具）" },
                ),
            )
        }
    }

    /** 从下载页捕获到的直链：带登录 Cookie 拉取并写入本地（后台跑，可取消）。 */
    fun startDownload(url: String, userAgent: String?, contentDisposition: String?, code: String? = null) {
        // 直链可能指向第三方 CDN，绝不能把 DLsite 会话 Cookie 发过去
        val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            ?.takeIf { isDlsiteHost(url) }
        downloads.start(
            url = url,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            code = code,
            rootDir = downloadBase(),
            cookie = cookie,
            onCompleted = { c ->
                // 标记「已下载」，主页的「已购未下载」列表就不会再列它
                c?.let { markDownloaded(it) }
            },
        )
    }

    /**
     * 下载落盘位置：设置里自选的目录优先，其次曲库根目录。
     * 都会在下面再建一层 `DLsite/`（用户已经选到 DLsite 目录时不再套一层）。
     */
    private fun downloadBase(): String? =
        downloadDir()?.trim()?.takeIf { it.isNotEmpty() }
            ?: rootPath()?.trim()?.takeIf { it.isNotEmpty() }

    /** 只对 DLsite 自己（及其子域）附带登录态。 */
    private fun isDlsiteHost(url: String): Boolean = runCatching {
        val host = java.net.URL(url).host.lowercase()
        host == "dlsite.com" || host.endsWith(".dlsite.com")
    }.getOrDefault(false)
}
