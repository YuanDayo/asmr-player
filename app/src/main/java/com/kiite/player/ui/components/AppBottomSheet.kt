package com.kiite.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.TouchTarget

/**
 * 统一底部弹层（计划书 4.5）：排序、筛选、播放选项、定时关闭、长按操作菜单全部基于它。
 *
 * 之前只有两处直接写 ModalBottomSheet，圆角/内边距各写一份；而排序筛选散落在
 * 页面内的下拉框里。统一之后只要认这一个入口，就不会再出现「有的筛选在顶部
 * 下拉、有的在底部弹层」的混乱。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    /** 内容区最大高度；列表型内容建议给 440dp，超过就内部滚动。 */
    maxContentHeight: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = Spacing.xl, end = Spacing.lg, bottom = Spacing.sm),
                )
            }
            Column(
                Modifier.then(
                    if (maxContentHeight != null) Modifier.heightIn(max = maxContentHeight) else Modifier,
                ),
                content = content,
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** 弹层里的一条操作：图标 + 文案，可标红表示破坏性操作。 */
@Composable
fun SheetAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.min)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(IconSize.md), tint = tint)
        Spacer(Modifier.width(Spacing.md))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/** 弹层里的小标题（「排序」「筛选 · 社团」这类）。 */
@Composable
fun SheetSectionLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text,
        modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.md, bottom = Spacing.xs),
        style = MaterialTheme.typography.labelMedium,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
