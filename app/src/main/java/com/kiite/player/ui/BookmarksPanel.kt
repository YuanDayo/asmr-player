package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiite.player.core.Bookmark
import com.kiite.player.core.BookmarkMath
import com.kiite.player.core.HistoryMath
import com.kiite.player.core.HistoryRecord
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.Stroke

/**
 * 「书签 / 历史」面板。
 *
 * 两块内容合成一个入口：都回答同一个问题——「我之前在听什么、听到哪儿了」，
 * 分成两个页面反而要用户先猜自己要找的东西被归到哪一边。
 */
@Composable
fun BookmarksPanel(vm: MainViewModel, onDismiss: () -> Unit) {
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val current by vm.currentTrack.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Bookmark?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = asmrShape(Radius.lg),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.84f)
                .then(asmrBorder(asmrShape(Radius.lg))),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.itemGap, bottom = Spacing.itemGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("书签与历史", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterChip(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("书签 " + bookmarks.size) },
                        leadingIcon = { Icon(Icons.Default.Bookmark, null, Modifier.size(IconSize.xs)) },
                    )
                    FilterChip(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("历史 " + history.records.size) },
                        leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(IconSize.xs)) },
                    )
                }
                Spacer(Modifier.height(Spacing.itemGap))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                )
                Box(Modifier.weight(1f)) {
                    if (tab == 0) {
                        BookmarkList(
                            vm = vm,
                            bookmarks = bookmarks,
                            currentPath = current?.path,
                            // 只有当前在播那一集才有「现在的台本」，别的集无从判断行号是否还对得上
                            currentFingerprint = vm.scriptFingerprint(),
                            onEdit = { editing = it },
                        )
                    } else {
                        HistoryList(
                            vm = vm,
                            records = history.records,
                            todayMs = HistoryMath.todayMs(history.dailyListenedMs, System.currentTimeMillis()),
                            totalMs = history.totalListenedMs,
                        )
                    }
                }
            }
        }
    }

    editing?.let { bm ->
        NoteDialog(
            bookmark = bm,
            onDismiss = { editing = null },
            onConfirm = { note ->
                vm.updateBookmarkNote(bm.id, note)
                editing = null
            },
        )
    }
}

// ---------- 书签 ----------

@Composable
private fun BookmarkList(
    vm: MainViewModel,
    bookmarks: List<Bookmark>,
    currentPath: String?,
    currentFingerprint: String,
    onEdit: (Bookmark) -> Unit,
) {
    val grouped = remember(bookmarks) { BookmarkMath.grouped(bookmarks) }
    Column(Modifier.fillMaxSize()) {
        AddBookmarkBar(vm = vm, enabled = currentPath != null)
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
        if (grouped.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                Text(
                    "还没有书签。\n" +
                        "· 点下面这个按钮：记住当前时间点\n" +
                        "· 点台本每句右侧的小书签图标：按句标记（再点一次取消）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            grouped.forEach { (trackPath, list) ->
                val head = list.first()
                item(key = "head-" + trackPath) {
                    val isCurrent = trackPath == currentPath
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                            )
                            .padding(horizontal = Spacing.lg, vertical = Spacing.itemGap),
                    ) {
                        Text(
                            head.trackName.ifBlank { trackPath.substringAfterLast('/') },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            (if (isCurrent) "正在播放 · " else "") +
                                (head.projectName.takeIf { it.isNotBlank() } ?: "未知作品") +
                                " · " + list.size + " 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                items(list, key = { it.id }) { bm ->
                    BookmarkRow(
                        bookmark = bm,
                        stale = bm.trackPath == currentPath &&
                            BookmarkMath.lineStale(bm, currentFingerprint),
                        onClick = {
                            vm.openBookmark(bm)
                            // 不关面板：连续听的时候常常要跳好几次
                        },
                        onEdit = { onEdit(bm) },
                        onDelete = { vm.deleteBookmark(bm.id) },
                    )
                }
            }
        }
    }
}

/** 顶部「在当前位置加书签」：只在这里收集播放进度，避免整个列表跟着 400ms 重组。 */
@Composable
private fun AddBookmarkBar(vm: MainViewModel, enabled: Boolean) {
    val ui by vm.player.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.gap10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("在当前位置加书签", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (enabled) "当前 " + clockOf(ui.positionMs) else "先放一首再试",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = { vm.addBookmarkNow() },
            enabled = enabled,
        ) {
            Icon(Icons.Default.Bookmark, null, Modifier.size(IconSize.sm))
            Spacer(Modifier.width(Spacing.itemGap))
            Text("加书签")
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    stale: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(Radius.xs))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Text(
                clockOf(bookmark.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                // 备注优先，其次当初那一句的原文——按行书签至少还能看出标的是哪句
                bookmark.note.ifBlank { bookmark.lineText }
                    .ifBlank { if (bookmark.isLine) "（第 ${bookmark.lineIndex + 1} 句）" else "（无备注）" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (bookmark.note.isBlank() && bookmark.lineText.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (bookmark.isLine) {
                Text(
                    if (stale) "第 ${bookmark.lineIndex + 1} 句 · 台本已变更" else "第 ${bookmark.lineIndex + 1} 句",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stale) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, "编辑备注", Modifier.size(IconSize.sm))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "删除书签", Modifier.size(IconSize.sm))
        }
    }
}

@Composable
private fun NoteDialog(bookmark: Bookmark, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // 按 id 建 key：连续编辑两条不同书签时，输入框要跟着换成那一条的备注
    var text by remember(bookmark.id) { mutableStateOf(bookmark.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = asmrShape(Radius.xl),
        title = { Text("书签备注 · " + clockOf(bookmark.positionMs)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("备注（可留空）") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ---------- 历史 ----------

@Composable
private fun HistoryList(
    vm: MainViewModel,
    records: List<HistoryRecord>,
    todayMs: Long,
    totalMs: Long,
) {
    val now = System.currentTimeMillis()
    // 一次算完哪些记录已失效，避免每一行都去曲库里线性查找一遍
    val unavailable = vm.unavailablePaths(records.map { it.trackPath })
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.gap10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "今日 " + HistoryMath.formatDuration(todayMs),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "累计 " + HistoryMath.formatDuration(totalMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (records.isNotEmpty()) {
                TextButton(onClick = { vm.clearHistory() }) { Text("清空") }
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                Text(
                    "还没有播放记录。听过的每一集都会出现在这里，长作品下次接着听。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(records, key = { it.trackPath }) { record ->
                HistoryRow(
                    record = record,
                    nowMs = now,
                    available = record.trackPath !in unavailable,
                    onContinue = { vm.continueFromHistory(record) },
                    onRemove = { vm.removeHistoryRecord(record.trackPath) },
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    record: HistoryRecord,
    nowMs: Long,
    available: Boolean,
    onContinue: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onContinue() }
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.trackName.ifBlank { record.trackPath.substringAfterLast('/') },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        record.projectName.takeIf { it.isNotBlank() },
                        HistoryMath.relativeTime(nowMs, record.lastPlayedAtMs),
                        if (record.playedCount > 1) "听过 " + record.playedCount + " 次" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!available) {
                Text(
                    "文件已失效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (record.finished) {
                Text(
                    "已听完",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    "继续",
                    Modifier.size(IconSize.md),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "从历史中移除", Modifier.size(IconSize.sm))
            }
        }
        if (record.durationMs > 0L) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.itemGap, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                // 自绘细进度条：不用 LinearProgressIndicator，避免不同 Material3 版本间的参数差异
                Box(
                    Modifier
                        .weight(1f)
                        .height(Stroke.medium)
                        .clip(RoundedCornerShape(Radius.xxs))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(record.progress)
                            .height(Stroke.medium)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                    )
                }
                Spacer(Modifier.width(Spacing.gap10))
                Text(
                    clockOf(record.positionMs) + " / " + clockOf(record.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 12:34（书签与历史都用整秒，不显示十分位——这里不需要那么精确）。 */
private fun clockOf(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1_000L)
    val hours = total / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
