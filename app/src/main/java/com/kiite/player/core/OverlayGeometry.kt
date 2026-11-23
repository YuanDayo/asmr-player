package com.kiite.player.core

/**
 * 桌面歌词浮窗的几何算术：位置钳制、贴边吸附、单句进度。
 *
 * 为什么单独抽出来：浮窗本体（`LyricOverlayController`）只有挂到 WindowManager 之后
 * 才活得起来，单测里跑不了；但「拖到屏幕外就再也找不回来」「横竖屏切换后坐标失效」
 * 这类事故，坏的地方全在这几行算术上。抽成纯函数就能在 JVM 里直接验。
 *
 * 所有坐标都是**像素**，屏幕尺寸取 `displayMetrics`（不含导航栏之外的虚拟区域），
 * 安全区由调用方算好传进来（状态栏 / 刘海高度）。
 */
object OverlayGeometry {

    /** 钳制后的位置（像素，相对屏幕左上角）。 */
    data class Pos(val x: Int, val y: Int)

    /**
     * 把窗口位置拉回可视区。
     *
     * - x ∈ [0, screenW - w]
     * - y ∈ [safeTop, screenH - h]，其中 safeTop 是状态栏/刘海高度（这块钱根本压不进去）
     *
     * 窗口比屏幕还大时不再谈「边界」，直接贴左上：否则 max < min，`coerceIn` 会抛异常。
     */
    fun clamp(
        x: Int,
        y: Int,
        screenW: Int,
        screenH: Int,
        w: Int,
        h: Int,
        safeTop: Int = 0,
    ): Pos {
        val maxX = (screenW - w).coerceAtLeast(0)
        val top = safeTop.coerceIn(0, (screenH - h).coerceAtLeast(0))
        val maxY = (screenH - h).coerceAtLeast(top)
        return Pos(x.coerceIn(0, maxX), y.coerceIn(top, maxY))
    }

    /**
     * 松手时的贴边吸附：离左/右边缘不到 [threshold] 就吸过去，其余位置原样返回。
     * 只吸左右不吸上下 —— 上下贴边会把浮窗顶到状态栏底下或压在导航条上。
     */
    fun snapToEdge(x: Int, screenW: Int, w: Int, threshold: Int): Int {
        if (threshold <= 0) return x
        val maxX = (screenW - w).coerceAtLeast(0)
        return when {
            x <= threshold -> 0
            x >= maxX - threshold -> maxX
            else -> x
        }
    }

    /**
     * 当前这一句的进度 0..1。
     *
     * 没有结束时间（最后一句、或台本只给了起始时间）时返回 null：这时画进度条没有意义，
     * 硬按「下一句开始」当终点又会在最后一句上卡在 0%。
     */
    fun lineProgress(positionMs: Long, startMs: Long, endMs: Long?): Float? {
        val end = endMs ?: return null
        val span = end - startMs
        if (span <= 0L) return null
        return ((positionMs - startMs).toFloat() / span).coerceIn(0f, 1f)
    }

    /**
     * 当前句还剩几秒（向上取整）。
     *
     * 向上取整是为了不出现「还剩 0 秒」还继续念半句的观感；已经过点就返回 0。
     * 没有结束时间返回 null（不知道就不显示，别编一个）。
     */
    fun remainingSec(positionMs: Long, endMs: Long?): Long? {
        val end = endMs ?: return null
        val left = end - positionMs
        if (left <= 0L) return 0L
        return (left + 999L) / 1000L
    }
}
