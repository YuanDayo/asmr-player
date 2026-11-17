package com.kiite.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.TrackEntry
import com.kiite.player.ui.theme.Containers
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing

/**
 * 台本匹配报告：列出**有音频没配到台本**的作品（含部分匹配：一部作品里只有几首缺也算），
 * 逐曲目「指定」直达手动选台本流程。曲库页与设置页共用。
 */
@Composable
fun ScriptReportDialog(
    projects: List<ProjectEntry>,
    tracksOf: (String) -> List<TrackEntry>,
    onPickTrack: (TrackEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    // 注意用逐曲目判断而不是 project.scriptCount == 0：后者会把「5 首里缺 2 首」的
    // 部分匹配作品漏掉，而那正是逐集配台本时最常见的形态
    val unmatched = projects.filter { p -> tracksOf(p.path).any { !it.hasScript } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = asmrShape(Radius.xl),
        title = { Text("台本匹配报告") },
        text = {
            if (unmatched.isEmpty()) {
                Text("全部音频都匹配到了台本，无需处理。", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(
                    Modifier
                        .heightIn(max = Containers.scrollMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "以下 " + unmatched.size + " 部作品里有音频没配到台本。点「指定」手动补上：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    unmatched.forEach { p ->
                        val missing = tracksOf(p.path).filter { !it.hasScript }
                        HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                        Text(
                            p.name + "（缺 " + missing.size + "/" + tracksOf(p.path).size + " 首）",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        missing.forEach { t ->
                            Row(
                                Modifier.fillMaxWidth().padding(start = Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    t.baseName,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = { onPickTrack(t) }) { Text("指定") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
