package com.kiite.player.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt

/**
 * 动效令牌：时长、曲线、标准 spec。
 *
 * 为什么单独一个文件：原先 `Motion` 挤在 `Dimens.kt` 里，只有时长和曲线两个常量，
 * 于是每个动画各自 `tween(160)` / `tween(200)` / `tween(220)` —— 同一类动作在不同页面
 * 差 60ms，肉眼看不出来但手感不一致。现在所有动画都从这里的三个入口拿 spec：
 *
 * - [standard] 普通变化（尺寸、颜色、位置）；
 * - [enter] 进入画面（减速）；
 * - [exit] 离开画面（加速，比进入略短）。
 *
 * 曲线语义：Android 的 FastOutSlowIn 等价于 [Standard]；进入用 [Decelerate] 让元素
 * 「落位」，离开用 [Accelerate] 让元素「让开」而不抢视线。
 */
object Motion {
    /** 150ms：状态切换、图标变化、淡入淡出。 */
    const val SHORT = 150

    /** 220ms：展开/收起、页面切换、共享元素飞行。 */
    const val MEDIUM = 220

    /** 400ms：大范围布局变化（整页骨架换挡）。 */
    const val LONG = 400

    /** 900ms：装饰性环境动画（均衡器条）的单程时长，只影响节奏，不影响状态切换。 */
    const val AMBIENT = 900

    /** 标准缓动（FastOutSlowIn）：进入用，比线性更「跟手」。 */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 减速：元素进入画面。 */
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** 加速：元素离开画面。 */
    val Accelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /**
     * 全局动效倍率：1 = 正常，0 = 直接切换。
     *
     * 由 `AsmrTheme` 按「系统动画缩放 × 应用内简化动效开关」写入，见 `MotionScale.kt`。
     * 用全局变量而不是 CompositionLocal 是被调用点逼的：spec 大量在
     * `transitionSpec = {}`、`animateDpAsState(...)` 这类**非可组合**位置构造，
     * 那里读不到 CompositionLocal；而 spec 又必须每次重建才能让开关立刻生效，
     * 缓存成常量反而会让「关掉动效」失灵。
     */
    @Volatile
    var scale: Float = 1f
        private set

    fun applyScale(value: Float) {
        scale = value
    }

    /** 基准时长换算成实际毫秒；倍率为 0 时返回 0（Compose 里等于立即到位）。 */
    fun ms(base: Int): Int = (base * scale).roundToInt().coerceAtLeast(0)

    /** 普通变化：尺寸 / 颜色 / 位置。 */
    fun <T> standard(base: Int = MEDIUM): TweenSpec<T> = tween(ms(base), easing = Standard)

    /** 进入：减速，起步快、落位稳。 */
    fun <T> enter(base: Int = MEDIUM): TweenSpec<T> = tween(ms(base), easing = Decelerate)

    /** 离开：加速，默认比进入更短，避免挡住下一个动作。 */
    fun <T> exit(base: Int = SHORT): TweenSpec<T> = tween(ms(base), easing = Accelerate)

    /**
     * 装饰性循环动画（均衡器条、脉冲点）的单程 spec。
     *
     * 故意**不吃** [scale]：倍率降到 0 时无限循环会变成「0 毫秒一轮」的空转，
     * 一直请求新帧，比不动画面更耗电。这类动画跟「有没有动效」是两件事。
     */
    fun ambient(): TweenSpec<Float> = tween(AMBIENT, easing = LinearEasing)
}
