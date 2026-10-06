package com.asmrplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.asmrplayer.ui.theme.LocalCardAlpha
import com.asmrplayer.ui.theme.LocalIsBright

/** 明快皮肤：2px 墨黑描边。其它皮肤返回空 Modifier。 */
@Composable
fun asmrBorder(shape: Shape = RoundedCornerShape(24.dp)): Modifier =
    if (LocalIsBright.current) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, shape)
    } else {
        Modifier
    }

/**
 * 统一卡片容器。
 * 明快皮肤：白色卡片 + 2px 墨黑描边 + 右下硬投影（范例的标志性做法）。
 * 其它皮肤：沿用原来的半透明圆角卡片。
 */
@Composable
fun AsmrCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val alpha = LocalCardAlpha.current
    if (!LocalIsBright.current) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
            ),
            content = content,
        )
        return
    }

    val ink = MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(24.dp)
    Box(modifier) {
        // 硬投影：等大矩形右下偏移，不模糊
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .clip(shape)
                .background(ink.copy(alpha = 0.16f)),
        )
        Card(
            shape = shape,
            border = BorderStroke(2.dp, ink),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
            ),
            modifier = Modifier.fillMaxWidth().padding(end = 3.dp, bottom = 3.dp),
            content = content,
        )
    }
}

/** 给非 Card 的容器用：取出当前卡片色。 */
@Composable
fun asmrSurfaceColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)

/** 列表行：比卡片更透，但仍保证逐条可辨别；明快皮肤下用实心卡片色 + 描边。 */
@Composable
fun asmrRowColor(): Color {
    val alpha = LocalCardAlpha.current
    return if (LocalIsBright.current) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = (alpha * 0.72f).coerceIn(0.28f, 0.86f))
    }
}

/** 给顶栏 / 底栏 / 迷你条用。 */
@Composable
fun asmrBarColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (LocalIsBright.current) {
        scheme.surface
    } else {
        scheme.surface.copy(alpha = com.asmrplayer.ui.theme.LocalBarAlpha.current)
    }
}
