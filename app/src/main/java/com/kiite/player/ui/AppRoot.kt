package com.kiite.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kiite.player.AppInfo
import com.kiite.player.ui.theme.LocalBottomBarInset
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.LocalHazeState
import com.kiite.player.ui.theme.LocalIsBright
import com.kiite.player.util.Permissions
import dev.chrisbanes.haze.hazeSource

/** 超过该宽度按「宽屏」处理：曲库 / 播放页切双栏布局。 */
internal val WideScreenMinWidth = 600.dp

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.hasAllFilesAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = Permissions.hasAllFilesAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!granted) {
        PermissionScreen(
            onGrant = { Permissions.requestAllFilesAccess(context) },
            onRecheck = { granted = Permissions.hasAllFilesAccess(context) },
        )
    } else {
        MainScaffold(vm)
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit, onRecheck: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Folder, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("需要「所有文件访问权限」", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "ASMR 解压包里的台本可能是 txt / lrc / docx / pdf 等任意文件，" +
                    "只授予音频权限读不到它们。请在系统设置里为本应用开启「所有文件访问」。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onGrant) { Text("去授权") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRecheck) { Text("已授权，重新检测") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: MainViewModel) {
    // 返回键/侧滑手势：退回后台而不是结束 Activity，
    // 这样重新进入时界面状态与正在播放的音频都还在
    val hostActivity = LocalContext.current as? android.app.Activity
    androidx.activity.compose.BackHandler(enabled = true) {
        hostActivity?.moveTaskToBack(true)
    }

    // 进了「作品详情」后，返回应先回列表（多数人的习惯），而不是直接退到后台
    val selectedProjectPath by vm.selectedProject.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = selectedProjectPath != null) {
        vm.selectProject(null)
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }
    // 可撤销操作（批量分级等）：snackbar 上给「撤销」按钮
    LaunchedEffect(Unit) {
        vm.undo.collect { req ->
            val result = snackbar.showSnackbar(req.text, actionLabel = "撤销", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) req.undo()
        }
    }

    // 版本更新后自动弹一次更新日志
    val settings by vm.settings.collectAsStateWithLifecycle()
    val videoFull by vm.videoFullscreen.collectAsStateWithLifecycle()
    var autoChangelog by remember { mutableStateOf(false) }
    LaunchedEffect(settings.seenVersion) {
        if (settings.seenVersion != null && settings.seenVersion != AppInfo.VERSION_NAME) {
            autoChangelog = true
        } else if (settings.seenVersion == null) {
            // 首次记录，不打扰
            vm.markVersionSeen(AppInfo.VERSION_NAME)
        }
    }
    if (autoChangelog) {
        ChangelogDialog {
            autoChangelog = false
            vm.markVersionSeen(AppInfo.VERSION_NAME)
        }
    }

    // 用 rememberSaveable 让当前标签在旋转屏幕后保持不变（否则横竖屏切换会跳回曲库）
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 播放页正在放视频时，用视频播放器自己的顶栏/工具条，收起 App 外壳的顶栏与底部导航
    val videoMode = tab == 1 && vm.currentIsVideo()
    // 离开播放页时复位视频全屏。
    // 不能放在 PlayerScreen 里：进全屏要隐藏顶栏/底栏，Scaffold 重排会让它重进，
    // onDispose/LaunchedEffect 立刻把状态改回 false，导致全屏永远进不去。
    LaunchedEffect(tab) {
        if (tab != 1) vm.setVideoFullscreen(false)
    }
    val current by vm.currentTrack.collectAsStateWithLifecycle()
    // 注意：不要在根部收集 vm.player（positionMs 每 400ms 变一次）——
    // 那会让整个 Scaffold 每次进度 tick 都重组，是掉帧主因。
    // 播放进度只留给真正需要它的 MiniPlayerCard 内部去收集。
    val busyTasks by vm.busyTasks.collectAsStateWithLifecycle()
    val scanProgress by vm.scanProgress.collectAsStateWithLifecycle()
    val coverPath by vm.currentCover.collectAsStateWithLifecycle()

    // 发现新版本的提示弹窗（1.5.5 之前只有状态没有界面，导致「检查更新」毫无反应）
    val updateCtx = LocalContext.current
    val updateInfo by vm.update.collectAsStateWithLifecycle()
    updateInfo?.let { info ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = asmrShape(20.dp),
            title = { Text("发现新版本 " + info.tag) },
            text = {
                Text(
                    info.notes.ifBlank { "有新版本可用，建议更新。" },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    runCatching {
                        updateCtx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(info.url),
                            ),
                        )
                    }.onFailure { vm.say("无法打开浏览器：" + info.url) }
                    vm.dismissUpdate()
                }) { Text("前往下载") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { vm.dismissUpdate() }) { Text("稍后") }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= WideScreenMinWidth
    // 悬浮玻璃底栏：不再用 Scaffold.bottomBar 占位，
    // 页面内容直接延伸到它后面（能隐约看到），卡片之间的空隙不拦截点击。
    // 悬浮栏实际测得的高度（含系统导航栏）通过 LocalBottomBarInset 告诉各页面的滚动容器。
    var floatingBarPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val showFloatingBar = !videoFull && !videoMode && !(settings.immersive && tab == 1)
    val bottomBarInset = if (showFloatingBar) {
        with(density) { floatingBarPx.toDp() } + 14.dp
    } else {
        0.dp
    }

    CompositionLocalProvider(LocalBottomBarInset provides bottomBarInset) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!videoFull && !videoMode) Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    // 默认皮肤：透明容器 + 高斯模糊磨砂；其它皮肤沿用半透明纯色
                    containerColor = if (LocalEditorial.current) Color.Transparent else asmrBarColor(),
                ),
                modifier = Modifier.asmrFrost(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (LocalIsBright.current) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(asmrShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                AsyncImage(
                                    model = com.kiite.player.R.drawable.kiite_logo,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column {
                            Text(
                                when (tab) {
                                    0 -> "曲库"
                                    1 -> "正在播放"
                                    else -> "设置"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (LocalIsBright.current) {
                                Text(
                                    when (tab) {
                                        0 -> "LIBRARY / LOCAL"
                                        1 -> "NOW PLAYING"
                                        else -> "SETTINGS"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // 「版式」皮肤：参考图那种印刷套色小色块（蓝/品红/黄）
                        EditorialTicks(Modifier.padding(start = 10.dp))
                    }
                },
            )
            // 「版式」皮肤：顶栏下加一条印刷细线
            if (LocalEditorial.current) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                )
            }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                // 页面内容注册为磨砂模糊源：悬浮栏 / 顶栏能对滚过的内容做高斯模糊
                Modifier
                    .let { if (wide) it.fillMaxSize() else it.fillMaxHeight().widthIn(max = 760.dp) }
                    .hazeSource(LocalHazeState.current),
            ) {
            // 标签页切换：按方向轻微滑动 + 淡入淡出，页面之间有连贯感
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(tween(220)) { full -> if (forward) full / 8 else -full / 8 } +
                        fadeIn(tween(220))
                    val exit = slideOutHorizontally(tween(180)) { full -> if (forward) -full / 8 else full / 8 } +
                        fadeOut(tween(150))
                    enter togetherWith exit
                },
                label = "tabContent",
            ) { page ->
                // 播放页是固定布局（底部有控制条），底栏悬浮时给它让出空间；
                // 曲库 / 设置是长列表，内容直接延伸到悬浮栏后面，滚动留白由各自列表处理
                Box(
                    Modifier.fillMaxSize().then(
                        if (page == 1) Modifier.padding(bottom = LocalBottomBarInset.current) else Modifier,
                    ),
                ) {
                    when (page) {
                        0 -> LibraryScreen(vm)
                        1 -> PlayerScreen(vm = vm, onExit = { tab = 0 })
                        else -> SettingsScreen(vm)
                    }
                }
            }
            }
            // 后台任务覆盖层：列出所有进行中的任务（互不覆盖），扫描可取消
            if (busyTasks.isNotEmpty()) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsmrCard {
                        Column(
                            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            busyTasks.forEach { (_, label) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (busyTasks.containsKey("scan")) {
                                scanProgress?.let { p ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "已扫 " + p.dirsVisited + " 个目录 · 音频 " + p.tracks + " · 台本 " + p.scripts,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                TextButton(onClick = { vm.cancelScan() }) { Text("取消扫描") }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // 悬浮玻璃底栏（迷你播放条 + 导航胶囊）：浮在内容上方，
    // 页面从它后面穿过；外层容器没有点击处理，卡片之间的空隙不影响后方内容的点击
    if (showFloatingBar) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { floatingBarPx = it.height },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tab != 1) {
                    AnimatedVisibility(
                        visible = current != null,
                        enter = asmrEnter(),
                        exit = asmrExit(),
                    ) {
                        current?.let { track ->
                            // 进度状态在这个子组件里收集：tick 只重组迷你条本身
                            MiniPlayerCard(
                                vm = vm,
                                title = track.baseName,
                                subtitle = track.folderName,
                                cover = coverPath,
                                onToggle = { vm.playPause() },
                                onOpen = { tab = 1 },
                            )
                        }
                    }
                }
                FloatingNavBar(tab = tab, onSelect = { tab = it })
            }
        }
    }

    // 提示条：iOS 磨砂玻璃胶囊，悬浮在底栏上方，不再用深色 Material 黑框（黑框会压住
    // 玻璃栏、风格割裂）；SnackbarHost 外层不拦截点击，只有胶囊本身可交互
    SnackbarHost(
        hostState = snackbar,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(
                bottom = if (showFloatingBar) {
                    bottomBarInset
                } else {
                    12.dp + with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
                },
            ),
    ) { data ->
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, asmrShape(18.dp))
                .clip(asmrShape(18.dp))
                .background(asmrBarColor())
                .asmrFrost()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                data.visuals.message,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            data.visuals.actionLabel?.let { action ->
                TextButton(onClick = { data.performAction() }) {
                    Text(action, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    }
}

/**
 * 悬浮导航胶囊：iOS 风格的紧凑玻璃底栏（高 54dp，替代 80dp 的 Material NavigationBar）。
 * 柔和投影浮在内容上方；选中项用主色，不再有胶囊指示块。
 */
@Composable
private fun FloatingNavBar(tab: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = asmrShape(24.dp)
    val items = listOf(
        Triple(0, "曲库", Icons.Default.LibraryMusic),
        Triple(1, "播放", Icons.Default.PlayCircle),
        Triple(2, "设置", Icons.Default.Settings),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(asmrBarColor())
            .asmrFrost(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (index, label, icon) ->
            val active = tab == index
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                    tint = if (active) scheme.primary else scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 迷你条的数据壳：在这个子组件里收集播放进度状态，
 * 让每 400ms 一次的 positionMs tick 只重组迷你条，而不是整个 MainScaffold。
 */
@Composable
private fun MiniPlayerCard(
    vm: MainViewModel,
    title: String,
    subtitle: String,
    cover: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val ui by vm.player.collectAsStateWithLifecycle()
    MiniPlayer(
        title = title,
        subtitle = subtitle,
        cover = cover,
        isPlaying = ui.isPlaying,
        progress = if (ui.durationMs > 0) {
            (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f)
        } else 0f,
        onToggle = onToggle,
        onOpen = onOpen,
    )
}

@Composable
private fun MiniPlayer(
    title: String,
    subtitle: String,
    cover: String?,
    isPlaying: Boolean,
    progress: Float,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    // 紧凑玻璃胶囊：单行 + 贴底细进度线，总高约 50dp（旧版约 78dp）
    val scheme = MaterialTheme.colorScheme
    val shape = asmrShape(20.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .clip(shape)
            .background(asmrBarColor())
            .asmrFrost()
            .clickable { onOpen() },
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(asmrShape(8.dp))
                        .background(scheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        AsyncImage(
                            model = java.io.File(cover),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(Icons.Default.LibraryMusic, null, Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "播放/暂停",
                        tint = scheme.primary,
                    )
                }
            }
            // 进度：贴着胶囊底边的 2dp 细线，比独立进度条省一行高度
            Box(Modifier.fillMaxWidth().height(2.dp).background(scheme.surfaceVariant.copy(alpha = 0.6f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(scheme.primary))
            }
        }
    }
}
