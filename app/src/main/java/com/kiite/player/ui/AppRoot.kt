package com.kiite.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.kiite.player.ui.theme.Elevation
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.LocalBottomBarInset
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.LocalHazeState
import com.kiite.player.ui.theme.LocalIsBright
import com.kiite.player.ui.theme.LocalScrollActivityHolder
import com.kiite.player.ui.theme.LocalSharedElementScope
import com.kiite.player.ui.theme.LocalNowPlayingTrack
import com.kiite.player.ui.theme.LocalSharedTransitionScope
import com.kiite.player.ui.theme.LocalTabReselect
import com.kiite.player.ui.theme.Motion
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.sharedNowPlayingCover
import com.kiite.player.ui.theme.tabTransition
import com.kiite.player.ui.theme.asmrEnter
import com.kiite.player.ui.theme.fadeThrough
import com.kiite.player.util.Permissions
import dev.chrisbanes.haze.hazeSource

/**
 * AppRoot 独有的控件尺寸。
 *
 * 这些不进全局令牌：它们是本页特有的控件大小（图标按钮、进度条、封面档位），
 * 同值不同义，硬塞进 Spacing / IconSize 会让令牌表变成一张数字表。
 * 集中在这里的目的是可搜、可一处改；真正的归一化（图标按钮统一到 48dp
 * 触控目标）留给单独一批，因为会改变布局。
 */
private object RootDim {
    val box14 = 14.dp
    val box36 = 36.dp
    val box600 = 600.dp
    val box760 = 760.dp
    val height40 = 40.dp
    val height54 = 54.dp
    val iconBtn40 = 40.dp
    val pad28 = 28.dp
}


/** 超过该宽度按「宽屏」处理：曲库 / 播放页切双栏布局。 */
internal val WideScreenMinWidth = RootDim.box600

/** 左右划切页的触发距离（手指横向位移）。 */
private val SwipeTabThreshold = 72.dp

/** 屏幕左右边缘的「系统返回手势」区：从这一带起手的横滑不是切页。 */
private val SwipeEdgeGuard = 24.dp

/**
 * 左右划切换标签页：曲库 ⇄ 播放 ⇄ 设置。
 *
 * 刻意不抢子元素的手势：纵向滚动列表、横向 chip 行、滑块、图片翻页、台本点击都在更里层，
 * 它们先拿到事件。只要有人消费了（`isConsumed`），或者纵向位移明显大于横向，这一次触摸
 * 就整个放弃 —— 宁可少切一次页，也不能让列表滚不动。
 *
 * 边缘 24dp 内起手也不接管：那是系统的侧滑返回（播放页正是靠它退出全页大图）。
 *
 * 没接管时不消费任何事件，所以点击 / 长按 / 拖拽的既有语义完全不变；接管之后才吃掉
 * 后续事件，避免松手时下层再把它当成一次点击。
 */
private fun Modifier.swipeBetweenTabs(
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
        val trigger = SwipeTabThreshold.toPx()
        val edge = SwipeEdgeGuard.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            var dx = 0f
            var claimed = false
            var abandoned = startX < edge || startX > size.width - edge
            while (!abandoned) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!claimed) {
                    dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    when {
                        // 里层已经接管（列表滚动 / 滑块 / 翻页）
                        change.isConsumed -> abandoned = true
                        // 更接近纵向：这是在滚动，不是切页
                        kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.3f -> abandoned = true
                        kotlin.math.abs(dx) > trigger &&
                            kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.6f -> claimed = true
                    }
                }
                if (abandoned) break
                if (claimed) {
                    dx = change.position.x - down.position.x
                    change.consume()
                }
            }
            if (claimed) {
                if (dx < 0) onSwipeLeft() else onSwipeRight()
            }
        }
    }
}

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
            Modifier.fillMaxSize().padding(RootDim.pad28),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Folder, null, Modifier.size(IconSize.hero), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.lg))
            Text("需要「所有文件访问权限」", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "ASMR 解压包里的台本可能是 txt / lrc / docx / pdf 等任意文件，" +
                    "只授予音频权限读不到它们。请在系统设置里为本应用开启「所有文件访问」。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Spacing.section))
            Button(onClick = onGrant) { Text("去授权") }
            Spacer(Modifier.height(Spacing.sm))
            Button(onClick = onRecheck) { Text("已授权，重新检测") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MainScaffold(vm: MainViewModel) {
    // 用 rememberSaveable 让当前标签在旋转屏幕后保持不变（否则横竖屏切换会跳回曲库）
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 重复点击当前标签 → 该页回到顶部（计数自增，页面自己监听，见 theme/TabReselect.kt）
    var tabReselect by remember { mutableIntStateOf(0) }

    // 切页期间暂停实时磨砂：页面在滑，模糊每帧都要把身后内容重新捕获成纹理再做高斯，
    // 是「切到播放页时顿一下」的最主要来源（gfxinfo 里表现为 Slow issue draw commands 偏高）。
    // 220ms 转场 + 80ms 余量之后恢复，观感上只是转场那一瞬少了层磨砂。
    val scrollActivity = LocalScrollActivityHolder.current
    var lastTab by remember { mutableIntStateOf(tab) }
    LaunchedEffect(tab) {
        if (tab != lastTab) {
            lastTab = tab
            scrollActivity.set(true)
            kotlinx.coroutines.delay(Motion.MEDIUM + 80L)
            scrollActivity.set(false)
        }
    }

    // 返回键/侧滑手势按层级退出：播放/设置 → 曲库（作品详情保留）→ 详情 → 列表 → 退后台。
    // 之前是「有详情就清详情」一条全局规则，从播放页返回时详情页会被静默清掉，不符合直觉。
    // 播放页内部的覆盖态（阅读模式/全页大图/图片等）在更里层组合了各自的 BackHandler，
    // 会先于这里命中——越里层的界面越先退出。
    val hostActivity = LocalContext.current as? android.app.Activity
    val selectedProjectPath by vm.selectedProject.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            tab != 0 -> tab = 0
            selectedProjectPath != null -> vm.selectProject(null)
            else -> hostActivity?.moveTaskToBack(true)
        }
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
    val chromeHidden by vm.chromeHidden.collectAsStateWithLifecycle()
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
            shape = asmrShape(Radius.xl),
            title = { Text("发现新版本 " + info.tag) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        info.notes.ifBlank { "有新版本可用，建议更新。" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (info.prerelease) {
                        Text(
                            "这是测试版",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            },
            confirmButton = {
                // 能拿到真实 Release 资产直链就直接下 APK，否则退回 Release 页面
                val target = info.apkUrl ?: info.url
                androidx.compose.material3.TextButton(onClick = {
                    runCatching {
                        updateCtx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(target),
                            ),
                        )
                    }.onFailure { vm.say("无法打开浏览器：" + target) }
                    vm.dismissUpdate()
                }) { Text(if (info.apkUrl != null) "下载 APK" else "前往下载") }
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
    //
    // 留白按「导航条高度 + 迷你条高度（在场时）」算，**不跟动画中的整条底栏高度走**：
    // 之前量的是整条底栏，迷你条退场的 shrinkVertically 每帧都在改高度，于是
    // LocalBottomBarInset 每帧变一次，曲库与播放两页跟着每帧重组重排 —— 这就是
    // 「切到播放页时顿一下」的来源。现在只在「迷你条在不在」翻转时变一次，且发生在
    // 转场第 0 帧（页面还没显形），看不见跳变。
    var navBarPx by remember { mutableIntStateOf(0) }
    var miniBarPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val immersivePlayer = settings.immersive && tab == 1 && !videoFull && !videoMode
    val showFloatingBar = !videoFull && !videoMode && !chromeHidden && !(settings.immersive && tab == 1)
    val miniBarShown = tab != 1 && current != null
    val gapPx = with(density) { Spacing.sm.roundToPx() }
    val barHeightPx = navBarPx + gapPx + if (miniBarShown) miniBarPx + gapPx else 0
    val bottomBarInset = if (showFloatingBar) {
        with(density) { barHeightPx.toDp() } + RootDim.box14
    } else {
        0.dp
    }

    // 沉浸播放：整页交给封面和台词，系统栏也藏起来（边缘侧滑可临时唤出）。
    // 视频全屏自己管理系统栏，这里让位，避免两边打架。
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(immersivePlayer, videoFull, videoMode) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null && !videoFull && !videoMode) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersivePlayer) controller.hide(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            ) else controller.show(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
        }
    }

    // 共享元素转场的作用域：MiniPlayer 的封面和播放页的封面靠它配对。
    // 放在这一层（而不是页面内）是因为两个封面分属不同的 tab 容器，
    // 只有共同的祖先才能在同一帧里看到它们。
    SharedTransitionLayout(Modifier.fillMaxSize()) {
    CompositionLocalProvider(
        LocalBottomBarInset provides bottomBarInset,
        LocalTabReselect provides tabReselect,
        LocalSharedTransitionScope provides this@SharedTransitionLayout,
    ) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!videoFull && !videoMode && !chromeHidden && !immersivePlayer) Column {
            // 自定义轻量顶栏：M3 TopAppBar 内部写死 64dp 行高 + 状态栏内边距，
            // 外部高度压不下去（会把标题裁没）。这里自己排一行 40dp 的内容。
            Surface(
                color = if (LocalEditorial.current) Color.Transparent else asmrBarColor(),
                modifier = Modifier.asmrFrost(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(RootDim.height40)
                        .padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (LocalIsBright.current) {
                        Box(
                            Modifier
                                .size(Spacing.xxl)
                                .clip(asmrShape(Radius.sm))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = com.kiite.player.R.drawable.kiite_logo,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(Spacing.gap10))
                    }
                    Column {
                        Text(
                            when (tab) {
                                0 -> "曲库"
                                1 -> "正在播放"
                                else -> "设置"
                            },
                            style = MaterialTheme.typography.titleMedium,
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
                    EditorialTicks(Modifier.padding(start = Spacing.gap10))
                }
            }
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
                    .let { if (wide) it.fillMaxSize() else it.fillMaxHeight().widthIn(max = RootDim.box760) }
                    .hazeSource(LocalHazeState.current)
                    // 左右划切页：视频全屏时不接管，那里自己的手势优先级更高
                    .swipeBetweenTabs(
                        enabled = !videoFull && !videoMode,
                        onSwipeLeft = { if (tab < 2) tab += 1 },
                        onSwipeRight = { if (tab > 0) tab -= 1 },
                    ),
            ) {
            // 标签页切换：按方向轻微滑动 + 淡入淡出，页面之间有连贯感
            AnimatedContent(
                targetState = tab,
                transitionSpec = { tabTransition(forward = targetState > initialState) },
                label = "tabContent",
            ) { page ->
                // AnimatedContentScope 本身实现了 AnimatedVisibilityScope。
                // 必须在这里存一份：进到内层 Box 之后 `this` 就变成 BoxScope 了。
                val pageScope = this
                // 播放页是固定布局（底部有控制条），底栏悬浮时给它让出空间；
                // 曲库 / 设置是长列表，内容直接延伸到悬浮栏后面，滚动留白由各自列表处理
                Box(
                    Modifier.fillMaxSize().then(
                        if (page == 1) Modifier.padding(bottom = LocalBottomBarInset.current) else Modifier,
                    ),
                ) {
                    CompositionLocalProvider(
                        LocalSharedElementScope provides pageScope,
                        // 共享封面的 key 带 trackId：换歌后两端 key 不相等，自动降级成普通进出场，
                        // 不会出现「新封面从上一首的位置飞进来」
                        LocalNowPlayingTrack provides current?.path,
                    ) {
                        when (page) {
                            0 -> LibraryScreen(vm)
                            1 -> PlayerScreen(vm = vm, onExit = { tab = 0 })
                            else -> SettingsScreen(vm)
                        }
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
                            Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.section),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            busyTasks.forEach { (_, label) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(Modifier.size(Spacing.lg), strokeWidth = Spacing.xxs)
                                    Spacer(Modifier.width(Spacing.md))
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (busyTasks.containsKey("scan")) {
                                scanProgress?.let { p ->
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(
                                        "已扫 " + p.dirsVisited + " 个目录 · 音频 " + p.tracks + " · 台本 " + p.scripts,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(Spacing.gap10))
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
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // 迷你条不再用 `if (tab != 1)` 整块摘掉：直接移除会让它在切到播放页的
                // 那一帧就消失，共享元素没有「两边同时在场」的窗口，只能得到硬切。
                // 改成常驻 AnimatedVisibility 后，切页动画期间它仍在组合中，封面才能飞过去。
                AnimatedVisibility(
                    visible = miniBarShown,
                    enter = asmrEnter(),
                    // 只淡出，不做高度收缩：收缩会让这条玻璃栏每帧重新测量、重新模糊，
                    // 播放页的底部留白也会跟着每帧变 —— 用户看到的「顿一下」就是它。
                    // 退出时长仍对齐 tab 切换的 220ms，否则迷你条先消失会提前打断共享元素动画。
                    exit = fadeOut(Motion.exit(Motion.MEDIUM)),
                ) {
                    current?.let { track ->
                        // 进度状态在这个子组件里收集：tick 只重组迷你条本身
                        CompositionLocalProvider(
                            LocalSharedElementScope provides this,
                            LocalNowPlayingTrack provides track.path,
                        ) {
                            Box(Modifier.onSizeChanged { miniBarPx = it.height }) {
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
                }
                Box(Modifier.onSizeChanged { navBarPx = it.height }) {
                    FloatingNavBar(
                        tab = tab,
                        onSelect = { index ->
                            // 点已选中的标签不切页，改为「回到该页顶部」
                            if (index == tab) tabReselect++ else tab = index
                        },
                    )
                }
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
            .padding(horizontal = Spacing.lg)
            .padding(
                bottom = if (showFloatingBar) {
                    bottomBarInset
                } else {
                    Spacing.md + with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
                },
            ),
    ) { data ->
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(Elevation.raised, asmrShape(Radius.xl))
                .clip(asmrShape(Radius.xl))
                .background(asmrBarColor())
                .asmrFrost()
                .padding(horizontal = Spacing.lg, vertical = Spacing.gap10),
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
    }
}

/**
 * 悬浮导航胶囊：iOS 风格的紧凑玻璃底栏（高 54dp，替代 80dp 的 Material NavigationBar）。
 * 柔和投影浮在内容上方；选中项用主色，不再有胶囊指示块。
 */
@Composable
private fun FloatingNavBar(tab: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = asmrShape(Radius.xxl)
    val items = listOf(
        Triple(0, "曲库", Icons.Default.LibraryMusic),
        Triple(1, "播放", Icons.Default.PlayCircle),
        Triple(2, "设置", Icons.Default.Settings),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(RootDim.height54)
            .shadow(Elevation.floating, shape)
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
                    modifier = Modifier.size(IconSize.md),
                    tint = if (active) scheme.primary else scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xxs))
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
    val shape = asmrShape(Radius.xl)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(Elevation.raised, shape)
            .clip(shape)
            .background(asmrBarColor())
            .asmrFrost()
            .clickable { onOpen() },
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.gap10, end = Spacing.xs, top = Spacing.itemGap, bottom = Spacing.itemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(RootDim.box36)
                        .clip(asmrShape(Radius.xs))
                        .background(scheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        AsyncImage(
                            model = java.io.File(cover),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // 共享元素的 key 与播放页封面一致，两边配对后封面会「飞」过去
                            modifier = Modifier.fillMaxSize().sharedNowPlayingCover(),
                        )
                    } else {
                        Icon(Icons.Default.LibraryMusic, null, Modifier.size(IconSize.sm))
                    }
                }
                Spacer(Modifier.width(Spacing.gap10))
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
                IconButton(onClick = onToggle, modifier = Modifier.size(RootDim.iconBtn40)) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = { fadeThrough() },
                        label = "miniPlayPause",
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playing) "暂停" else "播放",
                            tint = scheme.primary,
                        )
                    }
                }
            }
            // 进度：贴着胶囊底边的 2dp 细线，比独立进度条省一行高度
            Box(Modifier.fillMaxWidth().height(Spacing.xxs).background(scheme.surfaceVariant.copy(alpha = 0.6f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(Spacing.xxs).background(scheme.primary))
            }
        }
    }
}
