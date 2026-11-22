package com.kiite.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.asmrShape
import com.kiite.player.ui.theme.AccentFill
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Radius

/** 图标底块的尺寸档位（本组件专用，不进全局令牌表）。 */
object IconTileSize {
    /** 列表行里的紧凑档。 */
    val sm: Dp = 36.dp

    /** 设置分组行、首页快捷入口这一档。 */
    val md: Dp = 42.dp

    /** 引导页 / 空态那种大图块。 */
    val lg: Dp = 104.dp
}

/**
 * 图标底块：一块主色淡底 + 居中图标。
 *
 * 抽出来的理由很实在：设置分组行、首启引导、空态三处此前各画一遍
 * `Box(clip + background(primary.copy(alpha = 0.x)) + Icon)`，尺寸 36/42/104、
 * 圆角 xs/sm/xxl、浓度 0.10/0.12 各不相同 —— 换皮肤时只有一页跟着变，就成了
 * 「风格不统一」。现在尺寸与圆角从调用点传，浓度统一走 [AccentFill.tile]。
 *
 * 只负责「底块 + 图标」，不带点击：可点的场景由调用方在外层套 `clickable` / `TapCard`，
 * 这样同一个组件既能当图标块，也能当卡片里的装饰。
 */
@Composable
fun TintedIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = IconTileSize.md,
    iconSize: Dp = IconSize.md,
    corner: Dp = Radius.sm,
) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(size)
            .clip(asmrShape(corner))
            .background(tint.copy(alpha = AccentFill.tile)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint)
    }
}
