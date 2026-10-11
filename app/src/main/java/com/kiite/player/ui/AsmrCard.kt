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
import com.kiite.player.ui.theme.LocalCardAlpha
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.LocalIsBright

/**
 * 实验性「版式」皮肤下所有容器直角（印刷感）；其它皮肤沿用调用点传入的圆角。
 * 调用点写法：clip(asmrShape(12.dp)).then(asmrBorder(asmrShape(12.dp)))
 */
@Composable
fun asmrShape(radius: Dp): Shape =
    if (LocalEditorial.current) RoundedCornerShape(0.dp) else RoundedCornerShape(radius)

/**
 * 「版式」皮肤：1dp 墨黑细线（印刷描边），用来代替阴影和色块分层。
 * 其它皮肤沿用旧行为（恒为空），所以调用点不用改。
 */
@Composable
fun asmrBorder(shape: Shape = RoundedCornerShape(12.dp)): Modifier =
    if (LocalEditorial.current) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), shape)
    } else {
        Modifier
    }

/** 统一卡片容器：版式皮肤下是「纸白 + 墨黑细线 + 直角」。 */
@Composable
fun AsmrCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val alpha = LocalCardAlpha.current
    val editorial = LocalEditorial.current
    val shape: Shape = if (editorial) RoundedCornerShape(0.dp) else MaterialTheme.shapes.medium
    Card(
        modifier = if (editorial) modifier.then(asmrBorder(shape)) else modifier,
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        ),
        content = content,
    )
}

/** 给非 Card 的容器用：取出当前卡片色。 */
@Composable
fun asmrSurfaceColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)

/** 列表行底色：版式皮肤用纸白、靠细线分层；其它皮肤沿用半透明卡片色。 */
@Composable
fun asmrRowColor(): Color {
    val scheme = MaterialTheme.colorScheme
    if (LocalEditorial.current) return scheme.surface
    val alpha = LocalCardAlpha.current
    return if (LocalIsBright.current) {
        scheme.surface
    } else {
        scheme.surface.copy(alpha = (alpha * 0.72f).coerceIn(0.28f, 0.86f))
    }
}

/** 给顶栏 / 底栏 / 迷你条用。 */
@Composable
fun asmrBarColor(): Color {
    val scheme = MaterialTheme.colorScheme
    if (LocalEditorial.current) return scheme.background
    return if (LocalIsBright.current) {
        scheme.surface
    } else {
        scheme.surface.copy(alpha = com.kiite.player.ui.theme.LocalBarAlpha.current)
    }
}

/** 版式皮肤的索引标签（01 / 02 / VI 之类），等宽 + 宽字距。 */
@Composable
fun IndexTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 版式皮肤的小色块（蓝 / 品红 / 黄），印刷点缀；其它皮肤不占位。 */
@Composable
fun EditorialTicks(modifier: Modifier = Modifier) {
    if (!LocalEditorial.current) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        ).forEach { c ->
            Box(Modifier.size(width = 4.dp, height = 11.dp).background(c))
        }
    }
}
