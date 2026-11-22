package com.kiite.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.kiite.player.ui.components.ThemeGlyph
import com.kiite.player.ui.theme.Elevation
import com.kiite.player.ui.theme.concentricRadius
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.LocalBottomBarInset
import com.kiite.player.ui.theme.LocalTopBarMetrics
import com.kiite.player.ui.theme.TopBarMetrics
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.LocalHazeState
import com.kiite.player.ui.theme.LocalIsBright
import com.kiite.player.ui.theme.LocalScrollActivityHolder
import com.kiite.player.ui.theme.LocalSharedElementScope
import com.kiite.player.ui.theme.AsmrMiuixTheme
import com.kiite.player.ui.theme.LocalCoverFlight
import com.kiite.player.ui.theme.LocalBarCoverHidden
import com.kiite.player.ui.theme.LocalTopBarScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.kiite.player.ui.theme.LocalNowPlayingTrack
import com.kiite.player.ui.theme.LocalSharedTransitionScope
import com.kiite.player.ui.theme.LocalTabReselect
import com.kiite.player.ui.theme.Motion
import com.kiite.player.ui.theme.pressScale
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.sharedNowPlayingCover
import com.kiite.player.ui.theme.tabTransition
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
    val box12 = 12.dp
    val box36 = 36.dp
    val box600 = 600.dp
    val box760 = 760.dp
    val height40 = 40.dp
    val height54 = 54.dp
    val iconBtn40 = 40.dp
    val pad28 = 28.dp
    /** 播放页走带那一块的高度（进度条 + 走带行），提示条要浮在它上面。 */
    val playerControlsReserve = 132.dp

    /** 导航胶囊里选中块与胶囊边缘之间的空隙：太小像贴边，太大像另一颗按钮。 */
    val pillPad = 4.dp
}


/** 超过该宽度按「宽屏」处理：曲库 / 播放页切双栏布局。 */
internal val WideScreenMinWidth = RootDim.box600

/** 左右划切页的触发距离（手指横向位移）。 */
private val SwipeTabThreshold = 72.dp

// 页面下标：0 首页 / 1 曲库 / 2 播放页（虚拟，不进底栏）/ 3 设置。
private const val TAB_HOME = 0
private const val TAB_LIBRARY = 1
private const val TAB_PLAYER = 2
private const val TAB_SETTINGS = 3

/** 屏幕左右边缘的「系统返回手势」区：从这一带起手的横滑不是切页。 */
private val SwipeEdgeGuard = 24.dp

/**
 * 从迷你条往上拖多远才算「把播放页拉出来」。
 *
 * 44dp 是拇指一次自然上推的量级：低于 24dp 会和「只是按住不放」混淆，
 * 高于 72dp 又变成「得刻意甩一下」。
 */
private val LiftThreshold = 44.dp

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

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
private fun MainScaffold(vm: MainViewModel) {
    // 用 rememberSaveable 让当前标签在旋转屏幕后保持不变（否则横竖屏切换会跳回曲库）
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 重复点击当前标签 → 该页回到顶部（计数自增，页面自己监听，见 theme/TabReselect.kt）
    var tabReselect by remember { mutableIntStateOf(0) }
    // 首页点头像 / 昵称 → 切到设置并直接打开「个人资料」；设置页处理完就复位
    var settingsProfileRequest by remember { mutableStateOf(false) }

    // 播放页是「盖在主页上面的一层」，不是第三个标签页：
    // 它开着的时候曲库/设置**照样在下面活着**（封面长出来的那一瞬，背后得能看见主页，
    // 否则「迷你条长大成卡片」就变成在半透明黑底上长大，连贯感立刻没了）。
    //
    // 三档落点：收在下面（playerOpen = false，主页）、半屏（折叠卡片）、全屏（展开）。
    // 「上次停在哪一档」存在 DataStore（settings.playerStop），杀进程重进也还在；
    // 点迷你条时按它直接回到那一档，而不是每次都从半屏重来。
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val playerExpandedNow by vm.playerExpanded.collectAsStateWithLifecycle()
    // 迷你条在窗口里的矩形：播放页那一层从这一格「原地长出来」（见 PlayerScreen 的 morph），
    // 而不是从屏幕下沿整页推上来 —— 后者和「迷你条消失 + 新页面滑入」读起来就是两件事。
    var miniBarBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // 关闭请求：顶栏那颗 ⌄ 不能直接 `playerOpen = false`（那是硬切），
    // 得让播放页自己跑完「收下去」的动画再退出 —— 用一个自增的令牌传进去。
    var closeRequest by remember { mutableIntStateOf(0) }

    // ── 上滑（onLift）：这条手势是**拖拽驱动**的 ──────────────────────────────
    //
    // 不是「松手之后才开始长」：手指往上一过阈值，播放页就以「迷你条那一格」的样子立起来，
    // 之后每一格拖动都直接把卡片往上推、封面同时往卡片封面那格飞（见 PlayerScreen 的 morph）。
    // 三个量：
    // - liftPx：这次拖拽累计的位移（负 = 往上），只有拖的时候变；
    // - lifting：手指还按着（松手后交给补间）；
    // - liftDriven：这一次是从迷你条拖开的 —— 封面走「飞」，不是共享元素那条老路。
    // - liftCancel：拖回起点附近的取消令牌，让播放页自己退回去再 onExit。
    var liftPx by remember { mutableFloatStateOf(0f) }
    var lifting by remember { mutableStateOf(false) }
    var liftDriven by remember { mutableStateOf(false) }
    var liftCancel by remember { mutableIntStateOf(0) }

    // 切页期间暂停实时磨砂：页面在滑，模糊每帧都要把身后内容重新捕获成纹理再做高斯，
    // 是「切到播放页时顿一下」的最主要来源（gfxinfo 里表现为 Slow issue draw commands 偏高）。
    //
    // 这条开关现在**只在上滑的拖动阶段**置位：拖的那一下内容确实在每帧变，值得让位；
    // 松手后的补间立刻恢复，不再像以前那样把「无磨砂」拉满 220ms + 80ms ——
    // 那正是用户看到的「上滑进半屏、毛玻璃闪一下」：整块退回纯色，再闪回磨砂。
    // 磨砂本身也不再因为这一位而换半径（见 AsmrCard 的 asmrFrost），所以就算置位，
    // 观感上也只是「不再每帧重算」，不是「糊消失」。
    //
    // 顺带维护「封面飞行窗口」：进出播放页这一段里，迷你条 / 播放页封面槽位的灰色底板
    // 要淡出（否则图片在天上飞、灰方块留在原地，见 LocalCoverFlight 的注释）。
    // 时长取共享元素的 bounds 变换长度 + 余量；快速连续切页时旧效应会被取消，
    // 由下一次效应接手把它复位（`if (coverFlying)` 不看方向，任何一次切页都会收尾）。
    val scrollActivity = LocalScrollActivityHolder.current
    var lastTab by remember { mutableIntStateOf(tab) }
    var coverFlying by remember { mutableStateOf(false) }
    var wasPlayerOpen by remember { mutableStateOf(false) }
    // 曲库里的二级页面（下载页 / 作品页 / asmr.one 选文件）是否打开：
    // 这些页面里是 WebView，左右划切页会被它抢事件（右滑直接切到播放页）。
    // 由 LibraryScreen 自己往上汇报（见 LibraryScreen 的 onOverlayChange）。
    var libraryOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(lifting, playerOpen) {
        // 只有手指还按着、正在往上拖的这一段需要让位
        scrollActivity.set(lifting)
    }

    LaunchedEffect(tab, playerOpen) {
        val from = lastTab
        lastTab = tab
        val tabChanged = tab != from
        val playerChanged = playerOpen != wasPlayerOpen
        wasPlayerOpen = playerOpen
        if (!tabChanged && !playerChanged) return@LaunchedEffect
        // 时长必须乘上**系统**动画缩放：Compose 的 tween 会被系统缩放拉长，而这个 delay 不会。
        // 以前直接用毫秒常量，把系统动画调到 2 倍以上时封面底板会提前回来 —— 慢放抓帧时
        // 这个偏差被放大到「飞了一半就露灰底」。
        // Motion.ms() 里面已经乘过「简化动效」开关，这里只补系统那一份。
        val stretch = coroutineContext[androidx.compose.ui.MotionDurationScale]?.scaleFactor ?: 1f
        if (playerChanged) coverFlying = true
        // `if (coverFlying)` 不看这次是哪种变化：上一次效应可能在 delay 中途被取消
        // （比如刚退出播放页又立刻切标签），收尾必须由下一次效应接手，否则这条
        // 封面底板会永久保持透明。
        if (coverFlying) {
            kotlinx.coroutines.delay((Motion.ms(Motion.MEDIUM + 80) * stretch).toLong())
            coverFlying = false
        }
    }

    // 返回键/侧滑手势按层级退出：播放页 → 设置 → 曲库（作品详情保留）→ 详情 → 列表 → 退后台。
    // 播放页自己注册的 BackHandler 会先命中（它在更里层、更晚注册），这里只兜住
    // 「播放页已经关了」之后的层级。
    val hostActivity = LocalContext.current as? android.app.Activity
    val selectedProjectPath by vm.selectedProject.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = !playerOpen) {
        when {
            tab != TAB_HOME -> tab = TAB_HOME
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
    // 台本全屏阅读时顶部由 ReadModeBar 自己管；外层这颗 ⌄ 不能再显示，
    // 否则会和阅读条左侧的退出箭头叠在一起。
    val readerMode by vm.readerMode.collectAsStateWithLifecycle()
    var autoChangelog by remember { mutableStateOf(false) }
    // 首启引导：`onboardingSeen == false` 表示「盘已经读完、确实没看过」。
    // 初值是 null（还没读到盘），此时绝不能弹 —— 否则老用户每次冷启动都被盖一帧。
    var onboardingOpen by remember { mutableStateOf(false) }
    // 只补条款页（老用户已经看过功能页，引导内容升了一版）
    var onboardingLegalOnly by remember { mutableStateOf(false) }
    // 「设置 → 关于 → 功能介绍」手动重看：和首启走同一层，只是不写盘
    val onboardingReplay by vm.onboardingReplay.collectAsStateWithLifecycle()
    LaunchedEffect(onboardingReplay) {
        if (onboardingReplay) {
            onboardingLegalOnly = false
            onboardingOpen = true
            vm.consumeOnboardingReplay()
        }
    }
    // 两档触发，别合成一个 `== false`：
    // - 没走完过（新安装 / 中途杀进程）→ 功能页 + 条款页都走；
    // - 走完过、但记录的版本落后 → 只补条款页。这里要是也弹整段引导，
    //   每次给引导加一步都会把老用户按回第 1 页，等于「更新一次看一遍广告」。
    LaunchedEffect(settings.onboardingSeen, settings.onboardingVersion) {
        // null = 盘还没读到。这一帧什么都不做，等真正的值来。
        val seen = settings.onboardingSeen ?: return@LaunchedEffect
        when {
            !seen -> {
                onboardingLegalOnly = false
                onboardingOpen = true
            }
            settings.onboardingVersion < ONBOARDING_GUIDE_VERSION -> {
                onboardingLegalOnly = true
                onboardingOpen = true
            }
        }
    }
    LaunchedEffect(settings.seenVersion, settings.onboardingSeen) {
        when {
            // 首启引导期间什么都不做：既不叠更新日志，也不记「已看过这一版」——
            // 否则升级上来的用户会连这一版的更新日志都看不到。等引导关掉，
            // onboardingSeen 变 true，这个效应会再跑一次，日志该弹就弹。
            settings.onboardingSeen == false -> Unit
            settings.seenVersion != null && settings.seenVersion != AppInfo.VERSION_NAME -> {
                autoChangelog = true
            }
            settings.seenVersion == null -> {
                // 首次记录，不打扰
                vm.markVersionSeen(AppInfo.VERSION_NAME)
            }
        }
    }
    // 引导在场时不叠日志弹窗
    if (autoChangelog && !onboardingOpen) {
        ChangelogDialog {
            autoChangelog = false
            vm.markVersionSeen(AppInfo.VERSION_NAME)
        }
    }

    // 播放页正在放视频时，用视频播放器自己的顶栏/工具条，收起 App 外壳的顶栏与底部导航
    val videoMode = playerOpen && vm.currentIsVideo()
    // 离开播放页时复位视频全屏。
    // 不能放在 PlayerScreen 里：进全屏要隐藏顶栏/底栏，Scaffold 重排会让它重进，
    // onDispose/LaunchedEffect 立刻把状态改回 false，导致全屏永远进不去。
    LaunchedEffect(playerOpen) {
        if (!playerOpen) {
            vm.setVideoFullscreen(false)
            // 播放页折叠态会把 App 的顶栏/底栏一起藏掉（沉浸封面）。收起来时必须还原，
            // 否则曲库会留在一个没有导航栏的状态里 —— 那个状态下没有出口。
            vm.setChromeHidden(false)
        }
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
    // 顶栏是浮层，但仍要避开系统栏：safeDrawing 的 Top 同时覆盖状态栏与刘海/挖孔，
    // 以前只避 statusBars，异性屏（挖孔在顶部或横屏在左侧）就会压住顶栏内容。
    // 这段 inset 也下发给各页面，让它们自己算 contentPadding(top = 状态栏 + 栏高)。
    val topBarInsets = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )
    val topBarMetrics = TopBarMetrics(
        safeTop = with(density) { topBarInsets.getTop(this).toDp() },
        // 顶栏内容行 + 一点呼吸：内容不该贴着磨砂条下沿
        bar = RootDim.height40 + Spacing.sm,
    )
    val immersivePlayer = settings.immersive && playerOpen && !videoFull && !videoMode
    // 播放页整页都不显示悬浮底栏（导航胶囊 + 迷你条）。
    //
    // 原来只在「沉浸播放」时才让位，于是展开态（全屏播放页）里那颗导航胶囊仍浮在
    // 走带上面 —— 真机截图里进度条和播放键被它压掉一条边，就是「部分被遮挡」。
    // 播放页的出口已经改成左上角返回键，胶囊在这里没有意义，直接不给它出场。
    // 二级页（DLsite 登录 / 下载页、asmr.one 选择页）是整屏 WebView：底栏留着会压住页面底部
    // 的操作条，也挡住它自己那颗「完成」，所以整条一起让位（不只是导航胶囊）。
    // 拖拽期间这条栏必须留在组合里：手势的 pointerInput 就挂在迷你条上，
    // 一退出组合，drag 回调当场被取消 —— 卡片会僵在半路，松手也收不到。
    val showFloatingBar = (!videoFull && !videoMode && !chromeHidden && !playerOpen && !libraryOverlay) || lifting
    val miniBarShown = (!playerOpen && !libraryOverlay && current != null) || (lifting && current != null)
    val gapPx = with(density) { Spacing.sm.roundToPx() }
    val barHeightPx = navBarPx + gapPx + if (miniBarShown) miniBarPx + gapPx else 0
    // 悬浮栏的实际占位 = 栏本身 + 系统手势条 + 栏与内容之间的视觉间隙。
    //
    // 旧算法是 `栏高 + RootDim.box14`，把「系统手势条」用 14dp 顶替了：手势条在多数机型上
    // 是 24dp，于是播放页内容的下边界正好探进导航胶囊顶部 10dp —— 走带中间那颗大播放键
    // 就是被胶囊压掉一条边的那颗。现在按真实 inset 算，再留 Spacing.md 的空气。
    //
    // 这个值只在 `miniBarShown` 翻转时变一次，不跟动画走（见上面的注释）。
    val navBarsInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val bottomBarInset = if (showFloatingBar) {
        with(density) { barHeightPx.toDp() } + navBarsInset + Spacing.md
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
    // 顶栏收起进度：页面滚动时写进来，顶栏自己读（见 theme/TopBarScroll.kt）
    val topBarScroll = remember { com.kiite.player.ui.theme.TopBarScroll() }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
    CompositionLocalProvider(
        LocalBottomBarInset provides bottomBarInset,
        LocalTopBarMetrics provides topBarMetrics,
        LocalTabReselect provides tabReselect,
        LocalTopBarScroll provides topBarScroll,
        LocalSharedTransitionScope provides this@SharedTransitionLayout,
    ) {
    Box(Modifier.fillMaxSize()) {
    // 顶栏**不再占 Scaffold 的布局位**：它是浮在内容之上的一层（和悬浮底栏对称）。
    // 以前 Scaffold 会把整个内容区压到 topBar 下面，滚动内容永远滚不进顶栏后面，
    // 半透明磨砂条底下只有背景 —— 这就是「上滑了却看不到毛玻璃」的结构原因。
    // 现在各页面自己用 LocalTopBarMetrics 加 contentPadding(top = 状态栏 + 栏高)，
    // 首屏不被挡，滚动后内容自然穿过磨砂条被虚化。
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    // 左右划切页：视频全屏时不接管，那里自己的手势优先级更高；
                    // 曲库二级页（WebView）打开时也不接管，否则网页里的右滑会被当成「切到播放页」
                    .swipeBetweenTabs(
                        // 播放页已经不在标签页体系里（它是盖在上面的一层），
                        // 左右划只在曲库与设置之间生效
                        enabled = !videoFull && !videoMode && !libraryOverlay && !playerOpen,
                        onSwipeLeft = {
                            tab = when (tab) {
                                TAB_HOME -> TAB_LIBRARY
                                TAB_LIBRARY -> TAB_SETTINGS
                                else -> tab
                            }
                        },
                        onSwipeRight = {
                            tab = when (tab) {
                                TAB_SETTINGS -> TAB_LIBRARY
                                TAB_LIBRARY -> TAB_HOME
                                else -> tab
                            }
                        },
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
                // 曲库 / 设置是长列表，内容直接延伸到悬浮栏后面，滚动留白由各自列表处理
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalSharedElementScope provides pageScope,
                        // 共享封面的 key 带 trackId：换歌后两端 key 不相等，自动降级成普通进出场，
                        // 不会出现「新封面从上一首的位置飞进来」
                        LocalNowPlayingTrack provides current?.path,
                        LocalCoverFlight provides coverFlying,
                    ) {
                        when (page) {
                            // 首页也跑在 Miuix 主题里：卡片、按压反馈、图标块与曲库/设置同一套
                            TAB_HOME -> AsmrMiuixTheme {
                                HomeScreen(
                                    vm,
                                    onOpenSettings = { tab = TAB_SETTINGS },
                                    onOpenLibrary = { tab = TAB_LIBRARY },
                                    // 点头像 / 昵称：切到设置并直接落在「个人资料」
                                    onOpenProfile = {
                                        settingsProfileRequest = true
                                        tab = TAB_SETTINGS
                                    },
                                )
                            }
                            TAB_LIBRARY -> LibraryScreen(vm, onOverlayChange = { libraryOverlay = it })
                            else -> SettingsScreen(
                                vm,
                                onOverlayChange = { libraryOverlay = it },
                                requestProfile = settingsProfileRequest,
                                onProfileHandled = { settingsProfileRequest = false },
                            )
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

    // 悬浮磨砂顶栏：与悬浮底栏对称，浮在内容之上，不再吃 Scaffold 的布局位。
    // 页面自己用 LocalTopBarMetrics 让出首屏，滚动后内容就能穿过这条半透明磨砂条。
    // 进出场走 AnimatedVisibility：以前是 `if`，折叠播放页 / 切沉浸时顶栏「啪」地消失，
    // 和卡片慢慢收起的节奏对不上，用户读成「切换不连贯」。
    AnimatedVisibility(
        visible = !videoFull && !videoMode && !chromeHidden && !immersivePlayer && !libraryOverlay,
        enter = fadeIn(Motion.enter(Motion.SHORT)) +
            slideInVertically(Motion.enter(Motion.MEDIUM)) { -it },
        exit = fadeOut(Motion.exit(Motion.FLASH)) +
            slideOutVertically(Motion.exit(Motion.SHORT)) { -it },
        // 播放页整层是后组合的（见下面 `if (playerOpen)`），会把这条栏盖住：
        // 展开态的磨砂栏 + 返回键就整个被页面底色吃掉（截图里全屏播放页顶上只剩一片空白）。
        // 用 zIndex 把栏抬到页面之上，顺序仍然「页面 < 栏 < 提示条」。
        modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
    ) {
        FloatingTopBar(
            // 展开态顶栏要写「正在播放」而不是「曲库」：这时的页面是全屏播放页。
            // 必须带上 playerOpen：收下去的那 200ms 里 playerExpandedNow 还是 true，
            // 只看它会让主页顶栏先写着「正在播放」再翻回「曲库」，读成一次闪烁。
            tab = if (playerOpen && playerExpandedNow) TAB_PLAYER else tab,
            vm = vm,
            themeMode = settings.themeMode,
            insets = topBarInsets,
            // 播放页的返回键改由页面自己画（见 PlayerScreen 折叠态那条注释）。
            // 栏里再画一颗就是两颗箭头叠在同一格上，而且栏会被播放页整块盖住 ——
            // 位置对、看得见、按得着三件事只能由页面保证。
            onBack = null,
        )
    }
    }

    // 悬浮玻璃底栏（迷你播放条 + 导航胶囊）：浮在内容上方，
    // 页面从它后面穿过；外层容器没有点击处理，卡片之间的空隙不影响后方内容的点击
    // 整条底栏也走 AnimatedVisibility：导航胶囊以前跟 `if` 一起硬切，折叠时「啪」地
    // 消失；现在淡出 + 下沉，和顶栏/卡片同一节奏。
    AnimatedVisibility(
        visible = showFloatingBar,
        enter = fadeIn(Motion.enter(Motion.SHORT)) +
            slideInVertically(Motion.enter(Motion.MEDIUM)) { it / 4 },
        exit = fadeOut(Motion.exit(Motion.FLASH)) +
            slideOutVertically(Motion.exit(Motion.SHORT)) { it / 4 },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Box(
            Modifier
                .fillMaxWidth(),
        ) {
            // 迷你条是封面飞行的**起点**，同样要知道「封面正在飞」才好把灰色底板藏起来；
            // 它不在 AnimatedContent 里，所以这里为整条悬浮栏单独下发一次。
            CompositionLocalProvider(
                LocalCoverFlight provides coverFlying,
                // 拖拽上滑这一段封面由播放页自己那颗「飞」（见 PlayerScreen 的 FlyingCover），
                // 条里这颗必须让位，否则同一张封面会一静一动两个。
                LocalBarCoverHidden provides (lifting || liftDriven),
            ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // 迷你条的进出场：只做「淡 + 下沉」，不做尺寸动画。
                //
                // 不用 expandVertically / shrinkVertically：它们会**裁掉**超出边界的东西，
                // 而阴影整个画在边界外 —— 进场最后几帧只剩一条硬边，看起来就是「阴影突然
                // 出现」。淡 + 位移不改尺寸，阴影跟着一起淡，没有硬边。
                //
                // 必须是**真正的**进出场动画，不能自己拿 animateFloatAsState 驱动 alpha：
                // 封面的共享元素要「一端退场、另一端进场」才会配对。以前这里把 visible 写成
                // `miniBarShown || miniSink > 0.001f`，靠下沉没结束把迷你条硬撑在场 ——
                // 进播放页时它从头到尾没退场过，于是两个封面同时在场。
                // SharedTransitionLayout 的配对条件是「同一个 key 有两个 state、且其中一个
                // 正在成为可见目标」（SharedElement.kt: states.size > 1 && hasVisibleContent），
                // 两边都还活着就等于没有交接，它只能各画各的 —— 封面于是「跳」过去，
                // 全程没有中间尺寸。
                AnimatedVisibility(
                    visible = miniBarShown,
                    enter = fadeIn(Motion.enter(Motion.MEDIUM)) +
                        slideInVertically(Motion.enter(Motion.MEDIUM)) { it / 4 },
                    // 透明度只用 FLASH（90ms），位移留满 MEDIUM（220ms）。
                    //
                    // 两个时长故意不同：位移决定**这个节点在组合里活多久**，共享元素的配对
                    // 靠它撑着（beta8 的教训：节点提前消失，封面就从「飞」退化成「跳」）；
                    // 而透明度决定**眼睛还看不看得见它**。让位要快、拆台要慢 —— 90ms 后它已经
                    // 完全透明，播放页的走带这时才开始落位，中间不会出现「灰底幽灵条压在
                    // 播放键上」那一帧；节点却仍然挂到 220ms，配对不受影响。
                    exit = fadeOut(Motion.exit(Motion.FLASH)) +
                        slideOutVertically(Motion.exit(Motion.MEDIUM)) { it / 4 },
                ) {
                    current?.let { track ->
                        // 进度状态在这个子组件里收集：tick 只重组迷你条本身
                        CompositionLocalProvider(
                            LocalSharedElementScope provides this,
                            LocalNowPlayingTrack provides track.path,
                        ) {
                            // 高度只记最大值：退场时内容会被移出布局，跟着改会把
                            // LocalBottomBarInset 拖着一帧一帧变（那是之前「顿一下」的根因）
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { if (it.height > miniBarPx) miniBarPx = it.height },
                            ) {
                                // 记下这一格在窗口里的矩形：播放页那一层要靠它「原地长出来」
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { c ->
                                            val r = c.boundsInRoot()
                                            if (r.height > 0f) miniBarBounds = r
                                        },
                                ) {
                                    MiniPlayerCard(
                                        vm = vm,
                                        title = track.baseName,
                                        subtitle = track.folderName,
                                        cover = coverPath,
                                        onToggle = { vm.playPause() },
                                        // 点一下：直接回到上次退出时的那一档（半屏或全屏），落点在 DataStore 里
                                        onOpen = {
                                            vm.setPlayerExpanded(settings.playerStop == 2)
                                            playerOpen = true
                                        },
                                        // 往上拖：过阈值的那一刻就把播放页立起来（此时它长得和这条迷你条一模一样），
                                        // 之后每一格位移都由下面两个回调直接驱动它的 morph —— 拖多少长多少。
                                        onLiftStart = {
                                            liftPx = 0f
                                            liftDriven = true
                                            lifting = true
                                            vm.setPlayerExpanded(false)
                                            playerOpen = true
                                        },
                                        onLiftDrag = { dy -> liftPx = dy },
                                        onLiftEnd = {
                                            lifting = false
                                            liftPx = 0f
                                        },
                                        onLiftCancel = {
                                            // 拖回原位：让播放页自己退回迷你条那一格再退出，不能硬切
                                            lifting = false
                                            liftPx = 0f
                                            liftCancel++
                                        },
                                        // 兜底：没有走拖拽流程的调用方（预览/别处）仍按老语义打开
                                        onLift = {
                                            vm.setPlayerExpanded(false)
                                            playerOpen = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                // 导航胶囊只淡出、不拆掉：拖拽上滑那一段整条栏要留在组合里（手势还挂在
                // 迷你条上），而胶囊一旦退出布局，上面那颗迷你条会当场往下跳一格 ——
                // 播放页那边记的「条的矩形」是跳之前的，卡片就从错位的地方长出来。
                val pillAlpha = androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (!playerOpen && !libraryOverlay) 1f else 0f,
                    animationSpec = Motion.standard(Motion.FLASH),
                    label = "navPillAlpha",
                )
                Box(
                    Modifier
                        .onSizeChanged { navBarPx = it.height }
                        .graphicsLayer { alpha = pillAlpha.value },
                ) {
                    FloatingNavBar(
                        tab = tab,
                        onSelect = { slot ->
                            val page = pageOf(slot)
                            // 点已选中的标签不切页，改为「回到该页顶部」
                            if (page == tab) tabReselect++ else tab = page
                        },
                    )
                }
            }
            }
        }
    }

    // 播放页这一层：盖在曲库/设置**上面**，下面那页不作废 —— 封面长出来的那几帧
    // 背后得看得见主页（否则就变成在半透明黑底上长大，「连贯」立刻没了）。
    // 进出场全由页面内部驱动（morph + dismissed），所以这里用 `if` 而不是 AnimatedVisibility。
    if (playerOpen) {
        // 播放页也包同一层 Miuix 主题：卡片材质与按压反馈跟其它页一致
        AsmrMiuixTheme {
    PlayerScreen(
                vm = vm,
                miniBarBounds = miniBarBounds,
                closeSignal = closeRequest,
                liftPx = { liftPx },
                liftActive = lifting,
                liftDriven = liftDriven,
                liftCancel = liftCancel,
                onExit = {
                    vm.leavePlayer(playerExpandedNow)
                    playerOpen = false
                    // 这一次的拖拽状态用完即弃：下次从点/拖进来都要从头算
                    liftDriven = false
                    lifting = false
                    liftPx = 0f
                },
                // 变形头几帧的「原样」：和主页那颗迷你条长得一模一样，
                // 于是「条 → 卡片」中间不会出现「条消失、卡片已在半空」的空档。
                miniBar = {
                    current?.let { track ->
                        CompositionLocalProvider(LocalCoverFlight provides true) {
                            MiniPlayerCard(
                                vm = vm,
                                title = track.baseName,
                                subtitle = track.folderName,
                                cover = coverPath,
                                onToggle = {},
                                onOpen = {},
                                onLift = {},
                            )
                        }
                    }
                },
            )
        }
    }

    // 播放页的出口 ⌄：单独一层，画在页面之上。
    //
    // 为什么不在页面里画：页面那条「卡片上沿补色带」是在自己的绘制相里补的，
    // 展开态会把画在它前面的按钮整块刷掉（模拟器上就是「顶上空白、语义节点还在」）。
    // 为什么不在顶栏里画：顶栏比播放页先组合，整页底色会把它盖住 —— 同一个现象。
    // 这一层比页面后组合、比提示条先组合，位置在两档之间也不动。
    if (playerOpen) {
        var exitReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            // 进场头几帧主页的磨砂顶栏还在淡出，这时候出 ⌄ 会和「曲库」标题叠在一起
            val stretch = coroutineContext[androidx.compose.ui.MotionDurationScale]?.scaleFactor ?: 1f
            kotlinx.coroutines.delay((Motion.ms(Motion.LONG) * 0.78f * stretch).toLong())
            exitReady = true
        }
        AnimatedVisibility(
            visible = exitReady && !readerMode,
            enter = fadeIn(Motion.enter(Motion.SHORT)),
            exit = fadeOut(Motion.exit(Motion.SHORT)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(topBarInsets)
                .padding(Spacing.sm),
        ) {
            IconButton(
                onClick = { closeRequest++ },
                modifier = Modifier.size(RootDim.height40),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    "回到曲库",
                    Modifier.size(Spacing.xxl),
                    tint = MaterialTheme.colorScheme.primary,
                )
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
                bottom = when {
                    // 悬浮栏在场：让出「栏高 + 手势条 + 空气」
                    showFloatingBar -> bottomBarInset
                    // 播放页：走带（进度条 + 播放键）占着底部一整块，提示条要浮在它上面。
                    // 播放页已经不显示导航胶囊了，若还按「手势条 + md」走，会正好盖住播放键。
                    playerOpen -> navBarsInset + RootDim.playerControlsReserve
                    else -> Spacing.md + navBarsInset
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

    // 首启引导：盖在最上层（连提示条一起压住），看完写一次盘，之后不再自动出现。
    // 「设置 → 关于 → 功能介绍」走 vm.replayOnboarding()，同样这一层，只是不写盘。
    if (onboardingOpen) {
        OnboardingScreen(
            legalOnly = onboardingLegalOnly,
            onDone = {
                onboardingOpen = false
                vm.markOnboardingDone()
            },
        )
    }
    }
    }
    }
}

/**
 * 悬浮磨砂顶栏：**不占布局位**，浮在内容之上（和悬浮底栏对称）。
 *
 * 页面停在顶部时它几乎透明（只留右上角那颗主题开关），内容里的大标题自己撑场；
 * 上滑之后半透明磨砂条才淡入、小标题跟着出现（HyperOS 那套「大标题滚走 → 磨砂条」）。
 * 进度由页面写进 LocalTopBarScroll，这里只在绘制相位读它，不触发重组。
 *
 * 为什么不再用 `Scaffold.topBar`：Scaffold 会按 topBar 高度把整个内容区压下去，
 * 滚动内容永远进不了顶栏后面，磨砂条底下只剩背景 —— 半透明虚化就没了内容可虚化。
 * 现在内容铺满整屏，各页面用 LocalTopBarMetrics 自己留首屏空白，滚动后内容自然穿过
 * 这条磨砂条。顶栏自己吃掉点击，避免上滑后点在磨砂条上误触后面的列表项。
 *
 * [insets] 用 `safeDrawing.only(Top + Horizontal)`：不只避状态栏，还避挖孔屏的刘海
 * （竖屏在顶部、横屏在左侧），只写 statusBars 时异性屏会把标题压掉一截。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingTopBar(
    tab: Int,
    vm: MainViewModel,
    themeMode: String,
    insets: WindowInsets,
    /**
     * 非空时，在标题左边画一个返回键（播放页用）。
     *
     * 播放页的全屏态**没有底部导航胶囊**（入口改成迷你条 → 半屏 → 上滑全屏），
     * 出口只剩这一个，所以它必须落在栏里最左、不压内容的位置；折叠（半屏）态
     * 顶栏是藏着的，出口由 PlayerScreen 自己画。
     */
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val topBarScroll = LocalTopBarScroll.current
    Column(
        modifier
            .fillMaxWidth()
            // 吃掉这一条的点击：内容会从它下面滚过，不吃就会误触后面的卡片
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        // 注意：这层栏面只铺到「安全区 + 40dp 内容行」，而页面留的首屏是
        // LocalTopBarMetrics.total（多出 8dp「呼吸」）。多出来的那一格没有栏面 ——
        // 普通页面里它是页面底色，看不出来；播放页背后是整屏封面，那一格就会漏成
        // 一条封面（顶栏与卡片之间夹一条图）。所以播放页自己给那段补了底色，
        // 见 PlayerScreen 里 commitCard 之前的 drawRect。
        Box(Modifier.fillMaxWidth()) {
            // 背景 / 磨砂层：整层跟着进度淡入（绘制相位读 progress，不触发重组）
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = topBarScroll.progress }
                    .background(asmrBarColor())
                    .then(if (LocalEditorial.current) Modifier.asmrFrost() else Modifier),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(insets)
                    .height(RootDim.height40)
                    .padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 播放页的全屏态没有底部导航胶囊，出口就是这个返回键：放在栏最左，
                // 和标题同一行。它**不跟进度淡入** —— 栏里唯一能点的东西，任何时候都得在。
                onBack?.let { back ->
                    IconButton(
                        onClick = back,
                        modifier = Modifier.size(RootDim.height40),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            "回到曲库",
                            Modifier.size(IconSize.lg),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // 左侧这一串（logo + 标题 + 套色块）随进度淡入：页面在顶部时内容里
                // 已有大标题，这里是重复信息；上滑后大标题滚走，才由小标题接手。
                Row(
                    Modifier
                        .graphicsLayer { alpha = topBarScroll.progress }
                        .weight(1f),
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
                                TAB_HOME -> "首页"
                                TAB_LIBRARY -> "曲库"
                                TAB_PLAYER -> "正在播放"
                                else -> "设置"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (LocalIsBright.current) {
                            Text(
                                when (tab) {
                                    TAB_HOME -> "HOME / DASHBOARD"
                                    TAB_LIBRARY -> "LIBRARY / LOCAL"
                                    TAB_PLAYER -> "NOW PLAYING"
                                    else -> "SETTINGS"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ClassicTicks(Modifier.padding(start = Spacing.gap10))
                }
                Spacer(Modifier.weight(1f))
                // 顶栏常驻主题开关：只做「现在是亮/暗，点一下换另一边」，
                // 三态里的「跟随系统」仍留在设置页。
                val mode = com.kiite.player.ui.theme.ThemeMode.of(themeMode)
                val darkNow = when (mode) {
                    com.kiite.player.ui.theme.ThemeMode.SYSTEM ->
                        androidx.compose.foundation.isSystemInDarkTheme()
                    com.kiite.player.ui.theme.ThemeMode.DARK -> true
                    com.kiite.player.ui.theme.ThemeMode.LIGHT -> false
                }
                IconButton(
                    onClick = { vm.setThemeMode(if (darkNow) "light" else "dark") },
                    modifier = Modifier.size(RootDim.height40),
                ) {
                    ThemeGlyph(
                        dark = darkNow,
                        modifier = Modifier.size(Spacing.xxl),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        // 「版式」皮肤：顶栏下加一条印刷细线（同样跟着进度淡入）
        if (LocalEditorial.current) {
            Box(Modifier.graphicsLayer { alpha = topBarScroll.progress }) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * 悬浮导航胶囊：iOS 风格的紧凑玻璃底栏（高 54dp，替代 80dp 的 Material NavigationBar）。
 * 柔和投影浮在内容上方；选中项用**滑动胶囊**标出，不再只靠换色。
 *
 * 只有「首页 / 曲库 / 设置」三格：播放页入口从底栏撤掉了，改成点迷你播放条进（半屏），
 * 上滑才是全屏。页面下标是 0 首页 / 1 曲库 / 2 播放页 / 3 设置，这里按 [slotOf] 映射成三格。
 */
/** 页面下标 → 导航胶囊格子（0 首页 / 1 曲库 / 2 设置；播放页回落到首页格）。 */
private fun slotOf(page: Int): Int = when (page) {
    TAB_LIBRARY -> 1
    TAB_SETTINGS -> 2
    else -> 0
}

/** 导航胶囊格子 → 页面下标。播放页不在胶囊里，只能由迷你条进入。 */
private fun pageOf(slot: Int): Int = when (slot) {
    1 -> TAB_LIBRARY
    2 -> TAB_SETTINGS
    else -> TAB_HOME
}

@Composable
private fun FloatingNavBar(tab: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = asmrShape(Radius.xxl)
    val dark = scheme.background.luminance() < 0.5f
    val items = listOf(
        Triple(0, "首页", Icons.Default.Home),
        Triple(1, "曲库", Icons.Default.LibraryMusic),
        Triple(2, "设置", Icons.Default.Settings),
    )
    // 播放页（tab = TAB_PLAYER）不属于这三格，胶囊停在「首页」那一格（播放页整页都看不到它）
    val slot = slotOf(tab)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(RootDim.height54)
            .shadow(Elevation.floating, shape)
            .clip(shape)
            .background(asmrBarColor())
            .asmrFrost()
            .glassEdge(shape),
    ) {
        val pillPad = RootDim.pillPad
        val itemWidth = (maxWidth - pillPad * 2) / items.size
        // 只动一层：胶囊的横向位置。静止时它只是一块已经画好的圆角矩形，
        // 没有逐帧变化，所以不会重演「给三个图标各挂一层缩放导致掉帧」的老问题。
        val pillX by animateDpAsState(
            targetValue = pillPad + itemWidth * slot,
            animationSpec = Motion.standard(Motion.MEDIUM),
            label = "navPillX",
        )
        Box(
            Modifier
                .offset(x = pillX, y = pillPad)
                .size(width = itemWidth, height = RootDim.height54 - pillPad * 2)
                // 同心圆角：胶囊贴在底栏内侧（内边距 pillPad），内层 R = 24 − 4 = 20，
                // 两层弧才同心。原先写死 Radius.lg(16)，角上差 4dp，看着像没对齐。
                .clip(asmrShape(concentricRadius(Radius.xxl, RootDim.pillPad)))
                .background(scheme.primary.copy(alpha = if (dark) 0.22f else 0.10f)),
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (index, label, icon) ->
                val active = slot == index
                // 颜色插值只跟着选中态走；按下的形体反馈交给 pressScale（按着才动）
                val tint by animateColorAsState(
                    targetValue = if (active) scheme.primary else scheme.onSurfaceVariant,
                    animationSpec = Motion.standard(Motion.SHORT),
                    label = "navTint" + index,
                )
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressScale(interaction, pressedScale = 0.92f)
                        // 不要涟漪：这一格已经有「胶囊滑动 + 整体缩一下」两重反馈，
                        // 再加一层 Material 涟漪会在玻璃上看出一块圆斑。
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(IconSize.md),
                        tint = tint,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        // 选中态靠字重再加一档，玻璃底上颜色插值的对比度不够
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint,
                    )
                }
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
    onLift: () -> Unit,
    onLiftStart: () -> Unit = {},
    onLiftDrag: (Float) -> Unit = {},
    onLiftEnd: () -> Unit = {},
    onLiftCancel: () -> Unit = {},
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
        onLift = onLift,
        onLiftStart = onLiftStart,
        onLiftDrag = onLiftDrag,
        onLiftEnd = onLiftEnd,
        onLiftCancel = onLiftCancel,
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
    onLift: () -> Unit,
    onLiftStart: () -> Unit = {},
    onLiftDrag: (Float) -> Unit = {},
    onLiftEnd: () -> Unit = {},
    onLiftCancel: () -> Unit = {},
) {
    // 紧凑玻璃胶囊：单行 + 贴底细进度线，总高约 50dp（旧版约 78dp）
    val scheme = MaterialTheme.colorScheme
    val shape = asmrShape(Radius.xl)
    val liftPx = with(LocalDensity.current) { LiftThreshold.toPx() }
    // 手势回调必须包在 rememberUpdatedState 里，pointerInput 的 key 只能是 Unit：
    // 否则每次重组（拖拽本身就会让外层状态变）都会换 key、重启 pointer 协程，
    // 手里这根手指当场被 cancel —— 表现就是「往上拖，卡片长到一半突然缩回去」。
    val liftStartNow by rememberUpdatedState(onLiftStart)
    val liftDragNow by rememberUpdatedState(onLiftDrag)
    val liftEndNow by rememberUpdatedState(onLiftEnd)
    val liftCancelNow by rememberUpdatedState(onLiftCancel)
    val liftOpenNow by rememberUpdatedState(onLift)
    // 整卡一个按压状态：只做「略缩 + 轻微变暗」，绝不碰模糊层。
    //
    // 以前这条卡片的按压反馈只挂在里面的播放键上（0.88 倍），整条按下没有任何反馈；
    // 而磨砂是 hazeEffect 画在**这一层**的，任何套在它外面的 background 叠色都会把
    // 「糊 + 半透明」抹成一块实色 —— 用户读到的就是「一按磨砂就消失」。
    val pressInteraction = remember { MutableInteractionSource() }
    val pressed by pressInteraction.collectIsPressedAsState()
    val pressDim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.05f else 0f,
        animationSpec = Motion.standard(Motion.SHORT),
        label = "miniPressDim",
    )
    Box(
        Modifier
            .fillMaxWidth()
            // 阴影不再跟着自定义的 reveal 值减弱：进出场改由外层 AnimatedVisibility 的
            // 淡入淡出统一接管，阴影会跟着整块一起淡，不会再剩下「没有实体的影子」。
            .shadow(Elevation.raised, shape)
            .clip(shape)
            // 只留 asmrFrost 这一层「糊 + tint」。这里以前还叠过一层 background(asmrBarColor())，
            // 两层 alpha 相乘的结果就是「更实、更白、透底感更弱」，按下去任何一点变化都会被它抹平。
            // asmrFrost 自己在关掉实时毛玻璃时已经会用 asmrBarColor() 兜底，删掉不损失退路。
            .asmrFrost()
            .glassEdge(shape)
            // 缩放挂在模糊层**之后**：graphicsLayer 只作用于内容层，模糊纹理不参与缩放，
            // 按下去不会重采样出一圈没糊到的边。幅度收到 0.985（整卡大了会晃）。
            .pressScale(pressInteraction, pressedScale = 0.985f)
            // 往上拖 = 把播放页这一层从底部拉出来（和在半屏上继续上滑是同一个手势）。
            // 必须挂在 clickable 外层：拖动超过阈值就吃掉事件，否则松手会被下层当成一次点击。
            .pointerInput(Unit) {
                var dy = 0f
                var claimed = false
                detectVerticalDragGestures(
                    onDragStart = {
                        dy = 0f
                        claimed = false
                    },
                    onVerticalDrag = { change, delta ->
                        dy += delta
                        // 一过阈值就把播放页立起来（此刻它长得和这条迷你条一模一样），
                        // 之后每一格位移都直接喂给它的 morph：拖多少，卡片就长多少。
                        if (!claimed && dy < -liftPx) {
                            claimed = true
                            liftStartNow()
                        }
                        if (claimed) {
                            change.consume()
                            liftDragNow(dy)
                        }
                    },
                    onDragEnd = {
                        if (claimed) {
                            // 拖得太少（不到小半屏）就当作没拉开：退回去，不留在半路
                            if (dy > -liftPx * 3f) liftCancelNow() else liftEndNow()
                        } else if (dy < -liftPx) {
                            liftOpenNow()
                        }
                    },
                    onDragCancel = {
                        if (claimed) liftCancelNow()
                        dy = 0f
                    },
                )
            }
            // 涟漪交给上面那层自己画的变暗：默认 indication 会在磨砂面上再铺一层水波，
            // 看起来像「玻璃被打湿」，和这条栏的材质对不上。
            .clickable(
                interactionSource = pressInteraction,
                indication = null,
                onClick = onOpen,
            ),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.gap10, end = Spacing.xs, top = Spacing.itemGap, bottom = Spacing.itemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 封面槽位灰底：飞行窗口内淡到 0、落地后再淡回（不是瞬时切换）。
                val plateAlpha = androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (LocalCoverFlight.current || LocalBarCoverHidden.current) 0f else 1f,
                    animationSpec = Motion.standard(Motion.SHORT),
                    label = "miniCoverPlate",
                )
                Box(
                    Modifier
                        .size(RootDim.box36)
                        .clip(asmrShape(Radius.xs))
                        // 飞行窗口里底板要「让开」：封面已经离场，留着就是一块空的灰方块
                        // （起点、落点两端都要藏，否则"飞"这件事只剩一块灰底在原地）。
                        // 但不能瞬时置透明 —— 起飞那一刻灰方块「啪」地消失，和封面开始移动
                        // 是同一帧，读起来是一次闪跳。改成淡出 / 淡回，两端都是连续的。
                        .background(scheme.surfaceVariant.copy(alpha = plateAlpha.value)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null && !LocalBarCoverHidden.current) {
                        AsyncImage(
                            model = java.io.File(cover),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // 共享元素的 key 与播放页封面一致，两边配对后封面会「飞」过去
                            modifier = Modifier.fillMaxSize().sharedNowPlayingCover(),
                        )
                    } else if (cover == null) {
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
                val playInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onToggle,
                    interactionSource = playInteraction,
                    modifier = Modifier
                        .size(RootDim.iconBtn40)
                        .pressScale(playInteraction, pressedScale = 0.88f),
                ) {
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
        // 按压变暗：叠在内容之上、圆角之内，只改这一层的绘制，不碰底下那层模糊纹理。
        if (pressDim > 0f) {
            Box(Modifier.matchParentSize().background(scheme.onSurface.copy(alpha = pressDim)))
        }
    }
}
