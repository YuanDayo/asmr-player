package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 默认 iOS 玻璃皮肤下：圆角不小于 10dp（iOS 大圆角质感），其余皮肤沿用调用点传入的圆角。
 * 调用点写法：clip(asmrShape(12.dp)).then(asmrBorder(asmrShape(12.dp)))
 */
@Composable
fun asmrShape(radius: Dp): Shape =
    if (LocalEditorial.current) RoundedCornerShape(radius.coerceAtLeast(10.dp)) else RoundedCornerShape(radius)

/**
 * 默认皮肤：极淡的分隔线描边（比 2.2beta 首版更淡，只做最轻的轮廓提示）。
 * 其它皮肤沿用旧行为（恒为空），所以调用点不用改。
 */
@Composable
fun asmrBorder(shape: Shape = RoundedCornerShape(12.dp)): Modifier =
    if (LocalEditorial.current) {
        val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        val stroke = if (light) Color(0x143C3C43) else Color.White.copy(alpha = 0.12f)
        Modifier.border(1.dp, stroke, shape)
    } else {
        Modifier
    }

/**
 * 高斯模糊磨砂：真正的毛玻璃 —— 把身后内容（背景层 + 滚动内容）做高斯模糊再叠色。
 * 背景（AsmrTheme 背景层）与页面内容（AppRoot 内容层）都注册进了 LocalHazeState。
 *
 * 性能（实时模糊是全局最贵的渲染项，官方基准约 +45% 帧耗时）：
 * ① **滚动中直接退回半透明纯色**（LocalScrollActivityHolder）—— 滚动是唯一每帧都要
 *    重新捕获 + 模糊的场景，暂停后滚动零模糊成本，停下 140ms 再恢复；
 * ② inputScale 0.5 降采样；③ 半径 14dp（比 20dp 省一半以上，观感几乎一致）。
 * 设置里的「实时毛玻璃」开关可整体关闭。
 * 非默认皮肤为 no-op（调用点需要先给纯色底，例如 asmrBarColor）。
 */
@Composable
fun Modifier.asmrFrost(): Modifier {
    if (!LocalEditorial.current) return this
    if (!LocalLiveBlur.current) return this.background(asmrBarColor())
    if (LocalScrollActivityHolder.current.scrolling) return this.background(asmrBarColor())
    val scheme = MaterialTheme.colorScheme
    val light = scheme.surface.luminance() > 0.5f
    val frostStyle = HazeStyle(
        backgroundColor = scheme.background,
        tints = listOf(HazeTint(scheme.surface.copy(alpha = if (light) 0.76f else 0.65f))),
        blurRadius = 14.dp,
        noiseFactor = 0f,
    )
    return this.hazeEffect(LocalHazeState.current) {
        style = frostStyle
        inputScale = HazeInputScale.Fixed(0.5f)
    }
}

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
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        ).forEach { c ->
            Box(
                Modifier
                    .size(width = 4.dp, height = 11.dp)
                    .background(c, RoundedCornerShape(2.dp)),
            )
        }
    }
}
