package com.kiite.player.ui

import com.kiite.player.core.Bookmark
import com.kiite.player.core.BookmarkMath
import com.kiite.player.core.TrackEntry
import com.kiite.player.data.BookmarkStore
import com.kiite.player.playback.AbLoopMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放书签：按音轨时间点记位置，与曲库里的「作品」无关。
 *
 * 位置取**播放器实时位置**，不用界面那份 400ms 轮询出来的值——后者总是偏后，
 * 加出来的书签会对不上用户刚听到的那句话。所以位置也走回调注入。
 */
class BookmarkController(
    private val store: BookmarkStore,
    private val currentTrack: () -> TrackEntry?,
    private val playbackPosition: () -> Long?,
    private val projectOfTrack: (TrackEntry) -> Pair<String, String>,
    private val trackOf: (String) -> TrackEntry?,
    private val queueFor: (TrackEntry, String) -> List<TrackEntry>,
    private val play: (TrackEntry, List<TrackEntry>, Long) -> Unit,
    private val seekTo: (Long) -> Unit,
    private val notify: (String) -> Unit,
    private val emitUndo: (UndoRequest) -> Unit,
    /** 第几句的时间轴起点（已应用校准）；没有时间轴时返回 null。 */
    private val lineStartMs: (Int) -> Long? = { null },
    /** 第几句的原文，存进书签做「台本换了也认得」的依据。 */
    private val lineText: (Int) -> String? = { null },
    /** 当前台本指纹，用来判断行号是否还有效。 */
    private val scriptFingerprint: () -> String = { "" },
) {
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    fun load() {
        runCatching { _bookmarks.value = store.load() }
    }

    private fun save() {
        store.save(_bookmarks.value)
    }

    /** 当前这一集的书签，按时间点升序。 */
    fun ofCurrentTrack(): List<Bookmark> {
        val path = currentTrack()?.path ?: return emptyList()
        return BookmarkMath.ofTrack(_bookmarks.value, path)
    }

    /** 在当前位置加一条书签。同一位置附近（[BookmarkMath.NEAR_MS] 内）会合并到已有那条。 */
    fun addNow(note: String = ""): Bookmark? {
        val track = currentTrack() ?: run { notify("先放一首再加书签"); return null }
        val pos = playbackPosition() ?: run { notify("播放服务还没起来，稍后再试"); return null }
        val (pp, pn) = projectOfTrack(track)
        val bm = Bookmark(
            id = java.util.UUID.randomUUID().toString(),
            trackPath = track.path,
            trackName = track.baseName,
            projectPath = pp,
            projectName = pn,
            positionMs = pos,
            note = note.trim(),
            createdAtMs = System.currentTimeMillis(),
        )
        val outcome = BookmarkMath.add(_bookmarks.value, bm)
        _bookmarks.value = outcome.bookmarks
        save()
        if (outcome.merged) {
            notify("这里已有一条书签，已合并过去")
        } else {
            notify("已加书签 " + AbLoopMath.formatClock(pos))
        }
        return outcome.bookmarks.firstOrNull {
            it.trackPath == bm.trackPath && it.positionMs == bm.positionMs
        } ?: bm
    }

    /** 当前这一集被标了书签的台本行号，供播放页给行上的小图标打亮。 */
    fun bookmarkedLines(): Set<Int> {
        val path = currentTrack()?.path ?: return emptySet()
        return BookmarkMath.lineIndexesOf(_bookmarks.value, path)
    }

    /**
     * 点亮/熄灭台本第 [index] 句的书签。
     *
     * 位置取**那一句的时间轴起点**，而不是当前的播放位置：
     * 用户点的是「这一句」，将来跳回来也该落在这一句的开头，而不是当初手指按下时的那一秒。
     */
    fun toggleLine(index: Int): Boolean? {
        val track = currentTrack() ?: run {
            notify("先放一首再给这一句加书签")
            return null
        }
        val startMs = lineStartMs(index) ?: run {
            notify("这一句还没有时间轴，先在顶部校准一下")
            return null
        }
        val (pp, pn) = projectOfTrack(track)
        val bm = Bookmark(
            id = java.util.UUID.randomUUID().toString(),
            trackPath = track.path,
            trackName = track.baseName,
            projectPath = pp,
            projectName = pn,
            positionMs = startMs.coerceAtLeast(0L),
            note = "",
            createdAtMs = System.currentTimeMillis(),
            lineIndex = index,
            lineText = lineText(index).orEmpty(),
            scriptFingerprint = scriptFingerprint(),
        )
        val outcome = BookmarkMath.toggleLine(_bookmarks.value, bm)
        _bookmarks.value = outcome.bookmarks
        save()
        notify(
            if (outcome.added) "已标记第 ${index + 1} 句" else "已取消第 ${index + 1} 句的标记",
        )
        return outcome.added
    }

    fun updateNote(id: String, note: String) {
        _bookmarks.value = BookmarkMath.updateNote(_bookmarks.value, id, note)
        save()
    }

    fun delete(id: String) {
        val target = _bookmarks.value.firstOrNull { it.id == id } ?: return
        _bookmarks.value = BookmarkMath.remove(_bookmarks.value, id)
        save()
        emitUndo(
            UndoRequest("已删除书签 " + AbLoopMath.formatClock(target.positionMs)) {
                if (_bookmarks.value.none { it.id == target.id }) {
                    _bookmarks.value = _bookmarks.value + target
                    save()
                }
            },
        )
    }

    /** 点书签：同一集就跳过去；别的集先切歌再定位到那个时间点。 */
    fun open(bm: Bookmark) {
        val current = currentTrack()
        if (current != null && current.path == bm.trackPath) {
            seekTo(bm.positionMs)
            return
        }
        val track = trackOf(bm.trackPath)
        if (track == null) {
            notify("这一集的文件已删除或移动，书签无法跳转")
            return
        }
        play(track, queueFor(track, bm.projectPath), bm.positionMs)
    }
}
