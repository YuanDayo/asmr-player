@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiite.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.kiite.player.ui.theme.Containers
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Stroke
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.ScanResult
import com.kiite.player.core.TrackEntry
import com.kiite.player.data.AppSettings
import com.kiite.player.ui.components.AppBottomSheet
import com.kiite.player.ui.components.EmptyState
import com.kiite.player.ui.components.ErrorState
import com.kiite.player.ui.components.SectionHeader
import com.kiite.player.ui.components.SheetAction
import com.kiite.player.ui.components.SheetSectionLabel
import com.kiite.player.ui.components.TrackListItem
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.TouchTarget
import com.kiite.player.ui.theme.LocalBottomBarInset
import com.kiite.player.ui.theme.LocalTopBarMetrics
import com.kiite.player.ui.theme.LocalTabReselect
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.asmrEnter
import com.kiite.player.ui.theme.asmrExit
import com.kiite.player.ui.theme.Motion
import com.kiite.player.ui.theme.fadeThrough
import com.kiite.player.ui.theme.pagePushIn
import com.kiite.player.ui.theme.pagePushOut
import com.kiite.player.ui.theme.popIn
import com.kiite.player.ui.theme.popOut
import com.kiite.player.ui.theme.LocalZoomOrigin
import com.kiite.player.ui.theme.ZoomOrigin
import com.kiite.player.ui.theme.reportZoomOrigin
import com.kiite.player.ui.theme.veilIn
import com.kiite.player.ui.theme.veilOut
import com.kiite.player.ui.theme.zoomIn
import com.kiite.player.ui.theme.zoomOut
import com.kiite.player.util.Permissions
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.io.File

/**
 * LibraryScreen 独有的控件尺寸。
 *
 * 这些不进全局令牌：它们是本页特有的控件大小（图标按钮、进度条、封面档位），
 * 同值不同义，硬塞进 Spacing / IconSize 会让令牌表变成一张数字表。
 * 集中在这里的目的是可搜、可一处改；真正的归一化（图标按钮统一到 48dp
 * 触控目标）留给单独一批，因为会改变布局。
 */
private object LibraryDim {
    val box4 = 4.dp
    val box38 = 38.dp
    val box44 = 44.dp
    val box48 = 48.dp
    val box150 = 150.dp
    val box280 = 280.dp
    val height440 = 440.dp
    val icon28 = 28.dp
    val icon40 = 40.dp
    val pad28 = 28.dp
    val spinner14 = 14.dp

    /** 首屏错峰入场：只作用于最前面几项，滚动时不再重复。 */
    val staggerRise = 10.dp
    const val staggerCount = 6
    const val staggerStepMs = 22
}


@Composable
fun LibraryScreen(vm: MainViewModel, onOverlayChange: (Boolean) -> Unit = {}) {
    // 曲库页跑在 Miuix 主题里（HyperOS 设计语言）：卡片形状、按压反馈、排版都走 Miuix。
    // 深浅跟应用自己的背景亮度走（应用三态主题不走系统夜间模式），强调色沿用当前皮肤。
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = MaterialTheme.colorScheme.primary
    val accentOn = MaterialTheme.colorScheme.onPrimary
    val miuixColors = if (dark) {
        top.yukonga.miuix.kmp.theme.darkColorScheme(primary = accent, onPrimary = accentOn)
    } else {
        top.yukonga.miuix.kmp.theme.lightColorScheme(primary = accent, onPrimary = accentOn)
    }
    MiuixTheme(colors = miuixColors) {
        LibraryContent(vm, onOverlayChange)
    }
}

@Composable
private fun LibraryContent(vm: MainViewModel, onOverlayChange: (Boolean) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val selectedProject by vm.selectedProject.collectAsStateWithLifecycle()
    val manualLinks by vm.manualLinks.collectAsStateWithLifecycle()
    val currentTrack by vm.currentTrack.collectAsStateWithLifecycle()
    var showRootPicker by remember { mutableStateOf(false) }
    // 搜索框默认收起：多数时候不用搜索，收起后音频列表整体上移
    var searchOpen by remember { mutableStateOf(false) }
    // 独立搜索页：点顶栏放大镜进入一整页搜索（搜索框 + 历史 + 命中结果），
    // 不再是列表里就地展开一截，翻两页就看不清了。
    var searchPageOpen by remember { mutableStateOf(false) }
    // 搜索页自己的滚动状态（与曲库列表分开，进出页面不互相带跑位置）
    val searchListState = rememberLazyListState()
    // 列表滚动状态：上滑收起顶部、并提供「回到顶部」
    val listState = rememberLazyListState()
    val scrolledDown = rememberTopCollapsed(listState)
    // 滚动中暂停实时毛玻璃（每帧全屏捕获是掉帧主因）
    TrackScroll(listState)
    val scope = rememberCoroutineScope()
    // 作品内页（二级页面）的滚动状态：只用来给「回到顶部」和滚动时暂停实时毛玻璃
    val detailListState = rememberLazyListState()
    TrackScroll(detailListState)
    var downloadCode by remember { mutableStateOf<String?>(null) }
    // 未购作品走作品页（购买/详情），已购走直链下载页
    var buyCode by remember { mutableStateOf<String?>(null) }
    // asmr.one 免费站：「浏览」打开作品页 WebView 手动翻看（网页内下载是 JS blob 流，拦不到）；
    // 「下载」先打开文件选择页（像网站里那样挑文件），确认后走原生 API 下载
    var asmrBrowse by remember { mutableStateOf<com.kiite.player.core.AsmrOneWork?>(null) }
    var asmrPick by remember { mutableStateOf<com.kiite.player.core.AsmrOneWork?>(null) }
    var scriptPickerTrack by remember { mutableStateOf<TrackEntry?>(null) }
    // 台本匹配报告的开关放在这一层：曲库顶栏与筛选弹窗都能打开它
    var scriptReportOpen by remember { mutableStateOf(false) }

    // 重复点「曲库」标签：先退掉二级页面（下载页/作品页/选文件页），都没有才滚回列表顶部。
    // 这是 iOS 上「再点一次当前 Tab」的通用语义，用户不用去找返回按钮。
    val tabReselect = LocalTabReselect.current
    val selectedProjectNow by rememberUpdatedState(selectedProject)
    LaunchedEffect(tabReselect) {
        if (tabReselect == 0) return@LaunchedEffect
        downloadCode = null
        buyCode = null
        asmrBrowse = null
        asmrPick = null
        if (selectedProjectNow != null) {
            vm.selectProject(null)
        } else {
            listState.animateScrollToItem(0)
        }
    }

    // 二级页面（下载页 / 作品页 / asmr.one 选文件）打开期间要通知外层「别接左右划切页」。
    // 这些页面里是 WebView：右滑会被当成「切到播放页」，网页自己的横向手势反而抢不到事件。
    // 用回调往上报而不是读 VM：这是纯 UI 层的临时状态，不该进 ViewModel。
    LaunchedEffect(downloadCode, buyCode, asmrBrowse, asmrPick, searchPageOpen) {
        onOverlayChange(
            downloadCode != null || buyCode != null || asmrBrowse != null || asmrPick != null || searchPageOpen,
        )
    }

    downloadCode?.let { code ->
        DlsiteDownloadScreen(vm, code, owned = true) { downloadCode = null }
        return
    }
    buyCode?.let { code ->
        DlsiteDownloadScreen(vm, code, owned = false) { buyCode = null }
        return
    }
    asmrBrowse?.let { w ->
        DlsiteDownloadScreen(
            vm = vm,
            code = w.code ?: ("W" + w.id),
            owned = true,
            startUrl = com.kiite.player.data.AsmrOneClient.workPageUrl(w),
            heading = "asmr.one · " + (w.code ?: w.id.toString()),
            hint = "网页仅供浏览，请回列表点「下载」直接保存到曲库",
        ) { asmrBrowse = null }
        return
    }
    asmrPick?.let { w ->
        AsmrOnePickerScreen(vm, w) { asmrPick = null }
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

    // 根目录失效（被删/移动/SD 卡拔出/权限收回）：给明确的修复路径，而不是停在旧列表
    if (!File(rootPath).isDirectory) {
        ErrorState(
            title = "曲库目录不存在或不可读",
            description = rootPath + "\n目录可能已被删除、移动，或存储权限被收回。重新选择一个目录即可恢复使用。",
            icon = Icons.Default.FolderOpen,
            action = { Button(onClick = { showRootPicker = true }) { Text("重新选择文件夹") } },
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideScreenMinWidth
        // 顶栏是浮层、不再占 Scaffold 的布局位（见 AppRoot 的 FloatingTopBar 注释）：
        // 页面自己让出「状态栏 / 刘海」的那一截，列表再让出「栏高」。
        val topBar = LocalTopBarMetrics.current
        val project = selectedProject
        Box(Modifier.fillMaxSize()) {
        if (wide) {
            // 宽屏（横屏/平板）：列表与详情并排，避免上下卡片挤在一起
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(top = topBar.safeTop)) {
                    RootHeader(
                        rootPath = rootPath,
                        scanning = scanning,
                        projectCount = scan?.projectCount ?: 0,
                        trackCount = scan?.trackCount ?: 0,
                        matched = scan?.withScriptCount ?: 0,
                        searchOpen = searchOpen,
                        onToggleSearch = { searchPageOpen = true },
                        onRescan = { vm.rescan() },
                        onChangeRoot = { showRootPicker = true },
                        onOpenReport = { scriptReportOpen = true },
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
                        onBuy = { buyCode = it },
                        onAsmrBrowse = { asmrBrowse = it },
                        onAsmrPick = { asmrPick = it },
                        onOpen = { vm.selectProject(it) },
                        showSearch = searchOpen,
                        listState = listState,
                        compact = true,
                        onChangeRoot = { showRootPicker = true },
                        reportOpen = scriptReportOpen,
                        onReportChange = { scriptReportOpen = it },
                    )
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Column(Modifier.weight(1f).fillMaxHeight().padding(top = topBar.safeTop)) {
                    // 宽屏没有「翻页」这回事，左右是并排的，所以不做缩放，
                    // 只让右栏内容换一下（纯淡），免得硬切一下显得左边点了个寂寞
                    AnimatedContent(
                        targetState = project,
                        transitionSpec = { fadeThrough() },
                        label = "wideDetail",
                    ) { openPath ->
                        val target = openPath?.let { id -> scan?.projects?.firstOrNull { it.path == id } }
                        if (target == null) {
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
                                project = target,
                                currentPath = currentTrack?.path,
                                onBack = { vm.selectProject(null) },
                                onPickScript = { scriptPickerTrack = it },
                                onDownload = { downloadCode = it },
                                listState = detailListState,
                            )
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = topBar.safeTop)) {
                // 转场原点：同一个持有者跨进/出复用（返回时要用「当时点的那张卡」的位置）
                val zoomOrigin = remember { ZoomOrigin() }
                // 大标题不再钉在顶部：交给 ProjectList 当列表第一项（iOS large title），
                // 往上滑时它随列表滚进磨砂条后面，顶栏的小标题接手。
                // 头部与首张卡片之间那截固定空档也随之消失。
                if (scanning && scan == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                // 列表 ↔ 作品详情：从被点的那张卡片位置放大进来（原点见 theme/ZoomOrigin.kt）。
                // 之前是硬切，点一下整页换内容，像被吞掉了；现在详情从卡片处「长开」，
                // 返回时缩回同一个点 —— 用户能感觉到「这是刚才那个作品」。
                AnimatedContent(
                    targetState = project,
                    transitionSpec = {
                        val origin = zoomOrigin.transformOrigin()
                        if (targetState != null) {
                            // 进详情：只有详情在动，列表原地淡出（两层一起缩放会糊成一团）
                            (zoomIn(origin) togetherWith veilOut())
                                .using(SizeTransform(clip = false))
                        } else {
                            // 返回：详情缩回卡片，列表原地露出来
                            (veilIn() togetherWith zoomOut(origin))
                                .using(SizeTransform(clip = false))
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        // 内容区矩形是缩放原点的分母。这里只写普通字段：写 snapshot state
                        // 会让「顶栏收起」那几帧把整个曲库重组成一帧一帧的重排
                        .onGloballyPositioned { zoomOrigin.containerRect = it.boundsInRoot() },
                    label = "projectOpen",
                ) { openPath ->
                    // 详情页的数据要从目标 path 反查，不能直接用 project：
                    // 返回时 project 已经是 null，而旧内容还要再画一帧退场
                    val target = openPath?.let { id -> scan?.projects?.firstOrNull { it.path == id } }
                    if (target == null) {
                        CompositionLocalProvider(LocalZoomOrigin provides zoomOrigin) {
                            ProjectList(
                                vm = vm,
                                scan = scan,
                                coverOf = { vm.dlsiteCoverOf(it) },
                                currentPath = currentTrack?.path,
                                onPickScript = { scriptPickerTrack = it },
                                onDownload = { downloadCode = it },
                                onBuy = { buyCode = it },
                                onAsmrBrowse = { asmrBrowse = it },
                                onAsmrPick = { asmrPick = it },
                                onOpen = { vm.selectProject(it) },
                                showSearch = searchOpen,
                                listState = listState,
                                // 大标题块：ProjectList 把它当列表第一项塞进滚动容器
                                header = {
                                    RootHeader(
                                        rootPath = rootPath,
                                        scanning = scanning,
                                        projectCount = scan?.projectCount ?: 0,
                                        trackCount = scan?.trackCount ?: 0,
                                        matched = scan?.withScriptCount ?: 0,
                                        searchOpen = searchOpen,
                                        onToggleSearch = { searchPageOpen = true },
                                        onRescan = { vm.rescan() },
                                        onChangeRoot = { showRootPicker = true },
                                        onOpenReport = { scriptReportOpen = true },
                                    )
                                },
                                onChangeRoot = { showRootPicker = true },
                                reportOpen = scriptReportOpen,
                                onReportChange = { scriptReportOpen = it },
                            )
                        }
                    } else {
                        ProjectDetail(
                            vm = vm,
                            project = target,
                            currentPath = currentTrack?.path,
                            onBack = { vm.selectProject(null) },
                            onPickScript = { scriptPickerTrack = it },
                            onDownload = { downloadCode = it },
                            listState = detailListState,
                            // 窄屏二级页没有大标题垫在顶栏下：顶栏常驻磨砂，别留一条透明的空带
                            frostTopBar = true,
                        )
                    }
                }
            }
        }
        // 「回到顶部」：翻了之后才出现，叠在列表右下角（抬高到悬浮玻璃底栏之上）
        // 用 popIn/popOut 而不是 if：整颗按钮硬出现/硬消失，在一屏静止内容里非常扎眼
        AnimatedVisibility(
            visible = !wide && project == null && !searchPageOpen && scrolledDown,
            enter = popIn(),
            exit = popOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.lg, bottom = LocalBottomBarInset.current + LibraryDim.box4),
        ) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
            ) {
                Icon(Icons.Default.KeyboardArrowUp, "回到顶部")
            }
        }
        // 独立搜索页：点顶栏放大镜后整页盖上来（宽屏也盖），自带「← 搜索」顶栏。
        // 用二级页的推入/推出，和下载页、作品页保持同一套节奏。
        androidx.activity.compose.BackHandler(enabled = searchPageOpen) { searchPageOpen = false }
        AnimatedVisibility(
            visible = searchPageOpen,
            enter = pagePushIn(),
            exit = pagePushOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(top = topBar.safeTop)) {
                    SearchTopBar(onBack = { searchPageOpen = false })
                    ProjectList(
                        vm = vm,
                        scan = scan,
                        coverOf = { vm.dlsiteCoverOf(it) },
                        currentPath = currentTrack?.path,
                        onPickScript = { scriptPickerTrack = it },
                        onDownload = { downloadCode = it },
                        onBuy = { buyCode = it },
                        onAsmrBrowse = { asmrBrowse = it },
                        onAsmrPick = { asmrPick = it },
                        // 从搜索结果打开作品：先关掉搜索页，让详情正常从卡片位置长出来
                        onOpen = {
                            searchPageOpen = false
                            vm.selectProject(it)
                        },
                        showSearch = true,
                        searchOnly = true,
                        listState = searchListState,
                        header = null,
                        onChangeRoot = { showRootPicker = true },
                        reportOpen = scriptReportOpen,
                        onReportChange = { scriptReportOpen = it },
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun EmptyLibrary(onChoose: () -> Unit) {
    EmptyState(
        title = "还没有选择曲库目录",
        description = "选一个装着 ASMR 解压文件夹的目录。应用会把每部作品识别成一个「总项目」，" +
            "并把它的全部音频统合在一起展示。",
        icon = Icons.Default.FolderOpen,
        action = { Button(onClick = onChoose) { Text("选择文件夹") } },
    )
}

/** 独立搜索页的顶栏：左返回、居中「搜索」标题。 */
@Composable
private fun SearchTopBar(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(TouchTarget.min)
            .padding(horizontal = Spacing.xs),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Default.ArrowBack, "返回曲库")
        }
        Text(
            "搜索",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
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
    onOpenReport: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
    // 大标题：曲库的「名字」放在内容里（参考图 4），跟着内容一起滚走；
    // 上滑之后由顶栏里那颗小标题接手 —— 以前这颗一直是固定在顶栏里的。
    androidx.compose.material3.Text(
        "曲库",
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.sm),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    // 压成一行（目录名 + 统计 + 图标），搜索框收到放大镜里，把高度让给音频列表
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xxs, top = Spacing.xxs, bottom = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixText(
            rootPath.substringAfterLast('/').ifBlank { rootPath } + "　" +
                projectCount + " 项目 · " + trackCount + " 音频 · " + matched + " 台本",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MiuixIconButton(modifier = Modifier.size(TouchTarget.min), onClick = onToggleSearch) {
            Icon(Icons.Default.Search, if (searchOpen) "收起搜索" else "搜索")
        }
        MiuixIconButton(modifier = Modifier.size(TouchTarget.min), onClick = onRescan, enabled = !scanning) {
            Icon(Icons.Default.Refresh, if (scanning) "扫描中" else "重新扫描")
        }
        // 台本匹配报告：扫描完一眼看哪些作品没台本，缺的逐首指定
        MiuixIconButton(modifier = Modifier.size(TouchTarget.min), onClick = onOpenReport) {
            Icon(Icons.Default.Description, "台本匹配报告")
        }
        MiuixIconButton(modifier = Modifier.size(TouchTarget.min), onClick = onChangeRoot) { Icon(Icons.Default.Folder, "更换目录") }
    }
    }
}

/**
 * 曲库搜索框：带一键清空，横竖屏共用。
 *
 * 这里**不用** Miuix 的 TextField：它内部按 Miuix 自己的字阶排版（18sp 左右、64dp 高），
 * 和下面 32dp 的筛选 chip 并排会明显一大一小。输入框保持 M3 + 项目圆角令牌。
 */
@Composable
private fun LibrarySearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSubmit: (String) -> Unit = {},
    /** 独立搜索页用：一进页面就把焦点交给输入框、弹出键盘。 */
    autoFocus: Boolean = false,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            kotlinx.coroutines.delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = if (autoFocus) modifier.focusRequester(focusRequester) else modifier,
        shape = asmrShape(Radius.pill),
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        // 按回车/搜索键才算「提交」：这里才写进历史，边打字边记会把每个前缀都塞进去
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = {
                onSubmit(query)
                focusManager.clearFocus()
            },
        ),
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "清空搜索") }
            }
        },
        placeholder = { Text("搜索作品名 / RJ 编号 / 曲目名") },
    )
}

/**
 * 搜索历史：只在「搜索框展开、还没输入」时出现，点了直接填回搜索框。
 */
@Composable
private fun SearchHistoryRow(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "搜索历史",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) {
                Text("清空", style = MaterialTheme.typography.labelMedium)
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            history.forEach { item ->
                FilterChip(
                    selected = false,
                    onClick = { onPick(item) },
                    label = {
                        Text(
                            item,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
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
    /** DLsite 在线搜索里未购作品的入口：打开作品页（购买/详情）。 */
    onBuy: (String) -> Unit,
    /** asmr.one 免费站：「浏览」打开作品页 WebView 手动翻看。 */
    onAsmrBrowse: (com.kiite.player.core.AsmrOneWork) -> Unit,
    /** asmr.one 免费站：「下载」打开文件选择页（确认后原生下载）。 */
    onAsmrPick: (com.kiite.player.core.AsmrOneWork) -> Unit,
    onOpen: (String) -> Unit,
    /** 是否显示搜索框（默认收起，点顶部放大镜展开）。 */
    showSearch: Boolean = true,
    /** 横屏（宽屏）：只留搜索 + 筛选下拉，把高度让给列表。 */
    compact: Boolean = false,
    /**
     * 独立搜索页模式：只渲染「搜索框 + 搜索历史 + 命中结果」，不铺大标题、筛选头，
     * 空查询时也不把整个曲库当结果铺出来。由曲库顶栏点放大镜打开。
     */
    searchOnly: Boolean = false,
    /** 由外层持有，便于「上滑收起顶部 / 回到顶部」。 */
    listState: LazyListState,
    /** 空态下「重新选择文件夹」的入口。 */
    onChangeRoot: () -> Unit,
    /** 台本匹配报告的开关（状态在外层：曲库顶栏与筛选弹窗都能打开）。 */
    reportOpen: Boolean,
    onReportChange: (Boolean) -> Unit,
    /**
     * 大标题块（曲库标题 + 统计 + 图标）。非空时它和筛选头一起作为列表的前两项进
     * 滚动容器（iOS large title：往上滑跟着滚走、滚进磨砂条后面）。
     * 宽屏左栏标题仍固定在栏外，传 null。
     */
    header: (@Composable () -> Unit)? = null,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    // 扫描实时进度：大曲库扫描期间给个递增计数，用户知道没有卡住
    val scanProgress by vm.scanProgress.collectAsStateWithLifecycle()
    // 手动分级 / DLsite 识别结果变化时，列表里的分级标签与「成人/全年龄」筛选也要刷新
    val manualRatings by vm.manualRatings.collectAsStateWithLifecycle()
    val dlsiteMeta by vm.dlsite.collectAsStateWithLifecycle()
    // 自选头图变化时，列表与详情页的封面要立刻跟着换
    val coverOverrides by vm.coverOverrides.collectAsStateWithLifecycle()
    // 自定义标签变化时，筛选与标签展示也要刷新
    val manualTags by vm.manualTags.collectAsStateWithLifecycle()
    // 作品级编辑（自定义名称 / 收藏置顶）变化时，标题、排序与置顶要刷新
    val workMetaMap by vm.workMeta.collectAsStateWithLifecycle()
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
    // 系列筛选：按 WorkMeta.series（组曲名）过滤
    var seriesFilter by remember { mutableStateOf<String?>(null) }
    // 只看收藏
    var favoriteOnly by remember { mutableStateOf(false) }
    // 智能收藏：把「关键词 + 标签 + 收藏 + 分级」存成命名集合，一键整体应用
    val smartCollections by vm.smartCollections.collectAsStateWithLifecycle()
    // DLsite：已购列表（标记在线结果是否已购）与在线搜索状态（第三个搜索标签页）
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val onlineSearchState by vm.onlineSearch.collectAsStateWithLifecycle()
    val asmrSearchState by vm.asmrSearch.collectAsStateWithLifecycle()
    // 搜索历史：只在搜索框展开、且还没输入时显示（见 SearchHistoryRow）
    val searchHistory by vm.searchHistory.collectAsStateWithLifecycle()
    // asmr.one 原生下载状态（进度显示在结果行与状态条上）
    val asmrDl by vm.asmrDownload.collectAsStateWithLifecycle()
    var smartSaveSheet by remember { mutableStateOf(false) }
    var smartManageSheet by remember { mutableStateOf(false) }
    // 筛选一变列表内容就变，但 LazyColumn 会按 key 锚定在原来的作品上，
    // 新出现在上方的作品全被藏在视口外——看起来就像「点了没反应」。
    // 任何整体性改变筛选的操作都把列表带回顶部，让变化立刻可见。
    val listScope = rememberCoroutineScope()
    val scrollListToTop = { listScope.launch { listState.animateScrollToItem(0) } }
    val applySmart: (com.kiite.player.data.SmartCollection) -> Unit = { c ->
        query = c.query
        customTagFilter = c.tag
        favoriteOnly = c.onlyFavorite
        ratingFilter = c.rating ?: 0
        vm.say("已应用智能收藏「" + c.name + "」")
        scrollListToTop()
    }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 排序 / 批量收进二级弹窗，保证这些按键永远在可视范围内
    var sortSheet by remember { mutableStateOf(false) }
    // 长按进入批量后，单选一个作品可直接「编辑名称 / 收藏置顶」
    var quickEdit by remember { mutableStateOf<ProjectEntry?>(null) }
    // 批量把所选项目标级 / 加进播放队列；标级走可撤销的批量接口
    val markSelected: (com.kiite.player.core.WorkRating) -> Unit = { rating ->
        vm.setRatingsBatch(all.filter { selected.contains(it.path) }, rating)
        selected = emptySet()
    }
    val addSelectedToQueue: () -> Unit = {
        vm.queueAddAll(selected.flatMap { vm.tracksOfProject(it) })
        selected = emptySet()
    }
    val base = remember(all, settings.hideR18, manualRatings, dlsiteMeta, manualTags, workMetaMap, query, scriptFilter, ratingFilter, circleFilter, tagFilter, vaFilter, customTagFilter, seriesFilter, favoriteOnly) {
        all.filter { p ->
        val work = vm.dlsiteOf(p.path)
        val hitQuery = query.isBlank() ||
            projectTitle(vm, p).contains(query, ignoreCase = true) ||
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
        // R18 默认隐藏（设置可关）；用户主动筛「成人」时视为明确查看，仍显示
        val hitR18Hide = !settings.hideR18 || ratingFilter == 1 ||
            rating != com.kiite.player.core.WorkRating.R18
        val hitFilter = hitScript && hitRating && hitR18Hide
        val hitCustomTag = customTagFilter == null || vm.tagsOf(p).contains(customTagFilter)
        val hitSeries = seriesFilter == null || vm.seriesOf(p) == seriesFilter
        val hitFavorite = !favoriteOnly || vm.isFavorite(p)
        hitQuery && hitFilter && hitMeta && hitCustomTag && hitSeries && hitFavorite
        }
    }
    val ordered = remember(base, settings.sortMode, workMetaMap) {
        when (settings.sortMode) {
            "code" -> base.sortedBy { (it.code ?: "zzz").lowercase() }
            "tracks" -> base.sortedBy { it.trackCount }
            // 发售日（DLsite 抓取）：未识别的排最后
            "release" -> base.sortedBy { vm.dlsiteOf(it.path)?.releaseDate?.takeIf { d -> d.isNotBlank() } ?: "￿" }
            // 添加日期：作品文件夹的修改时间
            "recent" -> base.sortedBy { java.io.File(it.path).lastModified() }
            else -> base.sortedBy { projectTitle(vm, it).lowercase() }
        }
    }
    // 收藏的作品永远排在最前（组内仍按上面规则），升降序只翻转组内顺序。
    // 顺序很重要：**先**按升降序排好，**再**把收藏稳定地提到最前。
    // 反过来写（先置顶再 reversed）会把收藏一起翻到列表最底部——降序时必然踩到。
    val projects = remember(ordered, settings.sortAsc, workMetaMap) {
        val inner = if (settings.sortAsc) ordered else ordered.reversed()
        inner.sortedByDescending { vm.isFavorite(it) }
    }
    // 已识别的作品数量变了就重建筛选项
    val q0 = vm.dlsiteCount
    if (all.isEmpty()) {
        // 空态三分：扫描中 / 扫描完没找到，不让新用户在扫描期间误以为「没找到」
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (scanning) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        "正在扫描曲库…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    scanProgress?.let { p ->
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "已扫 " + p.dirsVisited + " 个目录 · 发现 " + p.tracks + " 首音频",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(
                    Modifier.padding(LibraryDim.pad28),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("没有找到音频文件", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.itemGap))
                    Text(
                        "这个目录里没有可识别的音频。请确认选中的是装着 ASMR 解压文件夹的目录，或换一个目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.gap14))
                    Button(onClick = onChangeRoot) { Text("重新选择文件夹") }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
    // 顶栏不再是固定条：往上滑时它才淡入成磨砂条（见 theme/TopBarScroll.kt）。
    // 宽屏左栏的标题固定不动、不会滚走，顶栏保持透明 —— 否则磨砂条会盖住那颗标题。
    if (header == null) com.kiite.player.ui.theme.TopBarClear()
    else com.kiite.player.ui.theme.TrackTopBar(listState)
    val circles = remember(q0) { vm.knownCircles() }
    val tags = remember(q0) { vm.knownTags() }
    val vas = remember(q0) { vm.knownVoiceActors() }
    val customTags = remember(q0, manualTags) { vm.knownCustomTags() }
    val seriesList = remember(workMetaMap) { vm.knownSeries() }
    // 搜索框默认收起；一旦有搜索内容就一直显示（否则没法清空）
    val searchVisible = showSearch || query.isNotBlank()
    // 大标题 + 筛选头打包成一块：窄屏时它们进 LazyColumn 当第一、二项（iOS large title），
    // 宽屏（header == null）仍留在栏外固定。以前那截「栏高」留白就是头部与首张卡片间的空档。
    val filterBlock: @Composable () -> Unit = {
    if (compact) {
        // 横屏：只留搜索 + 「筛选」下拉，把高度让给列表
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.itemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchVisible) {
                LibrarySearchField(
                    query = query,
                    onQuery = {
                        query = it
                        // 清空搜索时列表会变回全量，带回顶部看到完整列表
                        if (it.isBlank()) scrollListToTop()
                    },
                    onSubmit = { vm.recordSearch(it) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
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
                series = seriesList,
                seriesFilter = seriesFilter,
                onSeries = { seriesFilter = it },
                onClearMeta = {
                    circleFilter = null
                    tagFilter = null
                    vaFilter = null
                },
            )
        }
        // 横屏也显示历史：放在搜索行下面单独一行（宽屏高度富余，不必挤进同一行）
        if (searchVisible && query.isBlank()) {
            SearchHistoryRow(
                history = searchHistory,
                onPick = {
                    query = it
                    vm.recordSearch(it)
                },
                onClear = { vm.clearSearchHistory() },
            )
        }
    } else {
        Column {
        if (searchVisible) {
            LibrarySearchField(
                query = query,
                onQuery = {
                    query = it
                    // 清空搜索时列表会变回全量，带回顶部看到完整列表
                    if (it.isBlank()) scrollListToTop()
                },
                onSubmit = { vm.recordSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.itemGap),
            )
            // 还没输入时先给历史：搜过的词点一下就回去，不用重新敲
            if (query.isBlank()) {
                SearchHistoryRow(
                    history = searchHistory,
                    onPick = {
                        query = it
                        vm.recordSearch(it)
                    },
                    onClear = { vm.clearSearchHistory() },
                )
            }
        }
        // 筛选头：一行搞定。常用项（排序 / 台本 / 年龄限制 / 收藏）留在行内，
        // 其余（显示方式 / 批量 / 社团 / 标签 / 声优 / 自定义标签 / 系列 / 智能收藏）收进
        // 「筛选」下拉——不再折成三四行挤占作品列表。
        val metaFilterActive = circleFilter != null || tagFilter != null || vaFilter != null || customTagFilter != null || seriesFilter != null
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FilterChip(
                selected = false,
                onClick = { sortSheet = true },
                label = {
                    Text(
                        "排序 ▾" +
                            (if (settings.sortMode == "code") " 编号" else if (settings.sortMode == "tracks") " 曲目" else if (settings.sortMode == "release") " 发售日" else if (settings.sortMode == "recent") " 添加" else " 名称") +
                            (if (settings.sortAsc) " ↑" else " ↓"),
                    )
                },
            )
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
            FilterChip(
                selected = ratingFilter != 0,
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
            FilterChip(
                selected = favoriteOnly,
                onClick = { favoriteOnly = !favoriteOnly },
                label = { Text("收藏") },
            )
            OverflowFilterMenu(
                vm = vm,
                settings = settings,
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
                series = seriesList,
                seriesFilter = seriesFilter,
                onSeries = { seriesFilter = it },
                smartCollections = smartCollections,
                onApplySmart = applySmart,
                onSmartSave = { smartSaveSheet = true },
                onSmartManage = { smartManageSheet = true },
            )
            // 「清除筛选」覆盖全部筛选（含台本 / 年龄限制），与横屏行为一致
            if (metaFilterActive || scriptFilter != 0 || ratingFilter != 0 || favoriteOnly) {
                FilterChip(
                    selected = true,
                    onClick = {
                        circleFilter = null
                        tagFilter = null
                        vaFilter = null
                        customTagFilter = null
                        seriesFilter = null
                        favoriteOnly = false
                        scriptFilter = 0
                        ratingFilter = 0
                        scrollListToTop()
                    },
                    label = { Text("清除筛选") },
                )
            }
        }
        } // end 筛选头 Column
    } // end if (compact) / else
    } // end filterBlock
    // 宽屏左栏标题固定：筛选头留在栏外，不跟着列表滚走
    // （独立搜索页也不要这一套：搜索框由搜索页自己出，这里再铺一遍就是两条搜索框）
    if (header == null && !searchOnly) filterBlock()
    // 批量模式横幅：退出按钮在最左最显眼，系统返回键同样能退出，不再依赖筛选行里那个开关
    androidx.activity.compose.BackHandler(enabled = batch) {
        batch = false
        selected = emptySet()
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = batch,
        enter = asmrEnter(),
        exit = asmrExit(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .clip(asmrShape(Radius.md))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = Spacing.xs),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    batch = false
                    selected = emptySet()
                }) {
                    Icon(Icons.Default.Close, null, Modifier.size(IconSize.xs))
                    Spacer(Modifier.width(Spacing.xxs))
                    Text("退出批量")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "已选 " + selected.size + " 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(enabled = selected.isNotEmpty(), onClick = { selected = emptySet() }) {
                    Text("清除")
                }
                TextButton(onClick = { selected = all.map { it.path }.toSet() }) { Text("全选") }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
            ) {
                TextButton(
                    enabled = selected.size == 1,
                    onClick = { all.firstOrNull { selected.contains(it.path) }?.let { quickEdit = it } },
                ) { Text("编辑名称") }
                TextButton(onClick = {
                    val targets = all.filter { selected.contains(it.path) }
                    targets.forEach { vm.setFavorite(it, true) }
                    if (targets.isNotEmpty()) vm.say("已收藏 " + targets.size + " 个作品（置顶显示）")
                    selected = emptySet()
                }) { Text("收藏置顶") }
                TextButton(onClick = { markSelected(com.kiite.player.core.WorkRating.R18) }) { Text("标为成人") }
                TextButton(onClick = { markSelected(com.kiite.player.core.WorkRating.ALL) }) { Text("标为全年龄") }
                TextButton(onClick = { addSelectedToQueue() }) { Text("加入播放列表") }
            }
            if (selected.isEmpty()) {
                Text(
                    "点作品勾选，或长按任意作品快速勾选",
                    Modifier.padding(start = Spacing.md, bottom = Spacing.itemGap),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // 长按批量里单选一个作品的快捷编辑：改名称（自定义名优先，RJ 编号退到小标题）+ 收藏置顶
    quickEdit?.let { p ->
        WorkQuickEditDialog(vm = vm, project = p, onDismiss = { quickEdit = null })
    }
    // 「存为智能收藏」：给当前筛选条件起个名字
    if (smartSaveSheet) {
        var smartName by remember { mutableStateOf("") }
        val hasConditions = query.isNotBlank() || customTagFilter != null ||
            favoriteOnly || ratingFilter != 0
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { smartSaveSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = asmrShape(Radius.xl),
            title = { Text("存为智能收藏") },
            text = {
                Column {
                    Text(
                        "会把当前的筛选条件整体存下来：搜索词「" + query.ifBlank { "不限" } + "」" +
                            "· 标签 " + (customTagFilter ?: "不限") +
                            " · " + (if (favoriteOnly) "只看收藏" else "不限收藏") +
                            " · " + when (ratingFilter) { 1 -> "成人"; 2 -> "全年龄"; else -> "不限分级" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!hasConditions) {
                        Spacer(Modifier.height(Spacing.itemGap))
                        Text(
                            "当前没有设置任何筛选，存下的集合应用后不会有任何效果。请先设好搜索词 / 标签 / 收藏 / 分级。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(Spacing.gap10))
                    androidx.compose.material3.OutlinedTextField(
                        value = smartName,
                        onValueChange = { smartName = it },
                        label = { Text("集合名称（如：睡前 · 温柔）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = smartName.isNotBlank() && hasConditions,
                    onClick = {
                        vm.saveSmartCollection(
                            smartName, query, customTagFilter, favoriteOnly,
                            ratingFilter.takeIf { it != 0 },
                        )
                        smartSaveSheet = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { smartSaveSheet = false }) { Text("取消") }
            },
        )
    }
    // 「管理智能收藏」：改名条件看说明、删除不要的
    if (smartManageSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { smartManageSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = asmrShape(Radius.xl),
            title = { Text("管理智能收藏") },
            text = {
                Column(Modifier.heightIn(max = Containers.scrollMaxHeight).verticalScroll(rememberScrollState())) {
                    if (smartCollections.isEmpty()) {
                        Text("还没有保存的智能收藏。筛好条件后点「存为智能收藏」即可。", style = MaterialTheme.typography.bodySmall)
                    }
                    smartCollections.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "搜索词「" + c.query.ifBlank { "不限" } + "」· 标签 " + (c.tag ?: "不限") +
                                        (if (c.onlyFavorite) " · 只看收藏" else "") +
                                        (when (c.rating) { 1 -> " · 成人"; 2 -> " · 全年龄"; else -> "" }),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { applySmart(c); smartManageSheet = false }) { Text("应用") }
                            TextButton(onClick = { vm.deleteSmartCollection(c.id) }) { Text("删除") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { smartManageSheet = false }) { Text("关闭") } },
        )
    }
    // 二级「排序与批量」弹窗：排序方式/方向 + 批量分类按键，保证永远点得到
    if (sortSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { sortSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = asmrShape(Radius.xl),
            title = { Text("排序") },
            text = {
                Column {
                    Text("排序方式", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf("name" to "名称", "code" to "编号", "tracks" to "曲目", "release" to "发售日", "recent" to "添加日期")
                            .forEach { (id, label) ->
                                FilterChip(
                                    selected = settings.sortMode == id,
                                    onClick = { vm.setSortMode(id) },
                                    label = { Text(label) },
                                )
                            }
                    }
                    Spacer(Modifier.height(Spacing.gap10))
                    Text("排序方向", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    Row {
                        FilterChip(
                            selected = settings.sortAsc,
                            onClick = { vm.setSortAsc(true) },
                            label = { Text("升序") },
                        )
                        Spacer(Modifier.width(Spacing.sm))
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
            shape = asmrShape(Radius.xl),
            title = { Text("台本") },
            text = {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf("全部", "有台本", "无台本").forEachIndexed { i, label ->
                            FilterChip(
                                selected = scriptFilter == i,
                                onClick = { scriptFilter = i },
                                label = { Text(label) },
                            )
                        }
                    }
                    // 台本匹配报告：一眼找到所有没台本的作品并修复（含部分匹配）
                    val unmatchedProjects = all.count { p -> vm.tracksOfProject(p.path).any { !it.hasScript } }
                    if (unmatchedProjects > 0) {
                        Spacer(Modifier.height(Spacing.gap10))
                        Text(
                            "有 " + unmatchedProjects + " 部作品还没匹配到台本",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = { scriptSheet = false; onReportChange(true) }) {
                            Text("查看台本匹配报告")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { scriptSheet = false }) { Text("关闭") } },
        )
    }
    // 台本匹配报告：作品 → 缺台本的曲目 → 直达手动指定
    if (reportOpen) {
        ScriptReportDialog(
            projects = all,
            tracksOf = { vm.tracksOfProject(it) },
            onPickTrack = { track ->
                onReportChange(false)
                onPickScript(track)
            },
            onDismiss = { onReportChange(false) },
        )
    }
    // 二级「年龄限制」：成人/全年龄筛选 + 批量分类（标为成人 / 标为全年龄）
    if (ratingSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ratingSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = asmrShape(Radius.xl),
            title = { Text("年龄限制") },
            text = {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf("全部", "成人", "全年龄").forEachIndexed { i, label ->
                            FilterChip(
                                selected = ratingFilter == i,
                                onClick = { ratingFilter = i },
                                label = { Text(label) },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = Spacing.md))
                    Text(
                        "批量分类（已选 " + selected.size + " 个项目）",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = batch,
                            onClick = {
                                batch = !batch
                                if (!batch) selected = emptySet()
                            },
                            label = { Text(if (batch) "退出批量" else "进入批量") },
                        )
                        FilterChip(
                            selected = false,
                            onClick = { markSelected(com.kiite.player.core.WorkRating.R18) },
                            label = { Text("标为成人") },
                        )
                        FilterChip(
                            selected = false,
                            onClick = { markSelected(com.kiite.player.core.WorkRating.ALL) },
                            label = { Text("标为全年龄") },
                        )
                        FilterChip(
                            selected = false,
                            onClick = { addSelectedToQueue() },
                            label = { Text("加入播放列表") },
                        )
                    }
                    Spacer(Modifier.height(Spacing.itemGap))
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
    // 记住过滤结果：查询没变就不重复全库过滤，滚动更顺滑
    val trackMatches = remember(scan, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            (scan?.tracks ?: emptyList()).filter {
                it.name.contains(query, ignoreCase = true) || it.baseName.contains(query, ignoreCase = true)
            }
        }
    }
    val trackHits = trackMatches.take(80)
    var searchTab by remember(query) { mutableStateOf(0) }
    val pending = if (!searchOnly && query.isBlank() && scriptFilter == 0 && ratingFilter == 0) vm.missingPurchases() else emptyList()
    // 切到「DLsite 在线」/「asmr.one」标签（或搜索词变化后再次切回）时触发在线搜索
    LaunchedEffect(query, searchTab) {
        if (searchTab == 2) vm.searchOnline(query)
        if (searchTab == 3 && query.isNotBlank()) vm.searchAsmrOne(query, 1)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        // 悬浮玻璃底栏浮在内容上方：给滚动内容留出底部空间，最后一项能滚出底栏。
        // 顶部不再留「栏高」：大标题本身就是列表第一项，它顶在状态栏下沿，
        // 以前那截留白正是固定头部与首张卡片之间那 48dp 空档的来源。
        contentPadding = PaddingValues(bottom = LocalBottomBarInset.current),
    ) {
        // 大标题进滚动容器（iOS large title）：跟着列表一起上滚、滚进磨砂条后面，
        // 顶栏按 TrackTopBar 上报的进度淡入小标题。筛选头紧随其后，一起滚走。
        val h = header
        if (searchOnly) {
            // 独立搜索页：第一项就是大搜索框（进入即聚焦），下面接历史 / 结果
            item(key = "search-page-field", contentType = "search") {
                Column(Modifier.fillMaxWidth()) {
                    LibrarySearchField(
                        query = query,
                        onQuery = {
                            query = it
                            if (it.isBlank()) scrollListToTop()
                        },
                        onSubmit = { vm.recordSearch(it) },
                        autoFocus = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.itemGap),
                    )
                    if (query.isBlank()) {
                        SearchHistoryRow(
                            history = searchHistory,
                            onPick = {
                                query = it
                                vm.recordSearch(it)
                            },
                            onClear = { vm.clearSearchHistory() },
                        )
                        // 没有历史时给一句引导，别让第一屏空着（参考图的提示卡）
                        if (searchHistory.isEmpty()) {
                            Text(
                                "输入作品名、RJ 编号或曲目名开始搜索。云端作品切到「DLsite 在线」或「asmr.one」再搜。",
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else if (h != null) {
            item(key = "root-header", contentType = "header") { h() }
            item(key = "filter-header", contentType = "filter") { filterBlock() }
        }
        // 全局下载条：asmr.one 下载在后台跑，切回曲库也能看到进度、随手取消
        asmrDl?.takeIf { it.running }?.let { d ->
            item(key = "dl-banner") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.itemGap)
                        .clip(asmrShape(Radius.md))
                        .background(asmrRowColor())
                        .then(asmrBorder(asmrShape(Radius.md)))
                        .padding(horizontal = Spacing.gap14, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "下载中 · " + d.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        DownloadProgressBar(
                            progress = d.progress,
                            modifier = Modifier.padding(top = Spacing.sm),
                            height = Stroke.thick,
                        )
                        Text(
                            downloadDetailText(d),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "可切到其他页面或后台，下载会继续；通知栏也能看进度",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.cancelAsmrDownload() }) {
                        Text("取消", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (pending.isNotEmpty()) {
            item(key = "buy-header") {
                Text(
                    "已购未下载 " + pending.size + " 部（点「下载」保存到 曲库根目录/DLsite）",
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.gap18, vertical = Spacing.itemGap),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(pending, key = { _, p -> "buy:" + p.code }) { _, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .clip(asmrShape(Radius.md))
                        .background(asmrRowColor())
                        .then(asmrBorder(asmrShape(Radius.md)))
                        .padding(horizontal = Spacing.gap14, vertical = Spacing.gap10),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 已识别过的已购作品直接带 DLsite 封面，主页一眼认出是哪部
                    val cover = remember(item.code) { vm.dlsiteCoverOfCode(item.code) }
                    Box(
                        Modifier
                            .size(LibraryDim.box44)
                            .clip(asmrShape(Radius.xs))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cover != null) {
                            AsyncImage(
                                model = File(cover),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Default.LibraryMusic, null, Modifier.size(IconSize.md))
                        }
                    }
                    Spacer(Modifier.width(Spacing.gap10))
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
            item(key = "buy-divider") { HorizontalDivider(Modifier.padding(vertical = Spacing.itemGap)) }
        }
        // 搜索结果三类：曲目命中 / 作品命中 / DLsite 在线。有搜索词时都能切换。
        if (query.isNotBlank() || trackHits.isNotEmpty()) {
            item(key = "hits-tabs") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.itemGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (trackMatches.isNotEmpty()) {
                        FilterChip(
                            selected = searchTab == 0,
                            onClick = { searchTab = 0 },
                            label = {
                                // 命中超过 80 条被截断时，显示真实总数并提示细化关键词
                                Text(
                                    "曲目命中 " + trackMatches.size +
                                        (if (trackMatches.size > trackHits.size) "（显示前 " + trackHits.size + "，请细化关键词）" else ""),
                                )
                            },
                        )
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    FilterChip(
                        selected = searchTab == 1,
                        onClick = { searchTab = 1 },
                        label = { Text("作品命中 " + projects.size) },
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    FilterChip(
                        selected = searchTab == 2,
                        onClick = { searchTab = 2 },
                        label = {
                            Text(
                                "DLsite 在线" + when {
                                    onlineSearchState.loading -> "…"
                                    onlineSearchState.query == query &&
                                        query.isNotBlank() &&
                                        onlineSearchState.hits.isNotEmpty() -> " " + onlineSearchState.hits.size
                                    else -> ""
                                },
                            )
                        },
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    FilterChip(
                        selected = searchTab == 3,
                        onClick = { searchTab = 3 },
                        label = {
                            Text(
                                "asmr.one" + when {
                                    asmrSearchState.loading -> "…"
                                    asmrSearchState.hits.isNotEmpty() -> " " + asmrSearchState.hits.size
                                    else -> ""
                                },
                            )
                        },
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
                    modifier = Modifier.animateItem(),
                )
            }
        }
        // 曲目没命中时不要留一片空白：给一句指引，把人导到「作品命中 / DLsite 在线」
        if (query.isNotBlank() && searchTab == 0 && trackHits.isEmpty()) {
            item(key = "hit-empty") {
                Text(
                    "没有匹配的曲目。可以切到「作品命中」或直接搜 DLsite / asmr.one。",
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.gap18, vertical = Spacing.lg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // DLsite 在线搜索结果：直接给下载入口（已购走下载页，未购走购买页）
        if (searchTab == 2 && query.isNotBlank()) {
            item(key = "online-status") {
                if (onlineSearchState.loading) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.gap18, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(IconSize.xs), strokeWidth = Stroke.thin)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "正在搜索 DLsite…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (!onlineSearchState.error.isNullOrBlank()) {
                    Text(
                        onlineSearchState.error!!,
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.gap18, vertical = Spacing.sm),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(onlineSearchState.hits, key = { _, h -> "online:" + h.code }) { _, hit ->
                val owned = purchases.any { it.code == hit.code }
                val cover = remember(hit.code) { vm.dlsiteCoverOfCode(hit.code) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .clip(asmrShape(Radius.md))
                        .background(asmrRowColor())
                        .then(asmrBorder(asmrShape(Radius.md)))
                        .padding(horizontal = Spacing.gap14, vertical = Spacing.gap10),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(LibraryDim.box44)
                            .clip(asmrShape(Radius.xs))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cover != null) {
                            AsyncImage(
                                model = File(cover),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Default.LibraryMusic, null, Modifier.size(IconSize.md))
                        }
                    }
                    Spacer(Modifier.width(Spacing.gap10))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                hit.title ?: hit.code,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (owned) {
                                Spacer(Modifier.width(Spacing.itemGap))
                                Text(
                                    "已购",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            hit.code +
                                (hit.circle?.let { " · " + it } ?: "") +
                                (hit.price?.let { " · " + it } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { if (owned) onDownload(hit.code) else onBuy(hit.code) }) {
                        Text(if (owned) "下载" else "购买页")
                    }
                }
            }
        }
        // asmr.one 在线搜索结果：免费资源站，「下载」直接走原生 API（不用网页那套 JS blob 下载）
        if (searchTab == 3 && query.isNotBlank()) {
            item(key = "asmr-status") {
                if (asmrSearchState.loading && asmrSearchState.hits.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.gap18, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(IconSize.xs), strokeWidth = Stroke.thin)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "正在搜索 asmr.one…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (!asmrSearchState.error.isNullOrBlank()) {
                    Text(
                        asmrSearchState.error!!,
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.gap18, vertical = Spacing.sm),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(asmrSearchState.hits, key = { _, w -> "asmr:" + w.id }) { _, w ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .clip(asmrShape(Radius.md))
                        .background(asmrRowColor())
                        .then(asmrBorder(asmrShape(Radius.md)))
                        .padding(horizontal = Spacing.gap14, vertical = Spacing.gap10),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val thisDl = asmrDl?.takeIf { it.workId == w.id }
                    Box(
                        Modifier
                            .size(LibraryDim.box44)
                            .clip(asmrShape(Radius.xs))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (w.thumbnailCoverUrl != null) {
                            AsyncImage(
                                model = w.thumbnailCoverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Default.LibraryMusic, null, Modifier.size(IconSize.md))
                        }
                    }
                    Spacer(Modifier.width(Spacing.gap10))
                    Column(Modifier.weight(1f)) {
                        Text(
                            w.title ?: ("#" + w.id),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            listOfNotNull(
                                w.code ?: ("#" + w.id),
                                w.circleName,
                                w.vaNames.take(2).takeIf { it.isNotEmpty() }?.joinToString("/"),
                                w.rateAverage?.let { "★" + it },
                                if ((w.price ?: 0) == 0) "免费" else w.price.toString(),
                            ).joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        thisDl?.let { dl ->
                            if (dl.running || dl.completed || dl.okCount > 0) {
                                DownloadProgressBar(
                                    progress = if (dl.running) dl.progress else if (dl.completed) 1f else 0f,
                                    modifier = Modifier.padding(top = Spacing.itemGap),
                                    height = Stroke.medium,
                                    bar = if (dl.failed) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                downloadDetailText(dl),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dl.failed) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    TextButton(onClick = { onAsmrBrowse(w) }) {
                        Text("浏览")
                    }
                    when {
                        thisDl?.running == true -> TextButton(onClick = { vm.cancelAsmrDownload() }) {
                            Text("取消", color = MaterialTheme.colorScheme.error)
                        }
                        else -> TextButton(
                            onClick = { onAsmrPick(w) },
                            enabled = asmrDl?.running != true,
                        ) {
                            Text(if (asmrDl?.running == true) "下载中…" else "下载")
                        }
                    }
                }
            }
            // 分页：结果没加载完时给「加载更多」
            if (!asmrSearchState.loading &&
                asmrSearchState.hits.isNotEmpty() &&
                asmrSearchState.hits.size < asmrSearchState.total
            ) {
                item(key = "asmr-more") {
                    androidx.compose.material3.TextButton(
                        onClick = { vm.searchAsmrOne(query, asmrSearchState.page + 1) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("加载更多（已加载 " + asmrSearchState.hits.size + " / " + asmrSearchState.total + "）")
                    }
                }
            }
            if (asmrSearchState.loading && asmrSearchState.hits.isNotEmpty()) {
                item(key = "asmr-more-loading") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.gap18, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(LibraryDim.spinner14), strokeWidth = Stroke.thin)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "正在加载更多…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // 搜索中不再把整个曲库铺在结果下面（§5）：只有「作品命中」这一页才铺作品卡，
        // 曲目命中 / 在线 / asmr.one 各页只显示自己的结果，列表不会被作品卡淹掉。
        if ((query.isBlank() && !searchOnly) || (searchTab == 1 && query.isNotBlank())) {
            val toggle: (ProjectEntry) -> Unit = { project ->
                // 从搜索结果里打开作品 = 用户确实用了这次搜索，记进历史
                if (query.isNotBlank()) vm.recordSearch(query)
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
            // 长按任意作品直接进入批量并勾选它——不用去「筛选」里找批量开关
            val longPress: (ProjectEntry) -> Unit = { project ->
                batch = true
                selected = selected + project.path
            }
            if (settings.projectLayout == "list") {
                itemsIndexed(projects, key = { _, p -> p.path }) { index, project ->
                    LibraryItemAppear(index = index, generation = projects) {
                        ProjectListRow(
                            vm = vm,
                            project = project,
                            cover = coverOf(project.path) ?: project.coverPath,
                            batch = batch,
                            checked = selected.contains(project.path),
                            favorite = vm.isFavorite(project),
                            meta = projectMeta(vm, project),
                            onToggle = { toggle(project) },
                            onLongPress = { longPress(project) },
                        )
                    }
                }
            } else {
                val cols = if (settings.projectLayout == "large") 1 else 2
                val rows = projects.chunked(cols)
                itemsIndexed(rows, key = { _, c -> "g:" + c.first().path }) { index, rowItems ->
                    LibraryItemAppear(index = index, generation = projects) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            rowItems.forEach { project ->
                                ProjectCard(
                                    vm = vm,
                                    project = project,
                                    cover = coverOf(project.path) ?: project.coverPath,
                                    batch = batch,
                                    checked = selected.contains(project.path),
                                    favorite = vm.isFavorite(project),
                                    meta = projectMeta(vm, project),
                                    large = settings.projectLayout == "large",
                                    modifier = Modifier.weight(1f),
                                    onToggle = { toggle(project) },
                                    onLongPress = { longPress(project) },
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
}

/**
 * 竖屏筛选行的溢出菜单：显示方式 / 批量 / 社团 / 标签 / 声优 / 自定义标签 / 系列 / 智能收藏。
 * 排序 / 台本 / 年龄限制 / 收藏留给行内 chip（最高频），其余收进来，让筛选头保持一行。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OverflowFilterMenu(
    vm: MainViewModel,
    settings: AppSettings,
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
    series: List<String>,
    seriesFilter: String?,
    onSeries: (String?) -> Unit,
    smartCollections: List<com.kiite.player.data.SmartCollection>,
    onApplySmart: (com.kiite.player.data.SmartCollection) -> Unit,
    onSmartSave: () -> Unit,
    onSmartManage: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(0) }
    val active = listOfNotNull(circleFilter, tagFilter, vaFilter, customTagFilter, seriesFilter).size
    Box {
        FilterChip(
            selected = active > 0,
            onClick = { open = true },
            label = { Text(if (active > 0) "筛选 " + active else "筛选") },
        )
    }
    // 底部面板 + 分区标签：筛选类型多、单个类型条目也多（社团/标签几十上百条），
    // 全部竖排要滚很久才摸到下一类。顶部一行横向滚动的分区 chip 直接跳转，
    // 下方只渲染当前分区（内部可滚）。有激活筛选的分区在 chip 前带 ● 提示。
    if (open) {
        AppBottomSheet(onDismissRequest = { open = false }) {
            val sections = buildList {
                add("显示" to (settings.projectLayout != "list"))
                add("批量" to batch)
                if (circles.isNotEmpty()) add("社团" to (circleFilter != null))
                if (tags.isNotEmpty()) add("标签" to (tagFilter != null))
                if (vas.isNotEmpty()) add("声优" to (vaFilter != null))
                if (customTags.isNotEmpty()) add("自定义" to (customTagFilter != null))
                if (series.isNotEmpty()) add("系列" to (seriesFilter != null))
                add("智能收藏" to false)
            }
            if (section >= sections.size) section = 0
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    sections.forEachIndexed { i, (label, on) ->
                        FilterChip(
                            selected = section == i,
                            onClick = { section = i },
                            label = { Text((if (on && section != i) "● " else "") + label) },
                        )
                    }
                }
                HorizontalDivider()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(LibraryDim.height440)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = Spacing.xl),
                ) {
                    when (sections.getOrNull(section)?.first) {
                        "显示" -> {
                            FilterMenuLabel("显示方式")
                            listOf("list" to "列表", "medium" to "中图标", "large" to "大图标").forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text((if (settings.projectLayout == id) "● " else "   ") + label) },
                                    onClick = { vm.setProjectLayout(id) },
                                )
                            }
                        }
                        "批量" -> {
                            FilterMenuLabel("批量分类")
                            DropdownMenuItem(
                                text = { Text(if (batch) "退出批量分类" else "进入批量分类") },
                                onClick = { onBatch(!batch); open = false },
                            )
                            FilterMenuLabel("DLsite 识别")
                            DropdownMenuItem(
                                text = { Text("批量识别全部作品（已识别 " + vm.dlsiteCount + " 部，已识别的自动跳过）") },
                                onClick = {
                                    vm.fetchDlsiteForAll()
                                    open = false
                                },
                            )
                        }
                        "社团" -> {
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
                        "标签" -> {
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
                        "声优" -> {
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
                        "自定义" -> {
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
                        "系列" -> {
                            FilterMenuLabel("系列")
                            DropdownMenuItem(
                                text = { Text((if (seriesFilter == null) "● " else "   ") + "全部") },
                                onClick = { onSeries(null) },
                            )
                            series.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text((if (seriesFilter == s) "● " else "   ") + s) },
                                    onClick = { onSeries(if (seriesFilter == s) null else s) },
                                )
                            }
                        }
                        else -> {
                            // 智能收藏（兜底分支）
                            FilterMenuLabel("智能收藏")
                            DropdownMenuItem(
                                text = { Text("存为智能收藏") },
                                onClick = { onSmartSave(); open = false },
                            )
                            smartCollections.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("★ " + c.name) },
                                    onClick = { onApplySmart(c); open = false },
                                )
                            }
                            if (smartCollections.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("管理…") },
                                    onClick = { onSmartManage(); open = false },
                                )
                            }
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
    series: List<String>,
    seriesFilter: String?,
    onSeries: (String?) -> Unit,
    onClearMeta: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val active = (if (scriptFilter != 0) 1 else 0) +
        (if (ratingFilter != 0) 1 else 0) +
        (if (circleFilter != null) 1 else 0) +
        (if (tagFilter != null) 1 else 0) +
        (if (vaFilter != null) 1 else 0) +
        (if (customTagFilter != null) 1 else 0) +
        (if (seriesFilter != null) 1 else 0)
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
            shape = asmrShape(Radius.md),
            containerColor = MaterialTheme.colorScheme.surface,
            border = asmrMenuBorder(),
        ) {
            FilterMenuLabel("排序 · " + totalCount + " 个项目")
            listOf("name" to "名称", "code" to "编号", "tracks" to "曲目", "release" to "发售日", "recent" to "添加日期")
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
            if (series.isNotEmpty()) {
                FilterMenuLabel("系列")
                DropdownMenuItem(
                    text = { Text((if (seriesFilter == null) "● " else "   ") + "全部") },
                    onClick = { onSeries(null) },
                )
                series.forEach { s ->
                    DropdownMenuItem(
                        text = { Text((if (seriesFilter == s) "● " else "   ") + s) },
                        onClick = { onSeries(if (seriesFilter == s) null else s) },
                    )
                }
            }
            if (active > 0) {
                DropdownMenuItem(
                    text = { Text("清除全部筛选") },
                    onClick = { onScriptFilter(0); onRatingFilter(0); onClearMeta(); onCustomTag(null); onSeries(null) },
                )
            }
        }
    }
}

@Composable
private fun FilterMenuLabel(text: String) {
    // 统一走 SheetSectionLabel：内边距与字号跟其它弹层一致（原来是 16/6 + labelSmall）
    SheetSectionLabel(text, color = MaterialTheme.colorScheme.primary)
}

/** 长按批量中「编辑名称」的快捷弹窗：自定义作品名 + 收藏置顶。 */
@Composable
private fun WorkQuickEditDialog(
    vm: MainViewModel,
    project: ProjectEntry,
    onDismiss: () -> Unit,
) {
    val current = vm.workMetaOf(project)
    var name by remember(project.path) { mutableStateOf(current.title) }
    var favorite by remember(project.path) { mutableStateOf(current.favorite) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = asmrShape(Radius.xl),
        title = { Text("编辑作品") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("作品名称") },
                    placeholder = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
                Spacer(Modifier.height(Spacing.itemGap))
                Text(
                    "留空恢复文件夹原名。自定义名称优先显示，RJ 编号会保留在小标题里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    Modifier.fillMaxWidth().clickable { favorite = !favorite }.padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Star,
                        null,
                        tint = if (favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(IconSize.md),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("收藏（在曲库置顶）", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setWorkTitle(project, name)
                vm.setFavorite(project, favorite)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
        shape = asmrShape(Radius.xl),
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                        draft.forEach { t ->
                            FilterChip(
                                selected = true,
                                onClick = { draft = draft - t },
                                label = { Text(t + "  ×") },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("新标签（空格 / 逗号分隔可加多个）") },
                )
                val suggestions = allTags.filter { it !in draft }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.gap10))
                    Text(
                        "已有标签（点一下加入）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                        suggestions.forEach { t ->
                            AssistChip(onClick = { draft = draft + t }, label = { Text(t) })
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
    /**
     * 顶栏是否常驻磨砂。窄屏二级页顶部没有大标题垫着，透明栏会留出一条空白带，
     * 所以让栏直接进入磨砂态；宽屏右栏顶部由左栏标题负责，不强制。
     */
    frostTopBar: Boolean = false,
) {
    val detailListState = listState
    val detailScrolled = rememberTopCollapsed(detailListState)
    val detailScope = rememberCoroutineScope()
    // 新旧详情页开关：旧版等同改动前的布局（标题+播放全部在顶栏、曲目列表内嵌、无头图）
    val detailSettings by vm.settings.collectAsStateWithLifecycle()
    val newDetail = detailSettings.detailLayout != "old"
    Box(Modifier.fillMaxSize()) {
    // 窄屏二级页顶栏没有大标题垫着：直接进入磨砂态，小标题常驻
    if (frostTopBar) com.kiite.player.ui.theme.TopBarAlwaysFrosted()
    // 系统返回键：详情页也归它管。之前只有左上角那个箭头能退，按返回键会直接退出应用
    // ——「我明明在第二层，怎么一下退到底了」。返回动画与箭头完全一致（同一个 onBack）。
    androidx.activity.compose.BackHandler(enabled = project != null) { onBack() }
    // 二级页没有大标题那一行，最上面就是返回/操作按钮：整列再让出栏高，
    // 免得这排按钮落在浮层顶栏的点击区里（栏在页面顶部时是透明的，但会吃掉点击）。
    Column(Modifier.fillMaxSize().padding(top = LocalTopBarMetrics.current.bar)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixIconButton(modifier = Modifier.size(TouchTarget.min), onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回")
            }
            if (!newDetail) {
                // 旧版：标题与「播放全部」就在这里
                MiuixText(
                    projectTitle(vm, project),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (project != null && project.trackCount > 0) {
                    MiuixIconTextButton(
                        text = "播放全部",
                        icon = Icons.Default.PlayArrow,
                        onClick = {
                            val queue = vm.tracksOfProject(project.path)
                            queue.firstOrNull()?.let { vm.playTrack(it, queue) }
                        },
                    )
                }
            } else {
                // 新版：标题与「播放全部」都在下面的头图区
                Spacer(Modifier.weight(1f))
            }
            if (project != null && project.trackCount > 0) {
                MiuixIconTextButton(
                    text = "写入台本标签",
                    icon = Icons.Default.Save,
                    onClick = { vm.embedProject(project.path) },
                )
            }
            val archives = if (project != null) vm.archivesOfProject(project.path) else emptyList()
            archives.firstOrNull { it.extractable }?.let { zip ->
                MiuixIconTextButton(
                    text = "解压",
                    icon = Icons.Default.FolderOpen,
                    onClick = { vm.extractArchive(zip.path) },
                )
            }
        }
        HorizontalDivider()

        if (project == null) return@Column

        // ---------- 共享状态与弹窗（头图 / 标签 / DLsite / 选集都在同一个滚动列表里） ----------
        val workMetaMap by vm.workMeta.collectAsStateWithLifecycle()
        val manualTags by vm.manualTags.collectAsStateWithLifecycle()
        val manualRatings by vm.manualRatings.collectAsStateWithLifecycle()
        val dlsiteMeta by vm.dlsite.collectAsStateWithLifecycle()
        val coverOverrides by vm.coverOverrides.collectAsStateWithLifecycle()
        val wm = remember(project, workMetaMap) { vm.workMetaOf(project) }
        val allTracks = remember(project) { vm.tracksOfProject(project.path) }
        val eps = remember(project, workMetaMap, allTracks) { vm.visibleEpisodes(project, allTracks) }
        val chapters = vm.chaptersOf(project)
        val myTags = remember(project, manualTags) { vm.tagsOf(project) }
        val rating = remember(project, manualRatings, dlsiteMeta) { vm.ratingOf(project) }
        val work = remember(project, dlsiteMeta) { vm.dlsiteOf(project.path) }
        val code = vm.dlsiteCodeOf(project)
        // 详情页头图：自选图 > DLsite 抓到的封面 > 作品目录里的图片；都没有就画占位图
        val heroCoverPath = vm.dlsiteCoverOf(project.path) ?: project.coverPath
        val uriHandler = LocalUriHandler.current
        var editOpen by androidx.compose.runtime.saveable.rememberSaveable(project.path) { mutableStateOf(false) }
        var coverViewer by androidx.compose.runtime.saveable.rememberSaveable(project.path) { mutableStateOf(false) }
        var coverMenu by androidx.compose.runtime.saveable.rememberSaveable(project.path) { mutableStateOf(false) }
        // 自选头图：只做复制，不动曲库里的原图
        val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) vm.setCustomCoverFromUri(project, uri)
        }
        var tagEditor by androidx.compose.runtime.saveable.rememberSaveable(project.path) { mutableStateOf(false) }
        var dlsiteOpen by androidx.compose.runtime.saveable.rememberSaveable(project.path) { mutableStateOf(false) }
        var dlsiteMore by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        if (coverViewer) {
            val shown = heroCoverPath
            if (shown != null) {
                ImageViewer(
                    images = listOf(shown),
                    start = 0,
                    onClose = { coverViewer = false },
                    onSave = { path -> vm.saveImageToGallery(path, coverFileName(project, work)) },
                )
            }
        }
        if (coverMenu) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { coverMenu = false },
                title = { Text("作品头图") },
                text = {
                    Column {
                        Text(
                            "可以换成自己喜欢的图（只在本机生效，不动曲库里的原图）；" +
                                "也可以让它重新去 DLsite 抓一次封面。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        if (heroCoverPath != null) {
                            TextButton(onClick = {
                                coverMenu = false
                                vm.saveImageToGallery(heroCoverPath, coverFileName(project, work))
                            }) { Text("保存到相册") }
                        }
                        TextButton(onClick = {
                            coverMenu = false
                            coverPicker.launch("image/*")
                        }) { Text("从相册选一张") }
                        TextButton(onClick = {
                            coverMenu = false
                            // 先清掉自选图，再强制重抓，历史上下错的那张才会被覆盖
                            vm.clearCustomCover(project)
                            vm.fetchDlsite(project.path)
                        }) { Text("重新抓取 DLsite 封面") }
                        if (vm.hasCustomCover(project)) {
                            TextButton(onClick = {
                                coverMenu = false
                                vm.clearCustomCover(project)
                            }) { Text("恢复默认头图") }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { coverMenu = false }) { Text("关闭") }
                },
            )
        }
        if (editOpen) {
            WorkEditDialog(
                project = project,
                tracks = allTracks,
                meta = wm,
                knownSeries = vm.knownSeries().filter { it != vm.seriesOf(project) },
                onSave = { title, favorite, synopsis, names, hidden, series ->
                    vm.setWorkTitle(project, title)
                    vm.setFavorite(project, favorite)
                    vm.setSynopsis(project, synopsis)
                    vm.setSeries(project, series)
                    allTracks.forEach { t ->
                        vm.setEpisodeName(project, t.path, names[t.path].orEmpty())
                        if ((t.path in hidden) != (t.path in wm.hidden)) vm.toggleEpisodeHidden(project, t.path)
                    }
                    editOpen = false
                },
                onDismiss = { editOpen = false },
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

        if (!newDetail) {
            // 旧版布局：标题与「播放全部」在顶栏，曲目列表直接内嵌
            EpisodeList(
                vm = vm,
                project = project,
                currentPath = currentPath,
                onPickScript = onPickScript,
                listState = detailListState,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
        // 新版布局：整页一个滚动列表 —— 头图 / 标签 / DLsite 都是列表项，
        // 往下滑自然进入选集，不再有独立的「选集」二级页面
        LazyColumn(
            state = detailListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current),
        ) {
        item(key = "hero") {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, top = Spacing.gap10, bottom = Spacing.xs),
            verticalAlignment = Alignment.Top,
        ) {
            val heroCover = heroCoverPath
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(132.dp)
                        .clip(asmrShape(Radius.xs))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(asmrBorder(asmrShape(Radius.xs)))
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
                        // 没有封面时不要留一块空白：用标题首字做占位，看起来是「还没图」而不是「坏了」
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.secondaryContainer,
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                projectTitle(vm, project).trim().take(1).ifBlank { "♪" },
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { coverMenu = true },
                    contentPadding = PaddingValues(horizontal = Spacing.itemGap, vertical = 0.dp),
                ) {
                    Text("换头图", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                MiuixText(
                    projectTitle(vm, project),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xs))
                // RJ 编号固定在小标题首位；末尾展示发售日（DLsite）与收录日期（文件夹）
                val addedDate = remember(project.path) {
                    runCatching {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                            .format(java.util.Date(java.io.File(project.path).lastModified()))
                    }.getOrDefault("")
                }
                MiuixText(
                    buildString {
                        if (code != null) append(code).append(" · ")
                        work?.circle?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                        append(project.chapters.size).append(" 章 · ").append(eps.size).append(" 集")
                        val r0 = vm.ratingOf(project)
                        if (r0 != com.kiite.player.core.WorkRating.UNKNOWN) append(" · ").append(r0.label)
                        work?.releaseDate?.takeIf { it.isNotBlank() }?.let { append(" · 发售 ").append(it) }
                        if (addedDate.isNotBlank()) append(" · 收录 ").append(addedDate)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 简介：优先手写，其次 DLsite 自动抓的（标题本身已用 DLsite 名，这里不再重复）
                val work0 = vm.dlsiteOf(project.path)
                val synopsisText = wm.synopsis.ifBlank { work0?.synopsis.orEmpty() }
                if (synopsisText.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.itemGap))
                    MiuixText(
                        synopsisText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // 操作行：自动换行，不再左右滚动
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DetailAction(
                text = "播放全部",
                icon = Icons.Default.PlayArrow,
                primary = true,
                enabled = eps.isNotEmpty(),
                onClick = { eps.firstOrNull()?.let { vm.playTrack(it, eps) } },
            )
            DetailAction(
                text = "加入队列",
                icon = Icons.Default.Add,
                enabled = eps.isNotEmpty(),
                onClick = { vm.queueAddAll(eps) },
            )
            DetailAction(
                text = if (wm.favorite) "已收藏" else "收藏",
                icon = Icons.Default.Star,
                onClick = { vm.setFavorite(project, !wm.favorite) },
            )
            DetailAction(
                text = if (wm.synopsis.isBlank()) "编辑 / 加简介" else "编辑",
                onClick = { editOpen = true },
            )
        }
        } // end item "hero"
        item(key = "info") {
        // 自定义标签：作品级、可多个、参与筛选。点标签即可删除，点「＋」编辑
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            myTags.forEach { t ->
                DetailAction(
                    text = t + "  ×",
                    onClick = { vm.setTags(project, myTags - t) },
                )
            }
            DetailAction(
                text = if (myTags.isEmpty()) "添加标签" else "＋",
                icon = Icons.Default.Add,
                onClick = { tagEditor = true },
            )
        }
        // DLsite 作品识别：默认折叠，只留一行作品标题（点标题展开操作与详情）
        val dlsiteTitle = work?.title?.takeIf { it.isNotBlank() }

        // 操作行：识别 / 编号 / 分级切换 / 更多（展开后显示）
        val dlsiteActions: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIconTextButton(
                    text = if (work == null) "DLsite 识别" else "重新识别",
                    icon = Icons.Default.Language,
                    enabled = code != null,
                    onClick = { vm.fetchDlsite(project.path) },
                )
                MiuixText(
                    if (code != null) code else "未识别到 RJ 编号",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                MiuixTextPill(
                    text = rating.label + " 切换",
                    onClick = {
                        val next = when (rating) {
                            com.kiite.player.core.WorkRating.UNKNOWN -> com.kiite.player.core.WorkRating.ALL
                            com.kiite.player.core.WorkRating.ALL -> com.kiite.player.core.WorkRating.R18
                            com.kiite.player.core.WorkRating.R18 -> com.kiite.player.core.WorkRating.UNKNOWN
                        }
                        vm.setRating(project, next)
                    },
                )
                if (code != null || work?.productUrl != null) {
                    Box {
                        MiuixIconButton(modifier = Modifier.size(TouchTarget.min), onClick = { dlsiteMore = true }) {
                            Icon(Icons.Default.MoreVert, "DLsite 更多操作")
                        }
                        DropdownMenu(
                            expanded = dlsiteMore,
                            onDismissRequest = { dlsiteMore = false },
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            shape = asmrShape(Radius.md),
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = asmrMenuBorder(),
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
                    .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.itemGap, bottom = Spacing.itemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    dlsiteTitle,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (dlsiteOpen) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    if (dlsiteOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (dlsiteOpen) "收起 DLsite 信息" else "展开 DLsite 信息",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.md),
                )
            }
            // 展开/收起走高度动画：以前是「点一下，一下多出两行」，像页面被撑开。
            // 只影响这一个列表项的重排（220ms），不牵动整页。
            AnimatedVisibility(visible = dlsiteOpen, enter = asmrEnter(), exit = asmrExit()) {
                Column {
                    dlsiteActions()
                    val meta = buildList {
                        work?.circle?.takeIf { it.isNotBlank() }?.let { add("社团：" + it) }
                        work?.tags?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" / ")) }
                        if (work?.owned == true) add("已购买")
                    }
                    if (meta.isNotEmpty()) {
                        MiuixText(
                            meta.joinToString(" · "),
                            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xxs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        } // end item "info"
        item(key = "arch") {
        val unsupported = vm.archivesOfProject(project.path).filter { !it.extractable }
        if (unsupported.isNotEmpty()) {
            Text(
                "含 " + unsupported.size + " 个 rar/7z 等压缩包（应用内暂不支持解压，请用其他工具解压后再扫描）：" +
                    unsupported.joinToString("、") { it.name },
                Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        } // end item "arch"
        item(key = "eps-head") {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.gap14, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(13.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(Spacing.sm))
            MiuixText(
                "选集　" + eps.size + " 集",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            MiuixIconTextButton(
                text = "播放全部",
                icon = Icons.Default.PlayArrow,
                enabled = eps.isNotEmpty(),
                onClick = { eps.firstOrNull()?.let { vm.playTrack(it, eps) } },
            )
        }
        } // end item "eps-head"
        // 选集列表：章节分组 + 每集一行（原「选集」二级页内容，现已并入同一滚动流）
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
                    modifier = Modifier.animateItem(),
                )
            }
        }
        } // end LazyColumn
        } // end else (newDetail)
        }
        // 一键回到顶部（抬高到悬浮玻璃底栏之上）
        AnimatedVisibility(
            visible = detailScrolled,
            enter = popIn(),
            exit = popOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.lg, bottom = LocalBottomBarInset.current + 4.dp),
        ) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { detailScope.launch { detailListState.animateScrollToItem(0) } },
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

/**
 * 首屏错峰入场：让刚铺开的一屏有层次（一行比上一行晚 22ms 落位）。
 *
 * 只作用于前 [LibraryDim.staggerCount] 项 —— 再往后的行不参与，滚起来时不会满屏都在飘。
 * generation 一变（筛选 / 搜索 / 排序换了内容）就重来一次。
 */
@Composable
private fun LibraryItemAppear(
    index: Int,
    generation: Any,
    content: @Composable () -> Unit,
) {
    val animate = index < LibraryDim.staggerCount
    var shown by remember(generation, index) { mutableStateOf(!animate) }
    LaunchedEffect(generation, index) {
        if (!animate) return@LaunchedEffect
        delay(index.toLong() * LibraryDim.staggerStepMs)
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.enter(Motion.MEDIUM),
        label = "libraryItemAlpha",
    )
    val risePx = with(LocalDensity.current) { LibraryDim.staggerRise.toPx() }
    val rise by animateFloatAsState(
        targetValue = if (shown) 0f else risePx,
        animationSpec = Motion.enter(Motion.MEDIUM),
        label = "libraryItemRise",
    )
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = rise
        },
    ) {
        content()
    }
}

/**
 * Miuix 的 tonal 胶囊按钮：外观借 Miuix，字号与配色按 UI-SPEC 走。
 *
 * 不用 Miuix 自带的 TextButton —— 它内部用 Miuix 自己的 button 字阶（17sp 左右），
 * 和页面里 MaterialTheme 的 labelLarge（14sp）摆在同一行会明显一大一小。
 */
@Composable
private fun MiuixTextPill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    MiuixButton(
        onClick = onClick,
        enabled = enabled,
        colors = MiuixButtonDefaults.buttonColors(),
        insideMargin = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        MiuixText(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 「图标 + 文字」的 Miuix 胶囊按钮：尺寸与字阶同样按项目规范。
 */
@Composable
private fun MiuixIconTextButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    MiuixButton(
        onClick = onClick,
        enabled = enabled,
        colors = MiuixButtonDefaults.buttonColors(),
        insideMargin = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Icon(
            icon,
            null,
            Modifier.size(IconSize.sm),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(Spacing.xs))
        MiuixText(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 列表模式的作品行。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectListRow(
    vm: MainViewModel,
    project: ProjectEntry,
    cover: String?,
    batch: Boolean,
    checked: Boolean,
    favorite: Boolean,
    meta: String,
    onToggle: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    // 「点哪儿从哪儿放大」：只记卡片坐标，点击时才取中心点（见 theme/ZoomOrigin.kt）
    val zoom = LocalZoomOrigin.current
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .let { if (zoom != null) it.reportZoomOrigin(zoom) else it }
            .then(asmrBorder(asmrShape(Radius.md))),
        color = asmrRowColor(),
        cornerRadius = Radius.md,
        insideMargin = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        // HyperOS 的「按下去会陷一下」：Miuix 自带的按压反馈，和设置页同一套手感
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = {
            zoom?.capture()
            onToggle()
        },
        onLongPress = onLongPress,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(LibraryDim.box48)
                    .clip(asmrShape(Radius.xs))
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
                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(IconSize.md))
                }
            }
            Spacer(Modifier.width(Spacing.gap14))
            Column(Modifier.weight(1f)) {
                MiuixText(
                    projectTitle(vm, project),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                projectCode(project)?.let { code ->
                    MiuixText(
                        code,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MiuixText(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 「已匹配台本」：有歌词/台本的作品一眼可辨（参考图 4 的那颗淡蓝胶囊）。
                // 用主色的低透明度底而不是写死的蓝：换皮肤/换深浅色都跟着走。
                if (project.scriptCount > 0) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        "已匹配台本",
                        Modifier
                            .clip(asmrShape(Radius.xl))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (favorite) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    Icons.Default.Star,
                    "已收藏（置顶）",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.sm),
                )
            }
            if (batch) {
                Spacer(Modifier.width(Spacing.sm))
                Checkbox(checked = checked, onCheckedChange = null)
            }
        }
    }
}

/** 中 / 大图标模式的作品卡（封面在上，标题与摘要在下）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    vm: MainViewModel,
    project: ProjectEntry,
    cover: String?,
    batch: Boolean,
    checked: Boolean,
    favorite: Boolean,
    meta: String,
    large: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val coverHeight = if (large) LibraryDim.box280 else LibraryDim.box150
    val zoom = LocalZoomOrigin.current
    MiuixCard(
        modifier = modifier
            .let { if (zoom != null) it.reportZoomOrigin(zoom) else it }
            .then(asmrBorder(asmrShape(Radius.sm))),
        color = asmrRowColor(),
        cornerRadius = Radius.sm,
        insideMargin = PaddingValues(Spacing.itemGap),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = {
            // 先定格卡片位置，再切页：转场原点要在 targetState 变化之前就位
            zoom?.capture()
            onToggle()
        },
        onLongPress = onLongPress,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .clip(asmrShape(Radius.xs))
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
                Icon(Icons.Default.LibraryMusic, null, Modifier.size(if (large) LibraryDim.icon40 else LibraryDim.icon28))
            }
            if (batch) {
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.align(Alignment.TopEnd))
            }
            if (favorite) {
                Icon(
                    Icons.Default.Star,
                    "已收藏（置顶）",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs).size(IconSize.md),
                )
            }
        }
        Spacer(Modifier.height(Spacing.itemGap))
        MiuixText(
            projectTitle(vm, project),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        projectCode(project)?.let { code ->
            MiuixText(
                code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MiuixText(
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
    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = LocalBottomBarInset.current),
    ) {
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
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** 详情页的编辑弹窗：作品名 / 收藏 / 简介 + 逐集改名 / 隐藏。 */
@Composable
private fun WorkEditDialog(
    project: ProjectEntry,
    tracks: List<TrackEntry>,
    meta: com.kiite.player.data.WorkMeta,
    knownSeries: List<String>,
    onSave: (String, Boolean, String, Map<String, String>, Set<String>, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(project.path) { mutableStateOf(meta.title) }
    var favorite by remember(project.path) { mutableStateOf(meta.favorite) }
    var synopsis by remember(project.path) { mutableStateOf(meta.synopsis) }
    var series by remember(project.path) { mutableStateOf(meta.series) }
    val names = remember(project.path) {
        mutableStateMapOf<String, String>().apply { meta.epNames.forEach { (k, v) -> put(k, v) } }
    }
    val hidden = remember(project.path) {
        mutableStateListOf<String>().apply { addAll(meta.hidden) }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = asmrShape(Radius.xl),
        title = { Text("编辑作品") },
        text = {
            Column(
                Modifier
                    .heightIn(max = Containers.scrollMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("作品名称（留空恢复文件夹原名）", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
                Row(
                    Modifier.fillMaxWidth().clickable { favorite = !favorite }.padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Star,
                        null,
                        tint = if (favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(IconSize.md),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("收藏（在曲库置顶）", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
                Spacer(Modifier.height(Spacing.sm))
                Text("简介", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("写点简介；留空则不显示") },
                )
                Spacer(Modifier.height(Spacing.sm))
                Text("系列（组曲名，留空不归类）", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = series,
                    onValueChange = { series = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("如：深夜病栋系列") },
                )
                if (knownSeries.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.itemGap))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                        knownSeries.take(8).forEach { s ->
                            FilterChip(
                                selected = series == s,
                                onClick = { series = if (series == s) "" else s },
                                label = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Text("选集（改名 / 隐藏）", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(Spacing.xs))
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
                                .padding(vertical = Spacing.sm, horizontal = Spacing.xxs),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        OutlinedTextField(
                            value = names[t.path].orEmpty(),
                            onValueChange = { names[t.path] = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(t.baseName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                    Spacer(Modifier.height(Spacing.itemGap))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, favorite, synopsis, names.toMap(), hidden.toSet(), series) }) { Text("保存") }
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
        // 注意：snapshotFlow 每滚一像素都会重算，所以这里直接返回「布尔结果」并
        // distinctUntilChanged —— 只有真正跨过阈值才写状态，避免滚动中反复触发重组。
        var current = false
        snapshotFlow {
            val index = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            val scrollable = state.canScrollForward || state.canScrollBackward
            current = when {
                !scrollable -> false
                current -> !(index == 0 && offset < EXPAND_AT)
                else -> index > 1 || offset > COLLAPSE_AT
            }
            current
        }.distinctUntilChanged().collect { collapsed = it }
    }
    return collapsed
}

/**
 * 作品显示名：自定义名称 > DLsite 作品标题 > 文件夹名。
 * RJ 编号不再拼进标题，单独走 [projectCode] 以小字浅色显示在标题下方。
 */
private fun projectTitle(vm: MainViewModel, p: ProjectEntry?): String = p?.let {
    vm.workTitleOf(it) ?: it.name
} ?: ""

/** 作品的 RJ 编号（用于标题下方的小字副行；没有编号返回 null）。 */
private fun projectCode(p: ProjectEntry?): String? = p?.code

/**
 * 保存头图到相册时的文件名：`RJ编号_作品名`，没有编号就用文件夹名。
 * 具体的非法字符过滤与扩展名在 [com.kiite.player.util.GallerySave] 里做。
 */
private fun coverFileName(project: ProjectEntry?, work: com.kiite.player.core.DlsiteWork?): String {
    val code = work?.code ?: project?.code
    val title = work?.title?.takeIf { it.isNotBlank() }
        ?: project?.let { java.io.File(it.path).name }
        ?: "kiite-cover"
    return listOfNotNull(code, title).joinToString("_")
}

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
            shape = asmrShape(Radius.md),
            containerColor = MaterialTheme.colorScheme.surface,
            border = asmrMenuBorder(),
        ) {
            DropdownMenuItem(text = { Text("全部") }, onClick = { onSelect(null); open = false })
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@Composable
private fun ChapterHeader(name: String, count: Int, onPlayAll: (() -> Unit)? = null) {
    // 排版交给 SectionHeader：两套皮肤的差异在组件内部消化，
    // 这里只表达「章节名 + 数量 + 整章播放」。
    val trailing: (@Composable () -> Unit)? = onPlayAll?.let { play ->
        {
            IconButton(onClick = play, modifier = Modifier.size(TouchTarget.min)) {
                Icon(Icons.Default.PlayArrow, "整章播放", Modifier.size(IconSize.sm))
            }
        }
    }
    SectionHeader(title = name, count = count, trailing = trailing)
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
    /** 列表里的位置动画由调用处传 `Modifier.animateItem()`（只能在 LazyItemScope 里构造）。 */
    modifier: Modifier = Modifier,
) {
    // 长按 = 操作菜单（手势语义表：单击播放、长按菜单、尾部按钮是快捷入口）
    var menuOpen by remember { mutableStateOf(false) }
    TrackListItem(
        title = title,
        subtitle = scriptSummary(track),
        active = active,
        leadingIcon = {
            Box(
                Modifier
                    .size(LibraryDim.box38)
                    .clip(asmrShape(Radius.sm))
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
        onClick = onPlay,
        onLongClick = { menuOpen = true },
        trailing = {
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
        },
        modifier = modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs),
    )
    if (menuOpen) {
        AppBottomSheet(onDismissRequest = { menuOpen = false }, title = title) {
            SheetAction("播放", Icons.Default.PlayArrow) {
                menuOpen = false
                onPlay()
            }
            SheetAction("加入播放列表", Icons.AutoMirrored.Filled.PlaylistAdd) {
                menuOpen = false
                onAddToQueue()
            }
            SheetAction("选择台本", Icons.Default.Description) {
                menuOpen = false
                onPickScript(track)
            }
        }
    }
}

/** 台本概要：曲目行的副标题文案（未匹配 / 格式 · 文件名 · 是否可逐句同步）。 */
private fun scriptSummary(track: TrackEntry): String {
    val primary = track.primaryScript ?: return "未匹配到台本"
    val manual = if (primary.manual) "手动 · " else ""
    val extra = if (track.scripts.size > 1) " 等 " + track.scripts.size + " 份" else ""
    val timed = if (primary.format.isTimed) " · 可逐句同步" else ""
    return manual + primary.format.label + " · " + primary.scriptName + extra + timed
}

/**
 * 作品详情页的操作按钮：统一高度、胶囊形、间距一致。
 *
 * 之前这一类按钮直接用了 Miuix 的 Pill / IconTextButton：底色是极淡的灰，
 * 放在浅色页面里几乎与背景同色，几个按钮挤成一团、边界也看不清。这里显式给
 * 副操作 `surfaceVariant` 底 + 发丝描边（`asmrBorder`），主操作仍是实心主色，
 * 两类按钮一眼能分开，间距统一走 Spacing.sm。
 */
@Composable
private fun DetailAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = asmrShape(Radius.pill)
    val bg = if (primary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    // 副操作补一道发丝描边，浅色下才分得出「按钮」与「页面底色」
    val edge = if (primary) Modifier else asmrBorder(shape)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = TouchTarget.min).then(edge),
        shape = shape,
        color = if (enabled) bg else bg.copy(alpha = 0.5f),
        contentColor = fg,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(IconSize.xs), tint = fg)
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                maxLines = 1,
            )
        }
    }
}
