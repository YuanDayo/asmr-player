package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.theme.LocalBarAlpha
import com.kiite.player.ui.theme.LocalCardAlpha
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.LocalIsBright

/**
 * 默认 iOS 玻璃皮肤下：圆角不小于 10dp（iOS 大圆角质感），其余皮肤沿用调用点传入的圆角。
 * 调用点写法：clip(asmrShape(12.dp)).then(asmrBorder(asmrShape(12.dp)))
 */
@Composable
fun asmrShape(radius: Dp): Shape =
    if (LocalEditorial.current) RoundedCornerShape(radius.coerceAtLeast(10.dp)) else RoundedCornerShape(radius)

/**
 * 默认皮肤：极淡的 iOS 分隔线描边（代替阴影分层，透明的容器靠它勾出轮廓）。
 * 其它皮肤沿用旧行为（恒为空），所以调用点不用改。
 */
@Composable
fun asmrBorder(shape: Shape = RoundedCornerShape(12.dp)): Modifier =
    if (LocalEditorial.current) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f), shape)
    } else {
        Modifier
    }

/** 统一卡片容器：默认皮肤是「半透明白 + 发丝描边 + 大圆角」的玻璃卡片。 */
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
 * 列表行底色：默认皮肤用半透明玻璃（背景图能透出来）；其它皮肤沿用原行为。
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
