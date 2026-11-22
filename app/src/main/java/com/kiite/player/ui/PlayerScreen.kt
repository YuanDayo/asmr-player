@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kiite.player.core.ParsedScript
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.TimedLine
import com.kiite.player.core.TrackEntry
import com.kiite.player.ui.components.AsmrSlider
import com.kiite.player.ui.components.EmptyState
import com.kiite.player.ui.components.TrackListItem
import com.kiite.player.ui.theme.AccentFill
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.Elevation
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.ScriptPalette
import com.kiite.player.ui.theme.Stroke
import com.kiite.player.ui.theme.TouchTarget
import com.kiite.player.ui.theme.LocalBottomBarInset
import com.kiite.player.ui.theme.LocalTopBarMetrics
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.LocalCoverFlight
import com.kiite.player.ui.theme.LocalIsBright
import com.kiite.player.ui.theme.LocalLiveBlur
import com.kiite.player.ui.theme.sharedNowPlayingCover
import com.kiite.player.ui.theme.Animations
import com.kiite.player.ui.theme.Motion
import com.kiite.player.ui.theme.collapseEnter
import com.kiite.player.ui.theme.collapseExit
import com.kiite.player.ui.theme.fadeThrough
import com.kiite.player.ui.theme.popIn
import com.kiite.player.ui.theme.popOut
import com.kiite.player.util.Permissions
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PlayerScreen 独有的控件尺寸。
 *
 * 这些不进全局令牌：它们是本页特有的控件大小（图标按钮、进度条、封面档位），
 * 同值不同义，硬塞进 Spacing / IconSize 会让令牌表变成一张数字表。
 * 集中在这里的目的是可搜、可一处改；真正的归一化（图标按钮统一到 48dp
 * 触控目标）留给单独一批，因为会改变布局。
 */
private object PlayerDim {
    val box2 = 2.dp
    val box13 = 13.dp
    val box15 = 15.dp
    val box19 = 19.dp
    val box21 = 21.dp
    val box28 = 28.dp
    val box30 = 30.dp
    val box34 = 34.dp
    val box48 = 48.dp
    val box72 = 72.dp
    val box132 = 132.dp
    val box220 = 220.dp
    val box340 = 340.dp
    val box600 = 600.dp
    // 圆角一律走 `Radius.*`：原先这里有三个自有档位（corner14 / corner24 / corner28），
    // 28dp 还只用来画卡片的裁剪路径，和卡片表面自己的 24 对不上，玻璃层会在圆角处露出 4dp。
    /**
     * 播放页不再显示悬浮导航胶囊（见 AppRoot 的 showFloatingBar），所以底部只留
     * 手势条 + 这一小格呼吸，不再让出胶囊那一格 —— 全屏页要真的铺到屏幕底部。
     * 走带（进度条 + 播放键）仍然离手势条有这么一截，不会贴边。
     */
    val sheetBottomBreath = 20.dp
    /**
     * 走带的最小高度：只用来破测量死锁用的**预留**，取值要比真实高度略大一点。
     *
     * 它决定「弹性块最多能吃掉多少」：预留偏小会把走带挤扁（实测 116dp 挤掉 11dp），
     * 预留偏大只是让台本卡少 1~2dp，看不出来。真实高度约 126dp。
     */
    val sheetControlsMin = 132.dp
    val gap28 = 28.dp
    val height30 = 30.dp
    val height32 = 32.dp
    val icon27 = 27.dp
    val iconBtn34 = 34.dp
    val iconBtn40 = 40.dp
    val iconBtn44 = 44.dp
    val pad40 = 40.dp
    /** 半屏收下时背景的雾化半径：从 0 跟着淡出量长到这个值。 */
    val bgFadeBlur = 22.dp
    /** 迷你条里那一格封面；拖拽起手飞出去的那颗也按它量尺寸。 */
    val barCover = 36.dp
}


/**
 * 用**布局相位**驱动的固定高度。
 *
 * 卡片高度在拖动/归位时每帧都在变。写成 `Modifier.height(sheetPx.value.toDp())` 会把
 * State 读在组合阶段 —— 每帧整页重组一次，台本卡、四格、走带全跟着重组。
 * 传一个 lambda 进来，在 measure 里读，逐帧变化的就只剩布局。
 */
private fun Modifier.sheetHeight(
    reservePx: () -> Int = { 0 },
    heightPx: () -> Int,
): Modifier = layout { measurable, constraints ->
    // reserve：给排在后面的兄弟留一口空间。它会挡住「弹性块吃掉全部空间 → 后面的块量成 0 →
    // 固定块总和偏小 → 弹性块吃得更多」这个死锁（实测：切页回来时走带整排消失）。
    val h = heightPx().coerceIn(0, (constraints.maxHeight - reservePx()).coerceAtLeast(0))
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(constraints.maxWidth, h) { placeable.place(0, 0) }
}

/**
 * 底部让位：内容只排在 `pageH - pad` 的区域里，底部留 pad 给悬浮栏 / 手势条。
 *
 * 写在 layout 相位（而不是 `Modifier.padding(dp)`）：pad 是从进度算出来的、逐帧都在变，
 * 传给 `padding` 就是每帧重组一次整页。
 */
private fun Modifier.sheetBottomInset(padPx: () -> Int): Modifier = layout { measurable, constraints ->
    val pad = padPx().coerceIn(0, constraints.maxHeight)
    val available = constraints.maxHeight - pad
    val placeable = measurable.measure(
        androidx.compose.ui.unit.Constraints(
            minWidth = constraints.minWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
            maxHeight = available,
        ),
    )
    // 内容贴底放，并让出底部那一格：内容的下沿 = 页面下沿 - pad。
    // pad 是「被悬浮栏/手势条盖住、不许放东西」的高度；卡片背景会盖到这一格里，
    // 视觉上是「内容浮在卡片上，栏浮在内容上」。
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(0, (constraints.maxHeight - pad - placeable.height).coerceAtLeast(0))
    }
}

/**
 * 折叠态的抓手：一条小横杠，点一下展开、往上拖跟手。
 *
 * 不做「上滑提示」文字：整张卡片本来就是可以拖的，抓手在这里是**可发现的**就够了，
 * 加一句话反而把沉浸封面割开。
 */
@Composable
private fun SheetGrip(
    onExpand: () -> Unit,
    dragState: androidx.compose.foundation.gestures.DraggableState,
    onDragStopped: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { onDragStopped() },
            )
            .clickable(onClick = onExpand)
            .padding(top = Spacing.sm, bottom = Spacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = PlayerDim.gap28 + PlayerDim.gap28, height = PlayerDim.box2 * 2)
                .clip(asmrShape(Radius.xxl))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)),
        )
    }
}

/**
 * 折叠态的底图：全屏封面 + 底部压暗。
 *
 * 只做「透明度 + 轻微放大」两种变化，且都写在 graphicsLayer 里（绘制阶段读 State），
 * 不进组合 —— 这是 2.5.5 那轮掉帧最贵的一课。
 */
@Composable
private fun PlayerArtwork(cover: String?, progress: () -> Float) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress()
                // 展开后封面**不再完全退场**：它留在玻璃底下当底纹（整页毛玻璃靠它），
                // 只是压暗、放大一点点。以前展开到 0 透明度，整页就只剩一块纯色板。
                alpha = (1f - p * 0.30f).coerceIn(0f, 1f)
                val scale = 1f + p * 0.06f
                scaleX = scale
                scaleY = scale
            },
    ) {
        if (cover != null) {
            AsyncImage(
                model = File(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(scheme.surfaceVariant))
        }
        // 底部压暗：折叠态的卡片、标题、走带压在上面，没有这一层字会飘。
        // 展开后整页是玻璃面板，这层压暗要退掉大部分，否则整页会拖一条很重的暗带。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - progress() * 0.68f).coerceIn(0f, 1f) }
                .background(
                    Brush.verticalGradient(
                        0.3f to Color.Transparent,
                        1f to scheme.background.copy(alpha = 0.92f),
                    ),
                ),
        )
    }
}


/**
 * 进页错峰：比封面晚一点落位。
 *
 * 播放页打开时，屏幕上最先该被认出来的是**封面** —— 它是从迷你条飞过来的，用户的眼睛
 * 一直跟着它走。下面的四格、走带如果跟封面同一帧一起淡进来，观感上就是「整页同时出现」，
 * 封面的飞行反而成了陪衬。
 *
 * 所以给它们各自压一小段延迟（四格 40ms、走带 80ms），再补 10dp 的上浮。总时长仍在一帧
 * 感知的范围内，但读起来是「封面先到，操作随后就位」。
 *
 * 只做 alpha + translationY（graphicsLayer），不动尺寸、不重排；延迟结束就停在那，
 * 不做无限动画。系统关掉动画（animator_duration_scale=0）时直接跳过，不做无意义等待。
 */
@Composable
private fun StaggerIn(
    delayMs: Long,
    content: @Composable () -> Unit,
) {
    if (!Animations.enabled()) {
        content()
        return
    }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        started = true
    }
    // 令牌可能带 @Composable getter，先在组合里取出来
    val rise = Spacing.gap10
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = Motion.enter(Motion.MEDIUM),
        label = "stagger",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * rise.toPx()
        },
    ) {
        content()
    }
}

@Composable
fun PlayerScreen(
    vm: MainViewModel,
    onExit: () -> Unit = {},
    /** 迷你条在窗口里的矩形：这一层要从这一格原地长出来（见下面的 morph）。 */
    miniBarBounds: androidx.compose.ui.geometry.Rect? = null,
    /** 外部（顶栏 ⌄ / 系统返回）发来的关闭令牌：收到就自己跑收下去的动画再 [onExit]。 */
    closeSignal: Int = 0,
    /** 变形头几帧用的「迷你条原样」，由 AppRoot 传进来，保证两端长得一模一样。 */
    miniBar: @Composable () -> Unit = {},
    /**
     * 上滑那条手势的实时位移（负 = 往上），由迷你条自己上报。
     *
     * 传的是取值函数而不是 Float：拖动每帧都变，当成普通参数传进来就是每帧重组整页
     * （这一页元素多，重组一次就是一帧卡）。取在 snapshotFlow 里，只在动画相位读。
     */
    liftPx: () -> Float = { 0f },
    /** 手指还按着：这段时间 morph 完全跟着 [liftPx] 走，不自己跑补间。 */
    liftActive: Boolean = false,
    /** 这一次是从迷你条拖开的：封面走「飞」，不是共享元素那条老路。 */
    liftDriven: Boolean = false,
    /** 拖回原位的取消令牌：退回迷你条那一格再 [onExit]。 */
    liftCancel: Int = 0,
) {
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val track by vm.currentTrack.collectAsStateWithLifecycle()
    val ui by vm.player.collectAsStateWithLifecycle()
    val script by vm.script.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val manualLinks by vm.manualLinks.collectAsStateWithLifecycle()
    val cover by vm.currentCover.collectAsStateWithLifecycle()
    val activePath by vm.activePath.collectAsStateWithLifecycle()
    val images = remember(track) { vm.imagesOfCurrent() }
    val videoFull by vm.videoFullscreen.collectAsStateWithLifecycle()
    val playMode by vm.playMode.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    // 全屏状态改在 AppRoot 里「切走播放页」时复位：
    // 放在这里会自我抵消——进全屏要隐藏顶栏/底栏，Scaffold 重排又让本 composable 重进，
    // LaunchedEffect(Unit)/onDispose 立刻把状态改回 false，于是永远进不去。
    var showImages by remember { mutableStateOf(false) }
    // 曲目卡封面的落点（窗口坐标）：上滑时那颗小封面就是飞到这格。
    // 由曲目卡自己上报 —— 它跟着档位在 72dp / 132dp 之间变，写死常量会在换档后错位。
    var cardCoverRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val scan by vm.scan.collectAsStateWithLifecycle()
    val sleepState by vm.sleepState.collectAsStateWithLifecycle()
    val abState by vm.abState.collectAsStateWithLifecycle()
    // 作品元数据（收藏）变化时曲目卡的爱心要跟着变
    val workMetaMap by vm.workMeta.collectAsStateWithLifecycle()

    /** 桌面歌词开关：没授权时先把用户送去系统页，而不是静默失败。 */
    val toggleLyricOverlay: () -> Unit = {
        if (!settings.lyricOverlay && !android.provider.Settings.canDrawOverlays(appCtx)) {
            runCatching {
                appCtx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + appCtx.packageName),
                    ),
                )
            }
            vm.say("请允许「显示在其他应用上层」，回来后再次点开桌面歌词")
        } else {
            vm.setLyricOverlay(!settings.lyricOverlay)
            vm.say(
                if (!settings.lyricOverlay) "桌面歌词已开启（可拖右下角缩放、可收成球）"
                else "桌面歌词已关闭",
            )
        }
    }

    var showPlaylist by remember { mutableStateOf(false) }
    var showAddToQueue by remember { mutableStateOf(false) }
    var showScriptPicker by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    // 「更多」：曲目卡的「⋯」与四格「更多」共用同一个分组底部抽屉。
    // 原来是两条下拉菜单（还各有一份展开状态），在大屏/单手场景都不好点，
    // 也装不下「播放 / 倍数 / 台本 / 显示」这么多分组。
    var moreSheet by remember { mutableStateOf(false) }
    // 全屏看封面：双击曲目卡封面、或「更多 → 全屏看封面」都进这个状态
    var coverViewer by remember { mutableStateOf(false) }
    // 「更多」里的两个信息类弹层：作品详细、音效
    var showWorkInfo by remember { mutableStateOf(false) }
    var showAudioFx by remember { mutableStateOf(false) }
    val audioFx by vm.audioFx.collectAsStateWithLifecycle()
    var batchCalibration by remember { mutableStateOf(false) }
    var calibrateLine by remember { mutableStateOf<Int?>(null) }
    // 阅读模式：藏起头部与控制卡，台本占满整屏（长台本一集几百句，读的时候不需要封面）
    var readMode by remember { mutableStateOf(false) }
    // 播放页两档：折叠（沉浸封面 + 底部卡片）/ 展开（完整布局）。
    // 状态放在 ViewModel 里 —— 切到曲库再回来，要还是离开时那一档。
    val expanded by vm.playerExpanded.collectAsStateWithLifecycle()
    // 播放页没有「大标题滚走」这件事（内容不是一条列表），顶栏固定为磨砂态
    com.kiite.player.ui.theme.TopBarAlwaysFrosted()

    val current = track
    // 当前这一集被标记过的台本行号：台本行右侧的小图标靠它打亮
    val bookmarkedLines = remember(bookmarks, current?.path) {
        bookmarks.asSequence()
            .filter { it.isLine && it.trackPath == current?.path }
            .map { it.lineIndex }
            .toSet()
    }
    if (current == null) {
        EmptyState(
            title = "还没有在播放的音频",
            description = "到曲库挑一部作品，点任意一集开始播放。",
            action = {
                // 沉浸模式会隐藏底栏，空态下没有播放页头部可以点，必须在这里留一个出口
                if (settings.immersive) {
                    Button(onClick = { vm.setImmersive(false) }) { Text("退出沉浸模式") }
                } else {
                    Button(onClick = onExit) { Text("去曲库挑一首") }
                }
            },
        )
        return
    }

    // 换集后收起校准：偏移是「这一份台本」的事，留着展开状态容易让人以为已经校准过新台本
    androidx.compose.runtime.LaunchedEffect(current.path) {
        showCalibration = false
        calibrateLine = null
    }

    if (showScriptPicker) {
        ScriptPickerDialog(
            start = File(current.folderPath).takeIf { it.isDirectory } ?: Permissions.storageRoot(),
            hasManual = manualLinks.containsKey(current.path),
            onPick = {
                showScriptPicker = false
                vm.assignScript(current.path, it.absolutePath)
            },
            onPickUri = { uri ->
                showScriptPicker = false
                vm.assignScriptFromUri(current.path, uri)
            },
            onClear = {
                showScriptPicker = false
                vm.clearScript(current.path)
            },
            onDismiss = { showScriptPicker = false },
        )
    }

    // 视频全屏：整页接管，避免两个 PlayerView 同时挂在同一个播放器上
    if (videoFull) {
        // 视频全屏：侧滑返回/返回键 = 退出全屏（之前只有画面上的手势能退，系统返回会退到后台）
        androidx.activity.compose.BackHandler(enabled = true) { vm.setVideoFullscreen(false) }
        val hostView = androidx.compose.ui.platform.LocalView.current
        val hostCtx = androidx.compose.ui.platform.LocalContext.current
        val osd = remember { VideoOsdState() }
        androidx.compose.runtime.DisposableEffect(videoFull) {
            onDispose { osd.dispose() }
        }
        // 全屏：沉浸式（隐藏系统栏）+ 横屏 + 屏幕常亮；退出时全部还原
        androidx.compose.runtime.DisposableEffect(videoFull) {
            val activity = hostCtx as? android.app.Activity
            val window = activity?.window
            val bars = if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, hostView)
            } else {
                null
            }
            val prevOrientation = activity?.requestedOrientation
            bars?.apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity?.requestedOrientation = prevOrientation
                    ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            // 之前是 useController=false，全屏里根本没法暂停/拖进度
                            useController = true
                            showController()
                            keepScreenOn = true
                            player = vm.playerForView
                            // 全屏：侧滑返回 = 退出全屏
                            installVideoGestures(this, osd) { vm.setVideoFullscreen(false) }
                        }
                    },
                    update = { it.player = vm.playerForView },
                )
                // 手势 OSD：手势发生在 PlayerView 的触摸层，反馈状态经 VideoOsdState
                // 回传后由 Compose 画在画面中央（同窗口内 SurfaceView 之上的绘制可见）
                VideoOsdOverlay(osd)
            }
            // 退出按钮必须放在视频「外面」：
            // PlayerView 用的是 SurfaceView，处于独立图层，会盖住叠在它上面的 Compose 控件
            Row(
                Modifier.fillMaxWidth().padding(Spacing.gap10),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { vm.setVideoFullscreen(false) }) {
                    Text("退出全屏", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        return
    }

    // 视频：走独立的视频播放器，不复用音频那套骨架（封面/台本/图片/播放列表胶囊都不要）
    if (vm.currentIsVideo()) {
        VideoPlayerScreen(vm = vm, onExit = onExit)
        return
    }

    val queueVersion by vm.queueVersion.collectAsStateWithLifecycle()
    // 队列增删后 queueVersion 变化 → 重新读真实队列
    val playlist: List<TrackEntry> = remember(scan, queueVersion) {
        if (scan == null) emptyList() else vm.currentPlaylist()
    }

    viewerIndex?.let { idx ->
        if (images.isNotEmpty()) {
            ImageViewer(images, idx, onClose = { viewerIndex = null })
        }
    }

    // 封面单独看：曲库里这张可能既不在图片墙里、也不进图库，直接给一张的浏览页
    val coverPath = cover
    if (coverViewer && coverPath != null) {
        ImageViewer(listOf(coverPath), 0, onClose = { coverViewer = false })
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideScreenMinWidth
        // 「又宽又矮」（横屏手机）：左栏放不下大封面 → 强制紧凑并隐藏布局切换；
        // 平板竖屏这类高度足够的宽屏仍可切大封面，按钮保留
        val tightHeight = maxHeight < PlayerDim.box600

        // 顶栏是浮层（见 AppRoot 的 FloatingTopBar）：展开态卡片 / 横屏双栏要给
        // 「状态栏 + 栏高」让位，否则封面与曲目卡会压在磨砂条下面。
        // 沉浸模式下顶栏本身是藏起来的，不用让。
        val topBarReserve = if (settings.immersive) 0.dp else LocalTopBarMetrics.current.total

        // 竖屏三档布局：紧凑(72dp) / 大封面(132dp，带台词卡) / 全页大图(无台词卡)。
        // 「全页大图」只在竖屏有意义：宽屏双栏本来就放得下，按大封面处理。
        val layout = if (wide && settings.playerLayout == "big") "new" else settings.playerLayout

        // 阅读模式的前提：竖屏、非全页大图、台本已解析出时间轴。
        // 条件不成立就自动退出：横屏没有入口、大图档没有走带，留着只会得到一个
        // 既没有头部也没有退出按钮的死态。
        val reading = readMode && !wide && layout != "big" &&
            script.parsed?.timedLines?.isNotEmpty() == true
        androidx.compose.runtime.LaunchedEffect(reading) {
            if (readMode && !reading) readMode = false
        }

        // 侧滑返回按层级恢复（后注册的优先命中）：
        // 阅读模式 → 全页大图 → 图片/播放列表 → 沉浸模式 →（AppRoot 接手：回曲库/详情/后台）
        androidx.activity.compose.BackHandler(enabled = settings.immersive) {
            vm.setImmersive(false)
        }
        androidx.activity.compose.BackHandler(enabled = showImages || showPlaylist) {
            showImages = false
            showPlaylist = false
        }
        androidx.activity.compose.BackHandler(enabled = !wide && layout == "big") {
            vm.setPlayerLayout("new")
        }
        androidx.activity.compose.BackHandler(enabled = reading) { readMode = false }

        // 头部、内容、控制拆成三段：竖屏上下排，横屏左右排
        // 曲目所属作品：曲目卡的爱心是「作品级」收藏，必须拿到作品对象才能复用曲库那份数据
        val workProject = remember(current.path) { vm.projectOfTrackPath(current) }
        val favorite = remember(workProject, workMetaMap) {
            workProject?.let { vm.isFavorite(it) } == true
        }
        val workCode = workProject?.code?.takeIf { it.isNotBlank() } ?: current.folderName

        // 播放页里也能换封面：和作品详情页「换头图」共用同一套存储（复制一份，不动曲库原图）
        val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        ) { uri ->
            val project = workProject
            if (uri != null && project != null) {
                vm.setCustomCoverFromUri(project, uri)
            } else if (uri != null) {
                vm.say("这个作品没有编号也没有路径，无法换封面")
            }
        }

        // 「⋯」与四格「更多」共用同一份动作表：两个入口、一份定义，
        // 抽屉里排成 4 列图标砖，下拉里排成带图标的行。加一项只改这里。
        // 播放倍数不在这张表里：它要一条可加减的滑杆，单独渲染（见 SpeedStepper）。
        val moreGroups: List<Pair<String, List<MoreItem>>> = run {
            val groups = mutableListOf<Pair<String, List<MoreItem>>>()
            groups += "播放" to listOf(
                MoreItem(Icons.Default.Replay10, "后退 10 秒", onClick = { vm.seekBy(-10_000) }),
                MoreItem(Icons.Default.Forward10, "前进 10 秒", onClick = { vm.seekBy(10_000) }),
            )
            val variants = vm.variantsOfCurrent()
            if (variants.size > 1) {
                groups += "格式（同一音频的不同版本）" to vm.variantLabels().map { pair ->
                    MoreItem(
                        Icons.Default.GraphicEq,
                        pair.second,
                        selected = pair.first == (activePath ?: variants.first()),
                        onClick = { vm.switchVariant(pair.first) },
                    )
                }
            }
            groups += "台本" to buildList {
                add(MoreItem(Icons.Default.Description, "选择台本", onClick = { showScriptPicker = true }))
                add(MoreItem(Icons.Default.Save, "写入标签", onClick = { vm.embedCurrent() }))
                if (!wide && script.parsed?.timedLines?.isNotEmpty() == true) {
                    add(
                        MoreItem(Icons.Default.MenuBook, "台本全屏阅读", onClick = {
                            showPlaylist = false
                            showImages = false
                            readMode = true
                        }),
                    )
                }
            }
            groups += "显示" to buildList {
                // 封面既能单独放大看，也能换成自己选的图——播放页里就地解决，
                // 不用退回曲库点开作品详情
                if (cover != null) {
                    add(MoreItem(Icons.Default.Fullscreen, "全屏看封面", onClick = { coverViewer = true }))
                }
                if (workProject != null) {
                    add(
                        MoreItem(
                            Icons.Default.Wallpaper,
                            if (vm.hasCustomCover(workProject)) "换一张封面" else "换封面图",
                            onClick = { coverPicker.launch("image/*") },
                        ),
                    )
                }
                if (images.isNotEmpty()) {
                    add(
                        MoreItem(
                            Icons.Default.Image,
                            if (showImages) "切回台本" else "查看图片",
                            selected = showImages,
                            onClick = { showImages = !showImages; showPlaylist = false },
                        ),
                    )
                }
                if (vm.currentIsVideo()) {
                    add(MoreItem(Icons.Default.OpenInFull, "视频全屏", onClick = { vm.setVideoFullscreen(true) }))
                }
                add(
                    MoreItem(
                        Icons.Default.Brightness6,
                        if (settings.immersive) "退出沉浸模式" else "沉浸模式",
                        selected = settings.immersive,
                        onClick = { vm.setImmersive(!settings.immersive) },
                    ),
                )
            }
            groups += "信息与音效" to listOf(
                MoreItem(Icons.Default.Info, "作品详细", onClick = { showWorkInfo = true }),
                MoreItem(
                    Icons.Default.Equalizer,
                    "音效",
                    selected = audioFx.enabled,
                    onClick = { showAudioFx = true },
                ),
            )
            groups
        }

        // 曲目卡「⋯」那份下拉：一行一项，带图标
        val moreMenu: @Composable (onDone: () -> Unit) -> Unit = { onDone ->
            moreGroups.forEach { (group, items) ->
                MenuLabel(group)
                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(item.icon, null, Modifier.size(IconSize.sm))
                                Spacer(Modifier.width(Spacing.sm))
                                Text(item.label)
                            }
                        },
                        onClick = { onDone(); item.onClick() },
                    )
                }
                // 下拉里不摆倍速滑杆（宽度不够加减两个按钮），只显示当前倍率并把人送去抽屉
                if (group == "播放") {
                    MenuLabel("播放倍数")
                    DropdownMenuItem(
                        text = { Text("倍率 " + formatSpeed(ui.speed) + "（在「更多」里加减）") },
                        onClick = { onDone(); moreSheet = true },
                    )
                }
            }
        }

        val header: @Composable (Boolean) -> Unit = { bigCover ->
            TrackCard(
                bigCover = bigCover,
                onCoverBounds = { cardCoverRect = it },
                // 全页大图档自己那张封面才是共享元素；本档只在这时候让位，
                // 免得切档时 Crossfade 里两张封面抢同一个 key
                sharedCover = layout != "big",
                track = current,
                cover = cover,
                subtitle = workCode,
                favorite = favorite,
                onToggleFavorite = {
                    workProject?.let { vm.setFavorite(it, !favorite) }
                },
                playing = ui.isPlaying,
                isVideo = vm.currentIsVideo(),
                videoPlayer = vm.playerForView,
                // 参考图里没有独立的「布局」按钮：点封面本身切换，最直觉。
                // 三档循环：紧凑(72dp) → 大封面(132dp，带台词卡) → 全页大图(无台词卡) → 紧凑；
                // 「全页大图」也可以侧滑返回退回大封面档。
                onToggleSize = {
                    vm.setPlayerLayout(
                        when (settings.playerLayout) {
                            "classic" -> "new"
                            "new" -> "big"
                            else -> "classic"
                        },
                    )
                },
                onVideoFullscreen = { vm.setVideoFullscreen(true) },
                onMore = { moreSheet = true },
                // 台本匹配状态与时间轴校准贴在曲目卡底部，不占用台词区
                calibration = if (script.parsed == null) {
                    null
                } else {
                    {
                        ScriptCalibrationStrip(
                            ui = script,
                            vm = vm,
                            expanded = showCalibration,
                            onToggle = { showCalibration = !showCalibration },
                            onOpenBatch = { batchCalibration = true },
                        )
                    }
                },
            )
        }

        // 「更多」：底部抽屉（分组），曲目卡「⋯」与四格「更多」都开这一个
        if (moreSheet) {
            PlayerMoreSheet(onDismiss = { moreSheet = false }) {
                MoreIconGrid(
                    groups = moreGroups,
                    onDone = { moreSheet = false },
                    // 播放倍数紧跟在「播放」后面，单独占一行（减 / 当前倍率 / 加）
                    inlineBlocks = mapOf<String, @Composable () -> Unit>(
                        "播放" to {
                            MenuLabel("播放倍数")
                            SpeedStepper(
                                speed = ui.speed,
                                onStep = { dir ->
                                    val steps = MainViewModel.SPEED_STEPS
                                    val index = steps.indexOfFirst { it >= ui.speed }
                                        .let { if (it < 0) steps.lastIndex else it }
                                    vm.setSpeed(steps[(index + dir).coerceIn(0, steps.lastIndex)])
                                },
                            )
                        },
                    ),
                )
            }
        }

        // 作品详细：DLsite 抓到的元信息 + 这条音频自己的实情（文件/体积/时长/台本）
        if (showWorkInfo) {
            WorkInfoSheet(
                dlsite = workProject?.path?.let { vm.dlsiteOf(it) },
                project = workProject,
                track = current,
                onDismiss = { showWorkInfo = false },
            )
        }
        // 音效：与设置页那份共用 AudioFxControls，改完立刻作用于输出
        if (showAudioFx) {
            AudioFxSheet(
                fx = audioFx,
                onApply = vm::applyAudioFx,
                say = vm::say,
                onDismiss = { showAudioFx = false },
            )
        }
        // 播放列表：弹出窗口（覆盖大部分区域），而不是挤在页面里
        if (showAddToQueue) {
            QueueAddDialog(
                vm = vm,
                onDismiss = { showAddToQueue = false },
            )
        }
        if (showBookmarks) {
            BookmarksPanel(vm = vm, onDismiss = { showBookmarks = false })
        }
        if (batchCalibration) {
            ScriptBatchCalibrationDialog(
                ui = script,
                vm = vm,
                onDismiss = { batchCalibration = false },
            )
        }
        calibrateLine?.let { idx ->
            if (idx < (script.parsed?.timedLines?.size ?: 0)) {
                ScriptLineCalibrationDialog(
                    ui = script,
                    index = idx,
                    playing = ui.isPlaying,
                    onTogglePlay = { vm.playPause() },
                    vm = vm,
                    onDismiss = { calibrateLine = null },
                )
            }
        }
        // 内容卡换页（台本 / 播放列表 / 图片）原来是同一帧硬切：标题换了、面板也换了，
        // 中间没有任何过渡。这三块都是不透明卡片，交叉淡变不会叠出灰块
        // （整页切换不行，见 MotionTransitions 里的注释），150ms 足够把「换页」讲清楚。
        val contentKind = when {
            showImages -> 2
            showPlaylist -> 1
            else -> 0
        }
        val content: @Composable () -> Unit = {
            androidx.compose.animation.Crossfade(
                targetState = contentKind,
                animationSpec = Motion.standard(Motion.SHORT),
                label = "playerContent",
            ) { kind ->
            when {
                kind == 2 -> {
                    ImagePanel(images) { viewerIndex = it }
                }
                // 播放列表不再是弹窗：和新布局的其他内容一样，在这张卡里显示，
                // 少一层「遮挡整个播放页」的界面，也少一个关闭按钮
                kind == 1 -> {
                    PlaylistPanel(
                        playlist = playlist,
                        currentPath = current.path,
                        autoScroll = settings.playlistAutoScroll,
                        onPick = { vm.playFromPlaylist(it) },
                        onRemove = { vm.queueRemove(it) },
                        onMove = { path, delta -> vm.queueMove(path, delta) },
                    )
                }
                script.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                script.parsed != null && !script.parsed!!.isEmpty -> {
                    // 台本区不再有常驻状态条与底部偏移面板，整块高度都留给台词本身
                    ScriptPanel(
                        parsed = script.parsed!!,
                        positionMs = ui.positionMs,
                        durationMs = ui.durationMs,
                        autoScroll = settings.autoScroll,
                        speed = settings.scrollSpeed,
                        fontScale = settings.scriptFontScale,
                        onSeek = { vm.seekTo(it) },
                        onLongPressLine = { calibrateLine = it },
                        // 只在当前这一集里找，别集的行号没有意义
                        bookmarkedLines = bookmarkedLines,
                        onToggleLineBookmark = { vm.toggleLineBookmark(it) },
                        lineHeightScale = settings.scriptLineHeight,
                        fontFamily = when (settings.scriptFontFamily) {
                            "serif" -> FontFamily.Serif
                            "mono" -> FontFamily.Monospace
                            else -> FontFamily.SansSerif
                        },
                        theme = ScriptReadTheme.of(settings.scriptTheme),
                    )
                }
                else -> {
                    // 台本空态：直接给出「选择台本」的出路，而不是只留一句话
                    Column(
                        Modifier.fillMaxSize().padding(Spacing.xl),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            script.error ?: "没有可显示的台本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Button(onClick = { showScriptPicker = true }) { Text("选择台本") }
                    }
                }
            }
            }
        }

        val controls: @Composable () -> Unit = {
            PlayerControls(
                isPlaying = ui.isPlaying,
                positionMs = ui.positionMs,
                durationMs = ui.durationMs,
                onSeekTo = { vm.seekTo(it) },
                onToggle = { vm.playPause() },
                onPrev = { vm.previous() },
                onNext = { vm.next() },
                sleepState = sleepState,
                fadeMs = vm.sleepFadeMs,
                onSetSleep = { vm.setSleepDuration(it) },
                onStopAfterTrack = { vm.setSleepStopAtTrackEnd() },
                onStopAfterWork = { vm.setSleepStopAtWorkEnd() },
                onClearSleep = { vm.clearSleepTimer() },
                onCycleFade = {
                    val opts = com.kiite.player.playback.SleepMath.FADE_OPTIONS_MS
                    val i = opts.indexOf(vm.sleepFadeMs).let { if (it < 0) 0 else it }
                    vm.setSleepFade(opts[(i + 1) % opts.size])
                },
                abState = abState,
                onAbSetStart = { vm.abSetStart() },
                onAbSetEnd = { vm.abSetEnd() },
                onAbNudgeStart = { vm.abNudgeStart(it) },
                onAbNudgeEnd = { vm.abNudgeEnd(it) },
                onAbToggle = { vm.abToggle() },
                onAbClear = { vm.abClear() },
                playMode = playMode,
                onCyclePlayMode = { vm.cyclePlayMode() },
                bookmarkCount = com.kiite.player.core.BookmarkMath.ofTrack(bookmarks, current.path).size,
                onOpenBookmarks = { showBookmarks = true },
            )
        }

        // 四格快捷入口（参考图第二块）：谁在被看谁高亮
        val quickActions: @Composable () -> Unit = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.itemGap)
                    .clip(asmrShape(Radius.xl))
                    .background(asmrRowColor()),
            ) {
                QuickAction(
                    icon = Icons.Default.PlaylistPlay,
                    label = "播放列表",
                    active = showPlaylist,
                    onClick = { showPlaylist = true; showImages = false },
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Default.MenuBook,
                    label = "查看台本",
                    active = !showPlaylist && !showImages,
                    onClick = { showPlaylist = false; showImages = false },
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Default.Subtitles,
                    label = "桌面歌词",
                    active = settings.lyricOverlay,
                    onClick = toggleLyricOverlay,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Default.MoreHoriz,
                    label = "更多",
                    active = false,
                    onClick = { moreSheet = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 进页节奏：四格 (40ms) 与走带 (80ms) 各自比封面晚半拍落位。
        // 包一层而不是改两个 lambda 的实现，是为了保持「定义」与「进页节奏」分开 ——
        // 布局分支里仍然只认 controls / quickActions 这两个名字。
        val arrivingControls: @Composable () -> Unit = { StaggerIn(delayMs = 80) { controls() } }
        val arrivingQuickActions: @Composable () -> Unit = { StaggerIn(delayMs = 40) { quickActions() } }

        // 台本 / 播放列表 / 图片 共用一张卡：标题跟着内容走，圆角只包一层
        val contentTitle = when {
            showImages -> "图片 " + images.size
            showPlaylist -> "播放列表 " + playlist.size
            else -> "台本"
        }
        val contentCard: @Composable () -> Unit = {
            // 放大时「同一张卡在长大」：外扩边距收窄、圆角略收、卡头换成阅读条。
            // 不能换分支重画，否则 ScriptPanel 会重挂，滚动位置与当前句全丢。
            val inset by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (reading) Spacing.xs else Spacing.lg,
                animationSpec = Motion.standard(Motion.MEDIUM),
                label = "cardInset",
            )
            val corner by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (reading) Radius.md else Radius.xl,
                animationSpec = Motion.standard(Motion.MEDIUM),
                label = "cardCorner",
            )
            ScriptCard(
                title = contentTitle,
                inset = inset,
                corner = corner,
                header = if (reading) {
                    {
                        ReadModeBar(
                            title = current.baseName,
                            positionMs = ui.positionMs,
                            durationMs = ui.durationMs,
                            playing = ui.isPlaying,
                            onToggle = { vm.playPause() },
                            onExit = { readMode = false },
                        )
                    }
                } else {
                    null
                },
                trailing = when {
                    showPlaylist -> {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 播放列表此时占满整页，四格被收起：留一个回台本的出口
                                IconButton(
                                    onClick = { showPlaylist = false },
                                    modifier = Modifier.size(PlayerDim.box34),
                                ) {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        "回到台本",
                                        Modifier.size(IconSize.sm),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.queueReset() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.sm)) { Text("重置") }
                                TextButton(onClick = { showAddToQueue = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.sm)) { Text("＋ 加入") }
                            }
                        }
                    }
                    !showImages && script.parsed != null -> {
                        {
                            // 台本放大就放在台本卡自己头上：功能长在它作用的那块内容旁边
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    script.confidence.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                if (!wide && layout != "big" && script.parsed?.timedLines?.isNotEmpty() == true) {
                                    IconButton(
                                        onClick = { readMode = true },
                                        modifier = Modifier.size(PlayerDim.box34),
                                    ) {
                                        Icon(
                                            Icons.Default.OpenInFull,
                                            "台本全屏阅读",
                                            Modifier.size(IconSize.sm),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> null
                },
            ) {
                content()
            }
        }

        if (wide) {
            // 横屏双栏：左 = 曲目卡 + 快捷入口 + 控制，右 = 台本 / 播放列表 / 图片
            //
            // 横屏没有两档卡片，底栏占位得自己补回来：AppRoot 那边已经不再给播放页垫底了
            // （竖屏由卡片的 padOf 按进度插值让位），不补的话走带会钻到导航胶囊下面。
            Row(
                Modifier.fillMaxSize()
                    .padding(top = topBarReserve, bottom = LocalBottomBarInset.current),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    header(!tightHeight && layout != "classic")
                    arrivingQuickActions()
                    Spacer(Modifier.weight(1f))
                    arrivingControls()
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = Spacing.itemGap)) {
                    contentCard()
                }
            }
        } else {
            val isBig = layout == "big"
            // BoxWithConstraints 的作用域在 Column/Crossfade 里会被后加的接收者挤掉，先存下来
            val pageW = maxWidth
            val pageH = maxHeight
            // ── 播放页两档：折叠（沉浸封面 + 底部卡片）/ 展开（完整布局）──────────────
            //
            // 折叠态**不是另一套界面**：它就是把这同一张卡片压矮。卡片里的内容一件都不换
            // （曲目卡、四格、台本、走带都是同一批实例），压矮之后台本卡那一格 weight(1f)
            // 自然拿到 0 高度，于是「上滑 = 卡片长高」—— 尺寸、圆角、底色连续，内容位置也
            // 连续，没有第二套布局要维护。这一点和 2.5.5 那轮「两张页面交叉淡变叠成灰块」
            // 是同一个教训：能用同一批元素连续变化，就别拿两套内容去淡入淡出。
            //
            // 全页大图档、阅读模式、播放列表 / 图片都强制展开：它们要整页高度，
            // 折叠态没有它们的落点 —— 折叠时点「播放列表」只把四格收起来、面板拿到
            // 0 高度，屏幕上什么也没变，用户看到的就是「点了没反应」。
            val canCollapse = !isBig && !reading && !showPlaylist && !showImages
            val open = expanded || !canCollapse
            // 折叠态没有 App 顶栏/悬浮底栏（沉浸封面要铺满），展开态还原；
            // 离开播放页时由 AppRoot 复位，这里只管「在页时跟随档位」。
            androidx.compose.runtime.LaunchedEffect(open, reading) {
                vm.setReaderMode(reading)
                if (reading) {
                    // 全屏阅读自己有退出/播放条：再留一条全局顶栏就是白白吃掉 ~48dp，
                    // 卡片只能从顶栏底下开始，长台本顶部看着一大块空。
                    vm.setChromeHidden(true)
                } else if (open) {
                    vm.setChromeHidden(false)
                } else {
                    // 等曲库淡干净再收：转场开头那 90ms 曲库还在屏幕上，
                    // 那一刻把它的顶栏/导航栏抽走，比晚一点收难看得多。
                    val stretch = coroutineContext[androidx.compose.ui.MotionDurationScale]?.scaleFactor ?: 1f
                    kotlinx.coroutines.delay((Motion.ms(Motion.FLASH) * stretch).toLong())
                    vm.setChromeHidden(true)
                }
            }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    vm.setChromeHidden(false)
                    vm.setReaderMode(false)
                }
            }
            val density = LocalDensity.current
            val sheetScope = rememberCoroutineScope()
            val pageHpx = with(density) { pageH.toPx() }
            // 阅读模式收起了全局顶栏，卡片就顶到屏幕最上沿（状态栏那格由
            // ReadModeBar 自己让出）。仍按原来那条留白算，放大后就多一条空带。
            val topReservePx = with(density) {
                (if (reading) 0.dp else topBarReserve).toPx()
            }
            // 卡片顶角的像素值：`Radius` 是 @Composable 读的，绘制相里读不到，必须在这里取一次。
            val sheetCornerPx = with(density) { Radius.xxl.toPx() }
            // 迷你条那颗胶囊的圆角（Radius.xl）：变形起点用它，绘制相里同样只能提前取。
            val barCornerPx = with(density) { Radius.xl.toPx() }
            val navInsetPx = WindowInsets.navigationBars.getBottom(density)
            val navInsetDp = with(density) { navInsetPx.toDp() }

            // 卡片里「固定那几块」的实测高度：抓手 + （曲目卡 + 四格） + 走带。
            // 不写死常量：曲目卡底下挂着台本匹配条（有台本才出现）、字号随系统缩放变、
            // 走带上那行按钮的字也会变。写死 328dp 的实测后果是「走带被卡片下沿切掉」。
            //
            // 头部与抓手是**会整块隐藏**的（阅读模式 / 播放列表 / 展开态）。高度必须量在
            // AnimatedVisibility 自己身上：量在里面那个 Box 上，节点退出组合后最后那次
            // 尺寸就留在状态里，卡片会一直以为那几百 dp 还在 —— 表现为「面板下面空一大块」。
            var gripPx by remember { mutableIntStateOf(0) }
            var topBlockPx by remember { mutableIntStateOf(0) }
            var controlsPx by remember { mutableIntStateOf(0) }

            /**
             * 唯一的动画量：0 = 折叠，1 = 展开。
             *
             * 卡片高度、台本卡高度、圆角、底色、背景遮罩**全部**由它算出来，
             * 不存在「目标值 ↔ 测量值」两个来源互相追的问题 —— 之前用
             * 「实测高度当目标」吃过一次亏：实测值在进页动画里每帧都变，把目标塞进
             * LaunchedEffect 的 key 就是每帧重启动画，表现是持续掉帧甚至 ANR。
             *
             * 它在组合阶段是**不许读**的：读一次就是整页重组一次。所有消费点都写在
             * layout / draw / graphicsLayer 的 lambda 里（绘制相位读 State 只重绘）。
             */
            val progress = remember { Animatable(if (open) 1f else 0f) }
            androidx.compose.runtime.LaunchedEffect(open) {
                progress.animateTo(if (open) 1f else 0f, Motion.standard(Motion.MEDIUM))
            }
            val progressOf: () -> Float = { progress.value }

            /**
             * 把播放页这一层收下去的进度（0 = 停在原位，1 = 整页推到屏幕外）。
             *
             * 半屏往下滑就是它在跟手：整页（封面底图 + 卡片）一起往下走，曲库从底下露出来
             * —— 这就是「播放页在主页更上面一层」的手感。松手过线才真的 `onExit`，
             * 没过线回弹原位，半屏不会被拖丢。
             */
            val dismissed = remember { Animatable(0f) }

            /**
             * 进场变形：0 = 迷你条那一格，1 = 这一档的正常尺寸。
             *
             * 以前进播放页是「整页从屏幕下沿推上来」+ 迷你条淡出：那是两件事同时发生，
             * 眼睛读到的是「条消失了、另一页滑进来」。参考那种连贯感是**同一个容器**
             * 从迷你条那一格长到半屏（再长到全屏）—— 高度、圆角、封面尺寸一路连续，
             * 底色/背景只是跟着变浓。所以这里不再依赖页面的进场动画，页面一挂上就从 0 开始长。
             */
            val morph = remember { Animatable(0f) }
            // 手指拖多远，卡片就长多少：位移直接换算成 morph。
            //
            // 分母取「迷你条顶边 → 半屏卡片顶边」这段距离的近似值（约 0.44 屏高）。
            // 不精确没关系 —— 它决定的是「跟手的比例」，松手之后由补间补齐到 1。
            val liftTravelPx = (pageHpx * 0.44f).coerceAtLeast(1f)
            val liftRatioOf: () -> Float = { (-liftPx() / liftTravelPx).coerceIn(0f, 0.95f) }
            LaunchedEffect(liftActive) {
                if (liftActive) {
                    // 跟手阶段：每一格位移都直接落成卡片尺寸，既不补间也不回弹
                    androidx.compose.runtime.snapshotFlow { liftRatioOf() }
                        .collect { ratio -> morph.snapTo(ratio) }
                } else {
                    // 松手（或者点一下进来的常规进场）：从当前值补到 1。
                    // 时长按剩余距离算，拖到一半松手不会又从头跑满 400ms。
                    val remain = (1f - morph.value).coerceIn(0.15f, 1f)
                    val stretch = coroutineContext[androidx.compose.ui.MotionDurationScale]?.scaleFactor ?: 1f
                    morph.animateTo(
                        1f,
                        Motion.enter<Float>((Motion.LONG * remain / stretch).toInt()),
                    )
                }
            }
            val morphOf: () -> Float = { morph.value }

            /**
             * 卡片内容在变形里的两段透明度。
             *
             * 卡片是从底下往上长的，而内容是按底边对齐排的：长到一半时，卡片的顶边
             * 正好横切过封面和标题 —— 抓帧里就是「半个封面 + 标题被削掉一截」，比硬切还难看。
             * 所以前半程先让「迷你条原样」占着（它是完整的、和主页那颗一模一样），
             * 等卡片长得够高，两边再快速交接：容器一直在长，内容只换一次，不出现切边。
             *
             * 拖拽起手（liftDriven）时交接点再往后推：手指压在屏幕上时，卡片要一路跟手长高，
             * 这个阶段只有封面飞进来、纸面是空的，比内容半透明地在底边对齐处晃更干净。
             */
            val contentFrom = if (liftDriven) 0.80f else 0.45f
            val contentSpan = 1f - contentFrom
            val cardContentAlphaOf: () -> Float = { ((morph.value - contentFrom) / contentSpan).coerceIn(0f, 1f) }
            val barSnapAlphaOf: () -> Float = {
                val end = if (liftDriven) contentFrom else 0.72f
                (1f - (morph.value - (end - 0.30f)) / 0.30f).coerceIn(0f, 1f)
            }

            // 折叠态的 ⌄ 等卡片长到位再出现（见下面 AnimatedVisibility 的注释）。
            // 用「等一段时间」而不是读 morph.value：读它会让整页每帧重组一次。
            var exitButtonReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val stretch = coroutineContext[androidx.compose.ui.MotionDurationScale]?.scaleFactor ?: 1f
                kotlinx.coroutines.delay((Motion.ms(Motion.LONG) * 0.78f * stretch).toLong())
                exitButtonReady = true
            }

            /** 收下去：整页往下走出屏幕，走完再告诉外面「这一层关了」。 */
            val closeNow: () -> Unit = {
                sheetScope.launch {
                    dismissed.animateTo(1f, Motion.exit(Motion.MEDIUM))
                    onExit()
                }
            }

            // 顶栏 ⌄ / 系统返回发来的关闭令牌：只在**变化**时响应。
            // 不能用 `if (closeSignal > 0)`：令牌是 rememberSaveable，重开播放页时它还是旧值，
            // 一进页面就会立刻自己关掉。
            var seenClose by remember { mutableIntStateOf(closeSignal) }
            LaunchedEffect(closeSignal) {
                if (closeSignal != seenClose) {
                    seenClose = closeSignal
                    closeNow()
                }
            }

            // 拖回起点附近的取消：退回迷你条那一格再退出（不能硬切，那样条会「啪」地跳回来）
            var seenLiftCancel by remember { mutableIntStateOf(liftCancel) }
            LaunchedEffect(liftCancel) {
                if (liftCancel != seenLiftCancel) {
                    seenLiftCancel = liftCancel
                    morph.animateTo(0f, Motion.exit(Motion.MEDIUM))
                    onExit()
                }
            }

            // 返回键先收卡片，再收这一层：和阅读模式 / 全页大图同一套层级顺序。
            // 播放列表 / 图片展开时不抢（那两层的返回键注册得更早，语义也更靠前）。
            androidx.activity.compose.BackHandler(
                enabled = canCollapse && !showPlaylist && !showImages,
            ) {
                // 展开态先折回半屏；已经在半屏就整层收下去 —— 播放页是盖在上面的一层，
                // 返回键的语义是「退一层」，不是「跳回曲库」（后者会把它硬切掉）。
                if (open) vm.setPlayerExpanded(false) else closeNow()
            }

            /**
             * 底部让位：折叠 = 手势条；展开 = 手势条 + 悬浮导航胶囊那一格。
             *
             * 展开态那一格**不读 `LocalBottomBarInset`**，用常量算：胶囊高度是固定的
             * （`RootDim.height54` + 两个间距 + `Spacing.md`），而这个环境里的 inset
             * 在「从曲库切回播放页」那一刻是 0（底栏刚重新进场、还没量到高度），
             * 布局拿到 0 的后果就是走带被顶到导航栏后面 —— 实测踩过两次。
             * 常量不会错位到那个程度，代价是胶囊换高度时要同步改这里。
             */
            /**
             * 底部让位：手势条 + 一小格呼吸（折叠/展开同一个值）。
             *
             * 以前展开态要额外让出 78dp 给悬浮导航胶囊 —— 播放页底栏撤掉之后（入口改成
             * 迷你条 → 半屏 → 上滑全屏，出口是左上角返回键），再让就是白白空一条；
             * 真机抓图里走带被胶囊压掉一条边，也是这一格算重了的缘故。
             *
             * 仍按常量算、不读 `LocalBottomBarInset`：那个值在「从曲库切回播放页」
             * 那一刻是 0（底栏刚重新进场、还没量到高度），布局拿到 0 会把走带顶到
             * 导航栏后面 —— 这个坑踩过两次（见 HANDOVER）。
             */
            val navInsetState = androidx.compose.runtime.rememberUpdatedState(navInsetDp)
            val bottomBreathPx = with(density) { PlayerDim.sheetBottomBreath.toPx() }
            val padOf: () -> Int = {
                val nav = with(density) { navInsetState.value.toPx() }.roundToInt()
                (nav + bottomBreathPx.roundToInt()).coerceIn(0, pageHpx.roundToInt())
            }

            /** 固定块高度之和（当前档位下实测）。 */
            val fixedOf: () -> Int = { gripPx + topBlockPx + controlsPx }

            val scheme = MaterialTheme.colorScheme
            // 播放页自己的磨砂源：只有「全屏封面」这一层被采集。
            // 不借用全局 LocalHazeState —— 那一份的源是整个页面内容，卡片在里面取用就是自己糊自己。
            val pageHaze = remember { HazeState() }
            // 玻璃皮肤的卡片要更透：底下那层封面被糊掉了，透出来才有玻璃感。
            // 展开态也一样透 —— 整页玻璃靠的就是这一层透出封面的底纹。
            // 别的皮肤（明快 / 经典）没有模糊，仍然用接近不透明的底，保证字清楚。
            val glassy = LocalEditorial.current && LocalLiveBlur.current
            val collapsedColor = scheme.surface.copy(alpha = if (glassy) 0.62f else 0.86f)
            val expandedColor = scheme.background.copy(alpha = if (glassy) 0.70f else 1f)
            val cardPath = remember { androidx.compose.ui.graphics.Path() }
            val dragState = rememberDraggableState { delta ->
                // 一个连续的手势轴：全屏 → 半屏 → 收起回主页。
                //
                // 以前只有「展开/折叠」两档：折到底之后再往下拖是**没有任何反应**的，
                // 用户读成「下滑失灵」。现在折叠到底继续往下，开始把整页往屏幕外推，
                // 松手过线就交给 onExit（AppRoot 那边同一时刻把 tab 置 0）。
                if (dismissed.value > 0f || (progress.value <= 0f && delta > 0f)) {
                    sheetScope.launch {
                        dismissed.snapTo((dismissed.value + delta / pageHpx).coerceIn(0f, 1f))
                    }
                } else {
                    val travel = (pageHpx - topReservePx - fixedOf() - padOf()).coerceAtLeast(1f)
                    sheetScope.launch {
                        progress.snapTo((progress.value - delta / travel).coerceIn(0f, 1f))
                    }
                }
            }
            // 松手后归位：过 1/3 就展开，否则回折叠。
            //
            // 动画**总是**由这里收尾，不靠 `LaunchedEffect(open)` 兜底：拖一点点就松手时
            // `setPlayerExpanded(false)` 是个 no-op（状态没变），LaunchedEffect 不会重跑，
            // 卡片会僵在手指松开的位置上 —— 这是「松手不回弹」的典型写法错误。
            val settle: () -> Unit = {
                if (dismissed.value > 0f) {
                    // 半屏（或从全屏一路拖到底）继续下滑：过两成屏高就真的收下去
                    if (dismissed.value > 0.22f) {
                        sheetScope.launch {
                            dismissed.animateTo(1f, Motion.exit(Motion.MEDIUM))
                            onExit()
                        }
                    } else {
                        sheetScope.launch {
                            dismissed.animateTo(0f, Motion.standard(Motion.MEDIUM))
                        }
                    }
                } else {
                    val toOpen = progress.value > 0.32f
                    vm.setPlayerExpanded(toOpen)
                    sheetScope.launch {
                        progress.animateTo(
                            if (toOpen) 1f else 0f,
                            Motion.standard(Motion.MEDIUM),
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    // 收下去的手感：整页跟着手指走。写在 graphicsLayer 里（绘制相位读 State），
                    // 拖动逐帧变的只有这一层的位移，页面内部不重排。
                    .graphicsLayer {
                        translationY = dismissed.value * size.height
                    },
            ) {
                // 1) 背景：全屏封面 + 底部压暗。展开后卡片不透明，它自然被完全盖住。
                //    封面同时是播放页的磨砂源：卡片上那层玻璃糊的就是它。
                //    进场时它跟着 morph 淡入 —— 迷你条那一格是 0，背后还是主页本身，
                //    所以「卡片长大」的那几帧看到的是主页，不是一块突然出现的整屏封面。
                //
                //    收下去时它**不跟着卡片一起下沉**：整页那层有 translationY，
                //    这里用同一个量反向抵消，封面就留在原地淡出 + 起雾 ——
                //    否则整块图跟卡片一起往下滑，最后一条硬边扫过整个屏幕，很生硬。
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 全部在绘制相位读：逐帧变的只有这一层的位移/透明度/雾化，
                            // 页面内部不重组（这页元素多，重组一次就是一帧卡）。
                            val fade = dismissed.value.coerceIn(0f, 1f)
                            translationY = -fade * size.height
                            alpha = morphOf() * (1f - fade * 0.85f)
                            renderEffect = if (
                                fade > 0.01f &&
                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                            ) {
                                // 22dp 封顶：再糊下去封面就成了一块纯色，反而看不出「在退场」
                                val r = fade * PlayerDim.bgFadeBlur.toPx()
                                android.graphics.RenderEffect.createBlurEffect(
                                    r, r, android.graphics.Shader.TileMode.CLAMP,
                                ).asComposeRenderEffect()
                            } else {
                                null
                            }
                        }
                        .hazeSource(pageHaze),
                ) {
                    PlayerArtwork(cover = cover, progress = progressOf)
                }
                // 雾化那一层：收下去的最后一程，封面先化成一片底色，再整层退场。
                // 用底色而不是黑色遮罩：这里是「淡出」，不是「压暗」。
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val fade = dismissed.value.coerceIn(0f, 1f)
                            alpha = fade * fade
                        }
                        .background(MaterialTheme.colorScheme.background),
                )

                // 2) 出口 ⌄ 不在这里画：它被下面那条「卡上沿补色带」刷掉过。
                //    位置、层级、可见性都由 AppRoot 在页面之上那一层负责（见那边的注释），
                //    两档共用同一颗，进出两档时按钮不会换位置、也不会出现两颗。

                // 3) 卡片本体。
                //
                // 布局高度恒定 = 整页，卡片的「多高」只体现在绘制阶段的裁剪上。
                // 这一点是被实测逼出来的：先按折叠高度去限制 Column，走带（最后一个孩子）
                // 拿不到足够约束，量出来的高度永远偏小，于是「压得越矮 → 量得越小 → 压得更矮」，
                // 走带被裁掉一截且永远修不回来。布局给足高度、只裁绘制，测量才是真的。
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            // 卡片可见区域：底部 cardH 高的一条，顶角跟着进度收圆。
                            // 这里的坐标空间是**整页**（内容还压着 pad 那一格），
                            // 所以上沿与高度上限都要减掉 pad。
                            val p = progressOf()
                            val fixed = fixedOf()
                            val pad = padOf()
                            // 阅读模式不再减任何「固定块」：头部/走带正在退场时 onSizeChanged
                            // 会残留上一帧的高度，照旧算就会在卡片上方留一条空带。
                            val avail = if (reading) {
                                (pageHpx - pad).coerceAtLeast(0f)
                            } else {
                                (pageHpx - topReservePx - pad - fixed).coerceAtLeast(0f)
                            }
                            // cardH 只算「内容」高度：pad 是底部的让位，已经由 top 那一行
                            // 减掉了（top = size.height - pad - cardH）。这里再加一次 pad，
                            // 展开态卡片上沿会变成 topReservePx - pad，整张卡（含大封面顶部）
                            // 就被顶到悬浮顶栏后面去 —— 即「大封面顶部被遮挡」。
                            val cardH0 = (if (reading) avail * p else fixed + avail * p)
                                .coerceAtMost((size.height - pad).coerceAtLeast(0f))
                            // 进场变形：0 时这一条正好是迷你条那一格（同一个底边、同一段高度），
                            // 于是「条 → 卡片」是同一个矩形在长，而不是一个消失、一个滑进来。
                            // 阅读模式不需要「迷你条 → 卡片」的进场变形；沿用它会把卡片
                            // 顶边停在半路上，顶部看着就是一条空带。
                            val m = if (reading) 1f else morphOf()
                            val originH = miniBarBounds?.height ?: 0f
                            val originLeft = miniBarBounds?.left ?: 0f
                            val cardH = if (originH > 0f && m < 1f) {
                                androidx.compose.ui.util.lerp(originH, cardH0, m)
                            } else {
                                cardH0
                            }
                            // 迷你条左右各留了边距，卡片是通栏：这一格也跟着插值，
                            // 否则第一帧会横向「啪」地撑满。
                            val inset = if (originLeft > 0f) originLeft * (1f - m) else 0f
                            val top = size.height - pad - cardH
                            // 卡片上沿到页顶这一段要自己补底色。
                            //
                            // 顶栏的磨砂层只铺到「安全区 + 40dp 内容行」，而页面留的首屏
                            // 是「安全区 + 48dp」（多出来那 8dp 是 TopBarMetrics 里的「呼吸」）。
                            // 别的页面那一格是页面底色，看不出来；播放页背后是整屏封面，
                            // 就变成顶栏与卡片之间夹着一条封面 —— 真机截图里那条就是它。
                            // 透明度跟着展开进度走：折叠态 p=0 半点不画，封面的沉浸感不变。
                            if (p > 0f) {
                                val band = androidx.compose.ui.graphics.lerp(
                                    collapsedColor, expandedColor, p,
                                )
                                drawRect(
                                    color = band.copy(alpha = band.alpha * p * m),
                                    topLeft = androidx.compose.ui.geometry.Offset(inset, 0f),
                                    size = androidx.compose.ui.geometry.Size(
                                        (size.width - inset * 2f).coerceAtLeast(0f), top,
                                    ),
                                )
                            }
                            // 圆角也从迷你条那个值长过来：条是 Radius.xl，卡片顶上是 xxl（展开态 0）。
                            val r = androidx.compose.ui.util.lerp(
                                barCornerPx,
                                sheetCornerPx * (1f - p),
                                m,
                            )
                            cardPath.reset()
                            cardPath.addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    rect = androidx.compose.ui.geometry.Rect(
                                        inset, top, size.width - inset, size.height,
                                    ),
                                    topLeft = androidx.compose.ui.geometry.CornerRadius(r, r),
                                    topRight = androidx.compose.ui.geometry.CornerRadius(r, r),
                                    bottomRight = androidx.compose.ui.geometry.CornerRadius.Zero,
                                    bottomLeft = androidx.compose.ui.geometry.CornerRadius.Zero,
                                ),
                            )
                            clipPath(cardPath) {
                                this@drawWithContent.drawContent()
                            }
                        },
                ) {
                    // 玻璃层：把身后的封面糊掉（无 tint，非 iOS 玻璃皮肤 / 关掉实时毛玻璃时是 no-op）
                    Box(Modifier.matchParentSize().asmrBackdropBlur(pageHaze))
                    // 卡片底色：独立一层画在玻璃上，仍然逐帧跟着展开进度变（进度只在绘制相位读）
                    Box(
                        Modifier.matchParentSize().drawBehind {
                            val p = progressOf()
                            drawRect(
                                color = androidx.compose.ui.graphics.lerp(
                                    collapsedColor, expandedColor, p,
                                ),
                            )
                        },
                    )
                    // 变形头几帧的「原样迷你条」：直接压在卡片内容上（zIndex），
                    // 随 morph 在前一半里淡出干净。有了它，「条」和「卡片」之间
                    // 不存在「条已经没了、卡片还没长到那儿」的空档 —— 两端是同一格。
                    //
                    // 淡出之后必须**从组合里摘掉**，不能只靠 alpha=0：Compose 的命中测试
                    // 不看 alpha，这一层照样吃点击。它正好压在折叠态的走带上（同一个底边、
                    // 整条全宽），而里面是完整的 MiniPlayerCard（pointerInput + clickable，
                    // 回调还是空 lambda）—— 表现就是「进度条和播放键都在，点上去毫无反应，
                    // 而再往上一行的书签却能点」。2026-11-22 用户报的就是这个。
                    //
                    // 判据只用 morph 到 1：两条淡出曲线最晚在 0.80 处就归零了，
                    // 等 1 保证那一刻 alpha 已经是 0，摘掉不会有视觉跳变。
                    var barSnapGone by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        androidx.compose.runtime.snapshotFlow { morph.value }
                            .collect { v -> if (v >= 1f) barSnapGone = true }
                    }
                    if (miniBarBounds != null && !barSnapGone) {
                        Box(
                            Modifier
                                .offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        miniBarBounds.left.roundToInt(),
                                        miniBarBounds.top.roundToInt(),
                                    )
                                }
                                .width(with(density) { miniBarBounds.width.toDp() })
                                .height(with(density) { miniBarBounds.height.toDp() })
                                .zIndex(3f)
                                // 纯装饰层：变形那几百毫秒里别让读屏念出第二份曲名与播放键
                                .clearAndSetSemantics { }
                                .graphicsLayer {
                                    alpha = barSnapAlphaOf()
                                },
                        ) {
                            // 上滑那一次：条里这颗封面已经「被取走」（飞行的那颗在下面），
                            // 留着就是同一张封面一静一动两个。
                            CompositionLocalProvider(
                                com.kiite.player.ui.theme.LocalBarCoverHidden provides liftDriven,
                            ) {
                                miniBar()
                            }
                        }
                    }
                    // 上滑那颗飞行的封面：从迷你条那一格（36dp）一路长到曲目卡封面那一格（72dp）。
                    //
                    // 为什么不直接用卡片自己那颗：卡片内容是按底边对齐的，半程时卡片顶边
                    // 还横在封面中段，卡片那颗会被自己的裁剪切掉一半 —— 抓帧就是「半个封面」。
                    // 飞的这颗不参与卡片的裁剪，落点那一帧再和卡片那颗精确重合（同一张图、
                    // 同一个矩形），肉眼读到的就是「同一张封面一路飞过去」。
                    if (liftDriven && miniBarBounds != null && cardCoverRect.width > 0f) {
                        cover?.let { c ->
                            FlyingCover(
                                cover = c,
                                from = miniBarBounds,
                                to = cardCoverRect,
                                morphOf = morphOf,
                            )
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxSize()
                            // 底部让位放在布局相位：scroll 内容在 pageH - pad 的区域里排，
                            // pad 逐帧变也不会触发重组。
                            .sheetBottomInset(padOf)
                            // 变形前半程内容先不出现（原因见 cardContentAlphaOf 的注释）
                            .graphicsLayer { alpha = cardContentAlphaOf() },
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                    // 抓手：只在折叠时出现；点一下也能展开，不必非得会滑。
                    //
                    // 用 AnimatedVisibility 而不是 if：它的高度要参与 fixedOf 的求和，
                    // 直接 if 掉会让「固定块总高」在展开那一刻瞬间少 20dp，内容跟着跳一下。
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !open,
                        enter = collapseEnter(),
                        exit = collapseExit(),
                        modifier = Modifier.onSizeChanged { gripPx = it.height },
                    ) {
                        SheetGrip(
                            onExpand = { vm.setPlayerExpanded(true) },
                            dragState = dragState,
                            onDragStopped = settle,
                        )
                    }
                    // 档位之间不再是 Crossfade（两套完整布局同时半透明叠在一起，
                    // 抓帧就是「台本、四格、走带各画两遍」的鬼影，用户读成「割裂」）。
                    //
                    // 换成 AnimatedContent + **每一档各自一个共享元素作用域**：
                    // 两档的封面用的是同一个 key，但注册在不同的 AnimatedVisibilityScope 里，
                    // 于是切换档位时封面走的是 sharedBounds 的 boundsTransform ——
                    // 位置和尺寸一起平滑插值（72dp ⇄ 132dp ⇄ 整页大图），不是「啪」地换大小。
                    // 其它元素（标题、四格、走带）只做一次短淡入，交接缝窄到不形成鬼影。
                    androidx.compose.animation.AnimatedContent(
                        targetState = isBig,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(Motion.enter(Motion.FLASH)) +
                                androidx.compose.animation.scaleIn(
                                    animationSpec = Motion.enter(Motion.FLASH),
                                    initialScale = 0.97f,
                                )) togetherWith androidx.compose.animation.fadeOut(Motion.exit(Motion.FLASH))
                        },
                        label = "playerLayout",
                    ) { big ->
                    // 这一档自己的 AnimatedVisibilityScope：封面共享元素靠它判断「在场」
                    CompositionLocalProvider(
                        com.kiite.player.ui.theme.LocalSharedElementScope provides this,
                    ) {
                if (big) {
                    // 全页大图：封面居中放大，台词卡整个让位。点封面或侧滑返回回到大封面档
                    // 尺寸先算好再进 Column：进列作用域后 maxWidth/maxHeight 需要显式接收者
                    val artTarget = minOf(pageW - PlayerDim.box48, pageH - PlayerDim.box340)
                        .coerceAtLeast(PlayerDim.box220)
                    Column(Modifier.fillMaxSize()) {
                        if (showPlaylist || showImages) {
                            // 大图档里四格仍然在场，但面板以前没有落点 —— 点「播放列表」只改了
                            // 状态，屏幕上还是那张大图，看着像按钮坏了。
                            // 现在面板直接占掉大图的位置：四格与走带原地不动，点「查看台本」
                            // 或侧滑返回就回到大图，不用先退回其他档位。
                            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.xs)) {
                                contentCard()
                            }
                        } else {
                            val artSize by androidx.compose.animation.core.animateDpAsState(
                                targetValue = artTarget,
                                animationSpec = Motion.standard(Motion.MEDIUM),
                                label = "art",
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // 点大图继续循环回紧凑档（侧滑返回则是退回大封面档）
                                    Box(Modifier.clickable { vm.setPlayerLayout("classic") }) {
                                        // 全页大图档的封面也是「正在播放封面」的共享元素，
                                        // 并且和另外两档用的是**同一个 key**。两档各在自己的
                                        // AnimatedVisibilityScope 里注册，配对成立后封面在
                                        // 切档时走 boundsTransform：位置与尺寸一起插值，
                                        // 不再是「整页大图」直接跳成 72dp 的小方块。
                                        CoverBox(
                                            cover,
                                            artSize,
                                            Radius.xxl,
                                            playing = ui.isPlaying,
                                            shared = true,
                                        )
                                    }
                                    Spacer(Modifier.height(Spacing.gap14))
                                    Text(
                                        current.baseName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        workCode,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        arrivingQuickActions()
                        arrivingControls()
                    }
                } else {
                    // 阅读模式不再整页换一套界面：头部、四格、走带在原地收起，
                    // 台本卡因为 weight(1f) 拿到腾出来的高度，一帧帧长大。
                    // 这样卡片是同一个实例，ScriptPanel 不重挂，滚动位置和当前句都在。
                    // 不 fillMaxSize：这一列的高度要被外层 Arrangement.Bottom 当成「内容高度」，
                    // 填满整页的话折叠态就会露出整页的下半截。
                    Column(Modifier.fillMaxWidth()) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !reading && !showPlaylist,
                            enter = collapseEnter(),
                            exit = collapseExit(),
                            modifier = Modifier.onSizeChanged { topBlockPx = it.height },
                        ) {
                            Column {
                                // 拖动只挂在头部：台本卡里有可滚的列表，整页 draggable 会把
                                // 上下滑动手势抢走（列表再也滚不动）。
                                Box(
                                    Modifier
                                        .draggable(
                                            state = dragState,
                                            orientation = Orientation.Vertical,
                                            enabled = canCollapse,
                                            onDragStopped = { settle() },
                                        ),
                                ) {
                                    // 折叠时用紧凑封面：后排那张大缩略图和全屏背景是同一张图，
                                    // 叠着看像重复画了两遍
                                    header(open && layout != "classic")
                                }
                                arrivingQuickActions()
                            }
                        }
                        // 台本 / 播放列表 / 图片：高度 = 卡片高度 - 固定那几块。
                        // 折叠时是 0，展开时吃满剩下的空间；高度写在 layout 相位里，
                        // 拖动时逐帧变的只有这一块，头部与走带不重排。
                        Box(
                            Modifier
                                .sheetHeight(
                                    // 阅读模式里走带整块退场，别再为它预留 132dp —— 那一段
                                    // 正是卡片里台本上方那条空白。
                                    reservePx = {
                                        if (reading) 0 else with(density) { PlayerDim.sheetControlsMin.toPx() }.toInt()
                                    },
                                ) {
                                    val avail = if (reading) {
                                        (pageHpx - padOf()).coerceAtLeast(0f)
                                    } else {
                                        (pageHpx - topReservePx - padOf() - fixedOf()).coerceAtLeast(0f)
                                    }
                                    (avail * progressOf()).roundToInt()
                                }
                                .fillMaxWidth()
                                .clipToBounds(),
                        ) {
                            contentCard()
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !reading,
                            enter = collapseEnter(),
                            exit = collapseExit(),
                        ) {
                            // heightIn(min=)：破一个测量死锁。
                            //
                            // 台本卡的高度 = 剩余空间，它排在走带前面；一旦「固定块总和」暂时偏小
                            // （切页回来第一帧，孩子刚挂上还没量到），台本就会把空间吃光，走带被量成
                            // 0 高度 —— 而它的高度正是那个总和的一部分，于是自己把自己锁死在 0 上，
                            // 走带整排消失。给一个下限，第一帧就能拿到真实高度，下一帧自动收敛。
                            Box(
                                Modifier
                                    .heightIn(min = PlayerDim.sheetControlsMin)
                                    .onSizeChanged { controlsPx = it.height },
                            ) {
                                arrivingControls()
                            }
                        }
                    }
                }
            }
            }   // CompositionLocalProvider（本档的共享元素作用域）
            }   // Column（卡片内容）
            }   // Box（卡片本体：裁剪 + 玻璃 + 底色）
            }   // Box（封面 + 卡片）
        }
    }
}

/**
 * 阅读模式的顶栏：只有「退出 / 曲名 / 时间 / 播放暂停」。
 *
 * 比正常布局少了约 500dp 的封面卡与控制卡——一集 200 句的台本，
 * 正常布局只放得下八九行，这里能放三十行，跟读一整段不用一直滑。
 */
@Composable
private fun ReadModeBar(
    title: String,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onToggle: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(asmrRowColor())
            // 阅读模式卡片顶到屏幕边缘，状态栏那格只能由这里让出；
            // 不再额外让出整条顶栏高度，免得台本上方多一段空。
            .padding(
                start = Spacing.xs,
                end = Spacing.sm,
                top = LocalTopBarMetrics.current.safeTop,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Default.FullscreenExit, "退出阅读模式")
        }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatTime(positionMs) + " / " + formatTime(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onToggle) {
            androidx.compose.animation.AnimatedContent(
                targetState = playing,
                transitionSpec = { fadeThrough() },
                label = "readPlayPause",
            ) { p ->
                Icon(
                    if (p) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (p) "暂停" else "继续播放",
                )
            }
        }
    }
    // 2dp 的进度线：阅读模式下没有控制器，至少要能看出「播到哪儿了」。
    // 用自绘细线而不是 LinearProgressIndicator，后者自带内边距会多占十几 dp。
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(Stroke.thin).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(frac).height(Stroke.thin).background(MaterialTheme.colorScheme.primary))
    }
}

/** 播放页头部：新版 = 大封面居中；经典 = 紧凑一行，可随时切换。 */
/** 倍数显示：0.5 / 0.75 / 1.0 / 1.25 … */
private fun formatSpeed(v: Float): String = when (v) {
    0.5f -> "0.5"
    0.75f -> "0.75"
    1.0f -> "1.0"
    1.25f -> "1.25"
    1.5f -> "1.5"
    1.75f -> "1.75"
    2.0f -> "2.0"
    2.5f -> "2.5"
    3.0f -> "3.0"
    else -> String.format(java.util.Locale.US, "%.2f", v)
}

/**
 * 曲目卡（参考图第一块）：封面 + 标题 / 编号 / 播放状态 + 收藏 + 更多。
 *
 * 封面本身就是「切换大小」的按钮——新版布局里没有独立的布局按钮，
 * 而封面对用户就是这一块内容的代表，点它缩放最直觉：
 * 大 = 132dp（图示尺寸），小 = 72dp（省下来的高度全给台词）。
 */
@Composable
private fun TrackCard(
    bigCover: Boolean,
    track: TrackEntry,
    cover: String?,
    subtitle: String,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    playing: Boolean,
    isVideo: Boolean,
    videoPlayer: androidx.media3.common.Player?,
    onToggleSize: () -> Unit,
    onVideoFullscreen: () -> Unit,
    /** 点「⋯」：打开页面级的「更多」抽屉（菜单内容只有一份，见 PlayerMoreSheet）。 */
    onMore: () -> Unit,
    calibration: (@Composable () -> Unit)? = null,
    /**
     * 这张卡的封面是否作为「迷你条封面」的共享元素。
     *
     * 必须由调用方按当前布局档位传进来，不能恒为 true：布局三档里有**两张**封面
     * （紧凑档这张卡的、全页大图档居中的），切换档位时 Crossfade 会让新旧两档短暂
     * 同时在场 —— 同一个 key 注册两次，配对逻辑会以为「两端都在场」而直接各画各的，
     * 严重时还会抛异常（见 CoverBox 的 shared 参数注释）。
     */
    sharedCover: Boolean = true,
    /** 封面在窗口里的矩形（上滑时那颗飞行的封面要落到这格）。 */
    onCoverBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
) {
    var more by remember { mutableStateOf(false) }
    // 尺寸做补间：从 132 直接跳到 72 会让整页内容「闪一下」
    val size by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (bigCover) PlayerDim.box132 else PlayerDim.box72,
        animationSpec = Motion.standard(Motion.MEDIUM),
        label = "cover",
    )
    AsmrCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.itemGap)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(size)
                        .clip(asmrShape(Radius.md))
                        // 上报落点：上滑飞行的那颗封面就落在这格（只变档位时才上报一次）
                        .onGloballyPositioned { onCoverBounds(it.boundsInRoot()) }
                        // 单击切档：立即响应。之前为兼容「双击全屏看封面」用
                        // detectTapGestures，onTap 会被压到 300ms 双击窗口之后才触发，
                        // 用户点一下像没反应 —— 这就是「切换异常卡顿」的主因。
                        // 全屏看封面仍在「更多 → 全屏看封面」，不在封面上做双击了。
                        .clickable { onToggleSize() },
                ) {
                    MediaBox(
                        cover, size, Radius.xs, playing, isVideo, videoPlayer, onVideoFullscreen,
                        sharedCover = sharedCover,
                        // 解码尺寸固定成最大档：72↔132 补间时不再逐帧触发新请求
                        requestSize = PlayerDim.box132,
                    )
                }
                Spacer(Modifier.width(Spacing.gap14))
                // 右侧这一列和封面等高：标题在顶、状态行贴在封面底部，
                // 这样大封面时不至于右下方空出一大块（参考图就是这个对齐）
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = size),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            track.baseName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = if (bigCover) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(Spacing.itemGap))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NowPlayingPill(playing)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(PlayerDim.box34),
                        ) {
                            Icon(
                                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (favorite) "取消收藏" else "收藏这个作品",
                                Modifier.size(PlayerDim.box19),
                                tint = if (favorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = onMore, modifier = Modifier.size(PlayerDim.iconBtn34)) {
                            Icon(Icons.Default.MoreHoriz, "更多", Modifier.size(IconSize.md))
                        }
                    }
                }
            }
            calibration?.let {
                HorizontalDivider(
                    Modifier.padding(top = Spacing.sm),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                it()
            }
        }
    }
}

/** 「正在播放」胶囊：播放中带声波图标，暂停时换暂停图标，颜色不变（这是状态不是警告）。 */
@Composable
private fun NowPlayingPill(playing: Boolean) {
    Row(
        Modifier
            .clip(asmrShape(Radius.xl))
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = if (playing) 0.14f else 0.08f),
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (playing) Icons.Default.GraphicEq else Icons.Default.Pause,
            null,
            Modifier.size(PlayerDim.box13),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            if (playing) "正在播放" else "已暂停",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/** 四格快捷入口里的一个：图标 + 文字，选中态换主色。 */
@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, label, Modifier.size(IconSize.md), tint = tint)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

/**
 * 台词 / 播放列表 / 图片的外壳卡片。
 *
 * 卡头只有一行（图标 + 标题 + 右侧状态），比原来那套「状态条 + 校准条 + chips 行」
 * 省下不少高度——台本区现在是一整块，不再被切成几段。
 *
 * [inset] 与 [corner] 是给「台本放大」用的：放大过程里卡片的边距与圆角同时插值，
 * 观感上是同一张卡在长大；[header] 让阅读模式用操作带顶替标题行，卡片仍是同一块表面。
 */
@Composable
private fun ScriptCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    inset: androidx.compose.ui.unit.Dp = Spacing.lg,
    corner: androidx.compose.ui.unit.Dp = Radius.xl,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = inset)
            .clip(asmrShape(corner))
            .background(asmrRowColor()),
    ) {
        if (header != null) {
            header()
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.gap14, end = Spacing.gap14, top = Spacing.gap10, bottom = Spacing.itemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    Modifier.size(PlayerDim.box15),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

/**
 * 上滑时那颗「飞」的封面。
 *
 * 起点 = 迷你条里那颗 36dp 封面（迷你条现在一直留在组合里，它的矩形随 [from] 传进来），
 * 落点 = 曲目卡封面那一格（[to]，由曲目卡自己上报，换档时会跟着变）。
 *
 * 全程只读 [morphOf]（绘制相位）：缩放和位移都在 graphicsLayer 里做，不参与布局，
 * 所以拖动逐帧变的是这一颗小图的矩阵，不是整页重组。
 */
@Composable
private fun FlyingCover(
    cover: String,
    from: androidx.compose.ui.geometry.Rect,
    to: androidx.compose.ui.geometry.Rect,
    morphOf: () -> Float,
) {
    val d = LocalDensity.current
    // 迷你条那格封面的内缩与尺寸：和 AppRoot 的 MiniPlayer 同一套值
    // （Row 的 start = gap10 / top = itemGap，封面 36dp）。
    val gapX = with(d) { Spacing.gap10.toPx() }
    val gapY = with(d) { Spacing.itemGap.toPx() }
    val barCoverPx = with(d) { PlayerDim.barCover.toPx() }
    val src = androidx.compose.ui.geometry.Rect(
        left = from.left + gapX,
        top = from.top + gapY,
        right = from.left + gapX + barCoverPx,
        bottom = from.top + gapY + barCoverPx,
    )
    Box(
        Modifier
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    to.left.roundToInt(),
                    to.top.roundToInt(),
                )
            }
            .size(
                with(d) { to.width.toDp() },
                with(d) { to.height.toDp() },
            )
            .graphicsLayer {
                val t = morphOf().coerceIn(0f, 1f)
                // 全程按「中心 + 比例」插值：36dp 的中心 → 72dp 的中心，比例 0.5 → 1
                val s0 = if (to.width > 0f) src.width / to.width else 1f
                val s = androidx.compose.ui.util.lerp(s0, 1f, t)
                scaleX = s
                scaleY = s
                translationX = androidx.compose.ui.util.lerp(src.center.x, to.center.x, t) - to.center.x
                translationY = androidx.compose.ui.util.lerp(src.center.y, to.center.y, t) - to.center.y
                // 最后一成交给卡片自己那颗（同一张图、同一格）：叠着半透明会发灰
                alpha = (1f - (t - 0.90f) / 0.10f).coerceIn(0f, 1f)
            }
            .zIndex(4f)
            .clip(asmrShape(Radius.xs)),
    ) {
        AsyncImage(
            model = java.io.File(cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CoverBox(
    cover: String?,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    playing: Boolean = false,
    /**
     * 是否作为「迷你条封面」的共享元素目标。
     *
     * 播放页有**两张**封面参与这件事：紧凑/大封面档在曲目卡里，全页大图档是居中那一张。
     * 任何时刻只能有一张开启 —— 布局三档之间是 Crossfade，切档时新旧两档会短暂同时
     * 组合，同一个 key 注册两次，配对逻辑会以为「两端都在场」，封面反而完全不飞。
     * 所以两处都用 `layout` 判断，保证互斥（见两处调用点的注释）。
     */
    shared: Boolean = false,
    /**
     * 解码尺寸。给了固定值之后，容器尺寸在动画里变化也不会换 request key。
     *
     * 这是「点封面切档卡顿」的实测来源：以前 AsyncImage 不带尺寸，Coil 按当前约束
     * 算请求尺寸 —— 卡片封面在 72↔132 之间补间时，每一帧都是一个新尺寸（新 key），
     * 一路 cache miss、重新裁切/解码，一秒钟能产生十几个请求。固定成最大档的尺寸，
     * 解码只发生一次，之后只是把位图缩放进不同大小的槽位（纯 GPU 缩放）。
     */
    requestSize: androidx.compose.ui.unit.Dp? = null,
) {
    // 飞行期间不要底板：被共享的只有图片，底板留在落点就是一块目标尺寸的灰方块
    // （抓帧：灰方块 + 小一号的封面叠在它左下角）。落地后图片盖满槽位，底板本来就看不见。
    // 但开关不能瞬时切 —— 起飞 / 落地那一帧灰方块「啪」地消失是可见的闪跳，改成淡出淡回。
    val flying = LocalCoverFlight.current
    val plateAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shared && flying) 0f else 1f,
        animationSpec = Motion.standard(Motion.SHORT),
        label = "playerCoverPlate",
    )
    val ctx = LocalContext.current
    val px = requestSize?.let { with(LocalDensity.current) { it.roundToPx() } } ?: 0
    val fixedRequest = remember(cover, px) {
        if (cover == null || px <= 0) {
            null
        } else {
            ImageRequest.Builder(ctx).data(File(cover)).size(px, px).build()
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(asmrShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = plateAlpha.value))
            .then(asmrBorder(asmrShape(corner))),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            AsyncImage(
                model = fixedRequest ?: File(cover),
                contentDescription = "专辑封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().let {
                    if (shared) it.sharedNowPlayingCover() else it
                },
            )
        } else {
            Icon(
                Icons.Default.LibraryMusic,
                null,
                Modifier.size(size / 3),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 只在播放时画：暂停时这条黑色底带是静止的，在新版卡片里显得又重又没意义
        if (LocalIsBright.current && playing) {
            EqStrip(playing, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * 「更多」的分组操作面板（底部抽屉）。
 *
 * 以前是一个贴着「⋯」弹出的下拉菜单：分组全靠几行小标签，条目一多就顶到屏幕外，
 * 单手也够不到上面的项。现在统一成底部抽屉，按 播放 / 倍数 / 台本 / 显示 分组，
 * 内容可滚 —— 和「排序 / 筛选」那些弹层是同一套交互语言。
 */
@Composable
private fun PlayerMoreSheet(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    com.kiite.player.ui.components.AppBottomSheet(
        onDismissRequest = onDismiss,
        title = "更多",
        maxContentHeight = PlayerDim.box600,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        Spacer(Modifier.height(Spacing.lg))
    }
}

/**
 * 作品详细：上半是 DLsite 抓到的元信息（没有联网信息就退化成目录名），
 * 下半是这条音频自己的实情 —— 文件、格式、体积、时长、台本，免得为了看这些退回曲库。
 */
@Composable
private fun WorkInfoSheet(
    dlsite: com.kiite.player.core.DlsiteWork?,
    project: ProjectEntry?,
    track: TrackEntry,
    onDismiss: () -> Unit,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    com.kiite.player.ui.components.AppBottomSheet(
        onDismissRequest = onDismiss,
        title = "作品详细",
        maxContentHeight = PlayerDim.box600,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            Text(
                dlsite?.title ?: project?.name ?: track.folderName,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            InfoLine("编号", dlsite?.originalCode ?: dlsite?.code ?: project?.code ?: "—")
            if (dlsite?.translationCode != null && dlsite.translationCode != dlsite.originalCode) {
                InfoLine("译文版本", dlsite.translationCode)
            }
            InfoLine("社团", dlsite?.circle ?: "—")
            InfoLine("发售", dlsite?.releaseDate ?: "—")
            if (!dlsite?.voiceActors.isNullOrEmpty()) {
                InfoLine("声优", dlsite.voiceActors.joinToString("、"))
            }
            if (!dlsite?.tags.isNullOrEmpty()) {
                InfoLine("标签", dlsite.tags.joinToString(" / "))
            }
            if (dlsite?.r18 == true) {
                InfoLine("分级", "成人向")
            }
            InfoLine(
                "DLsite",
                when {
                    dlsite == null -> "没有联网信息（只有本地文件）"
                    dlsite.owned -> "作品页标记为已购买"
                    else -> "作品页未标记已购"
                },
            )
            if (!dlsite?.synopsis.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text("简介", style = MaterialTheme.typography.labelMedium)
                Text(
                    dlsite.synopsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            Text("本地文件", style = MaterialTheme.typography.labelMedium)
            InfoLine("文件", track.name)
            InfoLine("格式", track.formats.joinToString(" / "))
            InfoLine("大小", com.kiite.player.core.SizeFormat.text(track.sizeBytes))
            InfoLine("时长", track.durationMs?.takeIf { it > 0 }?.let { formatTime(it) } ?: "—")
            InfoLine(
                "台本",
                when {
                    track.hasTimedScript -> "含时间轴（${track.scripts.size} 份）"
                    track.hasScript -> "${track.scripts.size} 份"
                    else -> "无"
                },
            )
            if (project != null) {
                InfoLine("目录", project.name)
                InfoLine(
                    "内容",
                    "${project.trackCount} 首 · ${project.chapters.size} 章 · ${project.scriptCount} 份台本",
                )
            }
            Text(
                track.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dlsite?.productUrl != null) {
                TextButton(onClick = { uriHandler.openUri(dlsite.productUrl) }) { Text("在浏览器打开作品页") }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** 一行「标签 / 值」：标签定宽对齐，长值自己折行。 */
@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
    ) {
        Text(
            label,
            Modifier.width(PlayerDim.box48),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 音效弹层：复用设置页那份 [com.kiite.player.ui.components.AudioFxControls]，只是排得紧一点。 */
@Composable
private fun AudioFxSheet(
    fx: com.kiite.player.core.AudioFxSettings,
    onApply: (com.kiite.player.core.AudioFxSettings) -> Unit,
    say: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    com.kiite.player.ui.components.AppBottomSheet(
        onDismissRequest = onDismiss,
        title = "音效",
        maxContentHeight = PlayerDim.box600,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            com.kiite.player.ui.components.AudioFxControls(
                fx = fx,
                onApply = onApply,
                say = say,
                compact = true,
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun MenuLabel(text: String) {    Text(
        text,
        Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.itemGap),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 「更多」抽屉里的一项：图标 + 短标签，同一份数据也给「⋯」下拉复用。 */
private data class MoreItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 「更多」抽屉的图标砖：每行固定 4 列，用 [Row] 切块而不是 FlowRow ——
 * FlowRow 在最后一行不满时会左对齐、宽度也参差，而这里要的是整齐的 4 格网。
 */
@Composable
private fun MoreIconGrid(
    groups: List<Pair<String, List<MoreItem>>>,
    onDone: () -> Unit,
    /** 某些分组后面要插整行控件（倍速滑杆），按分组名挂上去。 */
    inlineBlocks: Map<String, @Composable () -> Unit> = emptyMap(),
) {
    groups.forEach { (title, items) ->
        MenuLabel(title)
        items.chunked(4).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                row.forEach { item ->
                    MoreTile(
                        item = item,
                        modifier = Modifier.weight(1f),
                    ) {
                        onDone()
                        item.onClick()
                    }
                }
                // 补齐空位：不满一行时也让上一项的宽度与满行一致，
                // 否则「显示」分组最后一行会比上面几行宽一圈
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        inlineBlocks[title]?.invoke()
    }
}

/**
 * 播放倍数：一行三个元素，左减右加、中间显示当前倍率。
 *
 * 以前是 7~9 块倍率砖平铺，占两行还看不出「现在是几倍」；这里收成一行，
 * 加减按 SPEED_STEPS 逐档走，到头就禁用按钮（不让它越界后静默无反应）。
 */
@Composable
private fun SpeedStepper(speed: Float, onStep: (Int) -> Unit) {
    val steps = MainViewModel.SPEED_STEPS
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.FilledTonalIconButton(
            onClick = { onStep(-1) },
            enabled = speed > steps.first(),
        ) {
            Icon(Icons.Default.Remove, contentDescription = "减慢")
        }
        Text(
            text = formatSpeed(speed),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        androidx.compose.material3.FilledTonalIconButton(
            onClick = { onStep(1) },
            enabled = speed < steps.last(),
        ) {
            Icon(Icons.Default.Add, contentDescription = "加快")
        }
    }
}

@Composable
private fun MoreTile(item: MoreItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = asmrShape(Radius.sm)
    Column(
        modifier
            .padding(Spacing.xxs)
            .clip(shape)
            .then(
                if (item.selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = AccentFill.selected))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            modifier = Modifier.size(IconSize.md),
            tint = if (item.selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (item.selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/**
 * A-B 微调行：显示当前秒数，右侧四个小按钮按 ±0.1s / ±1s 挪。
 * 用自绘 chip 而不是 TextButton：后者有 64dp 最小宽度，四个并排会把菜单撑爆。
 */
@Composable
private fun AbNudgeRow(value: String, onNudge: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        AbNudgeChip("−1s") { onNudge(-1_000L) }
        AbNudgeChip("−0.1s") { onNudge(-100L) }
        AbNudgeChip("+0.1s") { onNudge(100L) }
        AbNudgeChip("+1s") { onNudge(1_000L) }
    }
}

@Composable
private fun AbNudgeChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(asmrShape(Radius.xs))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 菜单里的「当前生效」勾选行。 */
@Composable
private fun SleepStopItem(text: String, checked: Boolean, onClick: () -> Unit) {    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        trailingIcon = {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    "当前生效",
                    Modifier.size(IconSize.sm),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        },
    )
}

/** 视频用画面，音频用封面。 */
@Composable
private fun MediaBox(
    cover: String?,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    playing: Boolean,
    isVideo: Boolean,
    videoPlayer: androidx.media3.common.Player?,
    onVideoFullscreen: () -> Unit,
    sharedCover: Boolean = false,
    requestSize: androidx.compose.ui.unit.Dp? = null,
) {
    if (isVideo && videoPlayer != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(asmrShape(corner))
                .then(asmrBorder(asmrShape(corner))),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = false
                        player = videoPlayer
                    }
                },
                update = { it.player = videoPlayer },
            )
        }
    } else {
        CoverBox(cover, size, corner, playing, shared = sharedCover, requestSize = requestSize)
    }
}

/**
 * 给视频画面挂上手势（只处理「画面」区域的触摸，控制器自己的操作照旧）：
 * - 双击左/右半屏：±10 秒
 * - 长按：临时 2 倍速，松手恢复到原来的倍速
 * - 上下滑：左半屏调音量、右半屏调亮度
 */
/**
 * 视频手势 OSD 状态：View 层手势直接更新，Compose 层渲染。
 * 手势发生在 PlayerView（SurfaceView）的触摸层，Compose 摸不到，
 * 所以用这个桥接对象把「音量 43%」这类即时反馈带回 Compose 叠层。
 */
class VideoOsdState {
    var icon by mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector>(Icons.Default.VolumeUp)
        private set
    var text by mutableStateOf("")
        private set
    var visible by mutableStateOf(false)
        private set

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideRunnable = Runnable { visible = false }

    fun show(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, durationMs: Long = 700) {
        this.icon = icon
        this.text = text
        this.visible = true
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, durationMs)
    }

    fun dispose() {
        handler.removeCallbacks(hideRunnable)
    }
}

/** 手势反馈 OSD：半透明胶囊（图标 + 数值），出现/消失带过渡。 */
@Composable
private fun VideoOsdOverlay(osd: VideoOsdState, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedVisibility(
        visible = osd.visible,
        enter = popIn(),
        exit = popOut(),
        modifier = modifier,
    ) {
        androidx.compose.material3.Surface(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
            shape = asmrShape(Radius.pill),
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.gap10),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(osd.icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(IconSize.md))
                Text(osd.text, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 首次进视频页的 4 格手势引导（只弹一次）。 */
@Composable
private fun VideoGestureGuideDialog(onDismiss: () -> Unit) {
    val items = listOf(
        Triple(Icons.Default.TouchApp, "双击画面", "左半边后退 10 秒，右半边快进 10 秒"),
        Triple(Icons.Default.Speed, "长按画面", "2 倍速播放，松手恢复"),
        Triple(Icons.Default.VolumeUp, "左侧上下滑动", "调节音量"),
        Triple(Icons.Default.Brightness6, "右侧上下滑动", "调节亮度"),
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = asmrShape(Radius.xl),
        title = { Text("视频手势") },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.md),
            ) {
                Text("这些手势没有按钮，全靠手感——第一次用可以先记两个：", style = MaterialTheme.typography.bodySmall)
                items.forEach { (icon, title, desc) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(IconSize.md), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.gap10))
                        androidx.compose.foundation.layout.Column {
                            Text(title, style = MaterialTheme.typography.labelLarge)
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

private fun installVideoGestures(
    view: androidx.media3.ui.PlayerView,
    osd: VideoOsdState? = null,
    onEdgeBack: () -> Unit = {},
) {
    val ctx = view.context
    var boostFrom = 0f
    var edgeBackFired = false
    val gd = android.view.GestureDetector(
        ctx,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent): Boolean {
                edgeBackFired = false
                return false
            }

            /**
             * 侧滑返回：从屏幕左右边缘起手、横向位移够大且明显大于纵向。
             * 必须在视频画面的触摸层做——SurfaceView 会盖住 Compose 叠层。
             */
            private fun maybeEdgeBack(
                e1: android.view.MotionEvent,
                e2: android.view.MotionEvent,
            ): Boolean {
                if (edgeBackFired || view.width <= 0) return false
                val edge = view.width * 0.08f
                val fromEdge = e1.x < edge || e1.x > view.width - edge
                val totalDx = e2.x - e1.x
                val totalDy = e2.y - e1.y
                if (!fromEdge) return false
                if (kotlin.math.abs(totalDx) < view.width * 0.15f) return false
                if (kotlin.math.abs(totalDx) < kotlin.math.abs(totalDy) * 1.6f) return false
                edgeBackFired = true
                onEdgeBack()
                return true
            }
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                val p = view.player ?: return false
                val delta = if (e.x < view.width / 2f) -10_000L else 10_000L
                p.seekTo((p.currentPosition + delta).coerceIn(0L, p.duration.coerceAtLeast(0L)))
                osd?.show(
                    if (delta < 0) Icons.Default.Replay10 else Icons.Default.Forward10,
                    (if (delta < 0) "后退 " else "快进 ") + 10 + " 秒",
                )
                return true
            }

            override fun onLongPress(e: android.view.MotionEvent) {
                val p = view.player ?: return
                boostFrom = p.playbackParameters.speed
                p.setPlaybackSpeed(2f)
                osd?.show(Icons.Default.Speed, "2× 倍速播放（松手恢复）", durationMs = 1_200)
            }

            override fun onScroll(
                e1: android.view.MotionEvent?,
                e2: android.view.MotionEvent,
                dx: Float,
                dy: Float,
            ): Boolean {
                if (e1 == null || view.height <= 0) return false
                if (maybeEdgeBack(e1, e2)) return true
                val delta = (e1.y - e2.y) / view.height // 上滑为正
                if (e1.x < view.width / 2f) {
                    val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE)
                        as? android.media.AudioManager ?: return true
                    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    val next = (cur + delta * max * 2f).toInt().coerceIn(0, max)
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, 0)
                    osd?.show(Icons.Default.VolumeUp, "音量 " + (next * 100 / max) + "%")
                } else {
                    val act = ctx as? android.app.Activity ?: return true
                    val attrs = act.window.attributes
                    val base = if (attrs.screenBrightness >= 0f) attrs.screenBrightness else 0.5f
                    attrs.screenBrightness = (base + delta * 2f).coerceIn(0.05f, 1f)
                    act.window.attributes = attrs
                    osd?.show(Icons.Default.Brightness6, "亮度 " + (attrs.screenBrightness * 100).toInt() + "%")
                }
                return true
            }
        },
    )
    view.setOnTouchListener { _, ev ->
        gd.onTouchEvent(ev)
        if (ev.actionMasked == android.view.MotionEvent.ACTION_UP ||
            ev.actionMasked == android.view.MotionEvent.ACTION_CANCEL
        ) {
            if (boostFrom > 0f) {
                view.player?.setPlaybackSpeed(boostFrom)
                boostFrom = 0f
            }
        }
        false
    }
}

/** mm:ss 时间文本。 */
private fun fmtMs(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    return (total / 60).toString() + ":" + (total % 60).toString().padStart(2, '0')
}

/**
 * 独立的视频播放器（参考 PotPlayer 那种形态）：黑底大画面 + 走带 + 精简工具栏。
 * 和音频播放页是两条路：这里没有封面、没有台本/图片胶囊、没有大封面布局。
 */
@Composable
private fun VideoPlayerScreen(vm: MainViewModel, onExit: () -> Unit) {
    val ui by vm.player.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val track by vm.currentTrack.collectAsStateWithLifecycle()
    val queueVersion by vm.queueVersion.collectAsStateWithLifecycle()
    val playlist = remember(queueVersion, track) { vm.currentPlaylist() }
    val index = playlist.indexOfFirst { it.path == track?.path }
    var speedMenu by remember { mutableStateOf(false) }
    var ratioMenu by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val hostActivity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    // 手势 OSD + 首次引导（只弹一次）
    val osd = remember { VideoOsdState() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { osd.dispose() } }
    if (!settings.videoGestureGuideSeen) {
        VideoGestureGuideDialog { vm.setVideoGestureGuideSeen(true) }
    }
    val duration = ui.durationMs.coerceAtLeast(1L)
    val sliderValue = dragging ?: (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 横屏看视频：隐藏系统栏让画面铺满（转回竖屏或离开本页时还原）。
    // MainActivity 已声明 configChanges，旋转不会重建 Activity，播放不中断。
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(landscape) {
        val window = hostActivity?.window
        val bars = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, hostView)
        }
        if (landscape) {
            bars?.apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (landscape) bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    val rm = resizeMode
    // 视频视图：旋转时会换布局位（重建视图），播放由前台服务继续，update 里重新挂上
    val videoView: @Composable (Modifier) -> Unit = { mod ->
        AndroidView(
            modifier = mod,
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    useController = true
                    showController()
                    keepScreenOn = true
                    setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    player = vm.playerForView
                    // 视频播放页：侧滑返回 = 回曲库页
                    installVideoGestures(this, osd) { onExit() }
                }
            },
            update = {
                it.player = vm.playerForView
                it.resizeMode = rm
            },
        )
    }

    // 顶栏：竖屏是普通一行（让开状态栏，否则右上角按钮的点击区被系统栏吃掉）；横屏半透明渐变叠加
    val header: @Composable (Modifier) -> Unit = { mod ->
        Row(
            mod
                .then(
                    if (landscape) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                                    androidx.compose.ui.graphics.Color.Transparent,
                                ),
                            ),
                        )
                    } else {
                        Modifier.statusBarsPadding()
                    },
                )
                .padding(start = Spacing.xxs, end = Spacing.itemGap, top = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.ArrowBack, "返回", tint = androidx.compose.ui.graphics.Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    track?.baseName ?: "视频",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (if (index >= 0) "第 " + (index + 1) + " / " + playlist.size + " 集 · " else "") +
                        (if (ui.durationMs > 0) fmtMs(ui.durationMs) else "--:--") +
                        (vm.progressOf(track?.path.orEmpty()).takeIf { it > 4_000 }?.let { " · 上次看到 " + fmtMs(it) } ?: ""),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = { vm.setVideoFullscreen(true) }) {
                Icon(Icons.Default.OpenInFull, "全屏", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }

    // 走带 + 工具条：竖屏是底部实体区（让开手势条）；横屏半透明渐变叠加在画面上
    val controls: @Composable (Modifier) -> Unit = { mod ->
        Column(
            mod
                .then(
                    if (landscape) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                                ),
                            ),
                        )
                    } else {
                        Modifier.navigationBarsPadding()
                    },
                )
                .padding(horizontal = Spacing.gap10, vertical = Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtMs(dragging?.let { (it * duration).toLong() } ?: ui.positionMs), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                AsmrSlider(
                    value = sliderValue,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { vm.seekTo((it * duration).toLong()) }
                        dragging = null
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.gap10),
                )
                Text(fmtMs(ui.durationMs), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.previous() }) {
                    Icon(Icons.Default.SkipPrevious, "上一集", tint = androidx.compose.ui.graphics.Color.White)
                }
                IconButton(onClick = { vm.playPause() }) {
                    Icon(
                        if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "播放/暂停",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(PlayerDim.box30),
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Default.SkipNext, "下一集", tint = androidx.compose.ui.graphics.Color.White)
                }
                Spacer(Modifier.weight(1f))
                // 倍速
                Box {
                    TextButton(onClick = { speedMenu = true }) {
                        Text(settings.playbackSpeed.toString().trimEnd('0').trimEnd('.') + "x", color = androidx.compose.ui.graphics.Color.White)
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        MainViewModel.SPEED_STEPS.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.toString().trimEnd('0').trimEnd('.') + "x") },
                                onClick = { vm.setSpeed(s); speedMenu = false },
                            )
                        }
                    }
                }
                // 画面比例
                Box {
                    TextButton(onClick = { ratioMenu = true }) {
                        Text("比例", color = androidx.compose.ui.graphics.Color.White)
                    }
                    DropdownMenu(expanded = ratioMenu, onDismissRequest = { ratioMenu = false }) {
                        listOf(
                            "适应" to androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            "裁边填满" to androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                            "拉伸" to androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,
                        ).forEach { (label, mode) ->
                            DropdownMenuItem(
                                text = { Text(if (resizeMode == mode) "● " + label else "   " + label) },
                                onClick = { resizeMode = mode; ratioMenu = false },
                            )
                        }
                    }
                }
                IconButton(onClick = { showList = true }) {
                    Icon(Icons.Default.PlaylistPlay, "播放列表", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        if (landscape) {
            // 横屏：画面铺满整屏，标题与控件改为半透明渐变叠加，不再挤占画面空间
            videoView(Modifier.fillMaxSize())
            VideoOsdOverlay(osd)
            header(Modifier.align(Alignment.TopCenter).fillMaxWidth())
            controls(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        } else {
            Column(Modifier.fillMaxSize()) {
                header(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    videoView(Modifier.fillMaxSize())
                    VideoOsdOverlay(osd)
                }
                controls(Modifier.fillMaxWidth())
            }
        }
    }

    if (showList) {
        Dialog(
            onDismissRequest = { showList = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = asmrShape(Radius.lg),
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.84f),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("播放列表", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(playlist.size.toString() + " 集", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showList = false }) { Icon(Icons.Default.Close, "关闭") }
                    }
                    Box(Modifier.weight(1f)) {
                        PlaylistPanel(
                            playlist = playlist,
                            currentPath = track?.path.orEmpty(),
                            autoScroll = true,
                            onPick = { vm.playFromPlaylist(it) },
                            onRemove = { vm.queueRemove(it) },
                            onMove = { path, delta -> vm.queueMove(path, delta) },
                        )
                    }
                }
            }
        }
    }
}

/** 图片墙：点开看大图。 */
@Composable
private fun ImagePanel(images: List<String>, onOpen: (Int) -> Unit) {
    if (images.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("这个作品没有图片", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val chunks = images.mapIndexed { i, p -> i to p }.chunked(3)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
    ) {
        chunks.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { item ->
                    val idx = item.first
                    val path = item.second
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(asmrShape(Radius.md))
                            .then(asmrBorder(asmrShape(Radius.md)))
                            .clickable { onOpen(idx) },
                    ) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

/** 全屏看图。 */
@Composable
internal fun ImageViewer(
    images: List<String>,
    start: Int,
    onClose: () -> Unit,
    /** 传了就显示「保存」，参数是当前这张图的路径（作品头图保存到相册用）。 */
    onSave: ((String) -> Unit)? = null,
) {
    var index by remember { mutableStateOf(start.coerceIn(0, (images.size - 1).coerceAtLeast(0))) }
    // 双击放大：1x ↔ 2.5x；放大后可以拖动查看，缩回 1x 才能上滑关闭
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    // 未放大时的纵向拖动累计，用来「滑走即隐藏」
    var dragY by remember { mutableStateOf(0f) }
    // 换图时复位缩放
    LaunchedEffect(index) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        dragY = 0f
    }
    fun resetZoom() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
    // 看图是真正的全屏：黑底、隐藏系统栏，底部操作条半透明叠在图片上
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (ctx as? android.app.Activity)?.window
        val bars = if (window != null) {
            androidx.core.view.WindowCompat.getInsetsController(window, hostView)
        } else {
            null
        }
        bars?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // 让对话框窗口真正铺满：默认会给状态栏区域留出高度（实测约 125px）
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.apply {
                setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                )
                addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            }
        }
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            AsyncImage(
                model = File(images[index]),
                contentDescription = "图片，双击放大，上滑关闭",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    )
                    .pointerInput(index) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.01f) resetZoom() else scale = 2.5f
                            },
                        )
                    }
                    .pointerInput(index) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 4f)
                            if (next > 1.01f) {
                                scale = next
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                // 没放大时，纵向滑走就关闭
                                dragY += pan.y
                                if (kotlin.math.abs(dragY) > 120f) onClose()
                            }
                        }
                    },
            )
            // 顶部右侧「保存」：放在底部操作条会挤掉翻页按钮，这里单列一个角标
            if (onSave != null) {
                androidx.compose.material3.IconButton(
                    onClick = { onSave(images[index]) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = PlayerDim.pad40, end = Spacing.sm)
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                            androidx.compose.foundation.shape.CircleShape,
                        ),
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.Save,
                        contentDescription = "保存到相册",
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            // 底部操作条：深色半透明 + 纯白文字（经典皮肤的主色是近黑，不能沿用默认文字色）
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f))
                    .padding(start = Spacing.itemGap, end = Spacing.itemGap, top = Spacing.itemGap, bottom = Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val white = androidx.compose.ui.graphics.Color.White
                TextButton(onClick = { index = (index - 1 + images.size) % images.size }) {
                    Text("上一张", color = white)
                }
                Text(
                    (index + 1).toString() + " / " + images.size,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = white.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { index = (index + 1) % images.size }) { Text("下一张", color = white) }
                if (scale > 1.01f) {
                    TextButton(onClick = { resetZoom() }) { Text("复位", color = white) }
                }
                TextButton(onClick = onClose) { Text("关闭", color = white) }
            }
        }
    }
}

/** 范例里封面底部那条黑色均衡器。暂停时不启动任何动画，避免空转掉帧。 */
@Composable
private fun EqStrip(playing: Boolean, modifier: Modifier = Modifier) {
    // 相位只在**绘制**阶段读。
    //
    // 以前是 `val phase by transition.animateFloat(...)`，然后 14 个 Bar 在组合阶段
    // 用 `fillMaxHeight(0.15 + 0.75 * sin(phase...))` 算高度 —— `phase` 一帧一换 ⇒
    // 整条 Row 每帧重组、每帧重新测量 14 个子节点。播放中它一直在跑，这就是
    // 「播放页什么都不动也卡」的主要来源（暂停时才停）。
    // 现在把它画进一个 Canvas：读 State 落在 draw 阶段，每帧只是重绘 14 个圆角矩形，
    // 不重组、不重排。
    val phase: androidx.compose.runtime.State<Float> = if (playing) {
        rememberInfiniteTransition(label = "eq").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = Motion.ambient(),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "phase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val barColor = MaterialTheme.colorScheme.secondaryContainer
    // 令牌（Spacing.*）是 @Composable 取值，只能在组合里先换算成像素再进绘制
    val padH = with(LocalDensity.current) { Spacing.gap10.toPx() }
    val padV = with(LocalDensity.current) { Spacing.sm.toPx() }
    val gap = with(LocalDensity.current) { Spacing.xs.toPx() }
    androidx.compose.foundation.Canvas(
        modifier
            .fillMaxWidth()
            .height(PlayerDim.height30)
            .background(MaterialTheme.colorScheme.onBackground),
    ) {
        val count = 14
        val usableW = (size.width - padH * 2 - gap * (count - 1)).coerceAtLeast(1f)
        val barW = usableW / count
        val usableH = (size.height - padV * 2).coerceAtLeast(1f)
        val p = phase.value
        for (i in 0 until count) {
            val f = if (playing) {
                val t = (p + i * 0.17f) % 1f
                (0.15f + 0.75f * kotlin.math.abs(kotlin.math.sin(t * 3.14159f))).coerceIn(0.15f, 0.9f)
            } else {
                0.25f
            }
            val barH = usableH * f
            drawRoundRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(padH + i * (barW + gap), padV + usableH - barH),
                size = androidx.compose.ui.geometry.Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f),
            )
        }
    }
}

/** 播放列表：当前项目里的全部音频（跨章节、跨格式统合）。 */
@Composable
private fun PlaylistPanel(
    playlist: List<TrackEntry>,
    currentPath: String,
    autoScroll: Boolean,
    onPick: (Int) -> Unit,
    /** 传了就在每行右侧显示「移除」按钮。 */
    onRemove: ((Int) -> Unit)? = null,
    /**
     * 传了就在行首显示拖拽把手（路径, ±1），用于调整播放顺序。
     * 传 null 时行首仍显示序号（只在不需要排序的场景，例如只读列表）。
     */
    onMove: ((String, Int) -> Boolean)? = null,
) {
    if (playlist.isEmpty()) {
        EmptyState(title = "播放列表为空", description = "从曲库长按曲目，选「加入播放列表」")
        return
    }
    val index = playlist.indexOfFirst { it.path == currentPath }
    val listState = rememberLazyListState()
    // 正在拖的是哪一行：用来给该行加抬升阴影（拖拽中它浮在其它行之上）
    var draggingPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(index, autoScroll) {
        if (autoScroll && index >= 0) listState.animateScrollToItem(index)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(playlist, key = { _, t -> t.path }) { i, t ->
            val active = i == index
            val dragging = draggingPath == t.path
            val ext = t.name.substringAfterLast('.', "").uppercase()
            val shape = asmrShape(Radius.md)
            TrackListItem(
                title = t.baseName,
                subtitle = ext + " · " + (if (t.hasScript) "有台本" else "无台本"),
                active = active,
                leadingIcon = if (onMove != null) {
                    {
                        QueueDragHandle(
                            onUp = { onMove(t.path, -1) },
                            onDown = { onMove(t.path, 1) },
                            onDraggingChange = { on -> draggingPath = if (on) t.path else null },
                        )
                    }
                } else {
                    {
                        Text(
                            (i + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { onPick(i) },
                trailing = {
                    if (active) {
                        Icon(
                            Icons.Default.PlayArrow,
                            "正在播放",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    onRemove?.let { remove ->
                        IconButton(onClick = { remove(i) }) {
                            Icon(Icons.Default.Close, "从播放列表移除", Modifier.size(IconSize.sm))
                        }
                    }
                },
                modifier = Modifier
                    .padding(horizontal = Spacing.md, vertical = Spacing.xxs)
                    // 重排时整行平滑让位，不做瞬间跳位
                    .animateItem()
                    // 拖拽中的行抬起来盖住相邻行（只有它自己有阴影 + 置顶）
                    .zIndex(if (dragging) 1f else 0f)
                    .then(if (dragging) Modifier.shadow(Elevation.raised, shape) else Modifier),
            )
        }
    }
}

/**
 * 队列行首的拖拽把手。
 *
 * 用固定步长（一行高度）而不是跟手 1:1 位移：行本身是 LazyColumn 的一项，
 * 想做出「拖着走」需要自己算每一项的位置并在滚动中追赶，代价大且容易抖。
 * 这里改成「按住上下拖 → 每越过半行换一位」，重排靠 `animateItem()` 平滑过渡，
 * 手感接近 iOS 的编辑列表，代码量却只有一个手势 + 计数器。
 */
@Composable
private fun QueueDragHandle(
    onUp: () -> Boolean,
    onDown: () -> Boolean,
    onDraggingChange: (Boolean) -> Unit,
) {
    val stepPx = with(LocalDensity.current) { (TouchTarget.row + Spacing.xxs * 2).toPx() }
    // 阈值取半行多一点：太灵敏会在手指哆嗦时来回换位
    val threshold = stepPx * 0.55f
    var acc by remember { mutableFloatStateOf(0f) }
    Icon(
        Icons.Default.DragHandle,
        contentDescription = "按住上下拖动，调整播放顺序",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(TouchTarget.min)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    acc += delta
                    // 一次事件可能跨越多行：逐行换位，换完用剩余位移继续判断
                    while (acc >= threshold) {
                        if (!onDown()) {
                            acc = 0f
                            break
                        }
                        acc -= stepPx
                    }
                    while (acc <= -threshold) {
                        if (!onUp()) {
                            acc = 0f
                            break
                        }
                        acc += stepPx
                    }
                },
                onDragStarted = {
                    acc = 0f
                    onDraggingChange(true)
                },
                onDragStopped = {
                    acc = 0f
                    onDraggingChange(false)
                },
            ),
    )
}

/** 从曲库里挑歌加进播放队列。 */
@Composable
private fun QueueAddDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val all = scan?.tracks ?: emptyList()
    val list = if (q.isBlank()) {
        all
    } else {
        all.filter { it.name.contains(q, ignoreCase = true) || it.folderName.contains(q, ignoreCase = true) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
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
                    Text("加入播放列表", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        list.size.toString() + " 首",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("搜索曲目名 / 文件夹") },
                )
                Spacer(Modifier.height(Spacing.itemGap))
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(list, key = { _, t -> t.path }) { _, t ->
                        ListItem(
                            headlineContent = {
                                Text(t.baseName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                val ext = t.name.substringAfterLast('.', "").uppercase()
                                Text(
                                    ext + " · " + t.folderName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Icon(Icons.Default.PlayArrow, "加入队列", tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable { vm.queueAdd(t) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底部控制区：进度条 + 时间行 + 走带。
 *
 * 按参考图拆成「裸」的两块（不套卡片）：进度条单独一行、走带单独一行，
 * 中间放睡眠定时与书签——它们是次级操作，用小字而不是按钮，不与大播放键抢视线。
 */
// 自定义滑块 thumb/track 在 material3 1.3 仍是实验 API
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    sleepState: com.kiite.player.playback.SleepState,
    fadeMs: Long,
    onSetSleep: (Long?) -> Unit,
    onStopAfterTrack: () -> Unit,
    onStopAfterWork: () -> Unit,
    onClearSleep: () -> Unit,
    onCycleFade: () -> Unit,
    abState: com.kiite.player.playback.AbState,
    onAbSetStart: () -> Unit,
    onAbSetEnd: () -> Unit,
    onAbNudgeStart: (Long) -> Unit,
    onAbNudgeEnd: (Long) -> Unit,
    onAbToggle: () -> Unit,
    onAbClear: () -> Unit,
    playMode: String,
    onCyclePlayMode: () -> Unit,
    bookmarkCount: Int,
    onOpenBookmarks: () -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    var timerMenu by remember { mutableStateOf(false) }
    var abMenu by remember { mutableStateOf(false) }
    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = dragging ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)

    // M3 默认给按钮/滑条强制 48dp 触控区，走带整块被撑得很高；
    // 播放页按钮少而大，关掉这个膨胀，垂直空间还给台词
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
        // 走带用项目统一的 AsmrSlider（细轨道 + 单点，见 components/AsmrControls.kt）：
        // M3 默认那套粗轨道加停止点在这里又重又吵，拖动时整条轨道还会变厚。
        AsmrSlider(
            modifier = Modifier.fillMaxWidth(),
            value = sliderValue,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeekTo((it * duration).toLong()) }
                dragging = null
            },
            height = PlayerDim.height30,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatTime(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 睡眠定时：不是每次都用，所以只在有定时时才上色
                Box {
                    TextButton(
                        onClick = { timerMenu = true },
                        modifier = Modifier.height(PlayerDim.height32),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.sm),
                    ) {
                        Text(
                            com.kiite.player.playback.SleepMath.buttonLabel(sleepState),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                sleepState.fading -> MaterialTheme.colorScheme.tertiary
                                sleepState.active -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    DropdownMenu(
                        expanded = timerMenu,
                        onDismissRequest = { timerMenu = false },
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        shape = asmrShape(Radius.lg),
                        containerColor = MaterialTheme.colorScheme.surface,
                        border = asmrMenuBorder(),
                    ) {
                        DropdownMenuItem(
                            text = { Text("关闭定时") },
                            enabled = sleepState.active,
                            onClick = { onClearSleep(); timerMenu = false },
                        )
                        MenuLabel("按曲目边界停止")
                        SleepStopItem(
                            text = "播完本章后停止",
                            checked = sleepState.mode == com.kiite.player.playback.SleepStopMode.END_OF_TRACK,
                            onClick = { onStopAfterTrack(); timerMenu = false },
                        )
                        SleepStopItem(
                            text = "播完本作品后停止",
                            checked = sleepState.mode == com.kiite.player.playback.SleepStopMode.END_OF_WORK,
                            onClick = { onStopAfterWork(); timerMenu = false },
                        )
                        MenuLabel("倒计时停止")
                        com.kiite.player.playback.SleepMath.MINUTE_OPTIONS.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("$m 分钟后停止") },
                                onClick = { onSetSleep(m * 60L); timerMenu = false },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("10 秒后停止（测试用）") },
                            onClick = { onSetSleep(10L); timerMenu = false },
                        )
                        MenuLabel("倒计时结束前的渐停")
                        DropdownMenuItem(
                            text = { Text(com.kiite.player.playback.SleepMath.fadeLabel(fadeMs) + "（点按切换）") },
                            onClick = { onCycleFade() },
                        )
                    }
                }
                TextButton(
                    onClick = onOpenBookmarks,
                    modifier = Modifier.height(PlayerDim.height32),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.sm),
                ) {
                    Text(
                        if (bookmarkCount > 0) "书签 " + bookmarkCount else "书签",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bookmarkCount > 0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Text(
                formatTime(if (durationMs > 0) durationMs else 0L),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 走带：五格等宽，播放键才是屏幕正中。
        //
        // 以前是「左图标 — weight 弹簧 ×4 — 右文字」：左边是 40dp 图标、右边是 A-B 文字按钮，
        // 两个端头宽度不等，中间那颗播放键就被推到偏左 ~10dp（一眼能看出来但说不上哪不对）。
        // 现在每格 weight(1f) 且内容居中，端头宽窄不再影响中心；顺带 A-B 那格也能放下
        // 「A-B 循环」这种更长的状态字，不会挤成竖排。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(PlayerDim.iconBtn40)) {
                    Icon(
                        if (playMode == "shuffle") Icons.Default.Shuffle else Icons.Default.Repeat,
                        when (playMode) {
                            "shuffle" -> "随机播放（点按切换）"
                            "loop" -> "列表循环（点按切换）"
                            else -> "顺序播放（点按切换）"
                        },
                        Modifier.size(PlayerDim.box21),
                        tint = if (playMode == "sequence") {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = onPrev, modifier = Modifier.size(PlayerDim.iconBtn44)) {
                    Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(PlayerDim.icon27))
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(IconSize.hero),
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    // 播放/暂停只淡入淡出、不做位移与缩放：这是同一个按钮换了状态，
                    // 动了位置反而像「另一个按钮滑进来」
                    androidx.compose.animation.AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = { fadeThrough() },
                        label = "playPause",
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playing) "暂停" else "播放",
                            Modifier.size(PlayerDim.box30),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = onNext, modifier = Modifier.size(PlayerDim.iconBtn44)) {
                    Icon(Icons.Default.SkipNext, "下一首", Modifier.size(PlayerDim.icon27))
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                // A-B 用文字而不是图标：Material 没有「区间循环」的现成图标，
                // 用一个含义模糊的箭头图标反而不如两个字母说得清楚
                Box {
                    TextButton(
                        onClick = { abMenu = true },
                        modifier = Modifier.height(PlayerDim.height32),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.sm),
                    ) {
                        Text(
                            com.kiite.player.playback.AbLoopMath.buttonLabel(abState),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            color = when {
                                abState.active -> MaterialTheme.colorScheme.secondary
                                abState.startMs != null -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                DropdownMenu(
                    expanded = abMenu,
                    onDismissRequest = { abMenu = false },
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    shape = asmrShape(Radius.lg),
                    containerColor = MaterialTheme.colorScheme.surface,
                    border = asmrMenuBorder(),
                ) {
                    MenuLabel(com.kiite.player.playback.AbLoopMath.statusLabel(abState))
                    DropdownMenuItem(
                        text = { Text("把 A 点设到当前位置") },
                        onClick = { onAbSetStart() },
                    )
                    if (abState.startMs != null) {
                        AbNudgeRow(
                            value = com.kiite.player.playback.AbLoopMath.formatClock(abState.startMs!!),
                            onNudge = onAbNudgeStart,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("把 B 点设到当前位置") },
                        onClick = { onAbSetEnd() },
                    )
                    if (abState.endMs != null) {
                        AbNudgeRow(
                            value = com.kiite.player.playback.AbLoopMath.formatClock(abState.endMs!!),
                            onNudge = onAbNudgeEnd,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (abState.active) "暂停循环" else "启用循环") },
                        enabled = abState.complete,
                        onClick = { onAbToggle(); abMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("清除 A-B 区间") },
                        enabled = abState.startMs != null,
                        onClick = { onAbClear(); abMenu = false },
                    )
                }
            }
            }
        }
    }
    }
}

@Composable
private fun ScriptPanel(
    parsed: ParsedScript,
    positionMs: Long,
    durationMs: Long,
    autoScroll: Boolean,
    speed: Float,
    fontScale: Float,
    onSeek: (Long) -> Unit,
    onLongPressLine: (Int) -> Unit = {},
    bookmarkedLines: Set<Int> = emptySet(),
    onToggleLineBookmark: (Int) -> Unit = {},
    lineHeightScale: Float = 1f,
    fontFamily: FontFamily = FontFamily.SansSerif,
    theme: ScriptReadTheme = ScriptReadTheme.FOLLOW,
) {
    val colors = scriptThemeColors(theme)
    if (parsed.timedLines.isNotEmpty()) {
        TimedScript(
            parsed.timedLines,
            positionMs,
            autoScroll,
            fontScale,
            onSeek,
            onLongPressLine,
            bookmarkedLines,
            onToggleLineBookmark,
            lineHeightScale,
            fontFamily,
            colors,
        )
    } else {
        PlainScript(
            parsed.plainText, positionMs, durationMs, autoScroll, speed, fontScale,
            lineHeightScale, fontFamily, colors,
        )
    }
}

/** 台本阅读主题：id 与设置存储一致。 */
enum class ScriptReadTheme(val id: String, val label: String) {
    FOLLOW("follow", "跟随应用"),
    PAPER("paper", "纸张"),
    NIGHT("night", "夜读"),
    ;

    companion object {
        fun of(id: String): ScriptReadTheme = entries.firstOrNull { it.id == id } ?: FOLLOW
    }
}

/** 主题解析出的颜色；null 表示沿用当前 MaterialTheme 的原配色。 */
private data class ScriptThemeColors(
    val background: Color,
    val text: Color?,
    val active: Color?,
)

@Composable
private fun scriptThemeColors(theme: ScriptReadTheme): ScriptThemeColors = when (theme) {
    ScriptReadTheme.FOLLOW -> ScriptThemeColors(Color.Transparent, null, null)
    ScriptReadTheme.PAPER -> ScriptThemeColors(
        ScriptPalette.paperBackground, ScriptPalette.paperText, ScriptPalette.paperAccent,
    )
    ScriptReadTheme.NIGHT -> ScriptThemeColors(
        ScriptPalette.nightBackground, ScriptPalette.nightText, ScriptPalette.nightAccent,
    )
}

/** 台本跟随状态：用户手动滚动时暂停自动跟随，[resume] 点击或超时后恢复。 */
@Stable
private class FollowState {
    var paused by mutableStateOf(false)
    var job: Job? = null
}

/** 松手多久后恢复自动跟随。 */
private val FOLLOW_RESUME_DELAY = 2_500L

/**
 * 台本时间轴的二分查找结构：构建一次，之后每次位置更新 O(log n)，
 * 代替逐行 indexOfLast（台本几百行时每 400ms 全量扫描不划算）。
 * 行序按 startMs 排序，返回值映射回原始行号，乱序 LRC 也能正确定位。
 */
private class TimedIndex(lines: List<TimedLine>) {
    private val starts: LongArray
    private val order: IntArray

    init {
        val sorted = lines.withIndex().sortedBy { it.value.startMs }
        starts = LongArray(sorted.size) { sorted[it].value.startMs }
        order = IntArray(sorted.size) { sorted[it].index }
    }

    /** 最后一个 startMs <= positionMs 的原始行号；都不满足时返回 0。 */
    fun indexOf(positionMs: Long): Int {
        if (starts.isEmpty()) return 0
        val i = starts.binarySearch(positionMs)
        val at = if (i >= 0) i else -i - 2
        return if (at < 0) 0 else order[at]
    }
}

/**
 * 监听滚动容器的手势交互：用户拖动期间暂停台本自动跟随，
 * 松手 [FOLLOW_RESUME_DELAY] 毫秒后恢复。动画滚动不会触发 DragInteraction，不受影响。
 */
@Composable
private fun rememberFollowState(source: InteractionSource): FollowState {
    val state = remember { FollowState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    state.job?.cancel()
                    state.paused = true
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    state.job?.cancel()
                    state.job = scope.launch {
                        delay(FOLLOW_RESUME_DELAY)
                        state.paused = false
                    }
                }
            }
        }
    }
    return state
}

/** 「回到当前进度」悬浮按钮：用户回看台本后一键恢复跟随。调用处需位于 BoxScope。 */
@Composable
private fun FollowResumeButton(visible: Boolean, modifier: Modifier = Modifier, onResume: () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = popIn(),
        exit = popOut(),
        modifier = modifier,
    ) {
        FilledTonalIconButton(onClick = onResume, modifier = Modifier.padding(Spacing.gap14)) {
            Icon(Icons.Default.Sync, "回到当前进度")
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TimedScript(
    lines: List<TimedLine>,
    positionMs: Long,
    autoScroll: Boolean,
    fontScale: Float,
    onSeek: (Long) -> Unit,
    onLongPressLine: (Int) -> Unit,
    bookmarkedLines: Set<Int>,
    onToggleLineBookmark: (Int) -> Unit,
    lineHeightScale: Float = 1f,
    fontFamily: FontFamily = FontFamily.SansSerif,
    themeColors: ScriptThemeColors = ScriptThemeColors(Color.Transparent, null, null),
) {
    val listState = rememberLazyListState()
    val follow = rememberFollowState(listState.interactionSource)
    // 二分查找当前句：台本可能几百行，逐行扫描每次重组都全量比对不值得
    val index = remember(lines) { TimedIndex(lines) }.indexOf(positionMs)
    LaunchedEffect(index, autoScroll, follow.paused) {
        if (autoScroll && !follow.paused && index < lines.size) listState.animateScrollToItem(index)
    }
    val activeColor = themeColors.active ?: MaterialTheme.colorScheme.primary
    val idleColor = themeColors.text ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .fillMaxSize()
            .background(themeColors.background),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // key 用「起始时间戳 + 原行号」：换台本会重排，纯 index 会让高亮跳到别的行；
            // 时间戳可能重复（同一声内的两行），所以补上行号保证唯一
            itemsIndexed(lines, key = { i, line -> "${line.startMs}#$i" }) { i, line ->
                val active = i == index
                val marked = i in bookmarkedLines
                // 高亮不再「啪」地换颜色/字号：逐句跟随时每 400ms 换一行，
                // 硬切看着像整段在闪；插值之后视线是顺着走的
                val lineColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (active) activeColor else idleColor,
                    animationSpec = Motion.standard(Motion.MEDIUM),
                    label = "lineColor",
                )
                val lineFont by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (active) 18f else 16f,
                    animationSpec = Motion.standard(Motion.MEDIUM),
                    label = "lineFont",
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.itemGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        line.text,
                        fontSize = (lineFont * fontScale).sp,
                        lineHeight = (26 * fontScale * lineHeightScale).sp,
                        // 字体跟随阅读器设置（默认无衬线；可切衬线/等宽）
                        fontFamily = fontFamily,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = lineColor,
                        modifier = Modifier
                            .weight(1f)
                            // 点按跳转、长按校准这一句——两个手势分工明确，不互相抢
                            .combinedClickable(
                                onClick = { onSeek(line.startMs) },
                                onLongClick = { onLongPressLine(i) },
                            ),
                    )
                    // 书签位：标过的行常亮；没标过的只给当前句一个淡色入口，
                    // 否则每一行都挂个图标，台本会变得很吵
                    if (marked || active) {
                        Box(
                            Modifier
                                .size(PlayerDim.box28)
                                .clip(asmrShape(Radius.xs))
                                .clickable { onToggleLineBookmark(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (marked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                if (marked) "取消这一句的书签" else "给这一句加书签",
                                Modifier.size(IconSize.xs),
                                tint = if (marked) activeColor else idleColor.copy(alpha = 0.35f),
                            )
                        }
                    } else {
                        // 占位，保持行高与有无书签时一致，避免整段台本随滚动轻微跳动
                        Spacer(Modifier.size(PlayerDim.gap28))
                    }
                }
            }
        }
        FollowResumeButton(
            visible = follow.paused,
            modifier = Modifier.align(Alignment.BottomEnd),
        ) { follow.paused = false }
    }
}

@Composable
private fun PlainScript(
    text: String,
    positionMs: Long,
    durationMs: Long,
    autoScroll: Boolean,
    speed: Float,
    fontScale: Float,
    lineHeightScale: Float = 1f,
    fontFamily: FontFamily = FontFamily.SansSerif,
    themeColors: ScriptThemeColors = ScriptThemeColors(Color.Transparent, null, null),
) {
    val scrollState = rememberScrollState()
    val follow = rememberFollowState(scrollState.interactionSource)
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // 动画滚动代替瞬时跳转：跟随是连续平滑的，不再一格一格地「跳」。
    // 目标几乎没动（<2px）时不重启动画，避免每 400ms 一次的无谓动画重启
    LaunchedEffect(progress, autoScroll, speed, scrollState.maxValue, follow.paused) {
        if (autoScroll && !follow.paused && scrollState.maxValue > 0) {
            val target = (scrollState.maxValue * progress * speed).toInt().coerceIn(0, scrollState.maxValue)
            if (kotlin.math.abs(target - scrollState.value) >= 2) scrollState.animateScrollTo(target)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(themeColors.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Text(
                text,
                fontSize = (15 * fontScale).sp,
                lineHeight = (26 * fontScale * lineHeightScale).sp,
                fontFamily = fontFamily,
                color = themeColors.text ?: MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Spacing.xl))
        }
        FollowResumeButton(
            visible = follow.paused,
            modifier = Modifier.align(Alignment.BottomEnd),
        ) { follow.paused = false }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
