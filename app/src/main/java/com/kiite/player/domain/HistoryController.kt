package com.kiite.player.domain

import com.kiite.player.core.HistoryMath
import com.kiite.player.core.HistoryRecord
import com.kiite.player.core.TrackEntry
import com.kiite.player.data.HistoryFile
import com.kiite.player.data.HistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放历史：记录、进度同步、收听时长、失效判断。
 *
 * 抽出来的边界是「谁的事谁管」：历史只关心「哪一集、听到哪、听完没、今天听了多久」，
 * 不关心队列怎么建、播放模式是什么——这些通过 [queueFor] / [play] 回调要。
 *
 * 落盘走单独的写线程 + CONFLATED 队列：每 5 秒一次的位置同步都会触发保存，
 * 直接在主线程序列化几十 KB 的 JSON 会卡住滚动；写慢时只保留最新一份。
 */
class HistoryController(
    private val store: HistoryStore,
    private val scope: CoroutineScope,
    private val projectOfTrack: (TrackEntry) -> Pair<String, String>,
    private val trackOf: (String) -> TrackEntry?,
    private val queueFor: (TrackEntry, String) -> List<TrackEntry>,
    private val play: (TrackEntry, List<TrackEntry>, Long) -> Unit,
    /** 播放器上报的时长（拿不到时回落到曲目自带时长）。 */
    private val mediaDuration: () -> Long,
    private val notify: (String) -> Unit,
    private val emitUndo: (UndoRequest) -> Unit,
) {
    private val _history = MutableStateFlow(HistoryFile())
    val history: StateFlow<HistoryFile> = _history.asStateFlow()

    private val saveChannel = Channel<HistoryFile>(Channel.CONFLATED)

    fun load() {
        runCatching { _history.value = store.load() }
    }

    /** 写盘协程。挂在 ViewModel 的 scope 上，随它一起收掉。 */
    fun startWriter() {
        scope.launch(Dispatchers.IO) {
            for (state in saveChannel) store.save(state)
        }
    }

    private fun save() {
        saveChannel.trySend(_history.value)
    }

    /** 开始播放时记一条（置顶、次数 +1、清掉「已听完」）。 */
    fun recordStart(track: TrackEntry, positionMs: Long = 0L) {
        val (pp, pn) = projectOfTrack(track)
        val dur = mediaDuration().takeIf { it > 0 } ?: (track.durationMs ?: 0L)
        val records = HistoryMath.onStart(
            _history.value.records,
            trackPath = track.path,
            trackName = track.baseName,
            projectPath = pp,
            projectName = pn,
            positionMs = positionMs,
            durationMs = dur,
            nowMs = System.currentTimeMillis(),
        )
        _history.value = _history.value.copy(records = records)
        save()
    }

    /** 补记「这一集听完了」。播放器自己切走 / 队列放到底时用。 */
    fun markFinished(trackPath: String) {
        val state = _history.value
        val records = HistoryMath.onFinished(state.records, trackPath, System.currentTimeMillis())
        if (records != state.records) {
            _history.value = state.copy(records = records)
            save()
        }
    }

    /** 播放中的同步。记录不存在时补一条（应用重启后直接续播会走到这里）。 */
    fun sync(track: TrackEntry, pos: Long, dur: Long, now: Long, finished: Boolean) {
        var records = _history.value.records
        if (records.none { it.trackPath == track.path }) {
            val (pp, pn) = projectOfTrack(track)
            records = HistoryMath.onStart(records, track.path, track.baseName, pp, pn, pos, dur, now)
        }
        records = if (finished) {
            HistoryMath.onFinished(records, track.path, now)
        } else {
            HistoryMath.onProgress(records, track.path, pos, dur, now)
        }
        val state = _history.value
        if (records != state.records) {
            _history.value = state.copy(records = records)
            save()
        }
    }

    /**
     * 累计收听时长。按**实际经过的时间**算，不看位置差——
     * 否则来回拖进度条就等于「刷时长」。
     */
    fun addListenedTime(deltaMs: Long) {
        val state = _history.value
        val (daily, total) = HistoryMath.addListened(
            state.dailyListenedMs,
            state.totalListenedMs,
            deltaMs,
            System.currentTimeMillis(),
        )
        if (daily != state.dailyListenedMs || total != state.totalListenedMs) {
            // 不在这里落盘：紧接着的历史同步会写一次，10 秒的时长差不必多写一个文件
            _history.value = state.copy(dailyListenedMs = daily, totalListenedMs = total)
        }
    }

    /** 从历史里继续播。文件已失效时给明确提示，而不是静默失败。 */
    fun continueFrom(record: HistoryRecord) {
        val track = trackOf(record.trackPath)
        if (track == null) {
            notify("这一集的文件已删除或移动，无法播放")
            return
        }
        play(track, queueFor(track, record.projectPath), HistoryMath.resumePositionOf(record))
    }

    fun remove(trackPath: String) {
        val state = _history.value
        val target = state.records.firstOrNull { it.trackPath == trackPath } ?: return
        _history.value = state.copy(records = HistoryMath.remove(state.records, trackPath))
        save()
        emitUndo(
            UndoRequest("已从历史中移除：" + target.trackName) {
                _history.value = _history.value.copy(
                    records = HistoryMath.prune(_history.value.records + target),
                )
                save()
            },
        )
    }

    fun clear() {
        val old = _history.value.records
        if (old.isEmpty()) return
        _history.value = _history.value.copy(records = emptyList())
        save()
        emitUndo(
            UndoRequest("已清空播放历史") {
                _history.value = _history.value.copy(records = old)
                save()
            },
        )
    }
}
