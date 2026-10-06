package com.asmrplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.asmrplayer.core.ChapterEntry
import com.asmrplayer.core.ManualLinks
import com.asmrplayer.core.ParsedScript
import com.asmrplayer.core.ProjectEntry
import com.asmrplayer.core.ProjectGrouper
import com.asmrplayer.core.ScanResult
import com.asmrplayer.core.ScriptAttachment
import com.asmrplayer.core.ScriptFormat
import com.asmrplayer.core.TagWriteResult
import com.asmrplayer.core.TextUtils
import com.asmrplayer.core.TrackEntry
import com.asmrplayer.data.AppSettings
import com.asmrplayer.data.LibraryRepository
import com.asmrplayer.data.SettingsStore
import com.asmrplayer.playback.PlayerConnection
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
    private val dlsiteStore = com.asmrplayer.data.DlsiteStore(app)
    private val connection = PlayerConnection(app)

    private val _dlsite = MutableStateFlow<Map<String, com.asmrplayer.core.DlsiteWork>>(emptyMap())
    val dlsite: StateFlow<Map<String, com.asmrplayer.core.DlsiteWork>> = _dlsite.asStateFlow()

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

    init {
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
    }

    // ---------- DLsite ----------

    /** 项目对应的作品编号（RJ/VJ/BJ）。 */
    fun dlsiteCodeOf(project: ProjectEntry): String? = project.code ?: TextUtils.workCode(project.name)

    fun dlsiteOf(projectPath: String): com.asmrplayer.core.DlsiteWork? =
        dlsiteOfCode(projectPath)?.let { _dlsite.value[it] }

    private fun dlsiteOfCode(projectPath: String): String? {
        val project = _scan.value?.projects?.firstOrNull { it.path == projectPath } ?: return null
        return dlsiteCodeOf(project)?.uppercase()
    }

    /** 按编号抓 DLsite 公开信息并缓存。 */
    fun fetchDlsite(projectPath: String) {
        val code = dlsiteOfCode(projectPath) ?: run { say("这个项目没有识别到 RJ 编号"); return }
        viewModelScope.launch {
            _busy.value = "正在从 DLsite 获取 " + code + " …"
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.asmrplayer.data.DlsiteClient.fetch(code)
            }
            _busy.value = null
            outcome
                .onSuccess { work ->
                    val merged = _dlsite.value + (work.code to work)
                    _dlsite.value = merged
                    runCatching { dlsiteStore.save(merged) }
                    say("已识别：" + (work.title ?: work.code))
                }
                .onFailure { say("获取失败：" + it.message) }
        }
    }

    private fun attachListener(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = updatePosition()
            override fun onPlaybackStateChanged(playbackState: Int) = updatePosition()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId
                val track = _scan.value?.tracks?.firstOrNull { it.path == id }
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
                    say("扫描完成：${merged.trackCount} 首音频，${merged.withScriptCount} 首匹配到台本")
                    if (_settings.value.embedOnImport && merged.withScriptCount > 0) {
                        say("已开启自动写入，开始把台本写进音频标签…")
                        embedAll()
                    }
                }
                .onFailure { say("扫描失败：${it.message}") }
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
    fun archivesOfProject(projectPath: String): List<com.asmrplayer.core.ArchiveEntry> {
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
            loadScriptFor(track)
            resolveCover(track)
        }
    }

    private fun toMediaItem(track: TrackEntry): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.fromFile(File(track.path)))
            .setMediaId(track.path)
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
        val projectCover = scan?.let { s ->
            val pp = ProjectGrouper.projectRootOf(s.rootPath, track.folderPath)
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
}
