package com.kiite.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.asmrTopShape
import com.kiite.player.ui.theme.Animations
import com.kiite.player.ui.theme.AsmrTheme
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Motion
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
        shape = asmrTopShape(Radius.xxl),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        SheetArrive {
            SheetContentFrame(title = title, maxContentHeight = maxContentHeight, content = content)
        }
    }
}

/**
 * 弹层内容的入场：轻微上浮 + 淡入，跟在容器滑上来之后。
 *
 * `ModalBottomSheet` 自己只做「整块从底部滑上来」，内容在滑动过程中是静止的一张图；
 * 补一层 8dp 的上浮 + 淡入（延迟一帧）之后，读起来是「板子到位，内容落上去」。
 * 缩放刻意不用：弹层本身已经在位移，再叠缩放会让人以为整块玻璃在被捏。
 *
 * 只动 alpha / translationY，不改尺寸，所以不会和 `ModalBottomSheet` 的拖拽/回弹打架。
 */
@Composable
private fun SheetArrive(content: @Composable () -> Unit) {
    if (!Animations.enabled()) {
        content()
        return
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // 令牌可能带 @Composable getter，先在组合里取出来，别在 graphicsLayer 的 lambda 里读
    val rise = Spacing.gap10
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.enter(Motion.SHORT),
        label = "sheetArrive",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = p
            translationY = (1f - p) * rise.toPx()
        },
    ) {
        content()
    }
}

/**
 * 弹层的内容骨架（标题 + 可限高的内容区 + 底部留白）。
 *
 * 抽出来是为了能预览：`ModalBottomSheet` 在 Compose Preview 里不渲染，
 * 直接预览 [AppBottomSheet] 只会得到空白。
 */
@Composable
private fun SheetContentFrame(
    title: String?,
    maxContentHeight: Dp? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
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

// ---------------- 预览 ----------------

@Composable
private fun SheetFramePreviewContent() {
    Surface(
        shape = asmrTopShape(Radius.xxl),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SheetContentFrame(title = "播放选项") {
            SheetSectionLabel("操作")
            SheetAction(label = "加入播放列表", icon = Icons.Default.PlaylistAdd, onClick = {})
            SheetAction(label = "选择台本", icon = Icons.Default.Description, onClick = {})
            SheetAction(label = "从播放列表移除", icon = Icons.Default.Close, destructive = true, onClick = {})
            SheetAction(label = "分享（未实现）", icon = Icons.Default.Share, enabled = false, onClick = {})
        }
    }
}

@Preview(name = "弹层 · 浅色", showBackground = true, backgroundColor = 0xFFFDFBF7, widthDp = 360)
@Composable
private fun AppBottomSheetPreviewLight() {
    AsmrTheme(themeMode = com.kiite.player.ui.theme.ThemeMode.LIGHT) { SheetFramePreviewContent() }
}

@Preview(name = "弹层 · 深色", showBackground = true, backgroundColor = 0xFF101418, widthDp = 360)
@Composable
private fun AppBottomSheetPreviewDark() {
    AsmrTheme(themeMode = com.kiite.player.ui.theme.ThemeMode.DARK) { SheetFramePreviewContent() }
}
