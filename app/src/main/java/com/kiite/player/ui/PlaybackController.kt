package com.kiite.player.ui

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.kiite.player.core.HistoryMath
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.ProjectGrouper
import com.kiite.player.core.ScanResult
import com.kiite.player.core.TrackEntry
import com.kiite.player.core.isImagePath
import com.kiite.player.core.isVideoPath
import com.kiite.player.core.stripFormatTokens
import com.kiite.player.data.AppSettings
import com.kiite.player.data.ProgressStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放器与播放队列。
 *
 * 这里持有 MediaController、界面用的播放状态（进度/曲目/实际文件）、
 * 播放模式、队列增删与恢复、多格式变体切换、每 5 秒的进度落盘。
 *
 * 与其它块的边界：
 * - 定时停止 / A-B 循环的判定在播放服务里，这里只提供「当前媒体 id」「实时位置」「队列快照」；
 * - 历史与书签只拿到回调（[historyStart] / [historyFinished] / [historySync] / [historyListened]），
 *   反过来它们调 [playTrack] 换歌，不共享内部状态；
 * - 台本与封面的加载通过 [onTrackChanged] 通知外部，播放器自己不关心台本。
 */
class PlaybackController(
    private val scope: CoroutineScope,
    private val progressStore: ProgressStore,
    /** 懒连接：ViewModel 的 init 连接还没完成时用户就点了播放，这里补一次。 */
    private val connect: suspend () -> MediaController,
    private val settings: () -> AppSettings,
    private val scan: () -> ScanResult?,
    private val saveSpeed: (Float) -> Unit,
    private val savePlayMode: (String) -> Unit,
    private val saveQueueSettings: (List<String>, String?) -> Unit,
    /** 换歌后加载台本与封面。 */
    private val onTrackChanged: (TrackEntry) -> Unit,
    private val historyStart: (TrackEntry, Long) -> Unit,
    private val historyFinished: (String) -> Unit,
    private val historySync: (TrackEntry, Long, Long, Long, Boolean) -> Unit,
    private val historyListened: (Long) -> Unit,
    /** 队列被增删后通知外部（「播完本作品停」的边界要跟着动）。 */
    private val onQueueChanged: () -> Unit,
    private val notify: (String) -> Unit,
) {
    private var controller: MediaController? = null

    private val _player = MutableStateFlow(PlayerUi())
    val player: StateFlow<PlayerUi> = _player.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackEntry?>(null)
    val currentTrack: StateFlow<TrackEntry?> = _currentTrack.asStateFlow()

    /** 当前实际在播的文件（同名多格式时用来标出选中的那个）。 */
    private val _activePath = MutableStateFlow<String?>(null)
    val activePath: StateFlow<String?> = _activePath.asStateFlow()

    private val _playMode = MutableStateFlow(MODE_SEQUENCE)
    val playMode: StateFlow<String> = _playMode.asStateFlow()

    /** 队列版本号：增删后自增，让界面重新读取队列。 */
    private val _queueVersion = MutableStateFlow(0)
    val queueVersion: StateFlow<Int> = _queueVersion.asStateFlow()

    // ---------- 播放进度记忆 ----------

    private val _progress = MutableStateFlow<Map<String, Long>>(emptyMap())
    val progress: StateFlow<Map<String, Long>> = _progress.asStateFlow()
    private var lastProgressSave = 0L

    /** 当前曲目（供外部读，避免到处持引用）。 */
    fun currentTrackOrNull(): TrackEntry? = _currentTrack.value

    /** 曲目对象被外部更新（例如重新指定了台本）时同步过来，不影响播放位置。 */
    fun replaceCurrentTrack(track: TrackEntry) {
        _currentTrack.value = track
    }

    fun loadProgress() {
        runCatching { _progress.value = progressStore.load() }
    }

    /** 某个曲目上次播放到的位置（毫秒）。 */
    fun progressOf(path: String): Long = _progress.value[path] ?: 0L

    // ---------- 连接与事件 ----------

    fun attach(c: MediaController) {
        // 同一个 controller 只挂一次监听：init 里两处连接、以及懒连接都可能走到这里，
        // 重复挂会让 onMediaItemTransition 触发两次（历史里会出现两条记录）
        if (controller === c) return
        controller = c
        attachListener(c)
    }

    /** 供视频画面（PlayerView）挂载用。 */
    fun playerForView(): Player? = controller

    /** 当前曲目在播放器里的媒体 id（没连上/没媒体时为 null）。 */
    fun currentMediaId(): String? = controller?.currentMediaItem?.mediaId

    /** 队列里所有媒体 id，按顺序（未连接时为空）。 */
    fun queueMediaIds(): List<String> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
    }

    /**
     * 播放器的实时位置。界面那份 positionMs 是 400ms 轮询值，
     * 用来定书签 / 校准点位会系统性偏后。
     */
    fun livePosition(): Long? {
        val c = controller ?: return null
        if (c.mediaItemCount == 0 || c.duration <= 0) return null
        return c.currentPosition.coerceAtLeast(0L)
    }

    /** 只要位置、不要求时长有效（书签用）。 */
    fun positionOrNull(): Long? {
        val c = controller ?: return null
        if (c.mediaItemCount == 0) return null
        return c.currentPosition.coerceAtLeast(0L)
    }

    private fun attachListener(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = updatePosition()

            override fun onPlaybackStateChanged(playbackState: Int) {
                // 队列放到底：没有下一首可切，transition 不会来，只能在这里收尾
                if (playbackState == Player.STATE_ENDED) {
                    _currentTrack.value?.path?.let { historyFinished(it) }
                }
                updatePosition()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 自动连播意味着上一集刚刚播完：5 秒一次的位置同步抓不到「最后一刻」，
                // 所以「听完」要在这里补记，否则历史里的记录永远停在 90% 出头
                val previous = _currentTrack.value?.path
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    previous != null && previous != mediaItem?.mediaId
                ) {
                    historyFinished(previous)
                }
                val id = mediaItem?.mediaId
                val track = scan()?.tracks?.firstOrNull { it.path == id }
                _activePath.value = id
                if (track != null) {
                    _currentTrack.value = track
                    onTrackChanged(track)
                    // 自动连播、手动切歌走 AUTO / SEEK，这两种才算「开始听新的一集」。
                    // PLAYLIST_CHANGED 是 setMediaItems 触发的（playTrack 自己已经记过账，
                    // 启动时恢复队列也会走到这里），REPEAT 是单曲循环回到同一首——
                    // 都记进来会让播放次数虚高。
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                    ) {
                        historyStart(track, 0L)
                    }
                }
                updatePosition()
            }

            override fun onPlayerError(error: PlaybackException) {
                com.kiite.player.data.AppLog.log("播放失败：" + error.errorCodeName + " · " + (error.message ?: ""))
                notify("无法播放这首音频（文件可能已被移动、删除或损坏）")
            }
        })
        updatePosition()
    }

    private fun updatePosition() {
        val c = controller ?: return
        val next = PlayerUi(
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = if (c.duration > 0) c.duration else 0L,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            hasMedia = c.mediaItemCount > 0,
            speed = c.playbackParameters.speed,
        )
        // 去重：暂停/无变化时不再发射新状态，避免界面每 400ms 无谓重组（掉帧主因之一）
        if (next != _player.value) _player.value = next
    }

    /** 界面轮询的节拍：刷新播放状态 + 按 5 秒节拍落进度。 */
    fun tick() {
        updatePosition()
        maybeSaveProgress()
    }

    // ---------- 播放操作 ----------

    /**
     * 播放一首。
     * @param startAtMs 指定起始位置（书签跳转、历史续播用）；null 时回落到「上次看到哪儿」。
     */
    fun playTrack(track: TrackEntry, queue: List<TrackEntry> = emptyList(), startAtMs: Long? = null) {
        scope.launch {
            val c = controller ?: connect().also { attach(it) }
            val list = if (queue.isEmpty()) listOf(track) else queue
            val items = list.map { toMediaItem(it) }
            val index = list.indexOfFirst { it.path == track.path }.coerceAtLeast(0)
            // 续播：这一集上次看到哪儿就从哪儿开始（<4 秒的不算）。
            // 用 setMediaItems 的 startPositionMs，而不是 prepare() 之后再 seekTo（后者会被初始位置覆盖）
            val resumeAt = startAtMs ?: (progressOf(track.path).takeIf { it > 4_000 } ?: 0L)
            c.setMediaItems(items, index, resumeAt)
            c.setPlaybackSpeed(settings().playbackSpeed)
            c.prepare()
            c.play()
            _currentTrack.value = track
            _activePath.value = track.path
            onTrackChanged(track)
            saveQueue()
            historyStart(track, resumeAt)
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

    /** 停止播放并清空界面上的「正在播放」。台本与封面由 [onStopped] 收尾。 */
    fun stop(onStopped: () -> Unit) {
        controller?.stop()
        // 已经停下来了，定时器没有意义；顺手把渐停压低的音量恢复回去
        com.kiite.player.playback.PlaybackService.clear()
        _currentTrack.value = null
        updatePosition()
        onStopped()
    }

    /** 播放倍数：0.5x – 3.0x，立即生效并持久化。 */
    fun setSpeed(speed: Float) {
        val value = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        saveSpeed(value)
        controller?.setPlaybackSpeed(value)
        updatePosition()
    }

    // ---------- 播放模式：顺序 / 列表循环 / 随机 ----------

    /** 设置持久化的播放模式（启动时从设置里恢复）。 */
    fun setPlayMode(mode: String) {
        _playMode.value = mode
        applyPlayMode(mode)
    }

    /** 在 顺序 → 列表循环 → 随机 之间轮转。 */
    fun cyclePlayMode() {
        val next = when (_playMode.value) {
            MODE_SEQUENCE -> MODE_LOOP
            MODE_LOOP -> MODE_SHUFFLE
            else -> MODE_SEQUENCE
        }
        _playMode.value = next
        savePlayMode(next)
        applyPlayMode(next)
        notify(
            when (next) {
                MODE_LOOP -> "列表循环"
                MODE_SHUFFLE -> "随机播放"
                else -> "顺序播放"
            },
        )
    }

    fun applyPlayMode(mode: String) {
        val c = controller ?: return
        runCatching {
            when (mode) {
                MODE_LOOP -> {
                    c.repeatMode = Player.REPEAT_MODE_ALL
                    c.shuffleModeEnabled = false
                }
                MODE_SHUFFLE -> {
                    c.repeatMode = Player.REPEAT_MODE_ALL
                    c.shuffleModeEnabled = true
                }
                else -> {
                    c.repeatMode = Player.REPEAT_MODE_OFF
                    c.shuffleModeEnabled = false
                }
            }
        }
    }

    // ---------- 进度存档 ----------

    /**
     * 每 ~5 秒落一次盘。播到离结尾 3 秒内、或开头 3 秒内，就把记录清掉，
     * 免得下次进来停在片尾或在开头原地不动。
     *
     * 同一个节拍上顺带做两件事：累计收听时长、同步播放历史。
     * 收听时长按**实际经过的时间**算，不看播放位置——否则用户来回拖进度条会被算成「听了很久」。
     */
    private fun maybeSaveProgress() {
        val c = controller ?: return
        val track = _currentTrack.value ?: return
        if (c.mediaItemCount == 0) return
        val now = System.currentTimeMillis()
        if (lastProgressSave == 0L) lastProgressSave = now
        val elapsed = now - lastProgressSave
        if (elapsed < PROGRESS_SAVE_INTERVAL_MS) return
        // 后台被系统冻结过（Doze）时间隔可能是几小时，不能整段算成收听时长
        val counted = elapsed.coerceAtMost(PROGRESS_SAVE_INTERVAL_MS * 3)
        lastProgressSave = now

        val pos = c.currentPosition.coerceAtLeast(0L)
        val dur = if (c.duration > 0) c.duration else 0L
        val reachedEnd = dur > 0 && pos > dur - HistoryMath.END_TOLERANCE_MS && c.isPlaying
        val map = _progress.value
        val updated = when {
            // 播到片尾：清掉记录，下次从头开始
            reachedEnd -> map - track.path
            // 还在开头：只有**正在播放**时才清。否则「启动恢复后暂停在 0」会把续播记录抹掉
            pos < 3_000 -> if (c.isPlaying) map - track.path else map
            else -> map + (track.path to pos)
        }
        if (updated != map) {
            _progress.value = updated
            progressStore.save(updated)
        }

        if (c.isPlaying) historyListened(counted)
        historySync(track, pos, dur, now, reachedEnd)
    }

    // ---------- 曲目归属 / 展示 ----------

    /** 当前曲目所属的总项目。 */
    fun currentProject(): ProjectEntry? {
        val track = _currentTrack.value ?: return null
        val s = scan() ?: return null
        val pp = ProjectGrouper.projectRootOf(s.rootPath, track.folderPath)
        return s.projects.firstOrNull { it.path == pp }
    }

    fun tracksOfProject(projectPath: String): List<TrackEntry> {
        val s = scan() ?: return emptyList()
        return s.tracks.filter { ProjectGrouper.projectRootOf(s.rootPath, it.folderPath) == projectPath }
    }

    /** 当前在播的是不是视频。 */
    fun currentIsVideo(): Boolean {
        val p = _activePath.value ?: _currentTrack.value?.path ?: return false
        return isVideoPath(p)
    }

    /** 当前作品里的图片：曲目所在文件夹 + 作品根目录。 */
    fun imagesOfCurrent(): List<String> {
        val t = _currentTrack.value ?: return emptyList()
        val s = scan()
        val projectRoot = s?.let { ProjectGrouper.projectRootOf(it.rootPath, t.folderPath) }
        val dirs = listOfNotNull(t.folderPath.takeIf { it.isNotBlank() }, projectRoot).distinct()
        val out = ArrayList<String>()
        for (d in dirs) {
            val dir = File(d)
            if (!dir.isDirectory) continue
            dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                if (f.isFile && isImagePath(f.name)) out.add(f.absolutePath)
            }
        }
        return out.distinct()
    }

    fun variantsOfCurrent(): List<String> {
        val t = _currentTrack.value ?: return emptyList()
        val base = listOf(t.path) + t.altPaths
        // 兜底：扫描阶段没合上的（比如两边文件名写法不同），播放时再按「同作品 + 同名」找一次
        val s = scan() ?: return base
        val project = ProjectGrouper.projectRootOf(s.rootPath, t.folderPath)
        val target = normName(t.path)
        val extras = s.tracks
            .filter { it.path != t.path }
            .filter { ProjectGrouper.projectRootOf(s.rootPath, it.folderPath) == project }
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
        val cats = paths.map { p -> stripFormatTokens(File(p).parentFile?.name ?: "") }
        val distinct = cats.filter { it.isNotBlank() }.distinct()
        val useCat = distinct.size == cats.size && distinct.isNotEmpty()
        return paths.mapIndexed { i, p ->
            val ext = p.substringAfterLast('.', "").uppercase()
            p to if (useCat && cats[i].isNotBlank()) cats[i] + " · " + ext else ext
        }
    }

    /** 归一化文件名：小写 + 去掉空格/下划线/连字符，避免「01 track」与「01_track」对不上。 */
    private fun normName(path: String): String =
        File(path).nameWithoutExtension.lowercase().replace(Regex("[\\s_\\-]+"), "")

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
        notify("已切换到 " + path.substringAfterLast('.').uppercase())
    }

    // ---------- 队列 ----------

    /** 队列只恢复一次（连接和扫描谁先完成都行）。 */
    private var queueRestored = false

    /**
     * 播放列表 = 播放器里**真实的队列**（所以能增删）。
     * 从曲库重新点歌会整队重置成曲库的列表；播放器还没连上时回落到当前项目的音频。
     */
    fun currentPlaylist(): List<TrackEntry> {
        val c = controller
        if (c != null && c.mediaItemCount > 0) {
            val byPath = (scan()?.tracks ?: emptyList()).associateBy { it.path }
            val fromQueue = (0 until c.mediaItemCount).mapNotNull { i ->
                byPath[c.getMediaItemAt(i).mediaId]
            }
            if (fromQueue.isNotEmpty()) return fromQueue
        }
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

    /** 记下当前队列，下次启动恢复（媒体库被删掉的路径会自动跳过）。 */
    private fun saveQueue() {
        val c = controller ?: return
        val paths = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        if (paths.isEmpty()) return
        val current = _currentTrack.value?.path
        saveQueueSettings(paths, current)
    }

    /** 启动时把上次的队列装回播放器（暂停状态，不自动播放）。 */
    fun restoreQueueIfNeeded() {
        if (queueRestored) return
        val c = controller ?: return
        val s = scan() ?: return
        queueRestored = true
        if (c.mediaItemCount > 0) return
        val paths = settings().playQueue.split("\n").filter { it.isNotBlank() }
        if (paths.isEmpty()) return
        val byPath = s.tracks.associateBy { it.path }
        val tracks = paths.mapNotNull { byPath[it] }
        if (tracks.isEmpty()) {
            com.kiite.player.data.AppLog.log("上次队列里的文件都找不到了，忽略")
            return
        }
        val index = tracks.indexOfFirst { it.path == settings().playQueueCurrent }.coerceAtLeast(0)
        val resumeAt = progressOf(tracks[index].path).takeIf { it > 4_000 } ?: 0L
        c.setMediaItems(tracks.map { toMediaItem(it) }, index, resumeAt)
        c.prepare()
        c.pause()
        _currentTrack.value = tracks[index]
        _activePath.value = tracks[index].path
        onTrackChanged(tracks[index])
        _queueVersion.value++
        com.kiite.player.data.AppLog.log("恢复上次播放队列：" + tracks.size + " 首，当前=" + tracks[index].baseName)
    }

    /** 从播放队列里移除一首。 */
    fun queueRemove(index: Int) {
        val c = controller ?: return
        if (index < 0 || index >= c.mediaItemCount) return
        if (c.mediaItemCount <= 1) {
            notify("队列里只剩一首，不能再删")
            return
        }
        val title = c.getMediaItemAt(index).mediaMetadata.title?.toString().orEmpty()
        c.removeMediaItem(index)
        _queueVersion.value++
        onQueueChanged()
        saveQueue()
        notify("已从播放列表移除：" + title)
    }

    /** 往播放队列末尾加一首。 */
    fun queueAdd(track: TrackEntry) {
        val c = controller ?: return
        c.addMediaItem(toMediaItem(track))
        _queueVersion.value++
        onQueueChanged()
        saveQueue()
        notify("已加入播放列表：" + track.baseName)
    }

    /** 批量加入播放队列（曲库里多选后一次加进去）。 */
    fun queueAddAll(tracks: List<TrackEntry>) {
        if (tracks.isEmpty()) {
            notify("没有可加入播放列表的曲目")
            return
        }
        val c = controller ?: return
        tracks.forEach { c.addMediaItem(toMediaItem(it)) }
        _queueVersion.value++
        onQueueChanged()
        saveQueue()
        notify("已加入播放列表：" + tracks.size + " 首")
    }

    /** 把队列恢复成当前项目的全部音频（从曲库重新点歌也会自动走到这个状态）。 */
    fun queueReset() {
        val project = currentProject() ?: return
        val tracks = tracksOfProject(project.path)
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val index = tracks.indexOfFirst { it.path == _currentTrack.value?.path }.coerceAtLeast(0)
        c.setMediaItems(tracks.map { toMediaItem(it) }, index, 0L)
        c.prepare()
        _queueVersion.value++
        saveQueue()
        notify("播放列表已按曲库重置（" + tracks.size + " 首）")
    }

    // ---------- 从还活着的服务恢复现场 ----------

    /** 需要等曲库扫描完成才能按路径找到曲目，所以扫描完成后也要调一次。 */
    private var pendingRestorePath: String? = null

    fun restoreFromController(c: MediaController) {
        if (c.mediaItemCount == 0) return
        val path = c.currentMediaItem?.mediaId ?: return
        pendingRestorePath = path
        applyPendingRestore()
    }

    fun applyPendingRestore() {
        val path = pendingRestorePath ?: return
        val track = scan()?.tracks?.firstOrNull { t ->
            t.path == path || t.altPaths.contains(path)
        } ?: return
        pendingRestorePath = null
        _currentTrack.value = track
        _activePath.value = path
        onTrackChanged(track)
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f

        const val MODE_SEQUENCE = "sequence"
        const val MODE_LOOP = "loop"
        const val MODE_SHUFFLE = "shuffle"

        /** 播放页可选的倍数档位。 */
        val SPEED_STEPS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

        /** 位置保存 / 历史同步的节拍间隔。 */
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    }
}
