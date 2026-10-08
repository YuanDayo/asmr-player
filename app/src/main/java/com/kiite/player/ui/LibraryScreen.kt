package com.kiite.player.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.ScanResult
import com.kiite.player.core.TrackEntry
import com.kiite.player.data.AppSettings
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
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
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
                    )
                } else {
                    ProjectDetail(
                        vm = vm,
                        project = scan?.projects?.firstOrNull { it.path == project },
                        currentPath = currentTrack?.path,
                        onBack = { vm.selectProject(null) },
                        onPickScript = { scriptPickerTrack = it },
                        onDownload = { downloadCode = it },
                    )
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
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    // 手动分级 / DLsite 识别结果变化时，列表里的分级标签与「成人/全年龄」筛选也要刷新
    val manualRatings by vm.manualRatings.collectAsStateWithLifecycle()
    val dlsiteMeta by vm.dlsite.collectAsStateWithLifecycle()
    // 自定义标签变化时，筛选与标签展示也要刷新
    val manualTags by vm.manualTags.collectAsStateWithLifecycle()
    val all = scan?.projects ?: emptyList()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    var batch by remember { mutableStateOf(false) }
    var circleFilter by remember { mutableStateOf<String?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var vaFilter by remember { mutableStateOf<String?>(null) }
    var customTagFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val base = remember(all, manualRatings, dlsiteMeta, manualTags, query, filter, circleFilter, tagFilter, vaFilter, customTagFilter) {
        all.filter { p ->
        val work = vm.dlsiteOf(p.path)
        val hitQuery = query.isBlank() ||
            projectTitle(p).contains(query, ignoreCase = true) ||
            work?.title?.contains(query, ignoreCase = true) == true ||
            work?.circle?.contains(query, ignoreCase = true) == true ||
            work?.tags?.any { it.contains(query, ignoreCase = true) } == true ||
            work?.voiceActors?.any { it.contains(query, ignoreCase = true) } == true
        val hitMeta = (circleFilter == null || work?.circle == circleFilter) &&
            (tagFilter == null || work?.tags?.contains(tagFilter) == true) &&
            (vaFilter == null || work?.voiceActors?.contains(vaFilter) == true)
        val rating = vm.ratingOf(p)
        val hitFilter = when (filter) {
            1 -> p.scriptCount > 0
            2 -> p.scriptCount == 0
            3 -> rating == com.kiite.player.core.WorkRating.R18
            4 -> rating == com.kiite.player.core.WorkRating.ALL
            else -> true
        }
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
                    shape = RoundedCornerShape(50),
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
                filter = filter,
                onFilter = { filter = it },
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
                shape = RoundedCornerShape(50),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("搜索作品名 / RJ 编号 / 曲目名") },
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            listOf("name" to "名称", "code" to "编号", "tracks" to "曲目", "recent" to "时间")
                .forEach { (id, label) ->
                    FilterChip(
                        selected = settings.sortMode == id,
                        onClick = { vm.setSortMode(id) },
                        label = { Text(label) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            FilterChip(
                selected = false,
                onClick = { vm.setSortAsc(!settings.sortAsc) },
                label = { Text(if (settings.sortAsc) "↑" else "↓") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = batch,
                onClick = {
                    batch = !batch
                    if (!batch) selected = emptySet()
                },
                label = { Text(if (batch) "退出批量" else "批量分类") },
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
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("全部", "有台本", "无台本", "成人", "全年龄").forEachIndexed { i, label ->
                FilterChip(
                    selected = filter == i,
                    onClick = { filter = i },
                    label = { Text(label) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                projects.size.toString() + " 个项目",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    val pending = if (query.isBlank() && filter == 0) vm.missingPurchases() else emptyList()
    LazyColumn(Modifier.fillMaxSize()) {
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(asmrRowColor())
                        .then(asmrBorder(RoundedCornerShape(12.dp)))
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
                )
            }
        }
        if (trackHits.isEmpty() || searchTab == 1) items(projects, key = { it.path }) { project ->
            ListItem(
                headlineContent = { Text(projectTitle(project), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        buildString {
                            append(project.chapters.size).append(" 章 · ").append(project.trackCount)
                            append(" 首 · ").append(project.scriptCount).append(" 份台本")
                            if (project.archiveCount > 0) append(" · 含压缩包 ").append(project.archiveCount).append(" 个")
                            val r = vm.ratingOf(project)
                            if (r != com.kiite.player.core.WorkRating.UNKNOWN) {
                                append(" · ").append(r.label)
                            }
                        },
                    )
                },
                leadingContent = {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val cover = coverOf(project.path) ?: project.coverPath
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
                    {
                        Checkbox(
                            checked = selected.contains(project.path),
                            onCheckedChange = null,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(asmrRowColor())
                    .then(asmrBorder(RoundedCornerShape(12.dp)))
                    .clickable {
                        if (batch) {
                            selected = if (selected.contains(project.path)) {
                                selected - project.path
                            } else {
                                selected + project.path
                            }
                        } else {
                            onOpen(project.path)
                        }
                    },
            )
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
    filter: Int,
    onFilter: (Int) -> Unit,
    batch: Boolean,
    onBatch: (Boolean) -> Unit,
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
    val active = (if (filter != 0) 1 else 0) +
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
            shape = RoundedCornerShape(12.dp),
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
            FilterMenuLabel("分类")
            listOf("全部", "有台本", "无台本", "成人", "全年龄").forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text((if (filter == i) "● " else "   ") + label) },
                    onClick = { onFilter(i) },
                )
            }
            FilterMenuLabel("批量分类")
            DropdownMenuItem(
                text = { Text(if (batch) "退出批量分类" else "进入批量分类") },
                onClick = { onBatch(!batch); if (!batch) open = false },
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
                    onClick = { onFilter(0); onClearMeta(); onCustomTag(null) },
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
        shape = RoundedCornerShape(20.dp),
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
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    projectTitle(project),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (project != null && project.trackCount > 0) {
                TextButton(onClick = {
                    val queue = vm.tracksOfProject(project.path)
                    queue.firstOrNull()?.let { vm.playTrack(it, queue) }
                }) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("播放全部")
                }
                TextButton(onClick = { vm.embedProject(project.path) }) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("写入台本标签")
                }
            }
            val archives = if (project != null) vm.archivesOfProject(project.path) else emptyList()
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
                            shape = RoundedCornerShape(12.dp),
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

        val chapters = vm.chaptersOf(project)
        LazyColumn(Modifier.fillMaxSize()) {
            for ((chapter, tracks) in chapters) {
                item(key = "h:" + chapter.path) {
                    ChapterHeader(name = chapter.name, count = tracks.size)
                }
                itemsIndexed(tracks, key = { _, t -> "t:" + t.path }) { index, track ->
                    TrackRow(
                        track = track,
                        index = index,
                        active = track.path == currentPath,
                        onPlay = { vm.playTrack(track, vm.tracksOfProject(project.path)) },
                        onPickScript = onPickScript,
                    )
                }
            }
        }
    }
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
            shape = RoundedCornerShape(12.dp),
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
private fun ChapterHeader(name: String, count: Int) {
    Text(
        name + " · " + count + " 首",
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TrackRow(
    track: TrackEntry,
    index: Int,
    active: Boolean,
    onPlay: () -> Unit,
    onPickScript: (TrackEntry) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                track.baseName,
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
                    .clip(RoundedCornerShape(10.dp))
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
            IconButton(onClick = { onPickScript(track) }) {
                Icon(
                    Icons.Default.Description,
                    "选择台本",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    asmrRowColor()
                },
            )
            .then(asmrBorder(RoundedCornerShape(12.dp)))
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
