package com.kiite.player.playback

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.session.DefaultMediaNotificationProvider

/**
 * 在系统媒体通知的副标题后面挂一段定时说明（「· 定时 12:34」「· 播完本章停」）。
 *
 * 为什么用继承而不是另起一条通知：
 * 另起一条意味着通知栏里常驻两条（媒体卡 + 定时卡），退到桌面看反而更乱；
 * 而 `DefaultMediaNotificationProvider` 的 `createNotification` 是 final，能改的只有正文两行，
 * 正好够用——播放控制、封面、进度条全都照旧由系统那套画。
 *
 * 正文每秒要变，但 Media3 只在播放器事件发生时重建通知，
 * 所以由 [PlaybackService] 在倒计时 loop 里主动调 `onUpdateNotification` 催它重画。
 */
class SleepNotificationProvider(context: Context) :
    DefaultMediaNotificationProvider(context) {

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence {
        val base = super.getNotificationContentText(metadata)?.toString().orEmpty()
        val tail = sleepSuffix()
        return if (tail.isEmpty()) base else base + tail
    }

    /** 定时说明：没有定时时返回空串，此时通知保持原样（与未改之前完全一致）。 */
    private fun sleepSuffix(): String {
        val state = PlaybackService.sleepState.value
        return when (state.mode) {
            SleepStopMode.DURATION -> {
                // 进入渐停区间后说明「正在渐停」，比单纯倒计时更能解释音量为什么在变小
                if (state.fading) {
                    " · 渐停 " + SleepMath.formatRemaining(state.remainingMs)
                } else {
                    " · 定时 " + SleepMath.formatRemaining(state.remainingMs)
                }
            }
            SleepStopMode.END_OF_TRACK -> " · 播完本章停"
            SleepStopMode.END_OF_WORK -> " · 播完本作品停"
            SleepStopMode.OFF -> ""
        }
    }
}
