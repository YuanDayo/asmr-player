package com.kiite.player.playback

/**
 * A-B 区间循环：数据模型与纯计算。
 *
 * 和睡眠定时器一样放在 playback 包里——循环判定必须跟着播放器走，
 * 页面销毁后循环还得继续，否则用户切走再回来就"跳出去了"。
 *
 * 与 Media3 队列的关系（计划书 2.1 明确要求避免冲突）：
 * 本功能**只做同一首曲目内的 seek**，从不修改 repeatMode / shuffleMode，
 * 所以不会和「顺序 / 列表循环 / 随机」三种播放模式互相干扰。
 */

/** 一个已确定的循环区间。 */
data class AbRange(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    /** 起止间隔太小的区间视为无效，避免高频跳播把播放器卡死。 */
    val valid: Boolean get() = endMs - startMs >= AbLoopMath.MIN_GAP_MS
}

/**
 * @param startMs A 点；null 表示还没设。
 * @param endMs B 点；null 表示还没设。
 * @param enabled 设好之后是否正在循环（对应界面上的「暂停循环」）。
 */
data class AbState(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val enabled: Boolean = false,
) {
    val range: AbRange?
        get() = if (startMs != null && endMs != null) AbRange(startMs, endMs) else null

    val complete: Boolean get() = range?.valid == true

    /** 正在循环：区间有效且已启用。 */
    val active: Boolean get() = complete && enabled

    /** 只设了 A、还没设 B——界面上要提示「下一步设 B」。 */
    val waitingForEnd: Boolean get() = startMs != null && endMs == null

    companion object {
        val IDLE = AbState()
    }
}

object AbLoopMath {

    /** A 与 B 的最小间隔。低于这个值时循环会变成疯狂的连续 seek。 */
    const val MIN_GAP_MS = 300L

    const val NUDGE_STEP_MS = 100L
    const val NUDGE_STEP_LARGE_MS = 1_000L

    /** 位置轮询间隔。50ms 对应的最大过冲约 50ms，ASMR 场景听不出来。 */
    const val POLL_MS = 50L

    /** seek 之后的冷却时间：播放器位置回报有延迟，防止连续触发 seek。 */
    const val SEEK_COOLDOWN_MS = 300L

    private fun clampPosition(positionMs: Long, durationMs: Long): Long {
        val low = 0L
        val high = if (durationMs > 0L) durationMs else Long.MAX_VALUE / 2
        return positionMs.coerceIn(low, high)
    }

    /**
     * 把 A 点设到 [positionMs]。
     *
     * 如果原来的 B 点仍然在新 A 之后且间隔够大，就保留 B——这样「A 再往前挪一点」
     * 这种微调不会把辛苦对准的 B 点弄丢；否则连同 B 一起清掉，等用户重新设。
     */
    fun setStart(state: AbState, positionMs: Long, durationMs: Long): AbState {
        val pos = clampPosition(positionMs, durationMs)
        val end = state.endMs
        return if (end != null && end - pos >= MIN_GAP_MS) {
            state.copy(startMs = pos)
        } else {
            state.copy(startMs = pos, endMs = null, enabled = false)
        }
    }

    /**
     * 把 B 点设到 [positionMs]。
     * @return 起点缺失或 B 不在 A 之后（间隔不足）时返回 null，调用方给用户提示。
     */
    fun setEnd(state: AbState, positionMs: Long, durationMs: Long): AbState? {
        val start = state.startMs ?: return null
        val pos = clampPosition(positionMs, durationMs)
        if (pos - start < MIN_GAP_MS) return null
        return state.copy(endMs = pos, enabled = true)
    }

    /** 微调 A 点：不能越过 0，也不能贴到 B 上。 */
    fun nudgeStart(state: AbState, deltaMs: Long): AbState {
        val start = state.startMs ?: return state
        val maxStart = state.endMs?.minus(MIN_GAP_MS) ?: Long.MAX_VALUE / 2
        val next = (start + deltaMs).coerceIn(0L, maxStart.coerceAtLeast(0L))
        return state.copy(startMs = next)
    }

    /** 微调 B 点：不能越过 A，也不能超过曲目时长。 */
    fun nudgeEnd(state: AbState, deltaMs: Long, durationMs: Long): AbState {
        val end = state.endMs ?: return state
        val start = state.startMs ?: return state
        val minEnd = start + MIN_GAP_MS
        val maxEnd = (if (durationMs > 0L) durationMs else Long.MAX_VALUE / 2).coerceAtLeast(minEnd)
        val next = (end + deltaMs).coerceIn(minEnd, maxEnd)
        return state.copy(endMs = next)
    }

    /** 播放位置越过 B 点时是否需要跳回 A。 */
    fun shouldJumpBack(positionMs: Long, range: AbRange?): Boolean =
        range != null && range.valid && positionMs >= range.endMs

    /** 0:12.4 —— 精确调整要看得到 0.1 秒。 */
    fun formatClock(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalTenths = safe / 100L
        val minutes = totalTenths / 600L
        val seconds = (totalTenths % 600L) / 10L
        val tenths = totalTenths % 10L
        return "%d:%02d.%d".format(minutes, seconds, tenths)
    }

    /** 播放页「A-B」按钮上的文字。 */
    fun buttonLabel(state: AbState): String = when {
        state.startMs == null -> "A-B"
        state.waitingForEnd -> "设 B"
        !state.complete -> "A-B"
        state.active -> "A-B 循环"
        else -> "A-B 暂停"
    }

    /** 循环区间当前状态的说明文案。 */
    fun statusLabel(state: AbState): String = when {
        state.startMs == null -> "未设置区间"
        state.waitingForEnd -> "已设 A 点，再点一次「B 设到当前」"
        !state.complete -> "区间太短，请拉开 A / B 的距离"
        state.active -> "循环中 · " + formatClock(state.startMs) + " → " + formatClock(state.endMs!!)
        else -> "已暂停 · " + formatClock(state.startMs) + " → " + formatClock(state.endMs!!)
    }
}
