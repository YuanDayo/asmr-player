package com.kiite.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.kiite.player.core.ChapterEntry
import com.kiite.player.core.ManualLinks
import com.kiite.player.core.ParsedScript
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.ProjectGrouper
import com.kiite.player.core.ScanResult
import com.kiite.player.core.ScriptAttachment
import com.kiite.player.core.ScriptFormat
import com.kiite.player.core.TagWriteResult
import com.kiite.player.core.TextUtils
import com.kiite.player.core.isImagePath
import com.kiite.player.core.stripFormatTokens
import com.kiite.player.core.isVideoPath
import com.kiite.player.core.TrackEntry
import com.kiite.player.data.AppSettings
import com.kiite.player.data.LibraryRepository
import com.kiite.player.data.SettingsStore
import com.kiite.player.playback.PlayerConnection
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUi(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val buffering: Boolean = false,
    val hasMedia: Boolean = false,
    val speed: Float = 1.0f,
)

data class ScriptUi(
    val attachment: ScriptAttachment? = null,
    val parsed: ParsedScript? = null,
    val loading: Boolean = false,
    val fromEmbedded: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository(app)
    private val settingsStore = SettingsStore(app)
    private val dlsiteStore = com.kiite.player.data.DlsiteStore(app)
    private val connection = PlayerConnection(app)

    private val _dlsite = MutableStateFlow<Map<String, com.kiite.player.core.DlsiteWork>>(emptyMap())
    val dlsite: StateFlow<Map<String, com.kiite.player.core.DlsiteWork>> = _dlsite.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _scan = MutableStateFlow<ScanResult?>(null)
    val scan: StateFlow<ScanResult?> = _scan.asStateFlow()

    /** 自动匹配的原始结果；手动指定始终在它之上重算，清除后能正确回落。 */
    private var rawScan: ScanResult? = null

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _selectedProject = MutableStateFlow<String?>(null)
    val selectedProject: StateFlow<String?> = _selectedProject.asStateFlow()

    private val _manualLinks = MutableStateFlow<Map<String, String>>(emptyMap())
    val manualLinks: StateFlow<Map<String, String>> = _manualLinks.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackEntry?>(null)
    val currentTrack: StateFlow<TrackEntry?> = _currentTrack.asStateFlow()

    private val _player = MutableStateFlow(PlayerUi())
    val player: StateFlow<PlayerUi> = _player.asStateFlow()

    private val _script = MutableStateFlow(ScriptUi())
    val script: StateFlow<ScriptUi> = _script.asStateFlow()

    /** 当前曲目的封面：优先项目文件夹里的封面图，其次音频内嵌封面。 */
    private val _currentCover = MutableStateFlow<String?>(null)
    val currentCover: StateFlow<String?> = _currentCover.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val message = _message.asSharedFlow()

    private var controller: MediaController? = null

    // 注意：init 块放在类体最后（文件末尾）。Kotlin 按声明顺序初始化属性，
    // 如果 init 写在这里，loadRatings() / loadDownloadedCodes() 会访问到尚未初始化的
    // 属性而静默失败（表现就是：手动分级、已下载标记重启后丢失）。

    // ---------- DLsite ----------

    /** 项目对应的作品编号（RJ/VJ/BJ）。 */
    fun dlsiteCodeOf(project: ProjectEntry): String? = project.code ?: TextUtils.workCode(project.name)

    /** 该项目的专辑封面：优先用 DLsite 抓到的封面，其次本地封面。 */
    fun dlsiteCoverOf(projectPath: String): String? =
        dlsiteOf(projectPath)?.coverLocalPath?.takeIf { java.io.File(it).isFile }

    /** 已有识别结果的项目数。 */
    val dlsiteCount: Int get() = _dlsite.value.size

    /** 批量识别所有带 RJ 编号的项目。 */
    fun fetchDlsiteForAll() {
        val projects = _scan.value?.projects.orEmpty().filter { dlsiteCodeOf(it) != null }
        if (projects.isEmpty()) {
            say("没有找到带 RJ/VJ/BJ 编号的项目")
            return
        }
        viewModelScope.launch {
            var ok = 0
            var failed = 0
            projects.forEachIndexed { i, project ->
                val code = dlsiteCodeOf(project)!!.uppercase()
                if (_dlsite.value.containsKey(code)) { ok++; return@forEachIndexed }
                _busy.value = "DLsite 识别 " + (i + 1) + "/" + projects.size + "：" + code
                val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
                val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.kiite.player.data.DlsiteClient.fetch(code, cookie)
                }
                outcome.onSuccess { work ->
                    val enriched = withContext(kotlinx.coroutines.Dispatchers.IO) { downloadCover(work) }
                    val merged = _dlsite.value + (enriched.code to enriched)
                    _dlsite.value = merged
                    runCatching { dlsiteStore.save(merged) }
                    ok++
                }.onFailure { failed++ }
            }
            _busy.value = null
            say("DLsite 批量识别完成：成功 " + ok + "，失败 " + failed)
        }
    }

    /** 把作品封面下载到应用内部，作为专辑封面。 */
    private fun downloadCover(work: com.kiite.player.core.DlsiteWork): com.kiite.player.core.DlsiteWork {
        val url = work.coverUrl ?: return work
        val path = runCatching {
            val dir = java.io.File(getApplication<Application>().filesDir, "dlsite-covers").apply { mkdirs() }
            val out = java.io.File(dir, work.code + ".jpg")
            if (out.isFile && out.length() > 0) return@runCatching out.absolutePath
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
            }
            conn.inputStream.use { input -> out.outputStream().use { output -> input.copyTo(output) } }
            conn.disconnect()
            if (out.length() > 0) out.absolutePath else null
        }.getOrNull()
        return if (path == null) work else work.copy(coverLocalPath = path)
    }

    fun dlsiteOf(projectPath: String): com.kiite.player.core.DlsiteWork? =
        dlsiteOfCode(projectPath)?.let { _dlsite.value[it] }

    private fun dlsiteOfCode(projectPath: String): String? {
        val project = _scan.value?.projects?.firstOrNull { it.path == projectPath } ?: return null
        return dlsiteCodeOf(project)?.uppercase()
    }

    /** 按编号抓 DLsite 公开信息并缓存。 */
    /** 登录态：必须联网验证过才算已登录，绝不只看 cookie。 */
    private val _dlsiteLoginState = MutableStateFlow(com.kiite.player.core.DlsiteLoginState.UNKNOWN)
    val dlsiteLoginState: StateFlow<com.kiite.player.core.DlsiteLoginState> = _dlsiteLoginState.asStateFlow()

    /** 已购作品（需登录后同步）。 */
    private val _purchases = MutableStateFlow<List<com.kiite.player.core.DlsitePurchase>>(emptyList())
    val purchases: StateFlow<List<com.kiite.player.core.DlsitePurchase>> = _purchases.asStateFlow()

    fun refreshDlsiteLogin() {
        viewModelScope.launch {
            val state = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.kiite.player.data.DlsiteClient.verifyLogin(
                    runCatching { dlsiteCookieHeader() }.getOrNull(),
                )
            }
            _dlsiteLoginState.value = state
        }
    }

    /** 同步已购作品列表。 */
    fun syncPurchases() {
        viewModelScope.launch {
            _busy.value = "正在同步已购作品…"
            val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.kiite.player.data.DlsiteClient.fetchPurchases(cookie)
            }
            _busy.value = null
            outcome
                .onSuccess { list ->
                    _purchases.value = list
                    _dlsiteLoginState.value = com.kiite.player.core.DlsiteLoginState.LOGGED_IN
                    say("已同步 " + list.size + " 部已购作品")
                }
                .onFailure {
                    _dlsiteLoginState.value = com.kiite.player.core.DlsiteLoginState.LOGGED_OUT
                    say("同步失败：" + it.message)
                }
        }
    }

    /** 已经下载过的编号（下载的是压缩包，还没解压时也不能算「未下载」）。 */
    private val downloadedCodesFile = java.io.File(getApplication<Application>().filesDir, "downloaded-codes.txt")
    private val _downloadedCodes = MutableStateFlow<Set<String>>(emptySet())
    val downloadedCodes: StateFlow<Set<String>> = _downloadedCodes.asStateFlow()

    private fun loadDownloadedCodes() {
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
    fun missingPurchases(): List<com.kiite.player.core.DlsitePurchase> {
        val local = _scan.value?.projects.orEmpty().mapNotNull { it.code?.uppercase() }.toSet()
        val rest = _purchases.value.filter {
            val c = it.code.uppercase()
            c !in local && c !in _downloadedCodes.value
        }
        return if (_settings.value.purchaseAsmrOnly) rest.filter { it.asmr } else rest
    }

    /** 已购里非音声的作品数量（用于提示被过滤掉多少）。 */
    fun skippedNonAsmr(): Int {
        val local = _scan.value?.projects.orEmpty().mapNotNull { it.code?.uppercase() }.toSet()
        return _purchases.value.count { it.code.uppercase() !in local && !it.asmr }
    }

    fun setPurchaseAsmrOnly(v: Boolean) = viewModelScope.launch { settingsStore.setPurchaseAsmrOnly(v) }

    // ---------- DLsite 应用内下载 ----------

    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    /** 刚下载完的文件，用于「立即解压」与跳转解压工具。 */
    private val _downloadedFile = MutableStateFlow<String?>(null)
    val downloadedFile: StateFlow<String?> = _downloadedFile.asStateFlow()

    /** 正在下载：用来立刻用进度面板顶掉 WebView，避免黑屏。 */
    private val _downloadActive = MutableStateFlow(false)
    val downloadActive: StateFlow<Boolean> = _downloadActive.asStateFlow()

    fun clearDownloadStatus() {
        _downloadStatus.value = null
        _downloadedFile.value = null
        _downloadActive.value = false
    }

    /** 下载的是压缩包，就地解压（复用已有的 zip 解压）。 */
    fun extractDownloaded() {
        val path = _downloadedFile.value ?: return
        viewModelScope.launch {
            _downloadStatus.value = "正在解压…"
            // 每个下载的作品解压成曲库根目录下的独立一级文件夹，
            // 否则全落在 DLsite/ 里会被当成同一个总项目
            val root = _settings.value.rootPath?.let { java.io.File(it) }
            val target = root?.let { java.io.File(it, java.io.File(path).nameWithoutExtension) }
            val outcome = runCatching { repo.extractZip(path, target) }
            _downloadStatus.value = outcome.fold(
                { n ->
                    rescan()
                    "已解压 " + n + " 个文件，正在重新扫描"
                },
                { "解压失败：" + it.message + "（rar/7z 需要外部解压工具）" },
            )
        }
    }

    /** 从下载页捕获到的直链：带登录 Cookie 拉取并写入本地。 */
    fun startDlsiteDownload(url: String, userAgent: String?, contentDisposition: String?, code: String? = null) {
        viewModelScope.launch {
            _downloadActive.value = true
            _downloadStatus.value = "正在下载…"
            // 直链可能指向第三方 CDN，绝不能把 DLsite 会话 Cookie 发过去
            val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
                ?.takeIf { isDlsiteHost(url) }
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val base = _settings.value.rootPath
                        ?: android.os.Environment
                            .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                            .absolutePath
                    val dir = java.io.File(base, "DLsite").apply { mkdirs() }
                    val out = java.io.File(dir, guessFileName(contentDisposition, url))
                    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 120_000
                        instanceFollowRedirects = true
                        if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                        if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
                    }
                    val total = runCatching { conn.contentLengthLong }.getOrDefault(-1L)
                    var read = 0L
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                output.write(buf, 0, n)
                                read += n
                                if (read / (2 * 1024 * 1024) != (read - n) / (2 * 1024 * 1024)) {
                                    _downloadStatus.value = "正在下载… " + (read / 1024 / 1024) + " MB" +
                                        if (total > 0) " / " + (total / 1024 / 1024) + " MB" else ""
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    out
                }
            }
            outcome.onSuccess {
                _downloadedFile.value = it.absolutePath
                code?.let { c -> markDownloaded(c) }
            }
            _downloadActive.value = false
            _downloadStatus.value = outcome.fold(
                { file ->
                    "已保存：" + file.name + "（" + (file.length() / 1024 / 1024) + " MB）"
                },
                { "下载失败：" + it.message },
            )
        }
    }

    /** 只对 DLsite 自己（及其子域）附带登录态。 */
    private fun isDlsiteHost(url: String): Boolean = runCatching {
        val host = java.net.URL(url).host.lowercase()
        host == "dlsite.com" || host.endsWith(".dlsite.com")
    }.getOrDefault(false)

    private fun guessFileName(disposition: String?, url: String): String {
        val fromHeader = disposition
            ?.substringAfter("filename=", "")
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() && it.contains('.') }
        if (fromHeader != null) return fromHeader
        val seg = url.substringBefore('?').substringAfterLast('/')
        return if (seg.isNotBlank() && seg.contains('.')) seg else "dlsite-download.zip"
    }

    // ---------- 社团 / 标签 / 声优 ----------

    fun knownCircles(): List<String> =
        _dlsite.value.values.mapNotNull { it.circle }.filter { it.isNotBlank() }.distinct().sorted()

    fun knownTags(): List<String> =
        _dlsite.value.values.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()

    fun knownVoiceActors(): List<String> =
        _dlsite.value.values.flatMap { it.voiceActors }.filter { it.isNotBlank() }.distinct().sorted()

    fun setDownloadDir(path: String?) = viewModelScope.launch { settingsStore.setDownloadDir(path) }

    // ---------- 分级（成人 / 全年龄） ----------

    private val ratingStore = com.kiite.player.data.RatingStore(app)
    private val _manualRatings = MutableStateFlow<Map<String, String>>(emptyMap())
    val manualRatings: StateFlow<Map<String, String>> = _manualRatings.asStateFlow()

    fun loadRatings() {
        runCatching { _manualRatings.value = ratingStore.load() }
    }

    /**
     * 作品级的稳定键：有 RJ 编号就用编号（跨设备/改名都稳），
     * 没有编号就用项目路径 —— 否则没有编号的文件夹根本没法单独标记。
     * 手动分级与自定义标签都用这个键。
     */
    private fun projectKey(project: ProjectEntry): String =
        dlsiteCodeOf(project)?.uppercase() ?: project.path

    fun ratingOf(project: ProjectEntry): com.kiite.player.core.WorkRating {
        _manualRatings.value[projectKey(project)]?.let { return com.kiite.player.core.WorkRating.of(it) }
        val code = dlsiteCodeOf(project)?.uppercase()
        if (code != null) {
            _dlsite.value[code]?.let {
                return if (it.r18) com.kiite.player.core.WorkRating.R18 else com.kiite.player.core.WorkRating.ALL
            }
        }
        return com.kiite.player.core.WorkRating.UNKNOWN
    }

    fun setRating(project: ProjectEntry, rating: com.kiite.player.core.WorkRating) {
        val key = projectKey(project)
        val merged = _manualRatings.value + (key to rating.id)
        _manualRatings.value = merged
        ratingStore.save(merged)
        com.kiite.player.data.AppLog.log("分级：" + project.name + " → " + rating.label)
        say("已标记为「" + rating.label + "」")
    }

    // ---------- 自定义标签（作品级，可多个） ----------

    private val tagStore = com.kiite.player.data.TagStore(app)
    private val _manualTags = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val manualTags: StateFlow<Map<String, List<String>>> = _manualTags.asStateFlow()

    fun loadTags() {
        runCatching { _manualTags.value = tagStore.load() }
    }

    /** 某个作品的自定义标签。 */
    fun tagsOf(project: ProjectEntry): List<String> = _manualTags.value[projectKey(project)].orEmpty()

    fun setTags(project: ProjectEntry, tags: List<String>) {
        val key = projectKey(project)
        val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val merged = if (clean.isEmpty()) _manualTags.value - key else _manualTags.value + (key to clean)
        _manualTags.value = merged
        tagStore.save(merged)
        com.kiite.player.data.AppLog.log(
            "标签：" + project.name + " → " + if (clean.isEmpty()) "（清空）" else clean.joinToString("/"),
        )
    }

    /** 全库用过的自定义标签，供筛选与推荐。 */
    fun knownCustomTags(): List<String> =
        _manualTags.value.values.flatten().distinct().sorted()

    fun setSortMode(mode: String) = viewModelScope.launch { settingsStore.setSortMode(mode) }
    fun setSortAsc(asc: Boolean) = viewModelScope.launch { settingsStore.setSortAsc(asc) }

    fun dlsiteLogout() {
        runCatching { clearDlsiteSession() }
        _dlsiteLoginState.value = com.kiite.player.core.DlsiteLoginState.LOGGED_OUT
        _purchases.value = emptyList()
        say("已退出 DLsite")
    }

    fun fetchDlsite(projectPath: String) {
        val code = dlsiteOfCode(projectPath) ?: run { say("这个项目没有识别到 RJ 编号"); return }
        viewModelScope.launch {
            _busy.value = "正在从 DLsite 获取 " + code + " …"
            com.kiite.player.data.AppLog.log("DLsite 识别开始：" + code + "（cookie " + (if (dlsiteCookieHeader().isNullOrBlank()) "无" else "有") + "）")
            val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.kiite.player.data.DlsiteClient.fetch(code, cookie)
            }
            _busy.value = null
            outcome
                .onSuccess { work ->
                    val withCover = withContext(kotlinx.coroutines.Dispatchers.IO) { downloadCover(work) }
                    val merged = _dlsite.value + (withCover.code to withCover)
                    _dlsite.value = merged
                    runCatching { dlsiteStore.save(merged) }
                    com.kiite.player.data.AppLog.log("DLsite 识别成功：" + code + " · " + (withCover.title ?: "（无标题）"))
                    say("已识别：" + (withCover.title ?: withCover.code) + if (withCover.owned) "（已购买）" else "")
                }
                .onFailure {
                    com.kiite.player.data.AppLog.log("DLsite 识别失败：" + code + " · " + it.message)
                    say("获取失败：" + it.message)
                }
        }
    }

    private fun attachListener(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = updatePosition()
            override fun onPlaybackStateChanged(playbackState: Int) = updatePosition()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId
                val track = _scan.value?.tracks?.firstOrNull { it.path == id }
                _activePath.value = id
                if (track != null) {
                    _currentTrack.value = track
                    loadScriptFor(track)
                    resolveCover(track)
                }
                updatePosition()
            }
        })
        updatePosition()
    }

    private fun updatePosition() {
        val c = controller ?: return
        _player.value = PlayerUi(
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = if (c.duration > 0) c.duration else 0L,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            hasMedia = c.mediaItemCount > 0,
            speed = c.playbackParameters.speed,
        )
    }

    /** 播放倍数：0.5x – 3.0x，立即生效并持久化。 */
    fun setSpeed(speed: Float) {
        val value = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        viewModelScope.launch { settingsStore.setPlaybackSpeed(value) }
        controller?.setPlaybackSpeed(value)
        updatePosition()
    }

    // ---------- 曲库 ----------

    fun setRoot(path: String?) {
        viewModelScope.launch {
            settingsStore.setRootPath(path)
            if (path == null) {
                _scan.value = null
                rawScan = null
                _selectedProject.value = null
            } else {
                rescan()
            }
        }
    }

    fun rescan() {
        val root = _settings.value.rootPath?.let(::File) ?: return
        if (!root.isDirectory) {
            say("根目录不存在或不可读")
            return
        }
        viewModelScope.launch {
            _scanning.value = true
            _busy.value = "正在扫描…"
            val outcome = runCatching { repo.scan(root) }
            _scanning.value = false
            _busy.value = null
            outcome
                .onSuccess { result ->
                    rawScan = result
                    val merged = withManualLinks(result, _manualLinks.value)
                    _scan.value = merged
                    com.kiite.player.data.AppLog.log(
                        "扫描完成：项目 " + merged.projectCount + "，音频 " + merged.trackCount +
                            "，已配台本 " + merged.withScriptCount + "，压缩包 " + merged.archiveCount,
                    )
                    say("扫描完成：${merged.trackCount} 首音频，${merged.withScriptCount} 首匹配到台本")
                    applyPendingRestore()
                    if (_settings.value.embedOnImport && merged.withScriptCount > 0) {
                        say("已开启自动写入，开始把台本写进音频标签…")
                        embedAll()
                    }
                }
                .onFailure {
                    com.kiite.player.data.AppLog.log("扫描失败：" + it.message)
                    say("扫描失败：${it.message}")
                }
        }
    }

    fun selectProject(path: String?) {
        _selectedProject.value = path
    }

    fun tracksOf(folderPath: String?): List<TrackEntry> {
        val tracks = _scan.value?.tracks ?: return emptyList()
        return if (folderPath == null) tracks else tracks.filter { it.folderPath == folderPath }
    }

    fun tracksOfProject(projectPath: String): List<TrackEntry> {
        val scan = _scan.value ?: return emptyList()
        return scan.tracks.filter { ProjectGrouper.projectRootOf(scan.rootPath, it.folderPath) == projectPath }
    }

    /** 某个总项目里未解压的压缩包。 */
    fun archivesOfProject(projectPath: String): List<com.kiite.player.core.ArchiveEntry> {
        val scan = _scan.value ?: return emptyList()
        return scan.archives.filter {
            ProjectGrouper.projectRootOf(scan.rootPath, it.folderPath) == projectPath
        }
    }

    /** 解压 zip 并重新扫描。 */
    fun extractArchive(archivePath: String) {
        viewModelScope.launch {
            _busy.value = "正在解压…"
            val outcome = runCatching { repo.extractZip(archivePath) }
            _busy.value = null
            outcome
                .onSuccess { n ->
                    say("已解压 " + n + " 个文件，正在重新扫描…")
                    rescan()
                }
                .onFailure { say("解压失败：" + it.message) }
        }
    }

    /** 项目内的章节分组：章节 → 该章节的曲目。 */
    fun chaptersOf(project: ProjectEntry): List<Pair<ChapterEntry, List<TrackEntry>>> {
        val all = tracksOfProject(project.path)
        return project.chapters
            .map { ch -> ch to all.filter { it.folderPath == ch.path } }
            .filter { it.second.isNotEmpty() }
    }

    // ---------- 播放 ----------

    fun playTrack(track: TrackEntry, queue: List<TrackEntry> = emptyList()) {
        viewModelScope.launch {
            val c = controller ?: connection.connect().also { controller = it; attachListener(it) }
            val items = (if (queue.isEmpty()) listOf(track) else queue).map { toMediaItem(it) }
            val index = (if (queue.isEmpty()) listOf(track) else queue).indexOfFirst { it.path == track.path }
                .coerceAtLeast(0)
            c.setMediaItems(items, index, 0L)
            c.setPlaybackSpeed(_settings.value.playbackSpeed)
            c.prepare()
            c.play()
            _currentTrack.value = track
            _activePath.value = track.path
            loadScriptFor(track)
            resolveCover(track)
        }
    }

    private fun toMediaItem(track: TrackEntry, path: String = track.path): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.fromFile(File(path)))
            .setMediaId(path)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.baseName)
                    .setArtist(track.folderName)
                    .setAlbumTitle(track.folderName)
                    .build(),
            )
            .build()

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f

        /** 播放页可选的倍数档位。 */
        val SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    }

    private val _videoFullscreen = MutableStateFlow(false)
    val videoFullscreen: StateFlow<Boolean> = _videoFullscreen.asStateFlow()

    fun setVideoFullscreen(v: Boolean) {
        _videoFullscreen.value = v
    }

    /** 从还活着的播放器里恢复当前曲目（退出又进来时用）。 */
    private var pendingRestorePath: String? = null

    private fun restoreFromController(c: MediaController) {
        if (c.mediaItemCount == 0) return
        val path = c.currentMediaItem?.mediaId ?: return
        pendingRestorePath = path
        applyPendingRestore()
    }

    /** 需要等曲库扫描完成才能按路径找到曲目，所以扫描完成后也要调一次。 */
    private fun applyPendingRestore() {
        val path = pendingRestorePath ?: return
        val track = _scan.value?.tracks?.firstOrNull { t ->
            t.path == path || t.altPaths.contains(path)
        } ?: return
        pendingRestorePath = null
        _currentTrack.value = track
        _activePath.value = path
        loadScriptFor(track)
        resolveCover(track)
    }

    // ---------- 定时停止播放 ----------

    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()
    private var sleepJob: kotlinx.coroutines.Job? = null

    /** seconds 为 null 或 <= 0 表示取消定时。到点后暂停播放，不改变曲目。 */
    fun setSleepTimer(seconds: Long?) {
        sleepJob?.cancel()
        sleepJob = null
        if (seconds == null || seconds <= 0L) {
            _sleepRemainingMs.value = null
            return
        }
        val total = seconds * 1_000L
        _sleepRemainingMs.value = total
        sleepJob = viewModelScope.launch {
            var left = total
            while (left > 0) {
                kotlinx.coroutines.delay(1_000L)
                left -= 1_000L
                _sleepRemainingMs.value = left.coerceAtLeast(0L)
            }
            runCatching { controller?.pause() }
            _sleepRemainingMs.value = null
            say("定时结束，已暂停播放")
        }
    }

    private val _update = MutableStateFlow<com.kiite.player.data.UpdateInfo?>(null)
    val update: StateFlow<com.kiite.player.data.UpdateInfo?> = _update.asStateFlow()

    fun checkUpdate() {
        viewModelScope.launch {
            _busy.value = "正在检查更新…"
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.kiite.player.data.UpdateChecker.checkVerbose()
            }
            _busy.value = null
            when {
                result.error != null -> say("检查更新失败：" + result.error)
                result.info == null -> say("已是最新版本 v" + com.kiite.player.AppInfo.VERSION_NAME)
                else -> _update.value = result.info
            }
        }
    }

    fun dismissUpdate() { _update.value = null }

    /** 供视频画面（PlayerView）挂载用。 */
    val playerForView: Player? get() = controller

    /** 当前在播的是不是视频。 */
    fun currentIsVideo(): Boolean {
        val p = activePath.value ?: _currentTrack.value?.path ?: return false
        return isVideoPath(p)
    }

    /** 当前作品里的图片：曲目所在文件夹 + 作品根目录。 */
    fun imagesOfCurrent(): List<String> {
        val t = _currentTrack.value ?: return emptyList()
        val scan = _scan.value
        val projectRoot = scan?.let { ProjectGrouper.projectRootOf(it.rootPath, t.folderPath) }
        val dirs = listOfNotNull(t.folderPath.takeIf { it.isNotBlank() }, projectRoot).distinct()
        val out = ArrayList<String>()
        for (d in dirs) {
            val dir = java.io.File(d)
            if (!dir.isDirectory) continue
            dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                if (f.isFile && isImagePath(f.name)) out.add(f.absolutePath)
            }
        }
        return out.distinct()
    }

    /** 当前实际在播的文件（同名多格式时用来标出选中的那个）。 */
    private val _activePath = MutableStateFlow<String?>(null)
    val activePath: StateFlow<String?> = _activePath.asStateFlow()

    fun variantsOfCurrent(): List<String> {
        val t = _currentTrack.value ?: return emptyList()
        val base = listOf(t.path) + t.altPaths
        // 兜底：扫描阶段没合上的（比如两边文件名写法不同），播放时再按「同作品 + 同名」找一次
        val scan = _scan.value ?: return base
        val root = scan.rootPath
        val project = ProjectGrouper.projectRootOf(root, t.folderPath)
        val target = normName(t.path)
        val extras = scan.tracks
            .filter { it.path != t.path }
            .filter { ProjectGrouper.projectRootOf(root, it.folderPath) == project }
            .filter { normName(it.path) == target }
            .map { it.path }
        return (base + extras).distinct()
    }

    /**
     * 变体显示标签。分类名各不相同就带上分类（se无 · MP3），
     * 否则只显示格式（MP3），避免出现一堆无意义的「音频 · MP3」。
     */
    fun variantLabels(): List<Pair<String, String>> {
        val paths = variantsOfCurrent()
        val cats = paths.map { p ->
            stripFormatTokens(java.io.File(p).parentFile?.name ?: "")
        }
        val distinct = cats.filter { it.isNotBlank() }.distinct()
        val useCat = distinct.size == cats.size && distinct.isNotEmpty()
        return paths.mapIndexed { i, p ->
            val ext = p.substringAfterLast('.', "").uppercase()
            p to if (useCat && cats[i].isNotBlank()) cats[i] + " · " + ext else ext
        }
    }

    /** 归一化文件名：小写 + 去掉空格/下划线/连字符，避免「01 track」与「01_track」对不上。 */
    private fun normName(path: String): String =
        java.io.File(path).nameWithoutExtension.lowercase().replace(Regex("[\\s_\\-]+"), "")

    /** 同名不同格式之间切换，保持播放位置。 */
    fun switchVariant(path: String) {
        val base = _currentTrack.value ?: return
        val c = controller ?: return
        if (c.currentMediaItem?.mediaId == path) return
        val pos = c.currentPosition
        val wasPlaying = c.isPlaying
        val idx = c.currentMediaItemIndex
        c.replaceMediaItem(idx, toMediaItem(base, path))
        c.seekTo(idx, pos)
        if (wasPlaying) c.play()
        _activePath.value = path
        updatePosition()
        say("已切换到 " + path.substringAfterLast('.').uppercase())
    }

    fun setImmersive(value: Boolean) = viewModelScope.launch { settingsStore.setImmersive(value) }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
        updatePosition()
    }

    fun next() {
        controller?.seekToNextMediaItem()
        updatePosition()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
        updatePosition()
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, maxOf(0L, c.duration)))
        updatePosition()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        updatePosition()
    }

    fun stop() {
        controller?.stop()
        _currentTrack.value = null
        _script.value = ScriptUi()
        _currentCover.value = null
        updatePosition()
    }

    // ---------- 封面 ----------

    private fun resolveCover(track: TrackEntry?) {
        if (track == null) {
            _currentCover.value = null
            return
        }
        val scan = _scan.value
        val pp = scan?.let { s -> ProjectGrouper.projectRootOf(s.rootPath, track.folderPath) }
        // DLsite 封面优先（已下载到本地）
        val dlsiteCover = pp?.let { dlsiteCoverOf(it) }
        if (dlsiteCover != null) {
            _currentCover.value = dlsiteCover
            return
        }
        val projectCover = scan?.let { s ->
            s.projects.firstOrNull { it.path == pp }?.coverPath
        }
        if (projectCover != null) {
            _currentCover.value = projectCover
            return
        }
        viewModelScope.launch {
            _currentCover.value = repo.artworkFile(track.path)?.absolutePath
        }
    }

    // ---------- 播放列表 ----------

    /** 当前曲目所属的总项目。 */
    fun currentProject(): ProjectEntry? {
        val track = _currentTrack.value ?: return null
        val scan = _scan.value ?: return null
        val pp = ProjectGrouper.projectRootOf(scan.rootPath, track.folderPath)
        return scan.projects.firstOrNull { it.path == pp }
    }

    /** 播放列表 = 当前项目内的全部音频（跨章节、跨格式统合）。 */
    fun currentPlaylist(): List<TrackEntry> {
        val project = currentProject() ?: return emptyList()
        return tracksOfProject(project.path)
    }

    fun currentPlaylistIndex(): Int {
        val track = _currentTrack.value ?: return -1
        return currentPlaylist().indexOfFirst { it.path == track.path }
    }

    fun playFromPlaylist(index: Int) {
        val list = currentPlaylist()
        val track = list.getOrNull(index) ?: return
        playTrack(track, list)
    }

    // ---------- 台本 ----------

    private fun loadScriptFor(track: TrackEntry) {
        val att = track.primaryScript
        if (att != null) {
            loadAttachment(att)
        } else {
            _script.value = ScriptUi(loading = true)
            viewModelScope.launch {
                val embedded = repo.readEmbeddedLyrics(track.path)
                _script.value = if (embedded != null) {
                    ScriptUi(
                        parsed = ParsedScript(ScriptFormat.TXT, embedded),
                        fromEmbedded = true,
                        loading = false,
                    )
                } else {
                    ScriptUi(loading = false)
                }
            }
        }
    }

    fun loadAttachment(att: ScriptAttachment) {
        _script.value = ScriptUi(attachment = att, loading = true)
        viewModelScope.launch {
            val parsed = repo.parseScript(att.scriptPath)
            if (parsed.isEmpty) {
                val track = _currentTrack.value
                val embedded = track?.let { repo.readEmbeddedLyrics(it.path) }
                _script.value = if (embedded != null) {
                    ScriptUi(parsed = ParsedScript(ScriptFormat.TXT, embedded), fromEmbedded = true)
                } else {
                    ScriptUi(attachment = att, parsed = parsed, error = parsed.warning ?: "台本为空")
                }
            } else {
                _script.value = ScriptUi(attachment = att, parsed = parsed)
            }
        }
    }

    fun loadEmbeddedLyrics() {
        val track = _currentTrack.value ?: return
        _script.value = ScriptUi(loading = true)
        viewModelScope.launch {
            val embedded = repo.readEmbeddedLyrics(track.path)
            _script.value = if (embedded != null) {
                ScriptUi(parsed = ParsedScript(ScriptFormat.TXT, embedded), fromEmbedded = true)
            } else {
                ScriptUi(error = "这首音频里没有嵌入台本")
            }
        }
    }

    // ---------- 手动指定台本 ----------

    fun assignScript(trackPath: String, scriptPath: String) {
        viewModelScope.launch {
            repo.saveManualLink(trackPath, scriptPath)
            _manualLinks.value = _manualLinks.value + (trackPath to scriptPath)
            reapplyManualLinks()
            refreshCurrentTrackScript(trackPath)
        }
    }

    fun clearScript(trackPath: String) {
        viewModelScope.launch {
            repo.clearManualLink(trackPath)
            _manualLinks.value = _manualLinks.value - trackPath
            reapplyManualLinks()
            refreshCurrentTrackScript(trackPath)
        }
    }

    private fun withManualLinks(scan: ScanResult, links: Map<String, String>): ScanResult {
        val tracks = ManualLinks.apply(scan.tracks, links)
        return scan.copy(
            tracks = tracks,
            projects = ProjectGrouper.group(scan.rootPath, tracks, scan.archives),
        )
    }

    private fun reapplyManualLinks() {
        val raw = rawScan ?: return
        _scan.value = withManualLinks(raw, _manualLinks.value)
    }

    private fun refreshCurrentTrackScript(trackPath: String) {
        if (_currentTrack.value?.path != trackPath) return
        val updated = _scan.value?.tracks?.firstOrNull { it.path == trackPath }
        if (updated != null) {
            _currentTrack.value = updated
            loadScriptFor(updated)
        }
    }

    // ---------- 写入标签 ----------

    fun embedCurrent() {
        val track = _currentTrack.value ?: return
        val att = _script.value.attachment ?: track.primaryScript
        viewModelScope.launch {
            _busy.value = "正在写入标签…"
            val result = if (att != null) {
                repo.embedFromScript(track.path, att.scriptPath)
            } else {
                val text = _script.value.parsed?.plainText
                if (text.isNullOrBlank()) TagWriteResult.Failed("没有可写入的台本") else repo.embed(track.path, text)
            }
            _busy.value = null
            say(describe(result))
        }
    }

    fun embedProject(projectPath: String) {
        val tracks = tracksOfProject(projectPath).filter { it.hasScript }
        if (tracks.isEmpty()) {
            say("该项目没有可写入的台本")
            return
        }
        viewModelScope.launch {
            var ok = 0
            var skipped = 0
            var failed = 0
            tracks.forEachIndexed { i, track ->
                _busy.value = "写入标签 ${i + 1}/${tracks.size}…"
                val att = track.primaryScript
                if (att == null) { skipped++; return@forEachIndexed }
                when (repo.embedFromScript(track.path, att.scriptPath)) {
                    is TagWriteResult.Written -> ok++
                    is TagWriteResult.Unsupported -> skipped++
                    is TagWriteResult.Failed -> failed++
                }
            }
            _busy.value = null
            say("写入完成：成功 $ok，跳过 $skipped，失败 $failed")
        }
    }

    fun embedAll() {
        viewModelScope.launch {
            val tracks = (_scan.value?.tracks ?: emptyList()).filter { it.hasScript }
            if (tracks.isEmpty()) { say("没有可写入的台本"); return@launch }
            var ok = 0; var skipped = 0; var failed = 0
            tracks.forEachIndexed { i, track ->
                _busy.value = "写入标签 ${i + 1}/${tracks.size}…"
                val att = track.primaryScript ?: run { skipped++; return@forEachIndexed }
                when (repo.embedFromScript(track.path, att.scriptPath)) {
                    is TagWriteResult.Written -> ok++
                    is TagWriteResult.Unsupported -> skipped++
                    is TagWriteResult.Failed -> failed++
                }
            }
            _busy.value = null
            say("全库写入完成：成功 $ok，跳过 $skipped，失败 $failed")
        }
    }

    private fun describe(result: TagWriteResult): String = when (result) {
        is TagWriteResult.Written -> "已写入 ${result.format} 标签"
        is TagWriteResult.Unsupported -> "该音频格式暂不支持写入标签（支持 mp3 / flac / m4a）"
        is TagWriteResult.Failed -> "写入失败：${result.message}"
    }

    // ---------- 设置 ----------

    fun setScanOnStart(v: Boolean) = viewModelScope.launch { settingsStore.setScanOnStart(v) }
    fun setEmbedOnImport(v: Boolean) = viewModelScope.launch { settingsStore.setEmbedOnImport(v) }
    fun setAutoScroll(v: Boolean) = viewModelScope.launch { settingsStore.setAutoScroll(v) }
    fun setScrollSpeed(v: Float) = viewModelScope.launch { settingsStore.setScrollSpeed(v) }
    fun setFontScale(v: Float) = viewModelScope.launch { settingsStore.setFontScale(v) }
    fun setThemeMode(v: String) = viewModelScope.launch { settingsStore.setThemeMode(v) }
    fun setSkinStyle(v: String) = viewModelScope.launch { settingsStore.setSkinStyle(v) }
    fun setShowPlaylist(v: Boolean) = viewModelScope.launch { settingsStore.setShowPlaylist(v) }
    fun setNoUpdatePrompt(v: Boolean) = viewModelScope.launch { settingsStore.setNoUpdatePrompt(v) }
    fun setPlaylistAutoScroll(v: Boolean) = viewModelScope.launch { settingsStore.setPlaylistAutoScroll(v) }

    fun setBackgroundImage(path: String?) = viewModelScope.launch {
        settingsStore.setBackgroundImage(path)
        say(if (path == null) "已清除自定义背景图" else "已设置自定义背景图")
    }

    fun setBackgroundPreset(id: String) = viewModelScope.launch { settingsStore.setBackgroundPreset(id) }
    fun setCardAlpha(v: Float) = viewModelScope.launch { settingsStore.setCardAlpha(v) }
    fun setBackgroundBlur(v: Float) = viewModelScope.launch { settingsStore.setBackgroundBlur(v) }
    fun setAccentColor(v: String) = viewModelScope.launch { settingsStore.setAccentColor(v) }
    fun markVersionSeen(v: String) = viewModelScope.launch { settingsStore.setSeenVersion(v) }
    fun setBackgroundDim(v: Float) = viewModelScope.launch { settingsStore.setBackgroundDim(v) }
    fun setPlayerLayout(v: String) = viewModelScope.launch { settingsStore.setPlayerLayout(v) }

    /** 从系统选择器拿到的 Uri 直接复制进应用内部存储，不依赖路径还原。 */
    fun setBackgroundFromUri(uri: Uri) {
        viewModelScope.launch {
            val path = repo.saveBackgroundImage(uri)
            if (path != null) {
                settingsStore.setBackgroundImage(path)
                say("背景图已更换")
            } else {
                say("这张图片读不出来（可能不是图片），请换一张")
            }
        }
    }

    /** 云盘等无法还原成路径的台本，先复制进应用内部存储再指定。 */
    fun assignScriptFromUri(trackPath: String, uri: Uri) {
        viewModelScope.launch {
            val path = repo.importScript(uri)
            if (path != null) assignScript(trackPath, path) else say("这个文件读不出来，请换一个")
        }
    }

    fun countAudio(dir: File) = viewModelScope.launch {
        val n = repo.countAudioFiles(dir)
        say("该文件夹及其子目录共有 $n 首音频")
    }

    fun say(text: String) {
        viewModelScope.launch { _message.emit(text) }
    }

    override fun onCleared() {
        connection.release()
        super.onCleared()
    }

    // ---------- 初始化（必须放在类体最后，见上方注释） ----------

    init {
        com.kiite.player.data.AppLog.init(getApplication())
        com.kiite.player.data.AppLog.log("应用启动 v" + com.kiite.player.AppInfo.VERSION_NAME)
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                val first = _settings.value == AppSettings()
                _settings.value = s
                if (first && s.scanOnStart && s.rootPath != null) rescan()
            }
        }
        viewModelScope.launch {
            runCatching { connection.connect() }
                .onSuccess { c ->
                    controller = c
                    attachListener(c)
                }
                .onFailure { say("播放服务连接失败：${it.message}") }
        }
        viewModelScope.launch {
            while (true) {
                updatePosition()
                delay(400)
            }
        }
        viewModelScope.launch {
            _manualLinks.value = repo.loadManualLinks()
        }
        runCatching { _dlsite.value = dlsiteStore.load() }
        loadRatings()
        loadTags()
        loadDownloadedCodes()
        // 启动后静默检查一次新版本
        viewModelScope.launch {
            kotlinx.coroutines.delay(4_000)
            val info = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.kiite.player.data.UpdateChecker.check()
            }
            if (info != null) {
                com.kiite.player.data.AppLog.log("发现新版本 " + info.tag + "（弹窗已" + (if (_settings.value.noUpdatePrompt) "关闭" else "开启") + "）")
                if (!_settings.value.noUpdatePrompt) _update.value = info
            }
        }
        // 若后台播放服务还活着，重连后把「正在播放」的状态还原到界面
        viewModelScope.launch {
            runCatching {
                val c = connection.connect()
                controller = c
                attachListener(c)
                restoreFromController(c)
            }
        }
    }
}
