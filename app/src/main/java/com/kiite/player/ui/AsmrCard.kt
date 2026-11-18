package com.kiite.player.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.theme.LocalBarAlpha
import com.kiite.player.ui.theme.LocalCardAlpha
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.LocalHazeState
import com.kiite.player.ui.theme.LocalIsBright
import com.kiite.player.ui.theme.LocalLiveBlur
import com.kiite.player.ui.theme.LocalScrollActivityHolder
import com.kiite.player.ui.theme.HairlineStroke
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.Stroke
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * AsmrCard 独有的控件尺寸。
 *
 * 这些不进全局令牌：它们是本页特有的控件大小（图标按钮、进度条、封面档位），
 * 同值不同义，硬塞进 Spacing / IconSize 会让令牌表变成一张数字表。
 * 集中在这里的目的是可搜、可一处改；真正的归一化（图标按钮统一到 48dp
 * 触控目标）留给单独一批，因为会改变布局。
 */
private object CardDim {
    val box10 = 10.dp
    val box20 = 20.dp
    val height4 = 4.dp
    val height11 = 11.dp
}


/**
 * 默认 iOS 玻璃皮肤下：圆角不小于 10dp（iOS 大圆角质感），其余皮肤沿用调用点传入的圆角。
 * 调用点写法：clip(asmrShape(12.dp)).then(asmrBorder(asmrShape(12.dp)))
 */
@Composable
fun asmrShape(radius: Dp): Shape =
    if (LocalEditorial.current) RoundedCornerShape(radius.coerceAtLeast(CardDim.box10)) else RoundedCornerShape(radius)

/**
 * 默认皮肤：极淡的分隔线描边（比 2.2beta 首版更淡，只做最轻的轮廓提示）。
 * 其它皮肤沿用旧行为（恒为空），所以调用点不用改。
 */
@Composable
fun asmrBorder(shape: Shape = RoundedCornerShape(Radius.md)): Modifier =
    if (LocalEditorial.current) {
        val stroke = HairlineStroke.of(MaterialTheme.colorScheme.surface.luminance() > 0.5f)
        Modifier.border(1.dp, stroke, shape)
    } else {
        Modifier
    }

/**
 * 弹层的描边，按皮肤分两种语言：
 *
 * - **明快 / 经典**：墨线 2dp 是它们的版式语言，保留；
 * - **iOS 玻璃**：整站都是「白卡片 + 无硬描边」，弹层却挂着 2dp 黑边，一换皮肤就突兀
 *   （用户反馈「更多」菜单与主题不和谐）。这里退回和卡片同一档的极淡 hairline。
 */
@Composable
fun asmrMenuBorder(): BorderStroke? = when {
    LocalIsBright.current -> BorderStroke(Stroke.thin, MaterialTheme.colorScheme.onBackground)
    LocalEditorial.current -> BorderStroke(
        1.dp,
        HairlineStroke.of(MaterialTheme.colorScheme.surface.luminance() > 0.5f),
    )
    else -> BorderStroke(Stroke.thin, MaterialTheme.colorScheme.onBackground)
}

/**
 * 高斯模糊磨砂：真正的毛玻璃 —— 把身后内容（背景层 + 滚动内容）做高斯模糊再叠色。
 * 背景（AsmrTheme 背景层）与页面内容（AppRoot 内容层）都注册进了 LocalHazeState。
 *
 * 观感参数沿用 2.2beta1：半径 20dp、不降采样（更细腻的磨砂颗粒）、
 * 浅色 tint 0.72 / 深色 0.60。低端设备掉帧时可用设置里的「实时毛玻璃」开关整体关闭
 * （退回半透明纯色，零 GPU 成本）。
 * 非默认皮肤为 no-op（调用点需要先给纯色底，例如 asmrBarColor）。
 *
 * 页面正在动（滚动 / 切标签）时退回半透明纯色：模糊每帧都要把身后内容重新捕获成纹理
 * 再做高斯，内容一动就是「每帧一次全屏捕获 + 模糊」，实测是切页顿一下的主要来源。
 */
@Composable
fun Modifier.asmrFrost(): Modifier {
    if (!LocalEditorial.current) return this
    if (!LocalLiveBlur.current) return this.background(asmrBarColor())
    // 静止 140ms 后自动恢复磨砂，见 theme/ScrollActivity.kt
    if (LocalScrollActivityHolder.current.scrolling) return this.background(asmrBarColor())
    val scheme = MaterialTheme.colorScheme
    val light = scheme.surface.luminance() > 0.5f
    val frostStyle = HazeStyle(
        backgroundColor = scheme.background,
        tints = listOf(HazeTint(scheme.surface.copy(alpha = if (light) 0.72f else 0.60f))),
        blurRadius = CardDim.box20,
        noiseFactor = 0f,
    )
    return this.hazeEffect(LocalHazeState.current, frostStyle)
}

// 进出场规格统一在 `ui/theme/MotionTransitions.kt`（asmrEnter / asmrExit / collapseEnter …）。
// 这里不再保留同名转发：两份入口一定会慢慢跑偏，页面直接 import 令牌那份。

/** 统一卡片容器：默认皮肤是「半透明 + 大圆角」的轻玻璃卡片（磨砂只用于栏，列表卡片不用以保证滚动性能）。 */
@Composable
fun AsmrCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val alpha = LocalCardAlpha.current
    val editorial = LocalEditorial.current
    val shape: Shape = if (editorial) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    Card(
        modifier = if (editorial) modifier.then(asmrBorder(shape)) else modifier,
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = if (editorial) (alpha * 0.78f).coerceIn(0.45f, 0.92f) else alpha,
            ),
        ),
        content = content,
    )
}

/** 给非 Card 的容器用：取出当前卡片色。 */
@Composable
fun asmrSurfaceColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)

/**
 * 列表行底色：默认皮肤半透明轻玻璃（背景能透出来）；其它皮肤沿用原行为。
 */
@Composable
fun asmrRowColor(): Color {
    val scheme = MaterialTheme.colorScheme
    if (LocalEditorial.current) {
        return scheme.surface.copy(alpha = (LocalCardAlpha.current * 0.74f).coerceIn(0.4f, 0.9f))
    }
    val alpha = LocalCardAlpha.current
    return if (LocalIsBright.current) {
        scheme.surface
    } else {
        scheme.surface.copy(alpha = (alpha * 0.72f).coerceIn(0.28f, 0.86f))
    }
}

/** 给顶栏 / 底栏 / 迷你条用：iOS 半透明毛玻璃栏（无背景图时也有轻微通透感）。 */
@Composable
fun asmrBarColor(): Color {
    val scheme = MaterialTheme.colorScheme
    if (LocalEditorial.current) {
        val a = (LocalBarAlpha.current * 0.80f).coerceIn(0.55f, 0.88f)
        return scheme.surface.copy(alpha = a)
    }
    return if (LocalIsBright.current) {
        scheme.surface
    } else {
        scheme.surface.copy(alpha = LocalBarAlpha.current)
    }
}

/** 默认皮肤的索引标签（01 / 02 / VI 之类），小号加粗。 */
@Composable
fun IndexTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 默认皮肤的小色块（蓝 / 靛 / 绿），iOS 点缀色；其它皮肤不占位。 */
@Composable
fun EditorialTicks(modifier: Modifier = Modifier) {
    if (!LocalEditorial.current) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        ).forEach { c ->
            Box(
                Modifier
                    .size(width = CardDim.height4, height = CardDim.height11)
                    .background(c, RoundedCornerShape(Radius.xxs)),
            )
        }
    }
}
