package com.kiite.player.ui.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

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
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedNowPlayingCover(key: String = NowPlayingCoverKey): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val avs = LocalSharedElementScope.current ?: return this
    if (!Animations.enabled()) return this
    return with(shared) {
        this@sharedNowPlayingCover.sharedBounds(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = avs,
            boundsTransform = { _, _ -> tween(Motion.MEDIUM) },
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Crop),
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
