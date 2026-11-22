package com.kiite.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kiite.player.core.HistoryMath
import com.kiite.player.core.HistoryRecord
import com.kiite.player.core.ScanResult
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.LocalBottomBarInset
import com.kiite.player.ui.theme.LocalTabReselect
import com.kiite.player.ui.theme.LocalTopBarMetrics
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import java.io.File
import kotlinx.coroutines.launch

/**
 * 首页：和曲库、设置同级的新标签页。
 *
 * 版式对齐参考图（Aura Player 首页）：
 * 应用名 + 欢迎行 → 2×2 快捷入口 → 最近在听 → 统计 → 建议 → 热门社团/声优 → 目标 → 备份。
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val restoredWorks by vm.restoredWorks.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    TrackScroll(listState)
    val tabReselect = LocalTabReselect.current
    LaunchedEffect(tabReselect) {
        if (tabReselect != 0) listState.animateScrollToItem(0)
    }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) vm.exportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    val records = history.records.sortedByDescending { it.lastPlayedAtMs }
    val recent = records.firstOrNull()
    val listenedProjects = records.map { it.projectPath }.filter { it.isNotBlank() }.distinct().size
    val todayMinutes = HistoryMath.todayMs(history.dailyListenedMs, System.currentTimeMillis()) / 60_000L
    val goal = settings.dailyGoalMinutes.coerceAtLeast(5)
    val people = remember { (vm.knownCircles() + vm.knownVoiceActors()).filter { it.isNotBlank() }.distinct().take(6) }
    val suggestProjects = remember(scan) { scan?.projects.orEmpty().shuffled().take(5) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = LocalTopBarMetrics.current.total + Spacing.md,
            bottom = LocalBottomBarInset.current + Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        item(key = "header") { HomeHeader(onOpenSettings) }

        item(key = "quick") {
            QuickActions(
                onRecent = { scope.launch { listState.animateScrollToItem(2) } },
                onRecentAdded = {
                    vm.setSortMode("recent")
                    onOpenLibrary()
                },
                onMostPlayed = onOpenLibrary,
                onShuffle = onOpenLibrary,
            )
        }

        item(key = "recent") {
            AsmrCard {
                Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                    Text(
                        "最近在听",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    if (recent != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HomeCover(coverOf(vm, scan, recent), HomeDim.recentCover)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    recent.projectName.ifBlank { recent.trackName },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    recent.trackName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    (recent.progress * 100f).toInt().toString() + "% · " +
                                        HistoryMath.relativeTime(System.currentTimeMillis(), recent.lastPlayedAtMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    } else {
                        Text(
                            "还没有播放记录，去曲库挑一部开始吧。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "stats") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
            ) {
                HomeStatCard("累计收听", listenedProjects.toString() + " 部", Modifier.weight(1f))
                HomeStatCard("收听时长", HistoryMath.formatDuration(history.totalListenedMs), Modifier.weight(1f))
            }
        }

        item(key = "suggest") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "建议",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.Default.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                ShuffleSuggestionCard(onOpenLibrary)
                if (suggestProjects.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.md))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        items(suggestProjects, key = { it.path }) { project ->
                            val cover = project.coverPath ?: vm.dlsiteCoverOf(project.path)
                            Column(
                                Modifier.width(HomeDim.stripCover),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                HomeCover(cover, HomeDim.stripCover, Radius.lg)
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    project.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (people.isNotEmpty()) {
            item(key = "people") {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onOpenLibrary),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "热门社团 / 声优",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(people) { name ->
                            FilterChip(
                                selected = false,
                                onClick = onOpenLibrary,
                                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "goal") {
            AsmrCard {
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(HomeDim.goalRing), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { (todayMinutes.toFloat() / goal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = HomeDim.ringStroke,
                        )
                        Text(
                            todayMinutes.toString() + " / " + goal,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    Column(Modifier.weight(1f)) {
                        Text("今日收听目标", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "今天已听 " + todayMinutes + " 分钟，目标 " + goal + " 分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.setDailyGoal(goal - 5) }) { Text("−5") }
                            Text(
                                "目标 " + goal + " 分钟",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { vm.setDailyGoal(goal + 5) }) { Text("+5") }
                        }
                    }
                }
            }
        }

        item(key = "backup") {
            AsmrCard {
                Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, null, Modifier.size(IconSize.md), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("备份与恢复", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "把设置、DLsite 登录状态和作品清单存成本地文件；换机或清数据后导入恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { exportLauncher.launch("kiite-backup.json") }) { Text("导出") }
                        TextButton(onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        }) { Text("导入") }
                        if (restoredWorks.isNotEmpty()) {
                            TextButton(onClick = { vm.startRecoveryDownload() }) { Text("批量补下") }
                        }
                    }
                }
            }
        }
    }
}

/** 应用名 + 欢迎行 + 右上设置入口，对齐参考图的「Aura Player + 欢迎，昵称」。 */
@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = com.kiite.player.R.drawable.kiite_logo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(HomeDim.logo).clip(asmrShape(Radius.sm)),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "Kiite Player",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "欢迎，本地听众",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, "设置", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun QuickActions(
    onRecent: () -> Unit,
    onRecentAdded: () -> Unit,
    onMostPlayed: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
            QuickAction("最近播放", Icons.Default.History, Modifier.weight(1f), onRecent)
            QuickAction("最近添加", Icons.AutoMirrored.Filled.PlaylistAdd, Modifier.weight(1f), onRecentAdded)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
            QuickAction("最常播放", Icons.Default.TrendingUp, Modifier.weight(1f), onMostPlayed)
            QuickAction("随机播放", Icons.Default.Shuffle, Modifier.weight(1f), onShuffle)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    AsmrCard(modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(IconSize.md), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.sm))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 「建议」里的随机混播大卡：压主色，对齐参考图里的「新歌混烧」。 */
@Composable
private fun ShuffleSuggestionCard(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(asmrShape(Radius.lg))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shuffle, null, Modifier.size(IconSize.lg), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text("随机混播", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "从曲库里随机挑，一口气听下去",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeCover(path: String?, size: Dp, radius: Dp = Radius.md) {
    Box(
        Modifier
            .size(size)
            .clip(asmrShape(radius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null && File(path).exists()) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.LibraryMusic, null, Modifier.size(IconSize.lg))
        }
    }
}

private fun coverOf(vm: MainViewModel, scan: ScanResult?, r: HistoryRecord): String? {
    val project = scan?.projects?.firstOrNull { it.path == r.projectPath } ?: return null
    return project.coverPath ?: vm.dlsiteCoverOf(r.projectPath)
}

@Composable
private fun HomeStatCard(title: String, value: String, modifier: Modifier = Modifier) {
    AsmrCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 首页本地尺寸。 */
private object HomeDim {
    val logo = 28.dp
    val recentCover = 64.dp
    val stripCover = 72.dp
    val goalRing = 88.dp
    val ringStroke = 8.dp
}
