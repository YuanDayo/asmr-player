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
import androidx.compose.ui.graphics.Brush
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
import com.kiite.player.ui.theme.LocalIsBright

/**
 * 默认 iOS 玻璃皮肤下：圆角不小于 10dp（iOS 大圆角质感），其余皮肤沿用调用点传入的圆角。
 * 调用点写法：clip(asmrShape(12.dp)).then(asmrBorder(asmrShape(12.dp)))
 */
@Composable
fun asmrShape(radius: Dp): Shape =
    if (LocalEditorial.current) RoundedCornerShape(radius.coerceAtLeast(10.dp)) else RoundedCornerShape(radius)

/**
 * 默认皮肤：液态玻璃描边 —— 顶部一道白色高光渐隐到底部微暗，
 * 靠光影勾轮廓，不再用深色发丝线勾边（那是把卡片「框住」，不是「玻璃」）。
 * 其它皮肤沿用旧行为（恒为空），所以调用点不用改。
 */
@Composable
fun asmrBorder(shape: Shape = RoundedCornerShape(12.dp)): Modifier =
    if (LocalEditorial.current) {
        val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        val stroke = if (light) {
            // 浅色：上缘高光亮、下缘微暗，玻璃被顶光照亮的观感
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0x1F3C3C43)))
        } else {
            // 深色：上缘白光 28% 渐隐到 5%，幽微的玻璃轮缘
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f)))
        }
        Modifier.border(1.dp, stroke, shape)
    } else {
        Modifier
    }

/**
 * 液态玻璃填充：全局统一的玻璃材质底 —— 垂直渐变（上亮下微暗）+ 随卡片不透明度缩放。
 * 浅色：白色 0.68 → 0.46；深色：#1C1C1E 0.74 → 0.52。所有卡片级容器统一用它，
 * 配合 asmrBorder 的白色轮缘描边构成完整的液态玻璃观感。
 */
@Composable
fun asmrGlassFill(): Brush {
    val scheme = MaterialTheme.colorScheme
    val a = LocalCardAlpha.current.coerceIn(0.25f, 1f)
    return if (scheme.surface.luminance() > 0.5f) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (0.68f * a).coerceIn(0.30f, 0.92f)),
                Color.White.copy(alpha = (0.46f * a).coerceIn(0.22f, 0.86f)),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                scheme.surface.copy(alpha = (0.74f * a).coerceIn(0.35f, 0.95f)),
                scheme.surface.copy(alpha = (0.52f * a).coerceIn(0.28f, 0.92f)),
            ),
        )
    }
}

/** 统一卡片容器：默认皮肤是「液态玻璃渐变填充 + 白色轮缘 + 大圆角」的玻璃卡片。 */
@Composable
fun AsmrCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val alpha = LocalCardAlpha.current
    val editorial = LocalEditorial.current
    val shape: Shape = if (editorial) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    if (editorial) {
        Column(
            modifier
                .clip(shape)
                .background(asmrGlassFill())
                .then(asmrBorder(shape)),
            content = content,
        )
    } else {
        Card(
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha)),
            content = content,
        )
    }
}

/** 给非 Card 的容器用：取出当前卡片色。 */
@Composable
fun asmrSurfaceColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)

/**
 * 列表行底色：仅剩个别以 Color 传参的调用点在用（如选中态切换）。
 * 默认皮肤的行级玻璃一律用 asmrGlassFill()，这里只保证其它皮肤行为不变。
 */
@Composable
fun asmrRowColor(): Color {
    val scheme = MaterialTheme.colorScheme
    if (LocalEditorial.current) {
        return scheme.surface.copy(alpha = (LocalCardAlpha.current * 0.58f).coerceIn(0.3f, 0.88f))
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
