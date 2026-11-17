package com.kiite.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kiite.player.ui.asmrBorder
import com.kiite.player.ui.asmrShape
import com.kiite.player.ui.theme.AsmrTheme
import com.kiite.player.ui.theme.Containers
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing

/** 统一对话框容器：标题 + 任意内容，圆角与内边距跟底部弹层一致。 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        DialogContentFrame(title = title, modifier = modifier, content = content)
    }
}

/**
 * 对话框的视觉主体（圆角卡片 + 内边距）。
 *
 * 单独抽出来是为了能预览：`Dialog` 在 Compose Preview 里不渲染内容，
 * 直接 Preview [AppDialog] 看到的是一片空白，等于没有预览。
 */
@Composable
private fun DialogContentFrame(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = asmrShape(Radius.xl),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = Containers.dialogMinWidth, max = Containers.dialogMaxWidth)
            .then(asmrBorder(asmrShape(Radius.xl))),
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Spacing.md))
            }
            content()
        }
    }
}

/**
 * 破坏性操作的二次确认（计划书 4.8 / 6.2）。
 *
 * 规则：删除播放列表、清空历史这类**不可撤销**的操作必须过它；
 * 移出队列这种能撤销的，走 Snackbar 的「撤销」，不再弹框打断。
 */
@Composable
fun ConfirmDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
    destructive: Boolean = false,
) {
    val textSlot: (@Composable () -> Unit)? = text?.let { t ->
        { Text(t, style = MaterialTheme.typography.bodyMedium) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = asmrShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = textSlot,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

// ---------------- 预览 ----------------

@Composable
private fun DialogFramePreviewContent() {
    DialogContentFrame(title = "删除这个播放列表？") {
        Text(
            "「睡前常用」里的 24 首不会被删除，只是不再归到这个列表。此操作不可撤销。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {}) { Text("取消") }
            TextButton(onClick = {}) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Preview(name = "确认框 · 浅色", showBackground = true, backgroundColor = 0xFFFDFBF7, widthDp = 360)
@Composable
private fun AppDialogPreviewLight() {
    AsmrTheme(themeMode = com.kiite.player.ui.theme.ThemeMode.LIGHT) { DialogFramePreviewContent() }
}

@Preview(name = "确认框 · 深色", showBackground = true, backgroundColor = 0xFF101418, widthDp = 360)
@Composable
private fun AppDialogPreviewDark() {
    AsmrTheme(themeMode = com.kiite.player.ui.theme.ThemeMode.DARK) { DialogFramePreviewContent() }
}
