package com.kiite.player.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kiite.player.core.HistoryMath
import com.kiite.player.core.HistoryRecord
import com.kiite.player.core.ScanResult
import com.kiite.player.data.AppSettings
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.LocalBottomBarInset
import com.kiite.player.ui.theme.LocalTabReselect
import com.kiite.player.ui.theme.LocalTopBarMetrics
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.pagePushIn
import com.kiite.player.ui.theme.pagePushOut
import com.kiite.player.ui.theme.pressScale
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首页：和曲库、设置同级的新标签页。
 *
 * 版式对齐参考图（Aura Player 首页）：
 * 应用名 + 欢迎行 → 2×2 快捷入口 → 最近在听 → 统计 → 建议 → 热门社团/声优 → 目标 → 备份。
 *
 * 这里是**入口页**，不是展示页：每个可点的东西都必须落到一个真实页面或一次真实播放
 * （历史页 / 统计页 / 曲库带条件 / 播放 / 设置子页），不留「按下去什么都不发生」的卡片。
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenProfile: () -> Unit = onOpenSettings,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val restoredWorks by vm.restoredWorks.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    // 首页内的二级页（整屏盖上来，返回键回首页）：
    // 历史 / 统计 / 已购作品都属于「首页这一支」，不值得单独占一个底部标签。
    var subPage by remember { mutableStateOf<HomeSubPage?>(null) }
    // 「建议」换一批：只改本地种子重新洗牌，不写盘
    var suggestSeed by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    TrackScroll(listState)
    // 重复点「首页」才回顶部/退二级页。计数是全局的，首次组合可能已经不是 0，
    // 所以只对「这次真的变了」反应，否则一进首页就把二级页状态清掉。
    val tabReselect = LocalTabReselect.current
    var seenReselect by remember { mutableIntStateOf(tabReselect) }
    LaunchedEffect(tabReselect) {
        if (tabReselect == seenReselect) return@LaunchedEffect
        seenReselect = tabReselect
        if (subPage != null) subPage = null else listState.animateScrollToItem(0)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) vm.exportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    val records = history.records.sortedByDescending { it.lastPlayedAtMs }
    val recent = records.firstOrNull()
    // 最近那集可能已经被删了：先算出来，卡片上直接标「文件已失效」，别让用户点了没反应。
    // 用 trackPath 当 key：records 每次重组都是新列表，拿它当 key 会变成「每次重组都查一次文件」
    val recentAvailable = remember(recent?.trackPath) {
        recent == null || recent.trackPath !in vm.unavailablePaths(listOf(recent.trackPath))
    }
    val listenedProjects = records.map { it.projectPath }.filter { it.isNotBlank() }.distinct().size
    val todayMinutes = HistoryMath.todayMs(history.dailyListenedMs, System.currentTimeMillis()) / 60_000L
    val goal = settings.dailyGoalMinutes.coerceAtLeast(5)
    // 社团 / 声优要遍历整个曲库：只在扫描结果变化时算一次，不要每次重组都跑
    val circles = remember(scan) { vm.knownCircles().filter { it.isNotBlank() }.distinct().take(6) }
    val voiceActors = remember(scan) { vm.knownVoiceActors().filter { it.isNotBlank() }.distinct().take(6) }
    val people = remember(circles, voiceActors) { circles.map { it to true } + voiceActors.map { it to false } }
    val suggestProjects = remember(scan, suggestSeed) { scan?.projects.orEmpty().shuffled().take(8) }

    // 所有「去曲库」的入口都从这里走：带上这次的条件，曲库消费一次就清掉
    val openLibraryWith: (LibraryIntent) -> Unit = { intent ->
        vm.requestLibrary(intent)
        onOpenLibrary()
    }
    // 随机播放只出迷你条（不弹半屏），播不了就明确告诉用户缺什么
    val playRandom: () -> Unit = {
        if (scan?.tracks.isNullOrEmpty()) {
            vm.say("曲库里还没有音频，先去曲库选个目录")
        } else {
            vm.playRandom()
        }
    }

    BackHandler(enabled = subPage != null) { subPage = null }

    Box(Modifier.fillMaxSize()) {
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
            item(key = "header") {
                HomeHeader(
                    settings = settings,
                    onOpenSettings = onOpenSettings,
                    onOpenProfile = onOpenProfile,
                )
            }

            item(key = "quick") {
                QuickActions(
                    // 最近播放 = 一集一集接着听，落点是播放历史，而不是「曲库换了排序」
                    onRecent = { subPage = HomeSubPage.HISTORY },
                    onRecentAdded = {
                        openLibraryWith(LibraryIntent(sortMode = "recent", asc = false, resetFilters = true))
                    },
                    onMostPlayed = {
                        openLibraryWith(LibraryIntent(sortMode = "plays", asc = false, resetFilters = true))
                    },
                    onShuffle = playRandom,
                )
            }

            item(key = "sources") {
                // 两个「去站里搜」的入口：DLsite（已购/在线）与 asmr.one（免费站）。
                // 它们不是曲库里的分类，而是「在线内容从哪来」，所以单独占一行，
                // 排在快捷入口之后、最近在听之前。
                SourcesSection(
                    onOpenDlsite = { openLibraryWith(LibraryIntent(source = "dlsite", resetFilters = true)) },
                    onOpenAsmrOne = { openLibraryWith(LibraryIntent(source = "asmrone", resetFilters = true)) },
                )
            }

            item(key = "recent") {
                RecentCard(
                    record = recent,
                    available = recentAvailable,
                    cover = recent?.let { coverOf(vm, scan, it) },
                    onContinue = { recent?.let { vm.continueFromHistory(it) } },
                    onMore = { subPage = HomeSubPage.HISTORY },
                )
            }

            item(key = "stats") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                ) {
                    HomeStatCard(
                        title = "累计收听",
                        value = listenedProjects.toString() + " 部",
                        modifier = Modifier.weight(1f),
                        onClick = { subPage = HomeSubPage.STATS },
                    )
                    HomeStatCard(
                        title = "收听时长",
                        value = HistoryMath.formatDuration(history.totalListenedMs),
                        modifier = Modifier.weight(1f),
                        onClick = { subPage = HomeSubPage.STATS },
                    )
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
                        IconButton(
                            onClick = {
                                suggestSeed++
                                vm.say("已换一批建议")
                            },
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                "换一批",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    if (scan?.projects.isNullOrEmpty()) {
                        EmptySuggestionCard(onOpenLibrary = onOpenLibrary)
                    } else {
                        ShuffleSuggestionCard(onPlay = playRandom)
                        if (suggestProjects.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.md))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                items(suggestProjects, key = { it.path }) { project ->
                                    val cover = project.coverPath ?: vm.dlsiteCoverOf(project.path)
                                    Column(
                                        Modifier
                                            .width(HomeDim.stripCover)
                                            .clip(asmrShape(Radius.lg))
                                            .clickable(role = Role.Button) {
                                                // 点封面直接进这部作品的详情（顺带清掉上一轮筛选，保证它确实在列表里）
                                                openLibraryWith(
                                                    LibraryIntent(
                                                        projectPath = project.path,
                                                        resetFilters = true,
                                                    ),
                                                )
                                            },
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
            }

            if (people.isNotEmpty()) {
                item(key = "people") {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "热门社团 / 声优",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "点一个筛选曲库",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(people) { person ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        // 带条件进曲库：落地后筛选行里会出现「社团 · X」标签，可一键撤掉
                                        openLibraryWith(
                                            if (person.second) {
                                                LibraryIntent(circle = person.first, resetFilters = true)
                                            } else {
                                                LibraryIntent(voiceActor = person.first, resetFilters = true)
                                            },
                                        )
                                    },
                                    label = {
                                        Text(person.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
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
                                TextButton(onClick = { subPage = HomeSubPage.STATS }) { Text("统计") }
                            }
                        }
                    }
                }
            }

            item(key = "purchases") {
                TapCard(onClick = { subPage = HomeSubPage.PURCHASES }) {
                    Row(
                        Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            null,
                            Modifier.size(IconSize.md),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text("已购作品", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (purchases.isEmpty()) "DLsite 已购作品会集中显示在这里"
                                else "共 " + purchases.size + " 部，点开查看与下载",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "backup") {
                AsmrCard {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Save,
                                null,
                                Modifier.size(IconSize.md),
                                tint = MaterialTheme.colorScheme.primary,
                            )
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

        // 二级页盖在主页之上，主页**留在底层不动**（不卸载，滚动位置与已加载的数据都还在）。
        // 以前这里是 `when (subPage)` 硬切：点一下整页内容一帧换掉，像被吞了。
        // 现在只让「二级页这一层」动：进场从右侧推入、退场推回右侧，主页不参与位移
        // （两层同时动会互相错位，见 MotionTransitions 里 tabTransition 的注释）。
        // 动效直接用曲库二级页那套 pagePushIn / pagePushOut，两个位置的手感一致。
        AnimatedContent(
            targetState = subPage,
            transitionSpec = {
                if (targetState == null) {
                    // 回主页：二级页推回右侧，主页原地露出（不给主页做进场，避免"再淡入一次"）
                    EnterTransition.None togetherWith pagePushOut()
                } else {
                    pagePushIn() togetherWith ExitTransition.None
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "homeSubPage",
        ) { page ->
            // 两个状态都包一层满屏 Box：AnimatedContent 默认会补间容器尺寸，
            // null 那一侧若量成 0×0，退场中的二级页会被挤扁。
            Box(Modifier.fillMaxSize()) {
                when (page) {
                    HomeSubPage.HISTORY -> HomeHistoryPage(vm) { subPage = null }
                    HomeSubPage.STATS -> HomeStatsPage(vm) { subPage = null }
                    HomeSubPage.PURCHASES -> PurchasesPage(vm) { subPage = null }
                    null -> Unit
                }
            }
        }
    }
}

/** 首页内可以整屏打开的二级页。 */
private enum class HomeSubPage { HISTORY, STATS, PURCHASES }

/**
 * 可点卡片：按下时卡片轻微「陷一下」，涟漪画在卡面之上。
 *
 * 之前首页的卡片是纯排版（`Card { Row { ... } }`），手指按下去没有任何响应，
 * 用户分不清「这里不能点」和「点了但没反应」。凡是可点的位置都走这个容器。
 */
@Composable
private fun TapCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    AsmrCard(modifier.pressScale(interaction)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                ),
            content = content,
        )
    }
}

/** 应用名 + 欢迎行 + 头像（点进个人资料）+ 右上设置入口。 */
@Composable
private fun HomeHeader(
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 标题与欢迎行整块可点：个人资料（昵称 / 头像）就在设置里，不用绕一圈
        Row(
            Modifier
                .weight(1f)
                .clip(asmrShape(Radius.md))
                .clickable(role = Role.Button, onClick = onOpenProfile)
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = com.kiite.player.R.drawable.kiite_logo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(HomeDim.logo).clip(asmrShape(Radius.sm)),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    "Kiite Player",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "欢迎，" + settings.nickname.ifBlank { "本地听众" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (settings.avatarPath != null && File(settings.avatarPath).exists()) {
                Spacer(Modifier.width(Spacing.sm))
                AsyncImage(
                    model = File(settings.avatarPath),
                    contentDescription = "个人资料",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(HomeDim.avatar).clip(CircleShape),
                )
            }
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            QuickAction("最近播放", Icons.Default.History, Modifier.weight(1f), onRecent)
            QuickAction("最近添加", Icons.AutoMirrored.Filled.PlaylistAdd, Modifier.weight(1f), onRecentAdded)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            QuickAction("最常播放", Icons.Default.TrendingUp, Modifier.weight(1f), onMostPlayed)
            QuickAction("随机播放", Icons.Default.Shuffle, Modifier.weight(1f), onShuffle)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    TapCard(onClick = onClick, modifier = modifier) {
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

/**
 * DLsite / asmr.one 两个在线来源入口。
 *
 * 落点都是曲库的独立搜索页，并把来源预选到对应站点（见 LibraryIntent.source）：
 * 这两个站没有「首页」可浏览，只能按作品名 / 编号搜，所以入口必须直接送到能搜的地方，
 * 而不是一个空列表。
 */
@Composable
private fun SourcesSection(onOpenDlsite: () -> Unit, onOpenAsmrOne: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        SourceCard("DLsite", "已购 · 在线", Icons.Default.ShoppingBag, Modifier.weight(1f), onOpenDlsite)
        SourceCard("asmr.one", "免费资源站", Icons.Default.CloudDownload, Modifier.weight(1f), onOpenAsmrOne)
    }
}

@Composable
private fun SourceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TapCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.lg)) {
            Icon(icon, null, Modifier.size(IconSize.md), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.sm))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 「最近在听」：点整块接着上次的位置继续听，右上「全部」进播放历史。 */
@Composable
private fun RecentCard(
    record: HistoryRecord?,
    available: Boolean,
    cover: String?,
    onContinue: () -> Unit,
    onMore: () -> Unit,
) {
    AsmrCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最近在听",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (record != null) {
                    TextButton(onClick = onMore) { Text("全部") }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            if (record == null) {
                Text(
                    "还没有播放记录，去曲库挑一部开始吧。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(asmrShape(Radius.md))
                        .clickable(enabled = available, role = Role.Button, onClick = onContinue)
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeCover(cover, HomeDim.recentCover)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            record.projectName.ifBlank { record.trackName },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            record.trackName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            if (available) {
                                (record.progress * 100f).toInt().toString() + "% · " +
                                    HistoryMath.relativeTime(System.currentTimeMillis(), record.lastPlayedAtMs)
                            } else {
                                "文件已失效，去曲库重新扫描"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (available) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    if (available) {
                        Icon(
                            Icons.Default.PlayArrow,
                            "继续播放",
                            Modifier.size(IconSize.lg),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** 「建议」里的随机混播大卡：**点了真的开始播**，不再只把用户送到曲库。 */
@Composable
private fun ShuffleSuggestionCard(onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(asmrShape(Radius.lg))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onPlay,
            )
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

/** 曲库还空着时的建议位：给出唯一能推进的动作，而不是一张点不动的空卡片。 */
@Composable
private fun EmptySuggestionCard(onOpenLibrary: () -> Unit) {
    TapCard(onClick = onOpenLibrary) {
        Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LibraryMusic,
                null,
                Modifier.size(IconSize.lg),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("曲库还是空的", style = MaterialTheme.typography.titleMedium)
                Text(
                    "去曲库选一个装着 ASMR 作品的文件夹，建议就会出现在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun HomeStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TapCard(onClick = onClick, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
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
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                "查看统计",
                Modifier.size(IconSize.sm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- 二级页：播放历史 / 收听统计 / 已购作品 ----------------

/**
 * 首页二级页的统一外壳：返回 + 标题 + 副标题，内容区自己滚。
 *
 * 顶栏用 LocalTopBarMetrics 让出状态栏高度（与首页首屏对齐），底部留给悬浮导航胶囊。
 */
@Composable
private fun HomeSubPageScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = LocalTopBarMetrics.current.total + Spacing.sm, bottom = Spacing.sm)
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f), content = content)
    }
}

/** 播放历史：复用「书签与历史」弹窗里那张列表，点一行从上次的位置接着听。 */
@Composable
private fun HomeHistoryPage(vm: MainViewModel, onBack: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    HomeSubPageScaffold(
        title = "播放历史",
        subtitle = if (history.records.isEmpty()) {
            "还没有播放记录"
        } else {
            "共 " + history.records.size + " 条 · 点一行从上次的位置接着听"
        },
        onBack = onBack,
    ) {
        HistoryList(
            vm = vm,
            records = history.records,
            todayMs = HistoryMath.todayMs(history.dailyListenedMs, System.currentTimeMillis()),
            totalMs = history.totalListenedMs,
            bottomPadding = LocalBottomBarInset.current + Spacing.xxl,
        )
    }
}

/**
 * 收听统计：今日 / 近 7 天 / 累计 + 近 14 天柱状图 + 连续天数 + 最常听的作品。
 *
 * 数据全部来自 history.json 里已有的 `dailyListenedMs` 与 `records`，不新增存储。
 */
@Composable
private fun HomeStatsPage(vm: MainViewModel, onBack: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val daily = history.dailyListenedMs
    val today = LocalDate.now(ZoneId.systemDefault())
    val todayMs = daily[today.toString()] ?: 0L
    val weekMs = (6 downTo 0).sumOf { daily[today.minusDays(it.toLong()).toString()] ?: 0L }
    val monthMs = (29 downTo 0).sumOf { daily[today.minusDays(it.toLong()).toString()] ?: 0L }
    val days = (13 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        date to (daily[date.toString()] ?: 0L)
    }
    val maxMs = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    // 连续天数：从今天往前数，遇到「没听」就停。今天还没听就是 0，不拿昨天冒充。
    val streak = run {
        var count = 0
        var date = today
        while ((daily[date.toString()] ?: 0L) > 0L) {
            count++
            date = date.minusDays(1)
        }
        count
    }
    val listenedProjects = history.records.map { it.projectPath }.filter { it.isNotBlank() }.distinct().size
    val goal = settings.dailyGoalMinutes.coerceAtLeast(5)
    val topWorks = remember(history.records) {
        history.records
            .groupBy { it.projectPath }
            .map { (path, rs) ->
                (rs.firstOrNull()?.projectName?.takeIf { it.isNotBlank() } ?: path) to rs.sumOf { it.playedCount }
            }
            .sortedByDescending { it.second }
            .take(3)
    }

    HomeSubPageScaffold(
        title = "收听统计",
        subtitle = "数据只存在本机，不联网",
        onBack = onBack,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                bottom = LocalBottomBarInset.current + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            item(key = "numbers") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                    ) {
                        HomeStatCard("今日", HistoryMath.formatDuration(todayMs), Modifier.weight(1f)) {}
                        HomeStatCard("近 7 天", HistoryMath.formatDuration(weekMs), Modifier.weight(1f)) {}
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                    ) {
                        HomeStatCard("近 30 天", HistoryMath.formatDuration(monthMs), Modifier.weight(1f)) {}
                        HomeStatCard("累计", HistoryMath.formatDuration(history.totalListenedMs), Modifier.weight(1f)) {}
                    }
                }
            }

            item(key = "chart") {
                AsmrCard {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        Text("近 14 天", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "每天的收听时长",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Row(
                            Modifier.fillMaxWidth().height(HomeDim.chart),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            days.forEach { (date, ms) ->
                                val frac = if (ms <= 0L) 0.02f else (ms.toFloat() / maxMs).coerceAtLeast(0.06f)
                                Column(
                                    Modifier.weight(1f).fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier.weight(1f).fillMaxWidth(),
                                        contentAlignment = Alignment.BottomCenter,
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(0.62f)
                                                .fillMaxHeight(frac)
                                                .clip(asmrShape(Radius.xxs))
                                                .background(
                                                    if (ms > 0L) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                ),
                                        )
                                    }
                                    Text(
                                        if (date == today) "今" else date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "summary") {
                AsmrCard {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "连续收听",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (streak > 0) streak.toString() + " 天" else "今天还没听",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "听过作品",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    listenedProjects.toString() + " 部",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "今日目标",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    (todayMs / 60_000L).toString() + " / " + goal + " 分",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (topWorks.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.lg))
                            Text(
                                "最常听",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            topWorks.forEach { (name, count) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "听过 " + count + " 次",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 首页「已购作品」入口打开的全屏列表：封面 / 标题 / 编号 / 下载 / 同步。 */
@Composable
private fun PurchasesPage(vm: MainViewModel, onBack: () -> Unit) {
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val loginState by vm.dlsiteLoginState.collectAsStateWithLifecycle()
    val missing = remember(purchases) { vm.missingPurchases().map { it.code.uppercase() }.toSet() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = LocalTopBarMetrics.current.total + Spacing.sm, bottom = Spacing.sm)
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text("已购作品", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        purchases.isEmpty() && loginState != com.kiite.player.core.DlsiteLoginState.LOGGED_IN ->
                            "先登录 DLsite，再点右上角同步"
                        purchases.isEmpty() -> "没有已购作品"
                        else -> "共 " + purchases.size + " 部"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.syncPurchases() }) {
                Icon(Icons.Default.Refresh, "同步已购", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (purchases.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (loginState == com.kiite.player.core.DlsiteLoginState.LOGGED_IN)
                        "没有已购作品，点右上角同步试试" else "还没有同步已购作品",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loginState != com.kiite.player.core.DlsiteLoginState.LOGGED_IN) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        "登录入口在「设置 · DLsite」，登录后回到这里点右上角同步。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    end = Spacing.md,
                    bottom = LocalBottomBarInset.current + Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
            ) {
                items(purchases, key = { it.code }) { p ->
                    val downloaded = p.code.uppercase() !in missing
                    AsmrCard {
                        Row(
                            Modifier.fillMaxWidth().padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HomeCover(vm.dlsiteCoverOfCode(p.code), HomeDim.recentCover)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.title?.takeIf { it.isNotBlank() } ?: p.code,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    p.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (downloaded) "已下载" else "未下载",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (downloaded) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!downloaded) {
                                IconButton(onClick = { vm.downloadDlsiteOwned(p.code) }) {
                                    Icon(Icons.Default.Download, "下载", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 首页本地尺寸。 */
private object HomeDim {
    val logo = 28.dp
    val avatar = 34.dp
    val recentCover = 64.dp
    val stripCover = 72.dp
    val goalRing = 88.dp
    val ringStroke = 8.dp
    val chart = 120.dp
}
