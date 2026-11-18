package com.kiite.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.kiite.player.core.Bookmark
import com.kiite.player.core.ChapterEntry
import com.kiite.player.core.HistoryRecord
import com.kiite.player.core.ParsedScript
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.ProjectGrouper
import com.kiite.player.core.ScanResult
import com.kiite.player.core.ScriptAttachment
import com.kiite.player.core.TagWriteResult
import com.kiite.player.core.TextUtils
import com.kiite.player.core.TrackEntry
import com.kiite.player.domain.UndoRequest
import com.kiite.player.domain.PlayerUi
import com.kiite.player.domain.ScriptUi
import com.kiite.player.domain.BookmarkController
import com.kiite.player.domain.DlsiteController
import com.kiite.player.domain.HistoryController
import com.kiite.player.domain.LibraryController
import com.kiite.player.domain.PlaybackController
import com.kiite.player.domain.ScriptController
import com.kiite.player.domain.WorkMetaController
import com.kiite.player.data.AppSettings
import com.kiite.player.data.BookmarkStore
import com.kiite.player.data.HistoryFile
import com.kiite.player.data.HistoryStore
import com.kiite.player.data.LibraryRepository
import com.kiite.player.data.ProgressStore
import com.kiite.player.data.SettingsStore
import com.kiite.player.playback.PlayerConnection
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/** 一条可撤销的操作提示：snackbar 上点「撤销」时执行 [undo]。 */

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LibraryRepository(app)
    private val settingsStore = SettingsStore(app)
    private val connection = PlayerConnection(app)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * 曲库：扫描 + 手动指定台本的覆盖层 + 各种按作品/章节的查询，全在 [LibraryController] 里。
     * 它持有 rawScan / manualLinks / scan 三者，重新计算时「原始结果 + 覆盖层」才不会丢。
     */
    private val library: LibraryController = LibraryController(
        repo = repo,
        scope = viewModelScope,
        settings = { _settings.value },
        notify = { say(it) },
        setBusy = { task, label -> setBusy(task, label) },
        onScanReady = { merged ->
            // 扫描完成后：把播放现场接回来、恢复上次队列、按设置自动写入标签
            playback.applyPendingRestore()
            playback.restoreQueueIfNeeded()
            if (_settings.value.embedOnImport && merged.withScriptCount > 0) {
                say("已开启自动写入，开始把台本写进音频标签…")
                embedAll()
            }
        },
    )

    val scan: StateFlow<ScanResult?> = library.scan
    val scanning: StateFlow<Boolean> = library.scanning
    val scanProgress: StateFlow<com.kiite.player.core.ScanProgress?> = library.scanProgress
    val selectedProject: StateFlow<String?> = library.selectedProject
    val manualLinks: StateFlow<Map<String, String>> = library.manualLinks

    /**
     * 播放器 / 队列 / 播放模式 / 进度存档全在 [PlaybackController] 里。
     * 这里只做状态出口与转发，具体规则见那个类。
     */
    private val playback: PlaybackController = PlaybackController(
        scope = viewModelScope,
        progressStore = ProgressStore(app),
        connect = { connection.connect() },
        settings = { _settings.value },
        scan = { library.scan.value },
        saveSpeed = { speed -> viewModelScope.launch { settingsStore.setPlaybackSpeed(speed) } },
        savePlayMode = { mode -> viewModelScope.launch { settingsStore.setPlayMode(mode) } },
        saveQueueSettings = { paths, current ->
            viewModelScope.launch { settingsStore.setPlayQueue(paths, current) }
        },
        onTrackChanged = { track ->
            loadScriptFor(track)
            resolveCover(track)
        },
        historyStart = { track, at -> historyLog.recordStart(track, at) },
        historyFinished = { path -> historyLog.markFinished(path) },
        historySync = { track, pos, dur, now, finished ->
            historyLog.sync(track, pos, dur, now, finished)
        },
        historyListened = { ms -> historyLog.addListenedTime(ms) },
        onQueueChanged = { refreshSleepWorkBoundary() },
        notify = { say(it) },
        emitUndo = { req -> viewModelScope.launch { _undo.emit(req) } },
    )

    val player: StateFlow<PlayerUi> = playback.player
    val currentTrack: StateFlow<TrackEntry?> = playback.currentTrack
    val activePath: StateFlow<String?> = playback.activePath
    val progress: StateFlow<Map<String, Long>> = playback.progress
    val playMode: StateFlow<String> = playback.playMode
    val queueVersion: StateFlow<Int> = playback.queueVersion

    /**
     * DLsite：识别、封面、登录态、已购、应用内下载。
     * 这一块与播放/台本/曲库结构无关，只对外部网络和几个回调负责，见 [DlsiteController]。
     */
    private val dlsiteBox: DlsiteController = DlsiteController(
        app = app,
        repo = repo,
        store = com.kiite.player.data.DlsiteStore(app),
        scope = viewModelScope,
        projects = { library.scan.value?.projects.orEmpty() },
        rootPath = { _settings.value.rootPath },
        downloadDir = { _settings.value.downloadDir },
        purchaseAsmrOnly = { _settings.value.purchaseAsmrOnly },
        hideR18 = { _settings.value.hideR18 },
        rescan = { library.rescan() },
        notify = { say(it) },
        setBusy = { task, label -> setBusy(task, label) },
    )

    val dlsite: StateFlow<Map<String, com.kiite.player.core.DlsiteWork>> = dlsiteBox.works

    /**
     * 台本：加载、匹配状态、时间轴校准都在 [ScriptController] 里。
     * 这里只保留状态出口与转发，具体规则见那个类。
     */
    private val scripts: ScriptController = ScriptController(
        repo = repo,
        calibrationStore = com.kiite.player.data.ScriptCalibrationStore(app),
        scope = viewModelScope,
        currentTrack = { playback.currentTrack.value },
        livePosition = { playback.livePosition() },
        notify = { say(it) },
    )
    val script: StateFlow<ScriptUi> = scripts.script

    /** 当前曲目的封面：优先项目文件夹里的封面图，其次音频内嵌封面。 */
    private val _currentCover = MutableStateFlow<String?>(null)
    val currentCover: StateFlow<String?> = _currentCover.asStateFlow()

    /**
     * 后台任务表（任务 id → 展示文案）。拆分成表是为了互不覆盖：
     * 扫描跑着的时候检查更新，两个进度都能同时看到。
     */
    private val _busyTasks = MutableStateFlow<Map<String, String>>(emptyMap())
    val busyTasks: StateFlow<Map<String, String>> = _busyTasks.asStateFlow()

    /** 写入或清除一个后台任务的进度文案（label 为 null 表示结束）。 */
    private fun setBusy(task: String, label: String?) {
        _busyTasks.value = if (label == null) _busyTasks.value - task else _busyTasks.value + (task to label)
    }

    /** 已自动备份的原始音频数量（写标签前备份）。 */
    private val _tagBackupCount = MutableStateFlow(0)
    val tagBackupCount: StateFlow<Int> = _tagBackupCount.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val message = _message.asSharedFlow()

    /** 可撤销操作的提示流（批量分级等）。 */
    private val _undo = MutableSharedFlow<UndoRequest>(extraBufferCapacity = 16)
    val undo = _undo.asSharedFlow()

    /** 台本悬浮窗（桌面歌词）：设置开启且播放器有媒体时挂载。 */
    private val lyricOverlay = com.kiite.player.playback.LyricOverlayController(app)

    // 注意：init 块放在类体最后（文件末尾）。Kotlin 按声明顺序初始化属性，
    // 如果 init 写在这里，loadRatings() / 已下载标记 会访问到尚未初始化的
    // 属性而静默失败（表现就是：手动分级、已下载标记重启后丢失）。

    // ---------- DLsite ----------

    /** 项目对应的作品编号（RJ/VJ/BJ）。 */
    fun dlsiteCodeOf(project: ProjectEntry): String? = project.code ?: TextUtils.workCode(project.name)

    /**
     * 该项目的专辑封面：优先用 DLsite 抓到的封面，其次本地封面。
     *
     * 性能：这里原本是「线性扫全部项目 → 查表 → File.isFile（磁盘 stat）」，
     * 而它在列表项的 composable 里被调用，滚动时每帧重复执行 —— 是全库滚动掉帧的主因之一。
     * 现在：项目索引按扫描结果快照重建（O(1) 查 path），封面结果按 DLsite 快照缓存，
     * 命中时只是两次内存查表。
     */
    private var scanIndexSnapshot: ScanResult? = null
    private val projectByPath = HashMap<String, ProjectEntry>()

    private var coverCacheSnapshot: Map<String, com.kiite.player.core.DlsiteWork>? = null
    private var coverOverrideSnapshot: Map<String, String>? = null
    private val coverCache = HashMap<String, String?>()

    /** 用户自选头图：键见 [com.kiite.player.data.CoverOverrideStore.keyOf]。 */
    private val coverOverrideStore = com.kiite.player.data.CoverOverrideStore(app)
    private val _coverOverrides = MutableStateFlow(coverOverrideStore.load())
    val coverOverrides: StateFlow<Map<String, String>> = _coverOverrides.asStateFlow()

    private fun coverKeyOf(project: ProjectEntry): String? =
        com.kiite.player.data.CoverOverrideStore.keyOf(dlsiteCodeOf(project), project.path)

    /** 这个项目有没有自选头图（详情页菜单用来切换文案）。 */
    fun hasCustomCover(project: ProjectEntry): Boolean = coverKeyOf(project)?.let { _coverOverrides.value[it] } != null

    /**
     * 把相册里选的图片存成这部作品的头图。
     *
     * 只是复制一份进应用目录，**不动曲库里的原图**；编号稳定时移动目录也不会丢。
     */
    fun setCustomCoverFromUri(project: ProjectEntry, uri: android.net.Uri) {
        val key = coverKeyOf(project)
        if (key == null) {
            say("这个作品没有编号也没有路径，无法设置头图")
            return
        }
        viewModelScope.launch {
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<android.app.Application>()
                    val mime = ctx.contentResolver.getType(uri).orEmpty()
                    val ext = when {
                        mime.contains("png") -> "png"
                        mime.contains("webp") -> "webp"
                        else -> "jpg"
                    }
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("读不到这张图片")
                    if (bytes.isEmpty()) error("这张图片是空的")
                    coverOverrideStore.importBytes(key, bytes, ext) ?: error("保存图片失败")
                }
            }
            outcome.fold(
                { path ->
                    val old = _coverOverrides.value[key]
                    coverOverrideStore.deleteFile(old)
                    val merged = _coverOverrides.value + (key to path)
                    _coverOverrides.value = merged
                    coverOverrideStore.save(merged)
                    say("头图已更新")
                },
                { say("设置头图失败：" + it.message) },
            )
        }
    }

    /**
     * 把一张图片保存到系统相册（`Pictures/Kiite`）。
     * 路径可以来自自选头图、DLsite 封面缓存，也可以是作品目录里的图。
     */
    fun saveImageToGallery(path: String, displayName: String) {
        viewModelScope.launch {
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.kiite.player.util.GallerySave.save(getApplication(), java.io.File(path), displayName)
            }
            outcome.fold(
                { name -> say("已保存到相册 Pictures/Kiite/" + name) },
                { say("保存失败：" + it.message) },
            )
        }
    }

    /** 恢复默认：删掉自选头图，回到 DLsite 封面 / 作品目录里的图片。 */
    fun clearCustomCover(project: ProjectEntry) {
        val key = coverKeyOf(project) ?: return
        coverOverrideStore.deleteFile(_coverOverrides.value[key])
        val merged = _coverOverrides.value - key
        _coverOverrides.value = merged
        coverOverrideStore.save(merged)
        say("已恢复默认头图")
    }

    private fun projectOf(path: String): ProjectEntry? {
        val scan = library.scan.value ?: return null
        if (scan !== scanIndexSnapshot) {
            scanIndexSnapshot = scan
            projectByPath.clear()
            scan.projects.forEach { projectByPath[it.path] = it }
        }
        return projectByPath[path]
    }

    fun dlsiteCoverOf(projectPath: String): String? {
        val snapshot = dlsite.value
        val overrides = _coverOverrides.value
        if (snapshot !== coverCacheSnapshot || overrides !== coverOverrideSnapshot) {
            coverCacheSnapshot = snapshot
            coverOverrideSnapshot = overrides
            coverCache.clear()
        }
        if (coverCache.containsKey(projectPath)) return coverCache[projectPath]
        val project = projectOf(projectPath)
        // 自选头图优先，其次 DLsite 抓到的封面
        val custom = project?.let { p -> coverKeyOf(p)?.let { k -> overrides[k] } }
            ?.takeIf { java.io.File(it).isFile }
        val resolved = custom
            ?: project?.let { p -> dlsiteCodeOf(p)?.uppercase() }
                ?.let { code -> snapshot[code]?.coverLocalPath }
                ?.takeIf { java.io.File(it).isFile }
        coverCache[projectPath] = resolved
        return resolved
    }

    /** 重新抓取全部作品的 DLsite 信息与封面（修历史上被站内 banner 污染的头图）。 */
    fun refreshDlsiteCovers() = dlsiteBox.fetchAll(force = true) { dlsiteCodeOf(it) }

    /** 已有识别结果的项目数。 */
    val dlsiteCount: Int get() = dlsiteBox.count

    /** 批量识别所有带 RJ 编号的项目。 */
    fun fetchDlsiteForAll() = dlsiteBox.fetchAll { dlsiteCodeOf(it) }


    fun dlsiteOf(projectPath: String): com.kiite.player.core.DlsiteWork? =
        dlsiteOfCode(projectPath)?.let { dlsite.value[it] }

    private fun dlsiteOfCode(projectPath: String): String? {
        val project = projectOf(projectPath) ?: return null
        return dlsiteCodeOf(project)?.uppercase()
    }

    val dlsiteLoginState: StateFlow<com.kiite.player.core.DlsiteLoginState> = dlsiteBox.loginState
    val purchases: StateFlow<List<com.kiite.player.core.DlsitePurchase>> = dlsiteBox.purchases
    val downloadedCodes: StateFlow<Set<String>> = dlsiteBox.downloadedCodes

    fun refreshDlsiteLogin() = dlsiteBox.refreshLogin()

    fun syncPurchases() = dlsiteBox.syncPurchases()

    /** 已购里、本地还没有的作品。 */
    fun missingPurchases(): List<com.kiite.player.core.DlsitePurchase> = dlsiteBox.missingPurchases()

    /** 已购里非音声的作品数量（用于提示被过滤掉多少）。 */
    fun skippedNonAsmr(): Int = dlsiteBox.skippedNonAsmr()

    /** 按作品编号取本地缓存的 DLsite 封面（主页「已购未下载」行用）。 */
    fun dlsiteCoverOfCode(code: String): String? =
        dlsite.value[code.uppercase()]?.coverLocalPath?.takeIf { java.io.File(it).isFile }

    // ---------- DLsite 在线搜索（曲库搜索第三个标签页） ----------

    val onlineSearch: StateFlow<DlsiteController.OnlineSearch> = dlsiteBox.onlineSearch

    fun searchOnline(keyword: String) = dlsiteBox.searchOnline(keyword)


    fun setPurchaseAsmrOnly(v: Boolean) = viewModelScope.launch { settingsStore.setPurchaseAsmrOnly(v) }

    // ---------- DLsite 应用内下载（转发到 DlsiteController / 应用级 DlsiteDownloader） ----------

    /** 当前 DLsite 下载状态（null = 空闲）；下载跑在应用级作用域，切页面/后台都不中断。 */
    val dlsiteDownload: StateFlow<com.kiite.player.domain.DlsiteDownloader.State?> = dlsiteBox.downloadState

    fun clearDownloadStatus() = dlsiteBox.clearStatus()

    fun extractDownloaded() = dlsiteBox.extractDownloaded()

    fun cancelDlsiteDownload() = dlsiteBox.cancelDownload()

    fun startDlsiteDownload(url: String, userAgent: String?, contentDisposition: String?, code: String? = null) {
        dlsiteBox.startDownload(url, userAgent, contentDisposition, code)
        // 拉成前台服务：通知栏出进度、锁屏/切后台都不断（与 asmr.one 共用同一个服务）
        if (dlsiteBox.downloadState.value?.running == true) {
            runCatching { com.kiite.player.download.DownloadService.start(getApplication()) }
        }
    }


    // ---------- 社团 / 标签 / 声优 ----------

    fun knownCircles(): List<String> =
        dlsite.value.values.mapNotNull { it.circle }.filter { it.isNotBlank() }.distinct().sorted()

    fun knownTags(): List<String> =
        dlsite.value.values.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()

    fun knownVoiceActors(): List<String> =
        dlsite.value.values.flatMap { it.voiceActors }.filter { it.isNotBlank() }.distinct().sorted()

    fun setDownloadDir(path: String?) = viewModelScope.launch { settingsStore.setDownloadDir(path) }

    // ---------- 分级 / 自定义标签 / 作品元数据（WorkMetaController） ----------

    /**
     * 作品级手工数据全部交给 [WorkMetaController]：
     * 它们都以作品为键、存在 App 自己的 JSON 里，与播放状态和扫描结果无关。
     */
    private val works: WorkMetaController = WorkMetaController(
        app = app,
        dlsiteCodeOfProject = { dlsiteCodeOf(it) },
        dlsiteWorkOf = { code -> dlsite.value[code] },
        notify = { say(it) },
        emitUndo = { req -> viewModelScope.launch { _undo.emit(req) } },
    )

    val manualRatings: StateFlow<Map<String, String>> = works.manualRatings
    val manualTags: StateFlow<Map<String, List<String>>> = works.manualTags
    val workMeta: StateFlow<Map<String, com.kiite.player.data.WorkMeta>> = works.workMeta

    fun loadRatings() = works.loadRatings()

    fun ratingOf(project: ProjectEntry): com.kiite.player.core.WorkRating = works.ratingOf(project)

    fun setRating(project: ProjectEntry, rating: com.kiite.player.core.WorkRating) =
        works.setRating(project, rating)

    fun setRatingsBatch(targets: List<ProjectEntry>, rating: com.kiite.player.core.WorkRating) =
        works.setRatingsBatch(targets, rating)

    fun loadTags() = works.loadTags()

    fun tagsOf(project: ProjectEntry): List<String> = works.tagsOf(project)

    fun setTags(project: ProjectEntry, tags: List<String>) = works.setTags(project, tags)

    fun loadWorkMeta() = works.loadWorkMeta()

    fun workMetaOf(project: ProjectEntry): com.kiite.player.data.WorkMeta = works.workMetaOf(project)

    fun setWorkTitle(project: ProjectEntry, name: String) = works.setWorkTitle(project, name)

    /** 自定义作品名（null 表示没有自定义，按原规则显示）。 */
    fun workTitleOf(project: ProjectEntry): String? =
        works.workMetaOf(project).title.takeIf { it.isNotBlank() }
            ?: dlsiteOf(project.path)?.title?.takeIf { it.isNotBlank() }

    /**
     * 播放页曲目卡用：按曲目路径找到它所属的作品。
     *
     * 曲目卡的「爱心」是**作品级**收藏（和曲库里长按作品收藏的是同一份数据），
     * 所以这里必须拿到 [ProjectEntry]，只按曲目路径标记是没法复用那份收藏的。
     * 扫描还没完成时返回 null，界面就不画爱心（而不是画一个点了没反应的）。
     */
    fun projectOfTrackPath(track: TrackEntry): ProjectEntry? {
        val scan = library.scan.value ?: return null
        // 曲目可能在「作品/章节」的子目录里，直接按曲目路径查项目永远查不到，
        // 要先按目录层级回到项目根（曲库列表的归并逻辑也是这么算的）
        val root = com.kiite.player.core.ProjectGrouper.projectRootOf(scan.rootPath, track.folderPath)
        return projectOf(root) ?: projectOf(track.folderPath)
    }

    fun isFavorite(project: ProjectEntry): Boolean = works.isFavorite(project)

    fun setFavorite(project: ProjectEntry, favorite: Boolean) = works.setFavorite(project, favorite)

    fun setSynopsis(project: ProjectEntry, text: String) = works.setSynopsis(project, text)

    // ---------- 系列（组曲） ----------

    fun seriesOf(project: ProjectEntry): String = works.seriesOf(project)

    fun setSeries(project: ProjectEntry, series: String) = works.setSeries(project, series)

    /** 全库用过的系列名，供筛选与按系列浏览。 */
    fun knownSeries(): List<String> = works.knownSeries()

    // ---------- 智能收藏（智能集合） ----------

    /** 把「关键词 + 标签 + 收藏 + 分级」的筛选组合存成命名集合，存的是条件不是作品清单。 */
    private val smartCollectionStore = com.kiite.player.data.SmartCollectionStore(app)
    private val _smartCollections =
        MutableStateFlow<List<com.kiite.player.data.SmartCollection>>(emptyList())
    val smartCollections: StateFlow<List<com.kiite.player.data.SmartCollection>> =
        _smartCollections.asStateFlow()

    fun loadSmartCollections() {
        _smartCollections.value = smartCollectionStore.load()
    }

    fun saveSmartCollection(
        name: String,
        query: String,
        tag: String?,
        onlyFavorite: Boolean,
        rating: Int?,
    ) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val entry = com.kiite.player.data.SmartCollection(
            id = java.util.UUID.randomUUID().toString(),
            name = clean,
            query = query.trim(),
            tag = tag?.takeIf { it.isNotBlank() },
            onlyFavorite = onlyFavorite,
            rating = rating,
            createdAtMs = System.currentTimeMillis(),
        )
        // 同名覆盖：改条件比留两份同名集合更符合直觉
        val list = _smartCollections.value.filterNot { it.name == clean } + entry
        _smartCollections.value = list.sortedBy { it.createdAtMs }
        smartCollectionStore.save(list)
        say("已保存智能收藏「" + clean + "」")
    }

    fun deleteSmartCollection(id: String) {
        val list = _smartCollections.value.filterNot { it.id == id }
        _smartCollections.value = list
        smartCollectionStore.save(list)
    }

    // ---------- 音效（EQ / 低音 / 环绕 / 响度） ----------

    private val _audioFx = MutableStateFlow(com.kiite.player.core.AudioFxSettings.DEFAULT)
    val audioFx: StateFlow<com.kiite.player.core.AudioFxSettings> = _audioFx.asStateFlow()

    /**
     * 应用音效：立即推给播放服务（挂在 audioSessionId 上，只影响输出），
     * 同时持久化；关掉总开关时服务会整体释放效果器，**源文件永远不被修改**。
     */
    fun applyAudioFx(fx: com.kiite.player.core.AudioFxSettings) {
        val s = com.kiite.player.core.AudioFx.sanitize(fx)
        _audioFx.value = s
        viewModelScope.launch {
            settingsStore.setEqEnabled(s.enabled)
            settingsStore.setEqGains(com.kiite.player.core.AudioFx.encodeGains(s.gains))
            settingsStore.setEqBass(s.bassBoost)
            settingsStore.setEqVirtualizer(s.virtualizer)
            settingsStore.setEqLoudness(s.loudnessGainMb)
        }
        com.kiite.player.playback.PlaybackService.applyAudioFx(s)
    }

    fun setEpisodeName(project: ProjectEntry, trackPath: String, name: String) =
        works.setEpisodeName(project, trackPath, name)

    fun toggleEpisodeHidden(project: ProjectEntry, trackPath: String) =
        works.toggleEpisodeHidden(project, trackPath)

    fun visibleEpisodes(project: ProjectEntry, tracks: List<TrackEntry>): List<TrackEntry> =
        works.visibleEpisodes(project, tracks)

    fun episodeTitle(project: ProjectEntry, track: TrackEntry): String =
        works.episodeTitle(project, track)

    /** 整章播放：把这一章（未隐藏的）曲目作为队列。 */
    fun playChapter(project: ProjectEntry, chapterPath: String) {
        val all = tracksOfProject(project.path)
        val eps = visibleEpisodes(project, all).filter { it.folderPath == chapterPath }
        eps.firstOrNull()?.let { playTrack(it, eps) }
            ?: say("这一章没有可播放的曲目")
    }

    /** 全库用过的自定义标签，供筛选与推荐。 */
    fun knownCustomTags(): List<String> = works.knownCustomTags()

    fun setSortMode(mode: String) = viewModelScope.launch { settingsStore.setSortMode(mode) }
    fun setSortAsc(asc: Boolean) = viewModelScope.launch { settingsStore.setSortAsc(asc) }

    fun dlsiteLogout() = dlsiteBox.logout()

    /** 按项目识别一次 DLsite 信息。 */
    fun fetchDlsite(projectPath: String) {
        val code = dlsiteOfCode(projectPath) ?: run { say("这个项目没有识别到 RJ 编号"); return }
        dlsiteBox.fetchOne(code)
    }

    // ---------- 曲库（转发到 LibraryController） ----------

    fun setRoot(path: String?) {
        viewModelScope.launch {
            settingsStore.setRootPath(path)
            if (path == null) library.clear() else library.rescan()
        }
    }

    fun rescan() = library.rescan()

    /** 取消进行中的扫描：已扫描到的部分丢弃，原有曲库保持不变。 */
    fun cancelScan() = library.cancelScan()

    fun selectProject(path: String?) = library.selectProject(path)

    fun tracksOf(folderPath: String?): List<TrackEntry> = library.tracksOf(folderPath)

    fun tracksOfProject(projectPath: String): List<TrackEntry> = library.tracksOfProject(projectPath)

    /** 某个总项目里未解压的压缩包。 */
    fun archivesOfProject(projectPath: String): List<com.kiite.player.core.ArchiveEntry> =
        library.archivesOfProject(projectPath)

    /** 解压 zip 并重新扫描。 */
    fun extractArchive(archivePath: String) = library.extractArchive(archivePath)

    /** 项目内的章节分组：章节 → 该章节的曲目。 */
    fun chaptersOf(project: ProjectEntry): List<Pair<ChapterEntry, List<TrackEntry>>> =
        library.chaptersOf(project)

    // ---------- 播放（转发到 PlaybackController） ----------

    /**
     * 播放一首。
     * @param startAtMs 指定起始位置（书签跳转、历史续播用）；null 时回落到「上次看到哪儿」。
     */
    fun playTrack(track: TrackEntry, queue: List<TrackEntry> = emptyList(), startAtMs: Long? = null) =
        playback.playTrack(track, queue, startAtMs)

    fun playPause() = playback.playPause()

    fun next() = playback.next()

    fun previous() = playback.previous()

    fun seekBy(deltaMs: Long) = playback.seekBy(deltaMs)

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun stop() = playback.stop {
        scripts.clear()
        _currentCover.value = null
    }

    /** 播放倍数：0.5x – 3.0x，立即生效并持久化。 */
    fun setSpeed(speed: Float) = playback.setSpeed(speed)

    /** 在 顺序 → 列表循环 → 随机 之间轮转。 */
    fun cyclePlayMode() = playback.cyclePlayMode()

    // ---------- 封面 ----------

    private fun resolveCover(track: TrackEntry?) {
        if (track == null) {
            _currentCover.value = null
            return
        }
        val scan = library.scan.value
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

    companion object {
        const val MIN_SPEED = PlaybackController.MIN_SPEED
        const val MAX_SPEED = PlaybackController.MAX_SPEED

        /** 播放页可选的倍数档位。 */
        val SPEED_STEPS = PlaybackController.SPEED_STEPS
    }

    private val _videoFullscreen = MutableStateFlow(false)
    val videoFullscreen: StateFlow<Boolean> = _videoFullscreen.asStateFlow()

    fun setVideoFullscreen(v: Boolean) {
        _videoFullscreen.value = v
    }

    /** 全屏二级页（应用内下载 WebView 等）请求隐藏悬浮迷你播放器与底部导航胶囊。 */
    private val _chromeHidden = MutableStateFlow(false)
    val chromeHidden: StateFlow<Boolean> = _chromeHidden.asStateFlow()

    fun setChromeHidden(v: Boolean) {
        _chromeHidden.value = v
    }

    // ---------- asmr.one 在线搜索 ----------
    //
    // 免费资源站，无需登录；结果直接给下载入口（打开作品页 WebView 挑文件，
    // 下载请求被拦截后复用 DLsite 的下载器保存到 曲库/DLsite/{RJ号}）。

    data class AsmrOneSearchState(
        val loading: Boolean = false,
        val hits: List<com.kiite.player.core.AsmrOneWork> = emptyList(),
        /** 搜索结果总条数（分页用）。 */
        val total: Int = 0,
        /** 已加载到的页码；0 = 尚未发起过搜索。 */
        val page: Int = 0,
        val error: String? = null,
    )

    private val _asmrSearch = MutableStateFlow(AsmrOneSearchState())
    val asmrSearch: StateFlow<AsmrOneSearchState> = _asmrSearch.asStateFlow()

    /** 搜索 / 加载下一页（page>1 时把结果追加到已有列表后面）。 */
    fun searchAsmrOne(query: String, page: Int = 1) {
        val q = query.trim()
        if (q.isEmpty()) return
        if (page > 1 && _asmrSearch.value.loading) return
        viewModelScope.launch {
            _asmrSearch.value = _asmrSearch.value.copy(loading = true, error = null)
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.kiite.player.data.AsmrOneClient.search(q, page)
            }
            outcome
                .onSuccess { (hits, total) ->
                    val prev = _asmrSearch.value
                    // 加载更多时按 id 去重，避免翻页重叠出现重复行
                    val merged = if (page > 1) {
                        (prev.hits + hits).distinctBy { it.id }
                    } else {
                        hits
                    }
                    _asmrSearch.value = AsmrOneSearchState(
                        loading = false,
                        hits = merged,
                        total = total,
                        page = page,
                        error = if (merged.isEmpty()) "asmr.one 没有搜到相关作品" else null,
                    )
                }
                .onFailure {
                    _asmrSearch.value = _asmrSearch.value.copy(loading = false, error = "在线搜索失败：" + it.message)
                }
        }
    }

    fun resetAsmrSearch() {
        _asmrSearch.value = AsmrOneSearchState()
    }

    // ---------- asmr.one 原生下载 ----------
    //
    // 网页里的下载按钮是 JS blob 流，WebView 拦不到；直接走它的公开 API
    // （guest 登录 → /api/tree 列文件 → /api/media 逐个下），见 [AsmrOneController]。

    private val asmrBox: com.kiite.player.domain.AsmrOneController =
        com.kiite.player.domain.AsmrOneDownloads.get(getApplication())

    /** 当前 asmr.one 下载状态（null = 空闲）；下载本身跑在应用级作用域，切页面/后台都不会断。 */
    val asmrDownload: StateFlow<com.kiite.player.domain.AsmrOneController.DownloadState?> = asmrBox.state

    /** 拉作品文件树（文件选择页用）。 */
    suspend fun fetchAsmrTree(work: com.kiite.player.core.AsmrOneWork): Result<com.kiite.player.core.AsmrOneTreeNode> =
        runCatching { asmrBox.fetchTree(work.id) }

    /** 已探测到的文件体积（source → 字节），下载前估算容量用。 */
    val asmrSizes: StateFlow<Map<String, Long>> = asmrBox.sizes

    /** 探测勾选文件的体积；已探测过的直接命中缓存，不会重复发请求。 */
    suspend fun probeAsmrSizes(workId: Long, sources: List<String>) {
        runCatching { asmrBox.probeSizes(workId, sources) }
    }

    /** 曲库根目录剩余可用空间（拿不到目录或异常时为 null，界面就不显示剩余空间）。 */
    fun asmrFreeBytes(): Long? {
        val root = _settings.value.rootPath?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { java.io.File(root).usableSpace }.getOrNull()?.takeIf { it > 0 }
    }


    fun downloadAsmrOne(
        work: com.kiite.player.core.AsmrOneWork,
        files: List<com.kiite.player.core.AsmrOneTreeNode>? = null,
        rootName: String? = null,
    ) {
        asmrBox.download(work, files, rootName)
        // 拉成前台服务：通知栏出进度、锁屏/切后台都不断。
        // 只有确实开始下载（状态为 running）才启动，避免「已有下载在进行中」时多起一个空服务。
        if (asmrBox.running) {
            runCatching { com.kiite.player.download.DownloadService.start(getApplication()) }
        }
    }

    fun cancelAsmrDownload() = asmrBox.cancel()

    fun clearAsmrDownload() = asmrBox.clear()


    // ---------- 定时停止播放 ----------
    //
    // 定时器本体在 PlaybackService 里跑（见 playback/SleepTimer.kt 的注释），
    // 这里只做两件事：把状态转发给界面，把用户操作转成服务命令。
    // ViewModel 被回收不影响已经设好的定时。

    val sleepState: StateFlow<com.kiite.player.playback.SleepState> =
        com.kiite.player.playback.PlaybackService.sleepState

    /** 渐停时长（毫秒）目前取的那个设置值。 */
    val sleepFadeMs: Long get() = _settings.value.sleepFadeMs

    fun setSleepFade(fadeMs: Long) = viewModelScope.launch { settingsStore.setSleepFadeMs(fadeMs) }

    /** 倒计时停止；seconds 为 null 或 <= 0 表示取消定时。 */
    fun setSleepDuration(seconds: Long?) {
        if (seconds == null || seconds <= 0L) {
            com.kiite.player.playback.PlaybackService.clear()
            return
        }
        val ok = com.kiite.player.playback.PlaybackService
            .armDuration(seconds * 1_000L, _settings.value.sleepFadeMs)
        if (!ok) say("播放服务还没起来，先放一首再设定时")
    }

    /** 播完当前章节（当前这一首）后停止。 */
    fun setSleepStopAtTrackEnd() {
        val ok = com.kiite.player.playback.PlaybackService
            .armStopAtItem(playback.currentMediaId(), com.kiite.player.playback.SleepStopMode.END_OF_TRACK)
        if (!ok) say("先放一首再设定时")
    }

    /**
     * 播完当前作品后停止。
     *
     * 作品边界取「播放队列里属于当前项目的最后一首」，而不是「项目里未隐藏的最后一首」：
     * 用户中途往队列里加了别的作品、或删掉了本作品最后几首时，停在队列里真实的那一首才符合预期。
     * 队列里一首本项目的都没有（例如曲库还没扫完）时，退回项目内未隐藏曲目的最后一首。
     */
    fun setSleepStopAtWorkEnd() {
        val ok = com.kiite.player.playback.PlaybackService
            .armStopAtItem(workBoundaryMediaId(), com.kiite.player.playback.SleepStopMode.END_OF_WORK)
        if (!ok) say("先放一首再设定时")
    }

    /** 队列里属于当前项目的最后一首；找不到时退回项目内未隐藏曲目的最后一首。 */
    private fun workBoundaryMediaId(): String? {
        val project = playback.currentProject()
        if (project != null) {
            val mine = playback.tracksOfProject(project.path)
                .flatMap { listOf(it.path) + it.altPaths }
                .toSet()
            playback.queueMediaIds().lastOrNull { it in mine }?.let { return it }
        }
        return project?.let { p ->
            visibleEpisodes(p, playback.tracksOfProject(p.path)).lastOrNull()?.path
        }
    }

    /** 队列被增删后，让「播完本作品停」的边界跟着更新。 */
    private fun refreshSleepWorkBoundary() {
        com.kiite.player.playback.PlaybackService.retargetStopAtItem(workBoundaryMediaId())
    }

    // ---------- A-B 区间循环 ----------
    //
    // 和定时器同理：判定逻辑在播放服务里（切换页面不会中断循环），
    // 位置一律由服务自己从播放器读，避免界面传过来的进度是 400ms 前的旧值。

    val abState: StateFlow<com.kiite.player.playback.AbState> =
        com.kiite.player.playback.PlaybackService.abState

    fun abSetStart() {
        if (!com.kiite.player.playback.PlaybackService.abSetStart()) say("先放一首再设 A-B")
    }

    fun abSetEnd() {
        com.kiite.player.playback.PlaybackService.abSetEnd()
    }

    fun abNudgeStart(deltaMs: Long) {
        com.kiite.player.playback.PlaybackService.abNudgeStart(deltaMs)
    }

    fun abNudgeEnd(deltaMs: Long) {
        com.kiite.player.playback.PlaybackService.abNudgeEnd(deltaMs)
    }

    fun abToggle() {
        if (!com.kiite.player.playback.PlaybackService.abToggle()) say("先设好 A/B 两点")
    }

    fun abClear() {
        com.kiite.player.playback.PlaybackService.abClear()
    }

    fun clearSleepTimer() {
        com.kiite.player.playback.PlaybackService.clear()
    }

    private val _update = MutableStateFlow<com.kiite.player.data.UpdateInfo?>(null)
    val update: StateFlow<com.kiite.player.data.UpdateInfo?> = _update.asStateFlow()

    fun checkUpdate() {
        viewModelScope.launch {
            setBusy("update", "正在检查更新…")
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.kiite.player.data.UpdateChecker.checkVerbose()
            }
            setBusy("update", null)
            when {
                result.error != null -> say("检查更新失败：" + result.error)
                result.info == null -> say("已是最新版本 v" + com.kiite.player.AppInfo.VERSION_NAME)
                else -> _update.value = result.info
            }
        }
    }

    fun dismissUpdate() { _update.value = null }

    /** 供视频画面（PlayerView）挂载用。 */
    val playerForView: Player? get() = playback.playerForView()

    /** 当前在播的是不是视频。 */
    fun currentIsVideo(): Boolean = playback.currentIsVideo()

    /** 当前作品里的图片：曲目所在文件夹 + 作品根目录。 */
    fun imagesOfCurrent(): List<String> = playback.imagesOfCurrent()

    fun variantsOfCurrent(): List<String> = playback.variantsOfCurrent()

    /**
     * 变体显示标签。分类名各不相同就带上分类（se无 · MP3），
     * 否则只显示格式（MP3），避免出现一堆无意义的「音频 · MP3」。
     */
    fun variantLabels(): List<Pair<String, String>> = playback.variantLabels()

    /** 同名不同格式之间切换，保持播放位置。 */
    fun switchVariant(path: String) = playback.switchVariant(path)

    fun setImmersive(value: Boolean) = viewModelScope.launch { settingsStore.setImmersive(value) }

    fun setDetailLayout(value: String) = viewModelScope.launch { settingsStore.setDetailLayout(value) }

    /** 曲库显示方式：直接指定 list / medium / large。 */
    fun setProjectLayout(value: String) {
        viewModelScope.launch { settingsStore.setProjectLayout(value) }
    }

    /** 曲库显示方式：list → medium → large 循环。 */
    fun cycleProjectLayout() {        val next = when (_settings.value.projectLayout) {
            "list" -> "medium"
            "medium" -> "large"
            else -> "list"
        }
        viewModelScope.launch { settingsStore.setProjectLayout(next) }
        say(
            when (next) {
                "medium" -> "中图标"
                "large" -> "大图标"
                else -> "列表"
            },
        )
    }

    // ---------- 播放进度记忆（转发到 PlaybackController） ----------

    fun loadProgress() = playback.loadProgress()

    /** 某个曲目上次播放到的位置（毫秒）。 */
    fun progressOf(path: String): Long = playback.progressOf(path)

    // ---------- 播放历史 / 书签 ----------

    /**
     * 历史与书签各自独立成控制器：它们只依赖「谁在放、放到哪、队列怎么建」这些回调，
     * 不反向依赖整个 ViewModel。具体规则见 HistoryController / BookmarkController。
     */
    private val historyLog: HistoryController = HistoryController(
        store = HistoryStore(app),
        scope = viewModelScope,
        projectOfTrack = { projectOf(it) },
        trackOf = { path -> library.trackOf(path) },
        queueFor = { track, projectPath -> queueAround(track, projectPath) },
        play = { track, queue, startAt -> playback.playTrack(track, queue, startAt) },
        mediaDuration = { playback.playerForView()?.duration ?: 0L },
        notify = { say(it) },
        emitUndo = { req -> viewModelScope.launch { _undo.emit(req) } },
    )

    val history: StateFlow<HistoryFile> = historyLog.history

    private val marks: BookmarkController = BookmarkController(
        store = BookmarkStore(app),
        currentTrack = { playback.currentTrack.value },
        playbackPosition = { playback.positionOrNull() },
        projectOfTrack = { projectOf(it) },
        trackOf = { path -> library.trackOf(path) },
        queueFor = { track, projectPath -> queueAround(track, projectPath) },
        play = { track, queue, startAt -> playback.playTrack(track, queue, startAt) },
        seekTo = { playback.seekTo(it) },
        notify = { say(it) },
        emitUndo = { req -> viewModelScope.launch { _undo.emit(req) } },
        lineStartMs = { idx -> scripts.lineStartMs(idx) },
        lineText = { idx -> scripts.lineText(idx) },
        scriptFingerprint = { scripts.fingerprint() },
    )

    val bookmarks: StateFlow<List<Bookmark>> = marks.bookmarks

    /**
     * 曲目所属的总项目（路径 + 名称）。
     * 历史与书签里都冗余存一份：曲库还没扫完、或作品文件夹被改名之后，
     * 列表里仍然能显示「这是哪部作品的哪一集」。
     */
    private fun projectOf(track: TrackEntry): Pair<String, String> {
        val scan = library.scan.value ?: return "" to ""
        val root = ProjectGrouper.projectRootOf(scan.rootPath, track.folderPath)
        val name = scan.projects.firstOrNull { it.path == root }?.name ?: track.folderName
        return root to name
    }


    /**
     * 从外面（历史、书签）跳过来播某一集时，用哪个队列。
     * 优先取所属作品的可见集数；这一集恰好被用户隐藏了，就单曲播——否则队列里没有它，
     * 播起来的是队列第一首，用户会以为点错了。
     */
    private fun queueAround(track: TrackEntry, projectPath: String): List<TrackEntry> {
        val pp = projectPath.ifBlank { projectOf(track).first }
        val project = library.projectOf(pp) ?: return emptyList()
        val list = visibleEpisodes(project, tracksOfProject(project.path))
        return if (list.any { it.path == track.path }) list else emptyList()
    }

    /** 从历史里继续播。文件已失效时给明确提示，而不是静默失败。 */
    fun continueFromHistory(record: HistoryRecord) = historyLog.continueFrom(record)

    fun removeHistoryRecord(trackPath: String) = historyLog.remove(trackPath)

    fun clearHistory() = historyLog.clear()

    /** 曲库里是否还有这一集（扫描未完成时一律当作「有」）。 */
    fun isTrackAvailable(path: String): Boolean = library.isTrackAvailable(path)

    /** 批量判断哪些路径已经不在曲库里（整张历史只扫一遍）。 */
    fun unavailablePaths(paths: Collection<String>): Set<String> = library.unavailablePaths(paths)

    // ---------- 书签（转发到 BookmarkController） ----------

    /** 当前这一集的书签，按时间点升序。 */
    fun bookmarksOfCurrentTrack(): List<Bookmark> = marks.ofCurrentTrack()

    /** 当前这一集被标了书签的台本行号（播放页给行打亮用）。 */
    fun bookmarkedLinesOfCurrentTrack(): Set<Int> = marks.bookmarkedLines()

    /** 点亮/熄灭台本第 index 句的书签；返回 true 表示这次是「加上」。 */
    fun toggleLineBookmark(index: Int): Boolean? = marks.toggleLine(index)

    /** 当前台本的指纹：书签面板用它判断按行书签的行号还对不对得上。 */
    fun scriptFingerprint(): String = scripts.fingerprint()

    fun addBookmarkNow(note: String = ""): Bookmark? = marks.addNow(note)

    fun updateBookmarkNote(id: String, note: String) = marks.updateNote(id, note)

    fun deleteBookmark(id: String) = marks.delete(id)

    /** 点书签：同一集就跳过去；别的集先切歌再定位到那个时间点。 */
    fun openBookmark(bm: Bookmark) = marks.open(bm)

    // ---------- 播放列表（转发到 PlaybackController） ----------

    /** 当前曲目所属的总项目。 */
    fun currentProject(): ProjectEntry? = playback.currentProject()

    /**
     * 播放列表 = 播放器里**真实的队列**（所以能增删）。
     * 从曲库重新点歌会整队重置成曲库的列表；播放器还没连上时回落到当前项目的音频。
     */
    fun currentPlaylist(): List<TrackEntry> = playback.currentPlaylist()

    fun currentPlaylistIndex(): Int = playback.currentPlaylistIndex()

    fun playFromPlaylist(index: Int) = playback.playFromPlaylist(index)

    /** 启动时把上次的队列装回播放器（暂停状态，不自动播放）。 */
    fun restoreQueueIfNeeded() = playback.restoreQueueIfNeeded()

    /** 从播放队列里移除一首。 */
    fun queueRemove(index: Int) = playback.queueRemove(index)

    /** 队列拖拽排序：把某首上移/下移一位（delta = ±1），按路径定位。 */
    fun queueMove(path: String, delta: Int): Boolean = playback.queueMove(path, delta)

    /** 往播放队列末尾加一首。 */
    fun queueAdd(track: TrackEntry) = playback.queueAdd(track)

    /** 批量加入播放队列（曲库里多选后一次加进去）。 */
    fun queueAddAll(tracks: List<TrackEntry>) = playback.queueAddAll(tracks)

    /** 把队列恢复成当前项目的全部音频（从曲库重新点歌也会自动走到这个状态）。 */
    fun queueReset() = playback.queueReset()

    // ---------- 台本（转发到 ScriptController） ----------

    private fun loadScriptFor(track: TrackEntry) = scripts.loadFor(track)

    fun loadAttachment(att: ScriptAttachment) = scripts.loadAttachment(att)

    fun loadEmbeddedLyrics() = scripts.loadEmbeddedLyrics()

    fun loadCalibrations() = scripts.loadCalibrations()

    fun nudgeScriptOffset(deltaMs: Long) = scripts.nudgeOffset(deltaMs)

    fun setScriptOffset(offsetMs: Long) = scripts.setOffset(offsetMs)

    fun resetScriptOffset() = scripts.resetOffset()

    fun resetScriptLines() = scripts.resetLines()

    fun resetScriptCalibration() = scripts.resetAll()

    fun alignScriptLineStart(index: Int) = scripts.alignLineStart(index)

    fun alignScriptLineEnd(index: Int) = scripts.alignLineEnd(index)

    fun nudgeScriptLineStart(index: Int, deltaMs: Long) = scripts.nudgeLineStart(index, deltaMs)

    fun nudgeScriptLineEnd(index: Int, deltaMs: Long) = scripts.nudgeLineEnd(index, deltaMs)

    fun resetScriptLine(index: Int) = scripts.resetLine(index)

    /** 批量校准：把 from–to 这些句子整段一起平移。 */
    fun shiftScriptLines(from: Int, to: Int, deltaMs: Long) = scripts.shiftLines(from, to, deltaMs)

    /** 批量校准：清掉 from–to 区间里的单句微调，区间外保留。 */
    fun resetScriptLinesRange(from: Int, to: Int) = scripts.resetLinesRange(from, to)

    // ---------- 手动指定台本 ----------

    fun assignScript(trackPath: String, scriptPath: String) {
        // 回调在快照重算之后：那时才能拿到带新台本附件的曲目对象
        library.setManualLink(trackPath, scriptPath) { refreshCurrentTrackScript(trackPath) }
    }

    fun clearScript(trackPath: String) {
        library.clearManualLink(trackPath) { refreshCurrentTrackScript(trackPath) }
    }

    /**
     * 手动指定/取消之后，如果改的正是当前在播的那一集，要把播放器里那份曲目对象也换掉
     * （它带着台本附件），否则播放页还显示旧的台本。
     */
    private fun refreshCurrentTrackScript(trackPath: String) {
        if (playback.currentTrack.value?.path != trackPath) return
        val updated = library.trackOf(trackPath) ?: return
        playback.replaceCurrentTrack(updated)
        loadScriptFor(updated)
    }

    // ---------- 写入标签 ----------

    fun embedCurrent() {
        val track = playback.currentTrack.value ?: return
        val att = scripts.script.value.attachment ?: track.primaryScript
        viewModelScope.launch {
            setBusy("embed", "正在写入标签…")
            val result = if (att != null) {
                repo.embedFromScript(track.path, att.scriptPath)
            } else {
                val text = scripts.script.value.parsed?.plainText
                if (text.isNullOrBlank()) TagWriteResult.Failed("没有可写入的台本") else repo.embed(track.path, text)
            }
            setBusy("embed", null)
            refreshTagBackupCount()
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
                setBusy("embed", "写入标签 ${i + 1}/${tracks.size}…")
                val att = track.primaryScript
                if (att == null) { skipped++; return@forEachIndexed }
                when (repo.embedFromScript(track.path, att.scriptPath)) {
                    is TagWriteResult.Written -> ok++
                    is TagWriteResult.Unsupported -> skipped++
                    is TagWriteResult.Failed -> failed++
                }
            }
            setBusy("embed", null)
            refreshTagBackupCount()
            say("写入完成：成功 $ok，跳过 $skipped，失败 $failed")
        }
    }

    fun embedAll() {
        viewModelScope.launch {
            val tracks = (library.scan.value?.tracks ?: emptyList()).filter { it.hasScript }
            if (tracks.isEmpty()) { say("没有可写入的台本"); return@launch }
            var ok = 0; var skipped = 0; var failed = 0
            tracks.forEachIndexed { i, track ->
                setBusy("embed", "写入标签 ${i + 1}/${tracks.size}…")
                val att = track.primaryScript ?: run { skipped++; return@forEachIndexed }
                when (repo.embedFromScript(track.path, att.scriptPath)) {
                    is TagWriteResult.Written -> ok++
                    is TagWriteResult.Unsupported -> skipped++
                    is TagWriteResult.Failed -> failed++
                }
            }
            setBusy("embed", null)
            refreshTagBackupCount()
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
    fun setVideoGestureGuideSeen(v: Boolean) = viewModelScope.launch { settingsStore.setVideoGestureGuideSeen(v) }

    /** 重新统计已备份的原始文件数。 */
    private fun refreshTagBackupCount() = viewModelScope.launch {
        _tagBackupCount.value = repo.tagBackupCount()
    }

    /** 把写标签前的原始文件恢复回去（覆盖被改写的版本）。 */
    fun restoreTagBackups() {
        viewModelScope.launch {
            setBusy("restore", "正在恢复标签备份…")
            val n = withContext(kotlinx.coroutines.Dispatchers.IO) { repo.restoreTagBackups() }
            setBusy("restore", null)
            say(if (n > 0) "已恢复 $n 个原始音频文件" else "没有可恢复的备份（原始文件可能已被移动或删除）")
        }
    }

    fun setBackgroundImage(path: String?) = viewModelScope.launch {
        settingsStore.setBackgroundImage(path)
        say(if (path == null) "已清除自定义背景图" else "已设置自定义背景图")
    }

    fun setBackgroundPreset(id: String) = viewModelScope.launch { settingsStore.setBackgroundPreset(id) }
    fun setCardAlpha(v: Float) = viewModelScope.launch { settingsStore.setCardAlpha(v) }
    fun setBackgroundBlur(v: Float) = viewModelScope.launch { settingsStore.setBackgroundBlur(v) }
    fun setLiveBlur(v: Boolean) = viewModelScope.launch { settingsStore.setLiveBlur(v) }
    fun setSimplifiedMotion(v: Boolean) = viewModelScope.launch { settingsStore.setSimplifiedMotion(v) }
    fun setHideR18(v: Boolean) = viewModelScope.launch { settingsStore.setHideR18(v) }
    fun setLyricOverlay(v: Boolean) = viewModelScope.launch { settingsStore.setLyricOverlay(v) }

    fun setLyricScale(v: Float) = viewModelScope.launch { settingsStore.setLyricScale(v) }

    fun setLyricSize(widthDp: Float, heightDp: Float) =
        viewModelScope.launch { settingsStore.setLyricSize(widthDp, heightDp) }

    fun setLyricCollapsed(v: Boolean) = viewModelScope.launch { settingsStore.setLyricCollapsed(v) }
    fun setAccentColor(v: String) = viewModelScope.launch { settingsStore.setAccentColor(v) }
    fun markVersionSeen(v: String) = viewModelScope.launch { settingsStore.setSeenVersion(v) }
    fun setBackgroundDim(v: Float) = viewModelScope.launch { settingsStore.setBackgroundDim(v) }
    fun setPlayerLayout(v: String) = viewModelScope.launch { settingsStore.setPlayerLayout(v) }

    fun setScriptLineHeight(v: Float) = viewModelScope.launch { settingsStore.setScriptLineHeight(v) }
    fun setScriptFontFamily(v: String) = viewModelScope.launch { settingsStore.setScriptFontFamily(v) }
    fun setScriptTheme(v: String) = viewModelScope.launch { settingsStore.setScriptTheme(v) }

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

    fun countAudio(dir: File) = library.countAudio(dir)

    fun say(text: String) {
        viewModelScope.launch { _message.emit(text) }
    }

    override fun onCleared() {
        lyricOverlay.hide()
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
                if (first) {
                    playback.setPlayMode(s.playMode)
                    // 音效设置从持久化装回来（服务启动时也会自己装，两边一致）
                    _audioFx.value = com.kiite.player.core.AudioFx.sanitize(
                        com.kiite.player.core.AudioFxSettings(
                            enabled = s.eqEnabled,
                            gains = com.kiite.player.core.AudioFx.decodeGains(s.eqGains),
                            bassBoost = s.eqBass,
                            virtualizer = s.eqVirtualizer,
                            loudnessGainMb = s.eqLoudness,
                        ),
                    )
                    loadSmartCollections()
                    if (s.scanOnStart && s.rootPath != null) rescan()
                }
            }
        }
        viewModelScope.launch {
            runCatching { connection.connect() }
                .onSuccess { c ->
                    playback.attach(c)
                    playback.applyPlayMode(playback.playMode.value)
                    playback.restoreQueueIfNeeded()
                }
                .onFailure { say("播放服务连接失败：${it.message}") }
        }
        viewModelScope.launch {
            while (true) {
                playback.tick()
                // 定时器 / A-B 循环在播放服务里跑，通知由它写进队列，这里取出来给用户看
                com.kiite.player.playback.PlaybackService.consumeNotice()?.let { say(it) }
                delay(400)
            }
        }
        // 台本悬浮窗：开关 × 有媒体 → 挂/摘；进度 tick 同步当前行（进程由前台播放服务保活）
        viewModelScope.launch {
            combine(_settings, playback.player) { s, p -> s to p.hasMedia }
                .collect { (s, hasMedia) ->
                    lyricOverlay.setSize(s.lyricWidth, s.lyricHeight)
                    lyricOverlay.setScale(s.lyricScale)
                    lyricOverlay.setCollapsed(s.lyricCollapsed)
                    if (s.lyricOverlay && hasMedia) lyricOverlay.show() else lyricOverlay.hide()
                }
        }
        // 浮窗上的「关闭」「字号」「拖拽缩放」「收成球」，写回设置后由上面的 collect 自然生效
        lyricOverlay.onRequestClose = {
            viewModelScope.launch {
                settingsStore.setLyricOverlay(false)
                _message.emit("已关闭桌面台本悬浮窗（播放页 / 设置里可重新开启）")
            }
        }
        lyricOverlay.onScaleChange = { v ->
            viewModelScope.launch { settingsStore.setLyricScale(v) }
        }
        lyricOverlay.onSizeChange = { w, h ->
            viewModelScope.launch { settingsStore.setLyricSize(w, h) }
        }
        lyricOverlay.onCollapsedChange = { collapsed ->
            viewModelScope.launch { settingsStore.setLyricCollapsed(collapsed) }
        }
        viewModelScope.launch {
            combine(scripts.script, playback.player) { s, p -> s.parsed to p.positionMs }
                .collect { (parsed, pos) ->
                    lyricOverlay.update(parsed, pos, playback.currentTrack.value?.baseName)
                }
        }
        viewModelScope.launch {
            library.loadManualLinks()
        }
        dlsiteBox.load()
        loadRatings()
        loadTags()
        loadWorkMeta()
        loadProgress()
        historyLog.load()
        marks.load()
        loadCalibrations()
        // 历史写盘线程：挂在这个 scope 上，ViewModel 结束时会一起收掉
        historyLog.startWriter()
        dlsiteBox.loadDownloadedCodes()
        refreshTagBackupCount()
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
                playback.attach(c)
                playback.restoreFromController(c)
            }
        }
        // asmr.one 下载：控制器在应用级作用域里跑，这里只做「提示 + 完成后重扫」
        viewModelScope.launch {
            asmrBox.events.collect { say(it) }
        }
        viewModelScope.launch {
            var prevRunning = false
            asmrBox.state.collect { st ->
                val wasRunning = prevRunning
                prevRunning = st?.running == true
                // 下载刚结束（完成/失败/取消都算）→ 落盘的文件需要重新扫进曲库
                if (wasRunning && st?.running == false) {
                    if (st.completed || st.okCount > 0) library.rescan()
                }
            }
        }
        // DLsite 下载同理：跑在应用级作用域，这里只转发提示与（非压缩包的）重扫
        viewModelScope.launch {
            dlsiteBox.downloadEvents.collect { say(it) }
        }
        viewModelScope.launch {
            var prevRunning = false
            dlsiteBox.downloadState.collect { st ->
                val wasRunning = prevRunning
                prevRunning = st?.running == true
                if (wasRunning && st?.completed == true) library.rescan()
            }
        }
    }
}
