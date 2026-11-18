package com.kiite.player.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 「从被点的那张卡片放大」的转场原点。
 *
 * 曲库点作品的放大转场（[zoomIn] / [zoomOut]）需要知道两件事：卡片在屏幕上的中心、
 * 内容区的矩形。两者的比值就是 `scaleIn` 的 transformOrigin。
 *
 * 为什么自己记坐标、而不是给卡片加一层 `onGloballyPositioned` 往 State 里写：
 * 那个回调**在列表滚动时每帧都会跑**（每个可见项各一次）。往 snapshot state 写会顺手
 * 触发一次重组，往 State 里存 Rect 还要每帧分配一个对象 —— 2.5.5-beta2 已经吃过一次
 * 「看不见的动效把掉帧从 5% 抬到 14%」的教训。这里只存 LayoutCoordinates 的引用
 * （零分配、不写 state），真正取中心点只在**点击那一帧**发生。
 */
class ZoomOrigin {

    @Volatile
    private var card: LayoutCoordinates? = null

    /** 最近一次被点卡片的中心（根坐标）；null = 还没点过。 */
    @Volatile
    var cardCenter: Offset? = null
        private set

    /** 内容区（列表 / 详情所在的那块）在根坐标里的矩形。 */
    @Volatile
    var containerRect: Rect = Rect.Zero

    /** 由卡片自己的 [onGloballyPositioned] 调用，只存引用。 */
    fun rememberCard(coords: LayoutCoordinates) {
        card = coords
    }

    /** 点击时调用：把卡片中心定格下来，供即将开始的转场使用。 */
    fun capture() {
        val c = card ?: return
        if (!c.isAttached) return
        cardCenter = runCatching { c.boundsInRoot().center }.getOrNull()
    }

    /**
     * 转场原点：卡片中心换算成内容区的比例。
     *
     * 卡片可能已经被滚出屏幕（返回时），比例会超出 0..1，这里只做粗略夹取 ——
     * 夹到 ±0.25 之外没有额外意义，`scaleIn` 允许原点在边界外。
     */
    fun transformOrigin(): TransformOrigin {
        val center = cardCenter ?: return TransformOrigin.Center
        val r = containerRect
        if (r.width <= 0f || r.height <= 0f) return TransformOrigin.Center
        return TransformOrigin(
            pivotFractionX = ((center.x - r.left) / r.width).coerceIn(-0.25f, 1.25f),
            pivotFractionY = ((center.y - r.top) / r.height).coerceIn(-0.25f, 1.25f),
        )
    }
}

/**
 * 下发转场原点。用 CompositionLocal 而不是一路加参数：
 * 卡片在 `ProjectList → 行 / 表格行 → 卡片` 三层之下，加参数会把「列表业务参数」
 * 污染成「动画参数」（与共享封面同一套考虑，见 SharedTransition.kt）。
 * 宽屏（左右分栏）不提供，卡片自然就没有原点。
 */
val LocalZoomOrigin = compositionLocalOf<ZoomOrigin?> { null }

/** 让这张卡在点击时能报出自己在屏幕上的位置（只存引用，不分配、不重组）。 */
fun Modifier.reportZoomOrigin(origin: ZoomOrigin): Modifier =
    this.onGloballyPositioned { coords -> origin.rememberCard(coords) }
