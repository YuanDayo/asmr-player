package com.kiite.player.playback

/**
 * 睡眠定时器：状态定义与纯计算。
 *
 * 为什么放在 playback 包而不是 ViewModel 里：
 * 定时必须和**播放器**同生命周期。放在 ViewModel 的 viewModelScope 里，一旦页面销毁、
 * 配置变更或系统回收 UI，定时就会无声消失——而 AudioPlayer 还在后台放，用户以为设了定时，
 * 结果一晚上都在响。这里只留纯逻辑，实际调度由 [PlaybackService] 持有。
 */

enum class SleepStopMode {
    /** 没有设置定时。 */
    OFF,

    /** 按分钟倒计时停止，可带渐停。 */
    DURATION,

    /** 播完当前章节（当前这一首）后停止。 */
    END_OF_TRACK,

    /** 播完当前作品（当前项目的最后一首）后停止。 */
    END_OF_WORK,
}

/**
 * @param remainingMs 仅 [SleepStopMode.DURATION] 有意义，剩余毫秒。
 * @param totalMs 本次定时设定的总时长，用于界面画剩余比例。
 * @param fadeMs 本次定时设定的渐停时长；0 表示硬停。
 */
data class SleepState(
    val mode: SleepStopMode = SleepStopMode.OFF,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val fadeMs: Long = 0L,
) {
    val active: Boolean get() = mode != SleepStopMode.OFF

    /** 是否已进入渐停区间（音量正在下降）。 */
    val fading: Boolean
        get() = mode == SleepStopMode.DURATION && fadeMs > 0L && remainingMs <= fadeMs

    companion object {
        val IDLE = SleepState()
    }
}

/** 倒计时一次 tick 的结果。 */
data class SleepStep(
    val remainingMs: Long,
    val volume: Float,
    /** 剩余时间已耗尽，调用方应暂停播放并清理定时。 */
    val done: Boolean,
)

object SleepMath {

    /** 渐停时长档位（毫秒）。 */
    val FADE_OPTIONS_MS: List<Long> = listOf(0L, 5_000L, 10_000L, 20_000L, 30_000L)

    /** 分钟档位。 */
    val MINUTE_OPTIONS: List<Int> = listOf(5, 10, 15, 30, 45, 60, 90)

    /**
     * 线性衰减的音量曲线。
     *
     * 用线性振幅而不是线性「感知音量」：睡眠场景下用户已经在半睡状态，
     * 线性振幅在最后几秒会掉得比较快，反而更容易让人察觉「声音突然没了」；
     * 实测下来线性足够平顺，也避免了曲线参数带来的额外调参成本。
     *
     * @return 0f..1f。fadeMs <= 0 时恒为 1f（硬停，不做音量处理）。
     */
    fun volumeAt(remainingMs: Long, fadeMs: Long): Float {
        if (fadeMs <= 0L) return 1f
        if (remainingMs >= fadeMs) return 1f
        if (remainingMs <= 0L) return 0f
        return (remainingMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 推进一次倒计时。
     *
     * 关键边界：[fadeMs] 大于 [totalMs] 时，第 0 秒就应当已经处于渐停区间，
     * 不允许出现 `remaining > fade` 但音量已经是 1 的假阴性；
     * 这里的实现天然满足——剩余时间不会超过总时长，[volumeAt] 只看剩余与渐停的相对大小。
     */
    fun step(remainingMs: Long, stepMs: Long, fadeMs: Long): SleepStep {
        val left = (remainingMs - stepMs).coerceAtLeast(0L)
        return SleepStep(
            remainingMs = left,
            volume = volumeAt(left, fadeMs),
            done = left <= 0L,
        )
    }

    /** 0:00 形式，向上取整，避免 0.4 秒显示成 0:00 却还在响。 */
    fun formatRemaining(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) + 999L) / 1_000L
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    fun fadeLabel(ms: Long): String = if (ms <= 0L) "不渐停" else "${ms / 1_000} 秒渐停"

    /** 播放页「定时停止」按钮上的文字。 */
    fun buttonLabel(state: SleepState): String = when (state.mode) {
        SleepStopMode.OFF -> "定时停止"
        SleepStopMode.DURATION ->
            (if (state.fadeMs > 0L && state.fading) "渐停 " else "定时 ") +
                formatRemaining(state.remainingMs)
        SleepStopMode.END_OF_TRACK -> "本章后停"
        SleepStopMode.END_OF_WORK -> "本作品后停"
    }
}
