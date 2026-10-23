package com.kiite.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.ScanResult
import com.kiite.player.core.TrackEntry
import com.kiite.player.data.AppSettings
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.util.Permissions
import java.io.File

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val selectedProject by vm.selectedProject.collectAsStateWithLifecycle()
    val manualLinks by vm.manualLinks.collectAsStateWithLifecycle()
    val currentTrack by vm.currentTrack.collectAsStateWithLifecycle()
    var showRootPicker by remember { mutableStateOf(false) }
    // 搜索框默认收起：多数时候不用搜索，收起后音频列表整体上移
    var searchOpen by remember { mutableStateOf(false) }
    // 列表滚动状态：上滑收起顶部、并提供「回到顶部」
    val listState = rememberLazyListState()
    val scrolledDown = rememberTopCollapsed(listState)
    val scope = rememberCoroutineScope()
    // 作品内页（二级页面）的滚动状态，用来一并收起上方标签
    val detailListState = rememberLazyListState()
    val detailScrolled = rememberTopCollapsed(detailListState)
    var downloadCode by remember { mutableStateOf<String?>(null) }
    var scriptPickerTrack by remember { mutableStateOf<TrackEntry?>(null) }

    downloadCode?.let { code ->
        DlsiteDownloadScreen(vm, code) { downloadCode = null }
        return
    }

    if (showRootPicker) {
        FolderPickerDialog(
            start = settings.rootPath?.let { File(it) }?.takeIf { it.isDirectory } ?: Permissions.storageRoot(),
            onPick = {
                showRootPicker = false
                vm.setRoot(it.absolutePath)
            },
            onDismiss = { showRootPicker = false },
        )
    }

    scriptPickerTrack?.let { track ->
        ScriptPickerDialog(
            start = File(track.folderPath).takeIf { it.isDirectory } ?: Permissions.storageRoot(),
            hasManual = manualLinks.containsKey(track.path),
            onPick = {
                scriptPickerTrack = null
                vm.assignScript(track.path, it.absolutePath)
            },
            onPickUri = { uri ->
                scriptPickerTrack = null
                vm.assignScriptFromUri(track.path, uri)
            },
            onClear = {
                scriptPickerTrack = null
                vm.clearScript(track.path)
            },
            onDismiss = { scriptPickerTrack = null },
        )
    }

    val rootPath = settings.rootPath
    if (rootPath == null) {
        EmptyLibrary(onChoose = { showRootPicker = true })
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideScreenMinWidth
        val project = selectedProject
        Box(Modifier.fillMaxSize()) {
        if (wide) {
            // 宽屏（横屏/平板）：列表与详情并排，避免上下卡片挤在一起
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    RootHeader(
                        rootPath = rootPath,
                        scanning = scanning,
                        projectCount = scan?.projectCount ?: 0,
                        trackCount = scan?.trackCount ?: 0,
                        matched = scan?.withScriptCount ?: 0,
                        searchOpen = searchOpen,
                        onToggleSearch = { searchOpen = !searchOpen },
                        onRescan = { vm.rescan() },
                        onChangeRoot = { showRootPicker = true },
                    )
                    if (scanning && scan == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    ProjectList(
                        vm = vm,
                        scan = scan,
                        coverOf = { vm.dlsiteCoverOf(it) },
                        currentPath = currentTrack?.path,
                        onPickScript = { scriptPickerTrack = it },
                        onDownload = { downloadCode = it },
                        onOpen = { vm.selectProject(it) },
                        showSearch = searchOpen,
                        listState = listState,
                        compact = true,
                    )
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (project == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "从左侧选择一个作品查看详情",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        ProjectDetail(
                            vm = vm,
                            project = scan?.projects?.firstOrNull { it.path == project },
                            currentPath = currentTrack?.path,
                            onBack = { vm.selectProject(null) },
                            onPickScript = { scriptPickerTrack = it },
                            onDownload = { downloadCode = it },
                            listState = detailListState,
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // 上滑（往下翻）时把顶部那一行收起来，把高度让给列表
                AnimatedVisibility(
                    visible = if (project != null) !detailScrolled else !scrolledDown,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    RootHeader(
                        rootPath = rootPath,
                        scanning = scanning,
                        projectCount = scan?.projectCount ?: 0,
                        trackCount = scan?.trackCount ?: 0,
                        matched = scan?.withScriptCount ?: 0,
                        searchOpen = searchOpen,
                        onToggleSearch = { searchOpen = !searchOpen },
                        onRescan = { vm.rescan() },
                        onChangeRoot = { showRootPicker = true },
                    )
                }
                if (scanning && scan == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (project == null) {
                    ProjectList(
                        vm = vm,
                        scan = scan,
                        coverOf = { vm.dlsiteCoverOf(it) },
                        currentPath = currentTrack?.path,
                        onPickScript = { scriptPickerTrack = it },
                        onDownload = { downloadCode = it },
                        onOpen = { vm.selectProject(it) },
                        showSearch = searchOpen,
                        listState = listState,
                    )
                } else {
                    ProjectDetail(
                        vm = vm,
                        project = scan?.projects?.firstOrNull { it.path == project },
                        currentPath = currentTrack?.path,
                        onBack = { vm.selectProject(null) },
                        onPickScript = { scriptPickerTrack = it },
                        onDownload = { downloadCode = it },
                        listState = detailListState,
                    )
                }
            }
        }
        // 「回到顶部」：翻了之后才出现，叠在列表右下角
        if (!wide && project == null && scrolledDown) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, "回到顶部")
            }
        }
        }
    }
}

@Composable
private fun EmptyLibrary(onChoose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("还没有选择曲库目录", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "选一个装着 ASMR 解压文件夹的目录。应用会把每部作品识别成一个「总项目」，" +
                "并把它的全部音频统合在一起展示。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onChoose) { Text("选择文件夹") }
    }
}

@Composable
private fun RootHeader(
    rootPath: String,
    scanning: Boolean,
    projectCount: Int,
    trackCount: Int,
    matched: Int,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onRescan: () -> Unit,
    onChangeRoot: () -> Unit,
) {
    // 压成一行（目录名 + 统计 + 图标），搜索框收到放大镜里，把高度让给音频列表
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rootPath.substringAfterLast('/').ifBlank { rootPath } + "　" +
                projectCount + " 项目 · " + trackCount + " 音频 · " + matched + " 台本",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleSearch) {
            Icon(Icons.Default.Search, if (searchOpen) "收起搜索" else "搜索")
        }
        IconButton(onClick = onRescan, enabled = !scanning) {
            Icon(Icons.Default.Refresh, if (scanning) "扫描中" else "重新扫描")
        }
        IconButton(onClick = onChangeRoot) { Icon(Icons.Default.Folder, "更换目录") }
    }
}

@Composable
private fun ProjectList(
    vm: MainViewModel,
    scan: ScanResult?,
    coverOf: (String) -> String?,
    currentPath: String?,
    onPickScript: (TrackEntry) -> Unit,
    onDownload: (String) -> Unit,
    onOpen: (String) -> Unit,
    /** 是否显示搜索框（默认收起，点顶部放大镜展开）。 */
    showSearch: Boolean = true,
    /** 横屏（宽屏）：只留搜索 + 筛选下拉，把高度让给列表。 */
    compact: Boolean = false,
    /** 由外层持有，便于「上滑收起顶部 / 回到顶部」。 */
    listState: LazyListState,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    // 手动分级 / DLsite 识别结果变化时，列表里的分级标签与「成人/全年龄」筛选也要刷新
    val manualRatings by vm.manualRatings.collectAsStateWithLifecycle()
    val dlsiteMeta by vm.dlsite.collectAsStateWithLifecycle()
    // 自定义标签变化时，筛选与标签展示也要刷新
    val manualTags by vm.manualTags.collectAsStateWithLifecycle()
    val all = scan?.projects ?: emptyList()
    var query by remember { mutableStateOf("") }
    // 筛选拆成两个：台本（有/无）+ 年龄限制（成人/全年龄），各自二级弹窗
    var scriptFilter by remember { mutableStateOf(0) }
    var ratingFilter by remember { mutableStateOf(0) }
    var scriptSheet by remember { mutableStateOf(false) }
    var ratingSheet by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf(false) }
    var circleFilter by remember { mutableStateOf<String?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var vaFilter by remember { mutableStateOf<String?>(null) }
    var customTagFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 排序 / 批量收进二级弹窗，保证这些按键永远在可视范围内
    var sortSheet by remember { mutableStateOf(false) }
    val markSelected: (com.kiite.player.core.WorkRating) -> Unit = { rating ->
        all.filter { selected.contains(it.path) }.forEach { vm.setRating(it, rating) }
        selected = emptySet()
    }
    // 批量把所选项目的曲目加进播放队列
    val addSelectedToQueue: () -> Unit = {
        vm.queueAddAll(selected.flatMap { vm.tracksOfProject(it) })
        selected = emptySet()
    }
    val base = remember(all, manualRatings, dlsiteMeta, manualTags, query, scriptFilter, ratingFilter, circleFilter, tagFilter, vaFilter, customTagFilter) {
        all.filter { p ->
        val work = vm.dlsiteOf(p.path)
        val hitQuery = query.isBlank() ||
            projectTitle(p).contains(query, ignoreCase = true) ||
            work?.title?.contains(query, ignoreCase = true) == true ||
            work?.circle?.contains(query, ignoreCase = true) == true ||
            work?.tags?.any { it.contains(query, ignoreCase = true) } == true ||
            work?.voiceActors?.any { it.contains(query, ignoreCase = true) } == true ||
            vm.tagsOf(p).any { it.contains(query, ignoreCase = true) }
        val hitMeta = (circleFilter == null || work?.circle == circleFilter) &&
            (tagFilter == null || work?.tags?.contains(tagFilter) == true) &&
            (vaFilter == null || work?.voiceActors?.contains(vaFilter) == true)
        val rating = vm.ratingOf(p)
        val hitScript = when (scriptFilter) {
            1 -> p.scriptCount > 0
            2 -> p.scriptCount == 0
            else -> true
        }
        val hitRating = when (ratingFilter) {
            1 -> rating == com.kiite.player.core.WorkRating.R18
            2 -> rating == com.kiite.player.core.WorkRating.ALL
            else -> true
        }
        val hitFilter = hitScript && hitRating
        val hitCustomTag = customTagFilter == null || vm.tagsOf(p).contains(customTagFilter)
        hitQuery && hitFilter && hitMeta && hitCustomTag
        }
    }
    val ordered = when (settings.sortMode) {
        "code" -> base.sortedBy { (it.code ?: "zzz").lowercase() }
        "tracks" -> base.sortedBy { it.trackCount }
        "recent" -> base.sortedBy { java.io.File(it.path).lastModified() }
        else -> base.sortedBy { it.name.lowercase() }
    }
    val projects = if (settings.sortAsc) ordered else ordered.reversed()
    // 已识别的作品数量变了就重建筛选项
    val q0 = vm.dlsiteCount
    if (all.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有找到音频文件", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
    val circles = remember(q0) { vm.knownCircles() }
    val tags = remember(q0) { vm.knownTags() }
    val vas = remember(q0) { vm.knownVoiceActors() }
    val customTags = remember(q0, manualTags) { vm.knownCustomTags() }
    // 搜索框默认收起；一旦有搜索内容就一直显示（否则没法清空）
    val searchVisible = showSearch || query.isNotBlank()
    if (compact) {
        // 横屏：只留搜索 + 「筛选」下拉，把高度让给列表
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchVisible) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    shape = asmrShape(999.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("搜索作品名 / RJ 编号 / 曲目名") },
                )
                Spacer(Modifier.width(8.dp))
            }
            CompactFilterMenu(
                vm = vm,
                settings = settings,
                totalCount = projects.size,
                scriptFilter = scriptFilter,
                onScriptFilter = { scriptFilter = it },
                ratingFilter = ratingFilter,
                onRatingFilter = { ratingFilter = it },
                selectedCount = selected.size,
                onMarkAdult = { markSelected(com.kiite.player.core.WorkRating.R18) },
                onMarkAllAges = { markSelected(com.kiite.player.core.WorkRating.ALL) },
                batch = batch,
                onBatch = { on ->
                    batch = on
                    if (!on) selected = emptySet()
                },
                circles = circles,
                circleFilter = circleFilter,
                onCircle = { circleFilter = it },
                tags = tags,
                tagFilter = tagFilter,
                onTag = { tagFilter = it },
                vas = vas,
                vaFilter = vaFilter,
                onVa = { vaFilter = it },
                customTags = customTags,
                customTagFilter = customTagFilter,
                onCustomTag = { customTagFilter = it },
                onClearMeta = {
                    circleFilter = null
                    tagFilter = null
                    vaFilter = null
                },
            )
        }
    } else {
        if (searchVisible) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                shape = asmrShape(999.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("搜索作品名 / RJ 编号 / 曲目名") },
            )
        }
        // 排序 / 批量进二级弹窗；分类筛选和它并成一行，少占高度
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = batch,
                onClick = { sortSheet = true },
                label = {
                    Text(
                        "排序 ▾" +
                            (if (settings.sortMode == "code") " 编号" else if (settings.sortMode == "tracks") " 曲目" else if (settings.sortMode == "recent") " 时间" else " 名称") +
                            (if (settings.sortAsc) " ↑" else " ↓"),
                    )
                },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = scriptFilter != 0,
                onClick = { scriptSheet = true },
                label = {
                    Text(
                        "台本 ▾" + when (scriptFilter) {
                            1 -> " 有"
                            2 -> " 无"
                            else -> ""
                        },
                    )
                },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = ratingFilter != 0 || batch,
                onClick = { ratingSheet = true },
                label = {
                    Text(
                        "年龄限制 ▾" + when (ratingFilter) {
                            1 -> " 成人"
                            2 -> " 全年龄"
                            else -> ""
                        },
                    )
                },
            )
            Spacer(Modifier.width(8.dp))
            // 显示方式：列表 → 中图标 → 大图标
            FilterChip(
                selected = settings.projectLayout != "list",
                onClick = { vm.cycleProjectLayout() },
                label = {
                    Text(
                        when (settings.projectLayout) {
                            "medium" -> "显示：中图标"
                            "large" -> "显示：大图标"
                            else -> "显示：列表"
                        },
                    )
                },
            )
        }
    }
    if (batch) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "已选 " + selected.size + " 个项目",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                all.filter { selected.contains(it.path) }.forEach {
                    vm.setRating(it, com.kiite.player.core.WorkRating.R18)
                }
                selected = emptySet()
            }) { Text("标为成人") }
            TextButton(onClick = {
                all.filter { selected.contains(it.path) }.forEach {
                    vm.setRating(it, com.kiite.player.core.WorkRating.ALL)
                }
                selected = emptySet()
            }) { Text("标为全年龄") }
            TextButton(onClick = { addSelectedToQueue() }) { Text("加入播放列表") }
        }
    }
    if (!compact) {
        if (circles.isNotEmpty() || tags.isNotEmpty() || vas.isNotEmpty() || customTags.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                FilterMenu("社团", circles, circleFilter) { circleFilter = it }
                FilterMenu("标签", tags, tagFilter) { tagFilter = it }
                FilterMenu("声优", vas, vaFilter) { vaFilter = it }
                FilterMenu("自定义标签", customTags, customTagFilter) { customTagFilter = it }
                if (circleFilter != null || tagFilter != null || vaFilter != null || customTagFilter != null) {
                    FilterChip(
                        selected = true,
                        onClick = {
                            circleFilter = null
                            tagFilter = null
                            vaFilter = null
                            customTagFilter = null
                        },
                        label = { Text("清除筛选") },
                    )
                }
            }
        }
    }
    // 二级「排序与批量」弹窗：排序方式/方向 + 批量分类按键，保证永远点得到
    if (sortSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { sortSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = asmrShape(20.dp),
            title = { Text("排序") },
            text = {
                Column {
                    Text("排序方式", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("name" to "名称", "code" to "编号", "tracks" to "曲目", "recent" to "时间")
                            .forEach { (id, label) ->
                                FilterChip(
                                    selected = settings.sortMode == id,
                                    onClick = { vm.setSortMode(id) },
                                    label = { Text(label) },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("排序方向", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        FilterChip(
                            selected = settings.sortAsc,
                            onClick = { vm.setSortAsc(true) },
                            label = { Text("升序") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = !settings.sortAsc,
                            onClick = { vm.setSortAsc(false) },
                            label = { Text("降序") },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sortSheet = false }) { Text("关闭") }
            },
        )
    }
    // 二级「台本」：只看有/无台本
    if (scriptSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { scriptSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = asmrShape(20.dp),
            title = { Text("台本") },
            text = {
                Column {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("全部", "有台本", "无台本").forEachIndexed { i, label ->
                            FilterChip(
                                selected = scriptFilter == i,
                                onClick = { scriptFilter = i },
                                label = { Text(label) },
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { scriptSheet = false }) { Text("关闭") } },
        )
    }
    // 二级「年龄限制」：成人/全年龄筛选 + 批量分类（标为成人 / 标为全年龄）
    if (ratingSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ratingSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = asmrShape(20.dp),
            title = { Text("年龄限制") },
            text = {
                Column {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("全部", "成人", "全年龄").forEachIndexed { i, label ->
                            FilterChip(
                                selected = ratingFilter == i,
                                onClick = { ratingFilter = i },
                                label = { Text(label) },
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(
                        "批量分类（已选 " + selected.size + " 个项目）",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(
                            selected = batch,
                            onClick = {
                                batch = !batch
                                if (!batch) selected = emptySet()
                            },
                            label = { Text(if (batch) "退出批量" else "进入批量") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = false,
                            onClick = { markSelected(com.kiite.player.core.WorkRating.R18) },
                            label = { Text("标为成人") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = false,
                            onClick = { markSelected(com.kiite.player.core.WorkRating.ALL) },
                            label = { Text("标为全年龄") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = false,
                            onClick = { addSelectedToQueue() },
                            label = { Text("加入播放列表") },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "进入批量后，在列表里点作品即可勾选，再回来点「标为成人 / 标为全年龄」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { ratingSheet = false }) { Text("关闭") } },
        )
    }
    // 搜索时把命中的单曲也列出来，可以直接选具体音频（不必进作品再找）
    val trackHits = if (query.isBlank()) {
        emptyList()
    } else {
        (scan?.tracks ?: emptyList())
            .filter { it.name.contains(query, ignoreCase = true) || it.baseName.contains(query, ignoreCase = true) }
            .take(80)
    }
    var searchTab by remember(query) { mutableStateOf(0) }
    val pending = if (query.isBlank() && scriptFilter == 0 && ratingFilter == 0) vm.missingPurchases() else emptyList()
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        if (pending.isNotEmpty()) {
            item(key = "buy-header") {
                Text(
                    "已购未下载 " + pending.size + " 部（点「下载」保存到 曲库根目录/DLsite）",
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(pending, key = { _, p -> "buy:" + p.code }) { _, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(asmrShape(12.dp))
                        .background(asmrRowColor())
                        .then(asmrBorder(asmrShape(12.dp)))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title ?: item.code,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            item.code + " · 未下载",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onDownload(item.code) }) { Text("下载") }
                }
            }
            item(key = "buy-divider") { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
        }
        // 搜索结果分「曲目命中 / 作品命中」两类，用标签切换，不再一屏堆两种
        if (trackHits.isNotEmpty()) {
            item(key = "hits-tabs") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = searchTab == 0,
                        onClick = { searchTab = 0 },
                        label = { Text("曲目命中 " + trackHits.size) },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = searchTab == 1,
                        onClick = { searchTab = 1 },
                        label = { Text("作品命中 " + projects.size) },
                    )
                }
            }
        }
        if (trackHits.isNotEmpty() && searchTab == 0) {
            itemsIndexed(trackHits, key = { _, t -> "hit:" + t.path }) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    active = track.path == currentPath,
                    onPlay = { vm.playTrack(track, trackHits) },
                    onPickScript = onPickScript,
                    onAddToQueue = { vm.queueAdd(track) },
                )
            }
        }
        if (trackHits.isEmpty() || searchTab == 1) {
            val toggle: (ProjectEntry) -> Unit = { project ->
                if (batch) {
                    selected = if (selected.contains(project.path)) {
                        selected - project.path
                    } else {
                        selected + project.path
                    }
                } else {
                    onOpen(project.path)
                }
            }
            if (settings.projectLayout == "list") {
                items(projects, key = { it.path }) { project ->
                    ProjectListRow(
                        project = project,
                        cover = coverOf(project.path) ?: project.coverPath,
                        batch = batch,
                        checked = selected.contains(project.path),
                        meta = projectMeta(vm, project),
                        onToggle = { toggle(project) },
                    )
                }
            } else {
                val cols = if (settings.projectLayout == "large") 1 else 2
                val rows = projects.chunked(cols)
                itemsIndexed(rows, key = { _, c -> "g:" + c.first().path }) { _, rowItems ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { project ->
                            ProjectCard(
                                project = project,
                                cover = coverOf(project.path) ?: project.coverPath,
                                batch = batch,
                                checked = selected.contains(project.path),
                                meta = projectMeta(vm, project),
                                large = settings.projectLayout == "large",
                                modifier = Modifier.weight(1f),
                                onToggle = { toggle(project) },
                            )
                        }
                        repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    }
}

/** 横屏用的「筛选」下拉：排序 / 分类 / 批量 / 社团 / 标签 / 声优 全收进来，给列表让出高度。 */
@Composable
private fun CompactFilterMenu(
    vm: MainViewModel,
    settings: AppSettings,
    totalCount: Int,
    scriptFilter: Int,
    onScriptFilter: (Int) -> Unit,
    ratingFilter: Int,
    onRatingFilter: (Int) -> Unit,
    batch: Boolean,
    onBatch: (Boolean) -> Unit,
    selectedCount: Int,
    onMarkAdult: () -> Unit,
    onMarkAllAges: () -> Unit,
    circles: List<String>,
    circleFilter: String?,
    onCircle: (String?) -> Unit,
    tags: List<String>,
    tagFilter: String?,
    onTag: (String?) -> Unit,
    vas: List<String>,
    vaFilter: String?,
    onVa: (String?) -> Unit,
    customTags: List<String>,
    customTagFilter: String?,
    onCustomTag: (String?) -> Unit,
    onClearMeta: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val active = (if (scriptFilter != 0) 1 else 0) +
        (if (ratingFilter != 0) 1 else 0) +
        (if (circleFilter != null) 1 else 0) +
        (if (tagFilter != null) 1 else 0) +
        (if (vaFilter != null) 1 else 0) +
        (if (customTagFilter != null) 1 else 0)
    Box {
        FilterChip(
            selected = active > 0 || batch,
            onClick = { open = true },
            label = { Text(if (active > 0) "筛选 " + active else "筛选") },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            shape = asmrShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
        ) {
            FilterMenuLabel("排序 · " + totalCount + " 个项目")
            listOf("name" to "名称", "code" to "编号", "tracks" to "曲目", "recent" to "时间")
                .forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text((if (settings.sortMode == id) "● " else "   ") + label) },
                        onClick = { vm.setSortMode(id) },
                    )
                }
            DropdownMenuItem(
                text = { Text("排序方向：" + (if (settings.sortAsc) "升序" else "降序") + "（点按切换）") },
                onClick = { vm.setSortAsc(!settings.sortAsc) },
            )
            FilterMenuLabel("台本")
            listOf("全部", "有台本", "无台本").forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text((if (scriptFilter == i) "● " else "   ") + label) },
                    onClick = { onScriptFilter(i) },
                )
            }
            FilterMenuLabel("年龄限制")
            listOf("全部", "成人", "全年龄").forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text((if (ratingFilter == i) "● " else "   ") + label) },
                    onClick = { onRatingFilter(i) },
                )
            }
            FilterMenuLabel("批量分类")
            DropdownMenuItem(
                text = { Text(if (batch) "退出批量分类" else "进入批量分类") },
                onClick = { onBatch(!batch); if (!batch) open = false },
            )
            DropdownMenuItem(
                text = { Text("标为成人" + (if (selectedCount > 0) "（已选 " + selectedCount + "）" else "")) },
                onClick = { onMarkAdult() },
            )
            DropdownMenuItem(
                text = { Text("标为全年龄") },
                onClick = { onMarkAllAges() },
            )
            if (circles.isNotEmpty()) {
                FilterMenuLabel("社团")
                DropdownMenuItem(
                    text = { Text((if (circleFilter == null) "● " else "   ") + "全部") },
                    onClick = { onCircle(null) },
                )
                circles.forEach { c ->
                    DropdownMenuItem(
                        text = { Text((if (circleFilter == c) "● " else "   ") + c) },
                        onClick = { onCircle(if (circleFilter == c) null else c) },
                    )
                }
            }
            if (tags.isNotEmpty()) {
                FilterMenuLabel("标签")
                DropdownMenuItem(
                    text = { Text((if (tagFilter == null) "● " else "   ") + "全部") },
                    onClick = { onTag(null) },
                )
                tags.forEach { t ->
                    DropdownMenuItem(
                        text = { Text((if (tagFilter == t) "● " else "   ") + t) },
                        onClick = { onTag(if (tagFilter == t) null else t) },
                    )
                }
            }
            if (vas.isNotEmpty()) {
                FilterMenuLabel("声优")
                DropdownMenuItem(
                    text = { Text((if (vaFilter == null) "● " else "   ") + "全部") },
                    onClick = { onVa(null) },
                )
                vas.forEach { v ->
                    DropdownMenuItem(
                        text = { Text((if (vaFilter == v) "● " else "   ") + v) },
                        onClick = { onVa(if (vaFilter == v) null else v) },
                    )
                }
            }
            if (customTags.isNotEmpty()) {
                FilterMenuLabel("自定义标签")
                DropdownMenuItem(
                    text = { Text((if (customTagFilter == null) "● " else "   ") + "全部") },
                    onClick = { onCustomTag(null) },
                )
                customTags.forEach { t ->
                    DropdownMenuItem(
                        text = { Text((if (customTagFilter == t) "● " else "   ") + t) },
                        onClick = { onCustomTag(if (customTagFilter == t) null else t) },
                    )
                }
            }
            if (active > 0) {
                DropdownMenuItem(
                    text = { Text("清除全部筛选") },
                    onClick = { onScriptFilter(0); onRatingFilter(0); onClearMeta(); onCustomTag(null) },
                )
            }
        }
    }
}

@Composable
private fun FilterMenuLabel(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 编辑作品的自定义标签（可多个；已有的可一键加入）。 */
@Composable
private fun TagEditDialog(
    current: List<String>,
    allTags: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    var input by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = asmrShape(20.dp),
        title = { Text("作品标签") },
        text = {
            Column {
                if (draft.isEmpty()) {
                    Text(
                        "还没有标签。标签是作品级的，可以加多个，并用于曲库筛选。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        draft.forEach { t ->
                            FilterChip(
                                selected = true,
                                onClick = { draft = draft - t },
                                label = { Text(t + "  ×") },
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("新标签（空格 / 逗号分隔可加多个）") },
                )
                val suggestions = allTags.filter { it !in draft }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "已有标签（点一下加入）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        suggestions.forEach { t ->
                            AssistChip(onClick = { draft = draft + t }, label = { Text(t) })
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val extra = input.trim().split(Regex("[\\s,，、]+")).filter { it.isNotBlank() }
                onSave((draft + extra).distinct())
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ProjectDetail(
    vm: MainViewModel,
    project: ProjectEntry?,
    currentPath: String?,
    onBack: () -> Unit,
    onPickScript: (TrackEntry) -> Unit,
    onDownload: (String) -> Unit,
    /** 由外层持有，上滑时连同曲库那行统计一起收起。 */
    listState: LazyListState,
) {
    val detailListState = listState
    val detailScrolled = rememberTopCollapsed(detailListState)
    val detailScope = rememberCoroutineScope()
    // 全屏选集模式：只留选集列表，把标题/封面/标签/DLsite 都藏掉
    var episodesFull by remember(project?.path) { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = episodesFull) { episodesFull = false }
    androidx.compose.runtime.LaunchedEffect(episodesFull, detailListState) {
        if (episodesFull) detailListState.animateScrollToItem(0)
    }
    // 新旧详情页开关：旧版等同改动前的布局（标题+播放全部在顶栏、曲目列表内嵌、无头图）
    val detailSettings by vm.settings.collectAsStateWithLifecycle()
    val newDetail = detailSettings.detailLayout != "old"
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (episodesFull) episodesFull = false else onBack() }) {
                Icon(Icons.Default.ArrowBack, if (episodesFull) "退出选集" else "返回")
            }
            if (episodesFull) {
                Text(
                    "选集",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                val total = if (project != null) {
                    vm.visibleEpisodes(project, vm.tracksOfProject(project.path)).size
                } else {
                    0
                }
                Text(
                    total.toString() + " 集",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!newDetail) {
                // 旧版：标题与「播放全部」就在这里
                Text(
                    projectTitle(project),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (project != null && project.trackCount > 0) {
                    TextButton(onClick = {
                        val queue = vm.tracksOfProject(project.path)
                        queue.firstOrNull()?.let { vm.playTrack(it, queue) }
                    }) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("播放全部")
                    }
                }
            } else {
                // 新版：标题与「播放全部」都在下面的头图区
                Spacer(Modifier.weight(1f))
            }
            if (!episodesFull && project != null && project.trackCount > 0) {
                TextButton(onClick = { vm.embedProject(project.path) }) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("写入台本标签")
                }
            }
            val archives = if (!episodesFull && project != null) vm.archivesOfProject(project.path) else emptyList()
            archives.firstOrNull { it.extractable }?.let { zip ->
                TextButton(onClick = { vm.extractArchive(zip.path) }) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("解压")
                }
            }
        }
        HorizontalDivider()

        if (project == null) return@Column

        // ---------- 影视剧式详情：头图 / 简介 / 编辑 ----------
        var editOpen by remember(project.path) { mutableStateOf(false) }
        var coverViewer by remember(project.path) { mutableStateOf(false) }
        if (coverViewer) {
            val shown = vm.dlsiteCoverOf(project.path) ?: project.coverPath
            if (shown != null) {
                ImageViewer(images = listOf(shown), start = 0, onClose = { coverViewer = false })
            }
        }
        if (newDetail && !episodesFull) {
        val workMetaMap by vm.workMeta.collectAsStateWithLifecycle()
        val wm = remember(project, workMetaMap) { vm.workMetaOf(project) }
        val allTracks = remember(project) { vm.tracksOfProject(project.path) }
        val eps = remember(project, workMetaMap, allTracks) { vm.visibleEpisodes(project, allTracks) }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val heroCover = vm.dlsiteCoverOf(project.path) ?: project.coverPath
            Box(
                Modifier
                    .size(132.dp)
                    .clip(asmrShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(asmrBorder(asmrShape(8.dp)))
                    .clickable(enabled = heroCover != null) { coverViewer = true },
                contentAlignment = Alignment.Center,
            ) {
                if (heroCover != null) {
                    AsyncImage(
                        model = File(heroCover),
                        contentDescription = "作品封面，点击查看大图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    projectTitle(project),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        vm.dlsiteOf(project.path)?.circle?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                        append(project.chapters.size).append(" 章 · ").append(eps.size).append(" 集")
                        val r0 = vm.ratingOf(project)
                        if (r0 != com.kiite.player.core.WorkRating.UNKNOWN) append(" · ").append(r0.label)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 红框区域：DLsite 作品名 + 简介（简介优先手写，其次 DLsite 自动抓的）
                val work0 = vm.dlsiteOf(project.path)
                val dlsiteName = work0?.title?.takeIf { it.isNotBlank() }
                val synopsisText = wm.synopsis.ifBlank { work0?.synopsis.orEmpty() }
                if (dlsiteName != null || synopsisText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    if (dlsiteName != null) {
                        Text(
                            dlsiteName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    if (synopsisText.isNotBlank()) {
                        Text(
                            synopsisText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // 操作行单独占满整宽；横向可滚，避免按钮多了排不下
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { eps.firstOrNull()?.let { vm.playTrack(it, eps) } },
                enabled = eps.isNotEmpty(),
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("播放全部")
            }
            Spacer(Modifier.width(6.dp))
            AssistChip(
                onClick = { vm.queueAddAll(eps) },
                label = { Text("＋ 加入队列") },
                enabled = eps.isNotEmpty(),
            )
            Spacer(Modifier.width(6.dp))
            AssistChip(
                onClick = { editOpen = true },
                label = { Text(if (wm.synopsis.isBlank()) "编辑 / 加简介" else "编辑") },
            )
        }
        if (editOpen) {
            WorkEditDialog(
                project = project,
                tracks = allTracks,
                meta = wm,
                onSave = { synopsis, names, hidden ->
                    vm.setSynopsis(project, synopsis)
                    allTracks.forEach { t ->
                        vm.setEpisodeName(project, t.path, names[t.path].orEmpty())
                        if ((t.path in hidden) != (t.path in wm.hidden)) vm.toggleEpisodeHidden(project, t.path)
                    }
                    editOpen = false
                },
                onDismiss = { editOpen = false },
            )
        }
        } // end if (newDetail)

        AnimatedVisibility(
            visible = !detailScrolled && !episodesFull,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
        Column {
        // 自定义标签：作品级、可多个、参与筛选。点标签即可删除，点「＋」编辑
        val manualTags by vm.manualTags.collectAsStateWithLifecycle()
        val myTags = remember(project, manualTags) { vm.tagsOf(project) }
        var tagEditor by remember(project.path) { mutableStateOf(false) }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            myTags.forEach { t ->
                FilterChip(
                    selected = true,
                    onClick = { vm.setTags(project, myTags - t) },
                    label = { Text(t + "  ×") },
                )
                Spacer(Modifier.width(6.dp))
            }
            FilterChip(
                selected = false,
                onClick = { tagEditor = true },
                label = { Text(if (myTags.isEmpty()) "＋ 添加标签" else "＋") },
            )
        }
        if (tagEditor) {
            TagEditDialog(
                current = myTags,
                allTags = vm.knownCustomTags(),
                onSave = {
                    vm.setTags(project, it)
                    tagEditor = false
                },
                onDismiss = { tagEditor = false },
            )
        }

        // DLsite 作品识别
        val uriHandler = LocalUriHandler.current
        // 手动分级 / DLsite 识别结果变化时刷新标签与「切换」按钮，
        // 否则点「切换」后按钮文字不更新，看起来像卡住
        val manualRatings by vm.manualRatings.collectAsStateWithLifecycle()
        val dlsiteMeta by vm.dlsite.collectAsStateWithLifecycle()
        val rating = remember(project, manualRatings, dlsiteMeta) { vm.ratingOf(project) }
        val work = remember(project, dlsiteMeta) { vm.dlsiteOf(project.path) }
        val code = vm.dlsiteCodeOf(project)
        // DLsite：默认折叠，只留一行作品标题（点标题展开操作与详情）
        var dlsiteOpen by remember(project.path) { mutableStateOf(false) }
        var dlsiteMore by remember { mutableStateOf(false) }
        val dlsiteTitle = work?.title?.takeIf { it.isNotBlank() }

        // 操作行：识别 / 编号 / 分级切换 / 更多（展开后显示）
        val dlsiteActions: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { vm.fetchDlsite(project.path) }, enabled = code != null) {
                    Icon(Icons.Default.Language, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (work == null) "DLsite 识别" else "重新识别")
                }
                Text(
                    if (code != null) code else "未识别到 RJ 编号",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val next = when (rating) {
                        com.kiite.player.core.WorkRating.UNKNOWN -> com.kiite.player.core.WorkRating.ALL
                        com.kiite.player.core.WorkRating.ALL -> com.kiite.player.core.WorkRating.R18
                        com.kiite.player.core.WorkRating.R18 -> com.kiite.player.core.WorkRating.UNKNOWN
                    }
                    vm.setRating(project, next)
                }) { Text(rating.label + " 切换") }
                if (code != null || work?.productUrl != null) {
                    Box {
                        IconButton(onClick = { dlsiteMore = true }) {
                            Icon(Icons.Default.MoreVert, "DLsite 更多操作")
                        }
                        DropdownMenu(
                            expanded = dlsiteMore,
                            onDismissRequest = { dlsiteMore = false },
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            shape = asmrShape(12.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
                        ) {
                            if (code != null) {
                                DropdownMenuItem(
                                    text = { Text("在线播放") },
                                    onClick = {
                                        uriHandler.openUri(com.kiite.player.core.DlsiteAuth.playUrl(code))
                                        dlsiteMore = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("下载") },
                                    onClick = { onDownload(code); dlsiteMore = false },
                                )
                            }
                            work?.productUrl?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("在 DLsite 打开作品页") },
                                    onClick = { uriHandler.openUri(url); dlsiteMore = false },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (dlsiteTitle == null) {
            // 还没识别到作品：直接露出操作行，方便触发识别
            dlsiteActions()
        } else {
            // 折叠时只显示一行标题，点标题才展开
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { dlsiteOpen = !dlsiteOpen }
                    .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    dlsiteTitle,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (dlsiteOpen) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (dlsiteOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (dlsiteOpen) "收起 DLsite 信息" else "展开 DLsite 信息",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (dlsiteOpen) {
                dlsiteActions()
                val meta = buildList {
                    work?.circle?.takeIf { it.isNotBlank() }?.let { add("社团：" + it) }
                    work?.tags?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" / ")) }
                    if (work?.owned == true) add("已购买")
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" · "),
                        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        }
        }
        val unsupported = vm.archivesOfProject(project.path).filter { !it.extractable }
        if (unsupported.isNotEmpty()) {
            Text(
                "含 " + unsupported.size + " 个 rar/7z 等压缩包（应用内暂不支持解压，请用其他工具解压后再扫描）：" +
                    unsupported.joinToString("、") { it.name },
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 下侧空白处的「选集」入口：点了进入全屏选集（隐藏标题/封面/标签）
        if (newDetail && !episodesFull) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { episodesFull = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Default.PlaylistPlay, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "选集 · " +
                        vm.visibleEpisodes(project, vm.tracksOfProject(project.path)).size + " 集",
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
        // 曲目列表直接内嵌在页面里
        run {
            EpisodeList(
                vm = vm,
                project = project,
                currentPath = currentPath,
                onPickScript = onPickScript,
                listState = detailListState,
                modifier = Modifier.fillMaxSize(),
            )
        }
        }
        // 一键回到顶部
        if (detailScrolled) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { detailScope.launch { detailListState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, "回到顶部")
            }
        }
    }
}
/** 作品行的摘要文案：章节 / 曲目 / 台本 / 压缩包 / 分级。 */
private fun projectMeta(vm: MainViewModel, project: ProjectEntry): String = buildString {
    append(project.chapters.size).append(" 章 · ").append(project.trackCount)
    append(" 首 · ").append(project.scriptCount).append(" 份台本")
    if (project.archiveCount > 0) append(" · 含压缩包 ").append(project.archiveCount).append(" 个")
    val r = vm.ratingOf(project)
    if (r != com.kiite.player.core.WorkRating.UNKNOWN) append(" · ").append(r.label)
}

/** 列表模式的作品行。 */
@Composable
private fun ProjectListRow(
    project: ProjectEntry,
    cover: String?,
    batch: Boolean,
    checked: Boolean,
    meta: String,
    onToggle: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(projectTitle(project), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(meta, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(asmrShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = File(cover),
                        contentDescription = "专辑封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(22.dp))
                }
            }
        },
        trailingContent = if (batch) {
            { Checkbox(checked = checked, onCheckedChange = null) }
        } else {
            null
        },
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(asmrShape(12.dp))
            .background(asmrRowColor())
            .then(asmrBorder(asmrShape(12.dp)))
            .clickable { onToggle() },
    )
}

/** 中 / 大图标模式的作品卡（封面在上，标题与摘要在下）。 */
@Composable
private fun ProjectCard(
    project: ProjectEntry,
    cover: String?,
    batch: Boolean,
    checked: Boolean,
    meta: String,
    large: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val coverHeight = if (large) 280.dp else 150.dp
    Column(
        modifier
            .clip(asmrShape(10.dp))
            .background(asmrRowColor())
            .then(asmrBorder(asmrShape(10.dp)))
            .clickable { onToggle() }
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .clip(asmrShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                AsyncImage(
                    model = File(cover),
                    contentDescription = "专辑封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.LibraryMusic, null, Modifier.size(if (large) 40.dp else 28.dp))
            }
            if (batch) {
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            projectTitle(project),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 选集列表：章节分组 + 每集一行（旧版内嵌 / 新版二级页面共用）。 */
@Composable
private fun EpisodeList(
    vm: MainViewModel,
    project: ProjectEntry,
    currentPath: String?,
    onPickScript: (TrackEntry) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val workMetaMap by vm.workMeta.collectAsStateWithLifecycle()
    val wm = remember(project, workMetaMap) { vm.workMetaOf(project) }
    val allTracks = remember(project) { vm.tracksOfProject(project.path) }
    val eps = remember(project, workMetaMap, allTracks) { vm.visibleEpisodes(project, allTracks) }
    val chapters = vm.chaptersOf(project)
    LazyColumn(modifier, state = listState) {
        for ((chapter, tracks) in chapters) {
            val shown = tracks.filter { it.path !in wm.hidden }
            if (shown.isEmpty()) continue
            item(key = "h:" + chapter.path) {
                ChapterHeader(
                    name = chapter.name,
                    count = shown.size,
                    onPlayAll = { vm.playChapter(project, chapter.path) },
                )
            }
            itemsIndexed(shown, key = { _, t -> "t:" + t.path }) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    active = track.path == currentPath,
                    onPlay = { vm.playTrack(track, eps) },
                    onPickScript = onPickScript,
                    onAddToQueue = { vm.queueAdd(track) },
                    title = vm.episodeTitle(project, track),
                )
            }
        }
    }
}

/** 详情页的编辑弹窗：简介 + 逐集改名 / 隐藏。 */
@Composable
private fun WorkEditDialog(
    project: ProjectEntry,
    tracks: List<TrackEntry>,
    meta: com.kiite.player.data.WorkMeta,
    onSave: (String, Map<String, String>, Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var synopsis by remember(project.path) { mutableStateOf(meta.synopsis) }
    val names = remember(project.path) {
        mutableStateMapOf<String, String>().apply { meta.epNames.forEach { (k, v) -> put(k, v) } }
    }
    val hidden = remember(project.path) {
        mutableStateListOf<String>().apply { addAll(meta.hidden) }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = asmrShape(20.dp),
        title = { Text("编辑作品") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("简介", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("写点简介；留空则不显示") },
                )
                Spacer(Modifier.height(12.dp))
                Text("选集（改名 / 隐藏）", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                tracks.forEach { t ->
                    val isHidden = t.path in hidden
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isHidden) "已隐藏" else "显示",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isHidden) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier
                                .clickable {
                                    if (isHidden) hidden.remove(t.path) else hidden.add(t.path)
                                }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = names[t.path].orEmpty(),
                            onValueChange = { names[t.path] = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(t.baseName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(synopsis, names.toMap(), hidden.toSet()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 收起阈值：滚过这里才收 */
private const val COLLAPSE_AT = 240

/** 展开阈值：回到这里以内才展开（和上面留出迟滞，避免抖动） */
private const val EXPAND_AT = 48

/**
 * 「上滑收起顶部」状态机。
 *
 * 如果直接用 firstVisibleItemScrollOffset > N 判断，**列表刚好够滚动**时会来回弹：
 * 收起 → 视口变高 → 可滚动距离变小 → 偏移被夹回 0 → 展开 → 又能滚 → 再收起……
 * 这里加两道保险：
 * 1. **迟滞**：滚过 COLLAPSE_AT 才收起，只有回到 EXPAND_AT 以内才展开；
 * 2. **不可滚动时永不收起**（列表短到没有滚动余量就没有「上滑」可言）。
 */
@Composable
private fun rememberTopCollapsed(state: LazyListState): Boolean {
    var collapsed by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow {
            listOf(
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
                if (state.canScrollForward || state.canScrollBackward) 1 else 0,
            )
        }.collect { v ->
            val index = v[0]
            val offset = v[1]
            val scrollable = v[2] == 1
            collapsed = when {
                !scrollable -> false
                collapsed -> !(index == 0 && offset < EXPAND_AT)
                else -> index > 1 || offset > COLLAPSE_AT
            }
        }
    }
    return collapsed
}

private fun projectTitle(p: ProjectEntry?): String =
    p?.let { if (it.code != null) "[" + it.code + "] " + it.name else it.name } ?: ""

/** 下拉式筛选项（社团 / 标签 / 声优）。 */
@Composable
private fun FilterMenu(label: String, options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: label) },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            shape = asmrShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
        ) {
            DropdownMenuItem(text = { Text("全部") }, onClick = { onSelect(null); open = false })
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false })
            }
        }
    }
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun ChapterHeader(name: String, count: Int, onPlayAll: (() -> Unit)? = null) {
    if (LocalEditorial.current) {
        // 参考图的小节标题：竖条 + 宽字距标签 + 右侧等宽序号
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(13.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(
                name.uppercase() + "　" + count + " 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (onPlayAll != null) {
                IconButton(onClick = onPlayAll, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.PlayArrow, "整章播放", Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            IndexTag(count.toString().padStart(2, '0'))
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    Text(
        name + " · " + count + " 首",
        Modifier.weight(1f),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    if (onPlayAll != null) {
        IconButton(onClick = onPlayAll, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.PlayArrow, "整章播放", Modifier.size(18.dp))
        }
    }
    }
}

@Composable
private fun TrackRow(
    track: TrackEntry,
    index: Int,
    active: Boolean,
    onPlay: () -> Unit,
    onPickScript: (TrackEntry) -> Unit,
    onAddToQueue: () -> Unit,
    /** 详情页的「集名」：自定义名优先，默认曲目名。 */
    title: String = track.baseName,
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = { ScriptBadge(track) },
        leadingContent = {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(asmrShape(10.dp))
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAddToQueue) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        "加入播放列表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onPickScript(track) }) {
                    Icon(
                        Icons.Default.Description,
                        "选择台本",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(asmrShape(12.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    asmrRowColor()
                },
            )
            .then(asmrBorder(asmrShape(12.dp)))
            .clickable { onPlay() },
    )
}

@Composable
private fun ScriptBadge(track: TrackEntry) {
    val primary = track.primaryScript
    if (primary == null) {
        Text("未匹配到台本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    } else {
        val manual = if (primary.manual) "手动 · " else ""
        val extra = if (track.scripts.size > 1) " 等 " + track.scripts.size + " 份" else ""
        val timed = if (primary.format.isTimed) " · 可逐句同步" else ""
        Text(
            manual + primary.format.label + " · " + primary.scriptName + extra + timed,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
