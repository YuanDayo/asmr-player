package com.kiite.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kiite.player.ui.asmrBorder
import com.kiite.player.ui.asmrShape
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
        Surface(
            shape = asmrShape(Radius.xl),
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier
                .fillMaxWidth()
                .widthIn(min = 280.dp, max = 560.dp)
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
