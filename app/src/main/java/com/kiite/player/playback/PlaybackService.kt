package com.kiite.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kiite.player.MainActivity
import com.kiite.player.data.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 后台播放 + 通知栏控制；点通知回到应用。
 *
 * 播放器实例只存在于服务里，**不挂在任何页面或 ViewModel 上**。
 * 睡眠定时器和 A-B 循环都在这里运行，所以退到后台、旋转屏幕、页面销毁都不会让它们失效。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var countdownJob: Job? = null
    private var abJob: Job? = null

    /** 播完这一首就停（按 mediaId 记，队列被改动也不会串位）。 */
    private var stopAtMediaId: String? = null

    /** 上次 A-B 跳回的时间，用于 seek 冷却。 */
    private var lastAbSeekAt = 0L

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player
        player.addListener(playerListener)

        // 点击系统播放通知 / 锁屏卡片时回到 MainActivity
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        // 通知副标题后面挂定时说明（没有定时时文案与原来完全一致）
        setMediaNotificationProvider(SleepNotificationProvider(this))

        instance = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        countdownJob = null
        abJob?.cancel()
        abJob = null
        exoPlayer?.let {
            runCatching { it.volume = 1f }
            it.removeListener(playerListener)
        }
        exoPlayer = null
        // 进程内状态随服务一起消失：不做跨进程恢复。
        // 理由是「渐停到一半」「A-B 循环到哪儿」都没有可恢复的语义。
        _sleep.value = SleepState.IDLE
        _ab.value = AbState.IDLE
        instance = null
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    // ---------- 播放器事件 ----------

    private val playerListener = object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A-B 是「一首曲目内」的时间区间，换曲目后 A/B 的秒数没有意义，必须清掉。
            // 否则会出现「跳到下一首却还在按上一首的时间段反复横跳」这种诡异行为。
            if (_ab.value.startMs != null) {
                _ab.value = AbState.IDLE
                cancelAbJob()
                postNotice("已切换曲目，A-B 循环已清除")
                AppLog.log("A-B 循环：曲目切换，已清除")
            }
            if (stopAtMediaId != null) {
                if (_sleep.value.mode == SleepStopMode.END_OF_TRACK) {
                    // 「播完本章停」跟着当前曲目走：手动切歌、重新点歌、队列被替换都算换了一章。
                    // 真正的「播完停住」由 pauseAtEndOfMediaItems 完成，走不到这里。
                    stopAtMediaId = mediaItem?.mediaId
                    if (stopAtMediaId == null) {
                        _sleep.value = SleepState.IDLE
                        return
                    }
                }
                syncPauseAtEnd()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // 只在真正播放时才跑 A-B 轮询，暂停就停掉，别每 50ms 白醒一次
            if (isPlaying) ensureAbJob() else cancelAbJob()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // 兜底：万一末尾的 playWhenReady 回调没按预期来（不同厂商 ROM 上出现过），
            // 队列放到底时也要把定时状态清掉、音量恢复，不能让下一次播放还是小声。
            if (playbackState == Player.STATE_ENDED && stopAtMediaId != null) {
                finishStopAtItem()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // ExoPlayer 在曲目末尾把我们停住了，说明「播完就停」已经生效
            if (!playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                stopAtMediaId != null
            ) {
                val label = if (_sleep.value.mode == SleepStopMode.END_OF_WORK) "本作品" else "本章"
                finishStopAtItem()
                postNotice("已播完$label，按定时停止")
                AppLog.log("睡眠定时：$label 已播完，已暂停播放")
            }
        }
    }

    // ---------- 睡眠定时 ----------

    /**
     * 催 Media3 重建通知。
     *
     * Media3 只在播放器事件（播放/暂停、切歌、进度）发生时重建通知，
     * 倒计时这种「没有任何播放器事件却在变」的状态它不会自己刷新，得每秒催一次。
     * 失败不抛出：通知刷不出来不该影响停止逻辑本身。
     */
    private fun refreshNotification() {
        val session = mediaSession ?: return
        runCatching { onUpdateNotification(session, false) }
            .onFailure { AppLog.log("刷新播放通知失败：" + it.message) }
    }

    /** 让 ExoPlayer 在目标曲目末尾自己停住，避免「下一首开头漏出半秒」的经典问题。 */
    private fun syncPauseAtEnd() {
        val player = exoPlayer ?: return
        val shouldPause = stopAtMediaId != null && player.currentMediaItem?.mediaId == stopAtMediaId
        if (player.pauseAtEndOfMediaItems != shouldPause) {
            player.pauseAtEndOfMediaItems = shouldPause
        }
    }

    private fun finishStopAtItem() {
        stopAtMediaId = null
        exoPlayer?.let {
            it.pauseAtEndOfMediaItems = false
            runCatching { it.volume = 1f }
        }
        _sleep.value = SleepState.IDLE
        refreshNotification()
    }

    private fun armDuration(totalMs: Long, fadeMs: Long) {
        cancelCountdown()
        stopAtMediaId = null
        exoPlayer?.let {
            it.pauseAtEndOfMediaItems = false
            runCatching { it.volume = 1f }
        }
        val player = exoPlayer ?: return
        _sleep.value = SleepState(
            mode = SleepStopMode.DURATION,
            remainingMs = totalMs,
            totalMs = totalMs,
            fadeMs = fadeMs,
        )
        refreshNotification()
        countdownJob = scope.launch {
            var remaining = totalMs
            // 通知正文只显示到秒：同一秒内不重复重建，省得每秒做好几次通知布局
            var lastNotifiedSecond = -1L
            while (remaining > 0L) {
                delay(TICK_MS)
                val step = SleepMath.step(remaining, TICK_MS, fadeMs)
                remaining = step.remainingMs
                // 暂停期间不压低音量（否则用户中途恢复播放会先听到一小段极小声）
                if (player.isPlaying) {
                    runCatching { player.volume = step.volume }
                } else if (player.volume != 1f) {
                    runCatching { player.volume = 1f }
                }
                _sleep.value = SleepState(
                    mode = SleepStopMode.DURATION,
                    remainingMs = remaining,
                    totalMs = totalMs,
                    fadeMs = fadeMs,
                )
                val second = (remaining + 999L) / 1_000L
                if (second != lastNotifiedSecond) {
                    lastNotifiedSecond = second
                    refreshNotification()
                }
                if (step.done) break
            }
            runCatching { player.volume = 1f }
            runCatching { player.pause() }
            _sleep.value = SleepState.IDLE
            // 定时结束后把「· 定时 0:00」从通知里摘掉
            refreshNotification()
            val hint = if (fadeMs > 0L) "定时结束，已渐停并暂停" else "定时结束，已暂停播放"
            postNotice(hint)
            AppLog.log(hint)
        }
    }

    private fun armStopAtItem(mediaId: String?, mode: SleepStopMode): Boolean {
        cancelCountdown()
        val player = exoPlayer ?: return false
        val target = mediaId ?: player.currentMediaItem?.mediaId ?: return false
        // 「播完就停」和 A-B 循环互斥：A-B 会一直往回跳，那个「播完」永远等不到。
        // 与其让用户以为设了两件事，不如只留他要的那件。
        if (_ab.value.active) {
            _ab.value = _ab.value.copy(enabled = false)
            cancelAbJob()
            postNotice("「播完就停」与 A-B 循环冲突，已暂停 A-B")
            AppLog.log("A-B 循环因设置播完停止而暂停")
        }
        stopAtMediaId = target
        _sleep.value = SleepState(mode = mode)
        syncPauseAtEnd()
        refreshNotification()
        AppLog.log(
            "睡眠定时：播完" +
                (if (mode == SleepStopMode.END_OF_WORK) "本作品" else "本章") +
                "后停止，目标=" + target.substringAfterLast('/'),
        )
        return true
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        exoPlayer?.let { runCatching { it.volume = 1f } }
    }

    private fun clearSleep() {
        cancelCountdown()
        stopAtMediaId = null
        exoPlayer?.pauseAtEndOfMediaItems = false
        _sleep.value = SleepState.IDLE
        refreshNotification()
    }

    // ---------- A-B 区间循环 ----------

    private fun ensureAbJob() {
        if (abJob?.isActive == true) return
        if (!_ab.value.active) return
        // 暂停时不挂轮询，等 onIsPlayingChanged 再来叫醒
        if (exoPlayer?.isPlaying != true) return
        abJob = scope.launch {
            while (isActive) {
                delay(AbLoopMath.POLL_MS)
                val player = exoPlayer ?: break
                val range = _ab.value.range ?: break
                if (!_ab.value.active || !player.isPlaying) continue
                val now = SystemClock.elapsedRealtime()
                // 播放器的位置回报有延迟，刚 seek 完可能还报旧位置，会触发第二轮 seek
                if (now - lastAbSeekAt < AbLoopMath.SEEK_COOLDOWN_MS) continue
                if (AbLoopMath.shouldJumpBack(player.currentPosition, range)) {
                    lastAbSeekAt = now
                    runCatching { player.seekTo(range.startMs) }
                }
            }
            abJob = null
        }
    }

    private fun cancelAbJob() {
        abJob?.cancel()
        abJob = null
    }

    private fun syncAbJob() {
        if (_ab.value.active) ensureAbJob() else cancelAbJob()
    }

    /** A-B 与睡眠定时的互斥处理：循环开始就别再等「播完」了。 */
    private fun dropConflictingSleep() {
        val mode = _sleep.value.mode
        if (mode == SleepStopMode.END_OF_TRACK || mode == SleepStopMode.END_OF_WORK) {
            clearSleep()
            postNotice("已取消「播完就停」定时，改为 A-B 循环")
            AppLog.log("睡眠定时因 A-B 循环被取消")
        }
    }

    private fun abSetStart(): Boolean {
        val player = exoPlayer ?: return false
        _ab.value = AbLoopMath.setStart(_ab.value, player.currentPosition, player.duration)
        syncAbJob()
        return true
    }

    private fun abSetEnd(): Boolean {
        val player = exoPlayer ?: return false
        val next = AbLoopMath.setEnd(_ab.value, player.currentPosition, player.duration)
        if (next == null) {
            postNotice(
                if (_ab.value.startMs == null) "请先设 A 点"
                else "B 点要在 A 点之后（至少间隔 ${AbLoopMath.MIN_GAP_MS / 1000.0} 秒）",
            )
            return false
        }
        _ab.value = next
        dropConflictingSleep()
        syncAbJob()
        return true
    }

    private fun abNudgeStart(deltaMs: Long) {
        _ab.value = AbLoopMath.nudgeStart(_ab.value, deltaMs)
    }

    private fun abNudgeEnd(deltaMs: Long) {
        val duration = exoPlayer?.duration ?: 0L
        _ab.value = AbLoopMath.nudgeEnd(_ab.value, deltaMs, duration)
    }

    private fun abToggle(): Boolean {
        val state = _ab.value
        if (!state.complete) return false
        val next = state.copy(enabled = !state.enabled)
        _ab.value = next
        if (next.enabled) dropConflictingSleep()
        syncAbJob()
        return true
    }

    private fun abClear() {
        _ab.value = AbState.IDLE
        cancelAbJob()
    }

    private fun postNotice(text: String) {
        notice = text
    }

    companion object {
        private const val TICK_MS = 200L

        @Volatile
        private var instance: PlaybackService? = null

        private val _sleep = MutableStateFlow(SleepState.IDLE)

        /** 定时状态。服务没起来时是 [SleepState.IDLE]。 */
        val sleepState: StateFlow<SleepState> = _sleep.asStateFlow()

        private val _ab = MutableStateFlow(AbState.IDLE)

        /** A-B 循环状态。 */
        val abState: StateFlow<AbState> = _ab.asStateFlow()

        /** 播放服务要告诉用户的一句话（定时到点、循环被清掉等），界面取走后清空。 */
        @Volatile
        private var notice: String? = null

        fun consumeNotice(): String? {
            val text = notice ?: return null
            notice = null
            return text
        }

        /**
         * 按分钟倒计时停止。
         * @param fadeMs 结束前的渐停时长，0 表示硬停。
         * @return false 表示播放服务尚未启动（此时设置定时没有意义）。
         */
        fun armDuration(totalMs: Long, fadeMs: Long): Boolean {
            val service = instance ?: return false
            if (totalMs <= 0L) {
                service.clearSleep()
                return true
            }
            service.armDuration(totalMs, fadeMs)
            return true
        }

        /**
         * 播完指定曲目后停止。
         * @param mediaId 目标曲目路径；null 时取当前曲目。
         */
        fun armStopAtItem(mediaId: String?, mode: SleepStopMode): Boolean {
            if (mode != SleepStopMode.END_OF_TRACK && mode != SleepStopMode.END_OF_WORK) return false
            val service = instance ?: return false
            return service.armStopAtItem(mediaId, mode)
        }

        /** 取消定时，并把音量恢复正常。 */
        fun clear(): Boolean {
            val service = instance ?: return false
            service.clearSleep()
            return true
        }

        /**
         * 队列变化后刷新「播完本作品停」的目标曲目。
         * 例如用户在中途删掉了队列里最后几首，作品边界会变。
         */
        fun retargetStopAtItem(mediaId: String?) {
            val service = instance ?: return
            if (_sleep.value.mode != SleepStopMode.END_OF_WORK) return
            service.stopAtMediaId = mediaId
            service.syncPauseAtEnd()
        }

        // ---------- A-B 循环命令 ----------

        /** 把 A 点设到当前位置。 */
        fun abSetStart(): Boolean = instance?.abSetStart() ?: false

        /** 把 B 点设到当前位置；B 不合法时会写一条提示。 */
        fun abSetEnd(): Boolean = instance?.abSetEnd() ?: false

        fun abNudgeStart(deltaMs: Long): Boolean {
            val service = instance ?: return false
            service.abNudgeStart(deltaMs)
            return true
        }

        fun abNudgeEnd(deltaMs: Long): Boolean {
            val service = instance ?: return false
            service.abNudgeEnd(deltaMs)
            return true
        }

        /** 在「循环中 / 已暂停」之间切换。 */
        fun abToggle(): Boolean = instance?.abToggle() ?: false

        fun abClear(): Boolean {
            val service = instance ?: return false
            service.abClear()
            return true
        }

        /** 播放服务是否已就绪。界面用它决定「定时」按钮是否可点。 */
        fun isReady(): Boolean = instance != null
    }
}
