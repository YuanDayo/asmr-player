package com.kiite.player.ui.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 共享元素（MiniPlayer 封面 → 播放页封面）。
 *
 * 为什么需要两个 CompositionLocal：
 * - `SharedTransitionLayout` 只有在作用域内部调用 `sharedElement`/`sharedBounds` 才生效，
 *   而播放页封面藏在 PlayerScreen → TrackCard → MediaBox → CoverBox 四层之下，
 *   一层层传参会把「播放器业务参数」污染成「动画参数」，用 CompositionLocal 下发最省事。
 * - `AnimatedVisibilityScope` 是 sharedBounds 的必填参数，它决定元素何时算「在场」。
 *   MiniPlayer 用它自己那层 `AnimatedVisibility` 的 scope，播放页用 `AnimatedContent` 的
 *   （AnimatedContentScope 实现了 AnimatedVisibilityScope）。
 *
 * 取不到任一 scope 时扩展函数原样返回 Modifier —— 这样组件在 Preview、单测、
 * 以及未套 SharedTransitionLayout 的宿主里都能正常渲染，只是没有动画。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalSharedElementScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** 现在播放这首的封面。MiniPlayer 与播放页两处必须用同一个 key 才会配对。 */
const val NowPlayingCoverKey = "now-playing-cover"

/**
 * 把当前 Modifier 标记为「正在播放的封面」的共享元素。
 *
 * `resizeMode = ScaleToBounds(Crop)` 很关键：直接从 36dp 缩到 132dp 时，
 * 默认的 ScaleToBounds 只比宽度，高度会被拉变形；用 Crop 让图片像 `ContentScale.Crop`
 * 一样铺满目标框，过渡期间不会出现被压扁的封面。
 *
 * `clipInOverlayDuringTransition` 也必须给：过渡期间元素画在 SharedTransitionLayout 的
 * 覆盖层里，**两端各自的圆角裁剪（父 Box 的 clip）不参与覆盖层绘制**，于是飞行中是一块
 * 四四方方的图，落地才「啪」地变回圆角 —— 2.5.3 用户反馈的就是这个。这里改用按短边
 * 比例算的圆角裁剪，全程都是圆角（见 [RoundedCoverClip]）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedNowPlayingCover(key: String = NowPlayingCoverKey): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val avs = LocalSharedElementScope.current ?: return this
    if (!Animations.enabled()) return this
    val clip = remember { RoundedCoverClip() }
    return with(shared) {
        this@sharedNowPlayingCover.sharedBounds(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = avs,
            boundsTransform = { _, _ -> tween(Motion.MEDIUM) },
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Crop),
            clipInOverlayDuringTransition = clip,
        )
    }
}

/**
 * 共享封面在过渡覆盖层里的圆角裁剪。
 *
 * 为什么不用固定 dp：起点是 36dp 的迷你条封面（圆角 8dp，比例 0.22），
 * 终点是 132dp 的曲目卡封面（圆角 14dp，比例 0.11）—— 同一个 dp 值在两端必然有一头不对。
 * 按短边比例算，尺寸一路变化时圆角连续变化，两端分别差 2～3dp，肉眼看不出来；
 * 而「方角 → 圆角」的突变是能一眼看见的（这次修的就是它）。
 */
private const val CoverCornerFraction = 0.15f

/** 圆角上限：再大就成了药丸，反而不像封面。 */
private val CoverCornerMax = 16.dp

@OptIn(ExperimentalSharedTransitionApi::class)
private class RoundedCoverClip : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        state: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path = Path().apply {
        val radius = with(density) {
            val byRatio = minOf(bounds.width, bounds.height) * CoverCornerFraction
            byRatio.coerceAtMost(CoverCornerMax.toPx())
        }
        addRoundRect(
            RoundRect(
                rect = bounds,
                cornerRadius = CornerRadius(radius, radius),
            ),
        )
    }
}


/**
 * 系统「动画时长缩放」为 0 时视为用户关闭了动画（开发者选项 / 无障碍设置里很常见）。
 * 此时不做共享元素，退化成普通替换 —— 这是计划书里说的「低端机降级」最可靠的信号源，
 * 比按 SDK 版本猜设备性能准确得多。
 */
object Animations {
    @Composable
    fun enabled(): Boolean {
        val context = LocalContext.current
        return remember(context) {
            runCatching {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }.getOrDefault(1f) > 0f
        }
    }
}
