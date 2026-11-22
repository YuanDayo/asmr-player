package com.kiite.player.ui.theme

import android.util.Log
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kiite.player.BuildConfig

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

/**
 * 当前在播曲目的唯一标识（曲目绝对路径）。
 *
 * 共享元素的 key 必须带上它：key 固定成 `now-playing-cover` 时，播放页里换了一首再返回，
 * 新封面会从**上一首**留下的位置起飞，看起来像封面「错位飞进了迷你条」。
 * 带 trackId 之后，换歌时两端的 key 不相等，共享元素自然不配对，退化成普通进出场。
 *
 * 由 `AppRoot` 在一处 provide：迷你条与播放页都在它下面，两端拿到的必然是同一首。
 */
val LocalNowPlayingTrack = compositionLocalOf<String?> { null }

/** 封面共享元素的 key：同一首歌两处一致，换歌后自动不配对。 */
private fun coverKey(trackId: String): String = "now-playing-cover:" + trackId

/**
 * 封面**正在飞**（迷你条 ⇄ 播放页这一段）。
 *
 * 为什么需要这个信号：飞行期间被共享的只有图片本身，播放页封面槽位那层灰色底板
 * （`surfaceVariant`）不参与飞行，于是它原地留在落点上，图片却在中途 —— 抓帧看得
 * 很清楚：一块目标尺寸的灰方块 + 一张小一号的封面叠在它左下角。用户读到的就是
 * 「卡片阴影变成方块」。
 *
 * 有了这个开关，落地端的底板在飞行期间是透明的：飞的过程中只有封面本身，
 * 落地那一帧图片盖满槽位，底板再出现也没有意义（被完全遮住）。
 *
 * 由 `AppRoot` 在切到/切离播放页时置位，时长取 [sharedBoundsSpec] 的长度。
 * 不用 `SharedContentState.isMatchFound`：那需要在第三个位置再取一次同 key 的
 * 共享状态，多一个注册点反而可能让配对逻辑糊涂（beta8 刚把配对修好，不赌这个）。
 */
val LocalCoverFlight = compositionLocalOf { false }

/**
 * 迷你条里那颗封面「已经被取走」。
 *
 * 上滑是拖拽驱动的：那条迷你条本身就是半屏那一格的长相，封面要在拖的过程中
 * 一路飞到卡片封面的位置 —— 飞的这颗由播放页画在自己的覆盖层里。
 * 条里（以及播放页里那份「迷你条原样」）那颗就得让位，否则同一张封面同时有两个，
 * 一个停在条里、一个在半空 —— 又回到 beta8 那个「灰方块 + 小一号封面」的老毛病。
 *
 * 只在「从迷你条往上拖」的那一次为 true；点一下进播放页仍走 sharedBounds 那套。
 */
val LocalBarCoverHidden = compositionLocalOf { false }

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
fun Modifier.sharedNowPlayingCover(): Modifier {
    val shared = LocalSharedTransitionScope.current
    val avs = LocalSharedElementScope.current
    val trackId = LocalNowPlayingTrack.current
    val anim = Animations.enabled()
    // 只要有一端没注册上，配对就永远不成立（SharedElement.states 只有 1 个），
    // 表现是封面「硬跳」而不是「飞」。光看画面分不出是没注册还是没配对，所以留一条诊断。
    if (CoverDiag) {
        remember(trackId, shared, avs, anim) {
            Log.d(
                CoverLogTag,
                "site key=$trackId scope=${shared != null} avs=${avs != null} " +
                    "anim=$anim avsId=${System.identityHashCode(avs)}",
            )
            Unit
        }
    }
    if (shared == null || avs == null || trackId == null || !anim) return this
    val clip = remember { RoundedCoverClip() }
    val state = shared.rememberSharedContentState(key = coverKey(trackId))
    if (CoverDiag) {
        // 配对成功时两端都会变成 true；只有一端为 true 说明另一端还没进场（或已离场）
        val matched = state.isMatchFound
        LaunchedEffect(matched) {
            Log.d(CoverLogTag, "match=$matched key=$trackId")
        }
    }
    return with(shared) {
        this@sharedNowPlayingCover.sharedBounds(
            state,
            animatedVisibilityScope = avs,
            boundsTransform = { _, _ -> sharedBoundsSpec() },
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Crop),
            clipInOverlayDuringTransition = clip,
        )
    }
}

private const val CoverLogTag = "KiiteCover"

/**
 * 共享元素诊断日志的开关。
 *
 * 平时只在 debug 包打；排查「封面不飞」这类问题时临时置 true，用**正式包**在模拟器上抓
 * logcat —— release 包走固定签名、能直接覆盖安装，不用为了看日志换一个 debug 包。
 * 一条 site 说明这个调用点有没有注册上，一条 match 说明两端有没有配对成功。
 */
private val CoverDiag = BuildConfig.DEBUG

/**
 * 共享封面在过渡覆盖层里的圆角裁剪。
 *
 * 为什么不用固定 dp：起点是 36dp 的迷你条封面（圆角 8dp），终点有两种 ——
 * 曲目卡封面 132dp（圆角 14dp）和全页大图档那张 300dp 左右（圆角 24dp）。
 * 同一个固定值在三端必然有两头不对，而「方角 → 圆角」的突变是一眼能看见的。
 *
 * 取值按比例 0.10 + 上限 24dp：落点那一帧最重要，132dp→13.2dp（真实 14dp）、
 * 300dp→24dp（真实 24dp），两头都只差不到 1dp；起点 36dp→3.6dp 比真实的 8dp
 * 略方，但那一刻封面正在**离开**已经被淡出的迷你条，比落地时的跳变不显眼得多。
 */
private const val CoverCornerFraction = 0.10f

/** 圆角上限：再大就成了药丸，反而不像封面。24dp 正好是全页大图档的真实值。 */
private val CoverCornerMax = 24.dp

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
