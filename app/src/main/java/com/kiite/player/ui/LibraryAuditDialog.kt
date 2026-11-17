package com.kiite.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiite.player.core.LibraryAudit
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.TrackEntry
import com.kiite.player.ui.theme.Containers
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing

/**
 * 曲库体检报告：空作品、可疑小文件、跨作品重复。
 * 与 [ScriptReportDialog] 同样的形态：数据通过参数传入，弹窗自己算派生结果。
 */
@Composable
fun LibraryAuditDialog(
    projects: List<ProjectEntry>,
    tracksOf: (String) -> List<TrackEntry>,
    onDismiss: () -> Unit,
) {
    val report = remember(projects) {
        LibraryAudit.audit(projects) { p -> tracksOf(p.path) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = asmrShape(Radius.xl),
        title = {
            Text(
                if (report.empty) "体检完成，一切正常"
                else "发现 " + report.totalIssues + " 处问题",
            )
        },
        text = {
            if (report.empty) {
                Text(
                    "没有空作品、可疑小文件或跨作品重复。\n" +
                        "（缺台本的逐首明细请看「台本匹配报告」）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                // 顶部问题分类小计
                val counts = report.issues.groupingBy { it.type }.eachCount()
                Column(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                        counts.entries.take(3).forEach { (type, count) ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(type.label + " " + count) },
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    LazyColumn(Modifier.heightIn(max = Containers.scrollMaxHeight)) {
                        items(report.issues, key = { it.hashCode() }) { issue ->
                            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        issue.type.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(
                                        issue.projectName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Text(
                                    issue.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (report.duplicates.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "重复组 " + report.duplicates.size + " 组；" +
                                "「疑似重复」表示同名且同大小，几乎可断定是拷贝副本。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
