package com.asmrplayer.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asmrplayer.ui.theme.LocalCardAlpha

/** 统一使用「卡片不透明度」设置，让自定义背景能透出来。 */
@Composable
fun AsmrCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val alpha = LocalCardAlpha.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        ),
        content = content,
    )
}

/** 给非 Card 的容器用：取出当前卡片色。 */
@Composable
fun asmrSurfaceColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)

/** 给顶栏 / 底栏 / 迷你条用。 */
@Composable
fun asmrBarColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = com.asmrplayer.ui.theme.LocalBarAlpha.current)
