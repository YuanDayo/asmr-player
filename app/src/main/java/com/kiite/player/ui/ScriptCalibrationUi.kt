package com.kiite.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiite.player.core.CalibrationMath
import com.kiite.player.domain.ScriptUi
import com.kiite.player.core.CalibrationMath.MatchConfidence

/**
 * 台本状态 + 时间轴校准条，**放在播放页上方卡片里**。
 *
 * 早期版本把它挂在台本内容区顶部（状态条 + 底部展开的偏移面板），
 * 结果是两块都从台本的显示面积里扣：状态条常驻吃掉约 30dp，
 * 偏移面板展开后还要再吃掉近 100dp，一集长台本剩不下几行。
 *
 * 现在整条搬到上方卡片、压成横向紧凑条：默认只有一行（匹配状态 + 偏移摘要 + 校准按钮），
 * 点开后在同一行下面追加步进按钮，台本区始终是满满一整块。
 *
 * 计划书要求「显示台本匹配状态，避免把不确定匹配误认为准确结果」，
 * 所以低置信度会变色，其余保持灰色——每份台本都喊「请核对」，用户很快就当成装饰。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScriptCalibrationStrip(
    ui: ScriptUi,
    vm: MainViewModel,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenBatch: () -> Unit = {},
) {
    val cal = ui.calibration
    val warn = ui.confidence == MatchConfidence.LOOSE
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                statusLabel(ui),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    warn -> MaterialTheme.colorScheme.tertiary
                    ui.confidence == MatchConfidence.MANUAL -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!cal.isEmpty) {
                Text(
                    CalibrationMath.describe(cal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                )
            }
            if (ui.calibratable) {
                TextButton(
                    onClick = onToggle,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        if (expanded) "收起" else "校准",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (expanded || !cal.isEmpty) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        // 两行提示只在真有话要说时才占高度
        ui.confidenceHint?.takeIf { !expanded }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (ui.staleLines) {
            Text(
                "台本行数已变化，单句微调已失效（全局偏移仍生效）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (expanded && ui.calibratable) {
            // 六个步长按钮在窄屏一行放不下（每个约 56dp，屏宽减去卡片内边距不够），
            // 用 FlowRow 自动换行；归零 / 清除放到下一行，避免把芯片挤扁
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CalibrationMath.OFFSET_STEPS.forEach { step ->
                    CalibrationChip(stepLabel(step)) { vm.nudgeScriptOffset(step) }
                }
                // 批量入口和步长放在同一行末尾：它属于「整份台本」这一档操作，
                // 塞进下面那行「归零 / 清除 / 重置」里会被当成清理类按钮
                CalibrationChip("批量…") { onOpenBatch() }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (cal.adjustedLineCount > 0) {
                        "长按任一句单独校准 · 已微调 " + cal.adjustedLineCount + " 句"
                    } else {
                        "长按任一句可单独校准"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { vm.resetScriptOffset() },
                    enabled = cal.offsetMs != 0L,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("归零", style = MaterialTheme.typography.labelSmall) }
                if (cal.adjustedLineCount > 0) {
                    TextButton(
                        onClick = { vm.resetScriptLines() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("清除单句", style = MaterialTheme.typography.labelSmall) }
                }
                TextButton(
                    onClick = { vm.resetScriptCalibration() },
                    enabled = !cal.isEmpty,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("全部重置", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

private fun statusLabel(ui: ScriptUi): String {
    val parts = ArrayList<String>(3)
    if (ui.fromEmbedded) {
        parts.add("内嵌歌词")
    } else {
        ui.attachment?.let { att ->
            parts.add(att.format.label)
            if (att.reason.isNotBlank()) parts.add(att.reason)
        }
    }
    parts.add(ui.confidence.label)
    return parts.joinToString(" · ")
}

/**
 * 单句校准对话框：把某一句的起点/终点对齐到当前位置，或按步长微调。
 *
 * 里面放了个播放/暂停：最准的做法是「听着，到这一句时暂停，再点设到当前位置」，
 * 否则用户得先关掉对话框、去控制条暂停、再长按回来，中间位置早跑掉了。
 */
@Composable
fun ScriptLineCalibrationDialog(
    ui: ScriptUi,
    index: Int,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    vm: MainViewModel,
    onDismiss: () -> Unit,
) {
    val lines = ui.parsed?.timedLines.orEmpty()
    val line = lines.getOrNull(index) ?: return
    val shift = ui.calibration.lines[index]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("第 " + (index + 1) + " 句")
                    Text(
                        if (shift == null || shift.isZero) {
                            "未单独微调"
                        } else {
                            "起点 " + CalibrationMath.formatSignedSeconds(shift.startDelta) +
                                "，终点 " + CalibrationMath.formatSignedSeconds(shift.endDelta)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "暂停" else "继续播放",
                    )
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                LineSection(
                    title = "起点 " + clock(line.startMs),
                    onAlign = { vm.alignScriptLineStart(index) },
                    onNudge = { vm.nudgeScriptLineStart(index, it) },
                )
                Spacer(Modifier.height(10.dp))
                LineSection(
                    title = "终点 " + (line.endMs?.let { clock(it) } ?: "未标注"),
                    onAlign = { vm.alignScriptLineEnd(index) },
                    onNudge = { vm.nudgeScriptLineEnd(index, it) },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "到这一句时先暂停，再点「设到当前位置」——位置取的是播放器实时进度，比界面上的进度更准。" +
                        "手动调整会被夹在相邻两句之间，不会把顺序弄乱。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = {
            TextButton(
                onClick = {
                    vm.resetScriptLine(index)
                    onDismiss()
                },
                enabled = shift != null && !shift.isZero,
            ) { Text("重置本句") }
        },
    )
}

@Composable
private fun LineSection(title: String, onAlign: () -> Unit, onNudge: (Long) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAlign) { Text("设到当前位置") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CalibrationMath.LINE_STEPS.forEach { step ->
                CalibrationChip(stepLabel(step)) { onNudge(step) }
            }
        }
    }
}

/** 小圆角按钮。TextButton 有 64dp 最小宽度，六个并排会把对话框撑破。 */
@Composable
private fun CalibrationChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(asmrShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
}

private fun stepLabel(ms: Long): String = when {
    ms % 1_000L == 0L -> if (ms > 0) "+" + (ms / 1000) + "s" else "−" + (-ms / 1000) + "s"
    else -> if (ms > 0) "+" + (ms / 100) / 10f + "s" else "−" + (-ms / 100) / 10f + "s"
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * 批量校准：选一段（或全部）句子，整段一起平移。
 *
 * 为什么需要它：一集 200 句的台本，如果第 60 句那里漏标了一个 5 秒的停顿，
 * 后面 140 句全部晚 5 秒。逐句微调每句只能动一点点（被相邻句夹住），
 * 逐句点 140 次还追不上；整段平移一次就能对齐。
 *
 * 区间**在打开对话框时按当时的播放位置锁定**，之后平移不会让区间自己漂走——
 * 边听边调的时候，起点一直变会让人完全失去参照。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScriptBatchCalibrationDialog(
    ui: ScriptUi,
    vm: MainViewModel,
    onDismiss: () -> Unit,
) {
    val lines = ui.parsed?.timedLines.orEmpty()
    if (lines.isEmpty()) return
    val player by vm.player.collectAsStateWithLifecycle()
    val last = lines.size - 1
    val openedAt = remember { lineIndexAt(lines, player.positionMs) }
    var mode by remember { mutableIntStateOf(0) }
    var anchor by remember { mutableIntStateOf(openedAt) }
    var from by remember { mutableIntStateOf(openedAt) }
    var to by remember { mutableIntStateOf(last) }

    val range = when (mode) {
        0 -> 0..last
        1 -> CalibrationMath.normalizeRange(lines.size, anchor, last) ?: (0..last)
        else -> CalibrationMath.normalizeRange(lines.size, from, to) ?: (0..last)
    }
    val cal = ui.calibration
    val adjusted = CalibrationMath.adjustedInRange(cal, range)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("批量校准")
                Text(
                    "整段一起平移，句与句之间的间隔保持不变",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("全部") })
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1; anchor = lineIndexAt(lines, player.positionMs) },
                        label = { Text("当前句之后") },
                    )
                    FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("自定义") })
                }
                Spacer(Modifier.height(8.dp))

                if (mode == 2) {
                    RangePicker(
                        value = from,
                        max = last,
                        onChange = { from = it },
                        label = "起",
                    )
                    RangePicker(
                        value = to,
                        max = last,
                        onChange = { to = it },
                        label = "止",
                    )
                    TextButton(
                        onClick = {
                            val now = lineIndexAt(lines, player.positionMs)
                            from = now
                            to = last
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text("从当前句开始", style = MaterialTheme.typography.labelSmall) }
                    Spacer(Modifier.height(4.dp))
                }
                if (mode == 1) {
                    Text(
                        "起点锁定为打开面板时播到的那句（第 ${anchor + 1} 句）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    "第 " + (range.first + 1) + "–" + (range.last + 1) + " 句 · 共 " +
                        range.count() + " 句" +
                        if (adjusted > 0) " · 其中 " + adjusted + " 句已单独微调" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(6.dp))

                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CalibrationMath.RANGE_STEPS.forEach { step ->
                        CalibrationChip(stepLabel(step)) {
                            vm.shiftScriptLines(range.first, range.last, step)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 1.dp)
                Text(
                    "· 点一下整段就移动，句内长短不变，所以「对得上的那半段」不会散开；\n" +
                        "· 移动到相邻句的边界会停住，不会把这段挤进上一句或下一句；\n" +
                        "· 只想修一句时，长按台本那一句逐句校准更合适。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = {
            TextButton(
                onClick = { vm.resetScriptLinesRange(range.first, range.last) },
                enabled = adjusted > 0,
            ) { Text("清除本区间微调") }
        },
    )
}

/** 起止行号选择：只给 ± 两个按钮，不做滑块——几百行的台本上滑块根本点不准。 */
@Composable
private fun RangePicker(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        CalibrationChip("−") { onChange((value - 1).coerceAtLeast(0)) }
        Text(
            "第 " + (value + 1) + " 句",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        CalibrationChip("＋") { onChange((value + 1).coerceAtMost(max)) }
        Spacer(Modifier.weight(1f))
        if (value != max) {
            TextButton(
                onClick = { onChange(max) },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) { Text("末句", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** 打开面板时锁定的「当前句」：最后一个起点不晚于播放位置的行。 */
private fun lineIndexAt(lines: List<com.kiite.player.core.TimedLine>, positionMs: Long): Int {
    var hit = 0
    for (i in lines.indices) {
        if (lines[i].startMs <= positionMs) hit = i else break
    }
    return hit
}
