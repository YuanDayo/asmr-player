package com.kiite.player.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 设计令牌（Design Token）：间距 / 圆角 / 图标 / 触控目标 / 动效。
 *
 * 规则很简单：**页面层不再随手写 13.dp、14.dp 这种值**，只用这里定义的档位。
 * 目的不是洁癖，而是让同一个列表在曲库、队列、搜索结果里长得一样 —— 之前
 * 各页面各写各的 padding，同一类行在不同页面高度能差 6dp，视觉上像两个应用。
 *
 * 皮肤（EDITORIAL / CLASSIC / BRIGHT ...）只影响颜色与圆角下限，
 * 间距梯度是全局一致的，所以这里用常量而不是按皮肤分叉。
 */
data class SpacingScale(
    /** 2dp：紧贴的两个元素（标签与数字之间）。 */
    val xxs: Dp = 2.dp,
    /** 4dp：图标与文字的最小间隔。 */
    val xs: Dp = 4.dp,
    /** 8dp：同一组内元素间隔。 */
    val sm: Dp = 8.dp,
    /** 12dp：列表项内部左右内边距。 */
    val md: Dp = 12.dp,
    /** 16dp：页面左右内边距、卡片内边距。 */
    val lg: Dp = 16.dp,
    /** 20dp：分区之间的间隔。 */
    val section: Dp = 20.dp,
    /** 24dp：大块之间的间隔、对话框内边距。 */
    val xl: Dp = 24.dp,
    /** 32dp：整页空态的上边距。 */
    val xxl: Dp = 32.dp,
    /** 列表项之间的垂直间隔（比 sm 略小，密排更整齐）。 */
    val itemGap: Dp = 6.dp,
    /** 页面滚动内容与悬浮底栏之间的额外留白。 */
    val barClearance: Dp = 4.dp,

    // 下面三个是「半档」：不在 4dp 网格上，但存量代码里用量很大且语义各自明确。
    // 与其把它们各页各写一遍（这正是此前同类卡片内边距 14/16/18 三种值并存的原因），
    // 收进令牌后至少保证「同一个名字到哪都是同一个数」。
    /** 10dp：卡片的纵向内边距（标题与副标题之间的呼吸感）。 */
    val gap10: Dp = 10.dp,
    /** 14dp：封面与文字之间的间隔。 */
    val gap14: Dp = 14.dp,
    /** 18dp：卡片横向内边距，比 lg 略宽，用于作品卡。 */
    val gap18: Dp = 18.dp,
)

/** 圆角梯度。asmrShape() 会在「iOS 玻璃」皮肤下把小于 10dp 的圆角抬到 10dp。 */
data class RadiusScale(
    /** 2dp：极小徽标、进度条端头、选中指示条。 */
    val xxs: Dp = 2.dp,
    /** 8dp：小徽标、封面缩略图。 */
    val xs: Dp = 8.dp,
    /** 10dp：小卡片、行内图片。 */
    val sm: Dp = 10.dp,
    /** 12dp：列表行。 */
    val md: Dp = 12.dp,
    /** 16dp：卡片、对话框内容区。 */
    val lg: Dp = 16.dp,
    /** 20dp：对话框、迷你播放条。 */
    val xl: Dp = 20.dp,
    /** 24dp：底栏胶囊、底部弹层顶角。 */
    val xxl: Dp = 24.dp,
    /** 全圆角（胶囊）。 */
    val pill: Dp = 999.dp,
)

/** 图标尺寸。播放控制类固定 24dp，列表尾部操作 20dp。 */
object IconSize {
    val xs: Dp = 16.dp
    val sm: Dp = 18.dp
    val md: Dp = 20.dp
    val lg: Dp = 24.dp

    /** 空态/错误态的大图标。 */
    val hero: Dp = 56.dp
}

/**
 * 触控目标下限。Material 规范是 48dp，本项目的图标按钮此前大量用 40dp/30dp，
 * 拇指难按准（尤其单手听 ASMR 时）。
 */
object TouchTarget {
    val min: Dp = 48.dp

    /** 列表行最小高度：低于这个值点击会连带误触相邻行。 */
    val row: Dp = 56.dp
}

/** 动效令牌：时长与缓动。列表增删、页面切换都用它们，避免每个动画各写一个 tween 时长。 */
object Motion {
    /** 150ms：状态切换、图标变化。 */
    const val SHORT = 150

    /** 220ms：展开/收起、页面切换。 */
    const val MEDIUM = 220

    /** 400ms：大范围布局变化。 */
    const val LONG = 400

    /** 标准缓动（FastOutSlowIn）：进入用，比线性更「跟手」。 */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 减速：元素进入画面。 */
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** 加速：元素离开画面。 */
    val Accelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}

/** 阴影/层叠高度令牌（dp）。卡片默认 0，只有悬浮栏与对话框才抬起来。 */
object Elevation {
    val flat: Dp = 0.dp
    val raised: Dp = 8.dp
    val floating: Dp = 10.dp
}

/**
 * 描边与分隔条厚度。
 *
 * 1dp 发丝线不单独建令牌（门禁也放行，满屏都是），2–4dp 这三档覆盖了分隔线、
 * 进度条、选中描边与卡片强调边。此前同一个「选中描边」在不同页面写的是 2dp 和 3dp。
 */
object Stroke {
    /** 2dp：分隔线、进度条、选中描边。 */
    val thin: Dp = 2.dp

    /** 3dp：细指示条（章节左侧竖条）。 */
    val medium: Dp = 3.dp

    /** 4dp：加粗进度条、卡片强调边。 */
    val thick: Dp = 4.dp
}

/**
 * 浮层容器的尺寸约束。
 *
 * 收这一组是因为同一类「弹层里的可滚动列表」此前有 300 / 380 / 420 / 430 / 440
 * 五种上限，同一台手机上不同的弹层收起高度差到 140dp，滑动手感完全不一致。
 */
object Containers {
    /** 弹层内可滚动列表的最大高度：超过就内部滚动，避免弹层吃掉整屏。 */
    val scrollMaxHeight: Dp = 420.dp

    /** 对话框宽度区间：手机上铺满留边，平板上不无限拉伸。 */
    val dialogMinWidth: Dp = 280.dp
    val dialogMaxWidth: Dp = 560.dp
}

/**
 * 间距令牌。页面写 `Spacing.lg`，不写 `16.dp`。
 * 通过 CompositionLocal 下发，将来要给「紧凑模式」做整体缩放时只改 provider。
 */
val LocalSpacing = staticCompositionLocalOf { SpacingScale() }

/** 圆角令牌，用法同上。 */
val LocalRadius = staticCompositionLocalOf { RadiusScale() }

/** 当前间距梯度：`Spacing.lg`。 */
val Spacing: SpacingScale
    @Composable @ReadOnlyComposable get() = LocalSpacing.current

/** 当前圆角梯度：`Radius.md` 配合 asmrShape 使用。 */
val Radius: RadiusScale
    @Composable @ReadOnlyComposable get() = LocalRadius.current
