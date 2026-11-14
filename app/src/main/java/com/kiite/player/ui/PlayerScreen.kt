@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kiite.player.core.ParsedScript
import com.kiite.player.core.TimedLine
import com.kiite.player.core.TrackEntry
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.LocalIsBright
import com.kiite.player.util.Permissions
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(vm: MainViewModel, onExit: () -> Unit = {}) {
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
    // 四格「更多」的菜单：与曲目卡「⋯」共用同一份菜单内容
    var showMoreQuick by remember { mutableStateOf(false) }
    var batchCalibration by remember { mutableStateOf(false) }
    var calibrateLine by remember { mutableStateOf<Int?>(null) }
    // 阅读模式：藏起头部与控制卡，台本占满整屏（长台本一集几百句，读的时候不需要封面）
    var readMode by remember { mutableStateOf(false) }

    val current = track
    // 当前这一集被标记过的台本行号：台本行右侧的小图标靠它打亮
    val bookmarkedLines = remember(bookmarks, current?.path) {
        bookmarks.asSequence()
            .filter { it.isLine && it.trackPath == current?.path }
            .map { it.lineIndex }
            .toSet()
    }
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有在播放的音频", style = MaterialTheme.typography.bodyMedium)
                // 沉浸模式会隐藏底栏，空态下没有播放页头部可以点，必须在这里留一个出口
                if (settings.immersive) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.setImmersive(false) }) { Text("退出沉浸模式") }
                }
            }
        }
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
                Modifier.fillMaxWidth().padding(10.dp),
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
            ImageViewer(images, idx) { viewerIndex = null }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideScreenMinWidth
        // 转成横屏就退出阅读模式：宽屏是双栏，台本已经占了一半，而横屏的头部
        // 也没有退出按钮（入口在竖屏头部），留着这个状态只会让人找不到出口
        androidx.compose.runtime.LaunchedEffect(wide) {
            if (wide) readMode = false
        }
        // 「又宽又矮」（横屏手机）：左栏放不下大封面 → 强制紧凑并隐藏布局切换；
        // 平板竖屏这类高度足够的宽屏仍可切大封面，按钮保留
        val tightHeight = maxHeight < 600.dp

        // 竖屏三档布局：紧凑(72dp) / 大封面(132dp，带台词卡) / 全页大图(无台词卡)。
        // 「全页大图」只在竖屏有意义：宽屏双栏本来就放得下，按大封面处理。
        val layout = if (wide && settings.playerLayout == "big") "new" else settings.playerLayout

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
        androidx.activity.compose.BackHandler(enabled = readMode) { readMode = false }

        // 头部、内容、控制拆成三段：竖屏上下排，横屏左右排
        // 曲目所属作品：曲目卡的爱心是「作品级」收藏，必须拿到作品对象才能复用曲库那份数据
        val workProject = remember(current.path) { vm.projectOfTrackPath(current) }
        val favorite = remember(workProject, workMetaMap) {
            workProject?.let { vm.isFavorite(it) } == true
        }
        val workCode = workProject?.code?.takeIf { it.isNotBlank() } ?: current.folderName

        // 「⋯」与四格「更多」共用同一份菜单：两个入口、一份定义，避免两处菜单逐渐跑偏
        val moreMenu: @Composable (onDone: () -> Unit) -> Unit = { onDone ->
            MenuLabel("播放")
            DropdownMenuItem(
                text = { Text("后退 10 秒") },
                onClick = { onDone(); vm.seekBy(-10_000) },
            )
            DropdownMenuItem(
                text = { Text("前进 10 秒") },
                onClick = { onDone(); vm.seekBy(10_000) },
            )
            MenuLabel("播放倍数")
            DropdownMenuItem(
                text = {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MainViewModel.SPEED_STEPS.forEach { sp ->
                            FilterChip(
                                selected = sp == ui.speed,
                                onClick = { vm.setSpeed(sp) },
                                label = { Text(formatSpeed(sp)) },
                            )
                        }
                    }
                },
                onClick = {},
            )
            val variants = vm.variantsOfCurrent()
            if (variants.size > 1) {
                MenuLabel("格式（同一音频的不同版本）")
                vm.variantLabels().forEach { pair ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                pair.second,
                                fontWeight = if (pair.first == (activePath ?: variants.first())) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        },
                        onClick = { onDone(); vm.switchVariant(pair.first) },
                    )
                }
            }
            MenuLabel("台本")
            DropdownMenuItem(
                text = { Text("选择台本") },
                onClick = { onDone(); showScriptPicker = true },
            )
            DropdownMenuItem(
                text = { Text("写入标签") },
                onClick = { onDone(); vm.embedCurrent() },
            )
            if (!wide && script.parsed?.timedLines?.isNotEmpty() == true) {
                DropdownMenuItem(
                    text = { Text("台本全屏阅读") },
                    onClick = {
                        onDone()
                        showPlaylist = false
                        showImages = false
                        readMode = true
                    },
                )
            }
            MenuLabel("显示")
            if (images.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(if (showImages) "切回台本" else "查看图片") },
                    onClick = { onDone(); showImages = !showImages; showPlaylist = false },
                )
            }
            if (vm.currentIsVideo()) {
                DropdownMenuItem(
                    text = { Text("视频全屏") },
                    onClick = { onDone(); vm.setVideoFullscreen(true) },
                )
            }
            DropdownMenuItem(
                text = { Text(if (settings.immersive) "退出沉浸模式" else "沉浸模式") },
                onClick = { onDone(); vm.setImmersive(!settings.immersive) },
            )
        }

        val header: @Composable (Boolean) -> Unit = { bigCover ->
            TrackCard(
                bigCover = bigCover,
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
                moreMenu = moreMenu,
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
        val content: @Composable () -> Unit = {
            when {
                showImages -> {
                    ImagePanel(images) { viewerIndex = it }
                }
                // 播放列表不再是弹窗：和新布局的其他内容一样，在这张卡里显示，
                // 少一层「遮挡整个播放页」的界面，也少一个关闭按钮
                showPlaylist -> {
                    PlaylistPanel(
                        playlist = playlist,
                        currentPath = current.path,
                        autoScroll = settings.playlistAutoScroll,
                        onPick = { vm.playFromPlaylist(it) },
                        onRemove = { vm.queueRemove(it) },
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
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            script.error ?: "没有可显示的台本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showScriptPicker = true }) { Text("选择台本") }
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
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(asmrShape(18.dp))
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
                    onClick = { showMoreQuick = true },
                    modifier = Modifier.weight(1f),
                    menu = { close -> moreMenu(close) },
                    menuExpanded = showMoreQuick,
                    onMenuDismiss = { showMoreQuick = false },
                )
            }
        }

        // 台本 / 播放列表 / 图片 共用一张卡：标题跟着内容走，圆角只包一层
        val contentTitle = when {
            showImages -> "图片 " + images.size
            showPlaylist -> "播放列表 " + playlist.size
            else -> "台本"
        }
        val contentCard: @Composable () -> Unit = {
            ScriptCard(
                title = contentTitle,
                trailing = when {
                    showPlaylist -> {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 播放列表此时占满整页，四格被收起：留一个回台本的出口
                                IconButton(
                                    onClick = { showPlaylist = false },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        "回到台本",
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = { vm.queueReset() },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                                ) { Text("重置", style = MaterialTheme.typography.labelSmall) }
                                TextButton(
                                    onClick = { showAddToQueue = true },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                                ) { Text("＋ 加入", style = MaterialTheme.typography.labelSmall) }
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
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.OpenInFull,
                                            "台本全屏阅读",
                                            Modifier.size(18.dp),
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
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    header(!tightHeight && layout != "classic")
                    quickActions()
                    Spacer(Modifier.weight(1f))
                    controls()
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp)) {
                    contentCard()
                }
            }
        } else if (readMode && script.parsed?.timedLines?.isNotEmpty() == true) {
            // 阅读模式：只留一条 48dp 的操作带，其余全是台词
            Column(Modifier.fillMaxSize()) {
                ReadModeBar(
                    title = current.baseName,
                    positionMs = ui.positionMs,
                    durationMs = ui.durationMs,
                    playing = ui.isPlaying,
                    onToggle = { vm.playPause() },
                    onExit = { readMode = false },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content()
                }
            }
        } else {
            val isBig = layout == "big"
            // 三档布局之间做淡入淡出过渡；封面尺寸再单独补间，切换时不跳变
            androidx.compose.animation.Crossfade(
                targetState = isBig,
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "playerLayout",
            ) { big ->
                if (big) {
                    // 全页大图：封面居中放大，台词卡整个让位。点封面或侧滑返回回到大封面档
                    val artSize by androidx.compose.animation.core.animateDpAsState(
                        targetValue = minOf(maxWidth - 48.dp, maxHeight - 340.dp).coerceAtLeast(220.dp),
                        animationSpec = androidx.compose.animation.core.tween(220),
                        label = "art",
                    )
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // 点大图继续循环回紧凑档（侧滑返回则是退回大封面档）
                                Box(Modifier.clickable { vm.setPlayerLayout("classic") }) {
                                    CoverBox(cover, artSize, 24.dp, playing = ui.isPlaying)
                                }
                                Spacer(Modifier.height(14.dp))
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
                        quickActions()
                        controls()
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (showPlaylist) {
                            // 播放列表铺满除走带外的全部区域：集数多时要一眼扫完，
                            // 曲目卡与四格此刻是干扰。侧滑返回或点「回台本」恢复
                            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp)) {
                                contentCard()
                            }
                        } else {
                            header(layout != "classic")
                            quickActions()
                            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp)) {
                                contentCard()
                            }
                        }
                        controls()
                    }
                }
            }
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
            .padding(start = 4.dp, end = 8.dp),
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
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "暂停" else "继续播放",
            )
        }
    }
    // 2dp 的进度线：阅读模式下没有控制器，至少要能看出「播到哪儿了」。
    // 用自绘细线而不是 LinearProgressIndicator，后者自带内边距会多占十几 dp。
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(frac).height(2.dp).background(MaterialTheme.colorScheme.primary))
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
    moreMenu: @Composable (onDone: () -> Unit) -> Unit,
    calibration: (@Composable () -> Unit)? = null,
) {
    var more by remember { mutableStateOf(false) }
    // 尺寸做补间：从 132 直接跳到 72 会让整页内容「闪一下」
    val size by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (bigCover) 132.dp else 72.dp,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "cover",
    )
    AsmrCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(size)
                        .clip(asmrShape(14.dp))
                        .clickable { onToggleSize() },
                ) {
                    MediaBox(cover, size, 14.dp, playing, isVideo, videoPlayer, onVideoFullscreen)
                }
                Spacer(Modifier.width(14.dp))
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
                        Spacer(Modifier.height(3.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NowPlayingPill(playing)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (favorite) "取消收藏" else "收藏这个作品",
                                Modifier.size(19.dp),
                                tint = if (favorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Box {
                            IconButton(onClick = { more = true }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.MoreHoriz, "更多", Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = more,
                                onDismissRequest = { more = false },
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                shape = asmrShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    2.dp,
                                    MaterialTheme.colorScheme.onBackground,
                                ),
                            ) {
                                moreMenu { more = false }
                            }
                        }
                    }
                }
            }
            calibration?.let {
                HorizontalDivider(
                    Modifier.padding(top = 8.dp),
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
            .clip(asmrShape(20.dp))
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = if (playing) 0.14f else 0.08f),
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (playing) Icons.Default.GraphicEq else Icons.Default.Pause,
            null,
            Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(5.dp))
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
    menu: (@Composable (() -> Unit) -> Unit)? = null,
    menuExpanded: Boolean = false,
    onMenuDismiss: () -> Unit = {},
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
                .padding(vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, label, Modifier.size(22.dp), tint = tint)
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
            )
        }
        if (menu != null) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                shape = asmrShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                menu { onMenuDismiss() }
            }
        }
    }
}

/**
 * 台词 / 播放列表 / 图片的外壳卡片。
 *
 * 卡头只有一行（图标 + 标题 + 右侧状态），比原来那套「状态条 + 校准条 + chips 行」
 * 省下不少高度——台本区现在是一整块，不再被切成几段。
 */
@Composable
private fun ScriptCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clip(asmrShape(18.dp))
            .background(asmrRowColor()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.MusicNote,
                null,
                Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun CoverBox(
    cover: String?,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    playing: Boolean = false,
) {
    Box(
        Modifier
            .size(size)
            .clip(asmrShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(asmrBorder(asmrShape(corner))),
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

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
            .padding(horizontal = 16.dp, vertical = 2.dp),
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
            .clip(asmrShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
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
                    Modifier.size(18.dp),
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
        CoverBox(cover, size, corner, playing)
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
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) +
            androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(180), initialScale = 0.85f),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
        modifier = modifier,
    ) {
        androidx.compose.material3.Surface(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
            shape = asmrShape(999.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Icon(osd.icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
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
        shape = asmrShape(20.dp),
        title = { Text("视频手势") },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                Text("这些手势没有按钮，全靠手感——第一次用可以先记两个：", style = MaterialTheme.typography.bodySmall)
                items.forEach { (icon, title, desc) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
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
                .padding(start = 2.dp, end = 6.dp, top = 2.dp),
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
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtMs(dragging?.let { (it * duration).toLong() } ?: ui.positionMs), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = sliderValue,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { vm.seekTo((it * duration).toLong()) }
                        dragging = null
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
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
                        modifier = Modifier.size(30.dp),
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
                shape = asmrShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.84f),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("播放列表", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    ) {
        chunks.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    val idx = item.first
                    val path = item.second
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(asmrShape(14.dp))
                            .then(asmrBorder(asmrShape(14.dp)))
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 全屏看图。 */
@Composable
internal fun ImageViewer(images: List<String>, start: Int, onClose: () -> Unit) {
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
            // 底部操作条：深色半透明 + 纯白文字（经典皮肤的主色是近黑，不能沿用默认文字色）
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f))
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 22.dp),
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
    if (playing) {
        val transition = rememberInfiniteTransition(label = "eq")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "phase",
        )
        EqStripContent(modifier) { i ->
            val t = (phase + i * 0.17f) % 1f
            (0.15f + 0.75f * kotlin.math.abs(kotlin.math.sin(t * 3.14159f))).coerceIn(0.15f, 0.9f)
        }
    } else {
        EqStripContent(modifier) { _ -> 0.25f }
    }
}

@Composable
private fun EqStripContent(modifier: Modifier = Modifier, heightAt: (Int) -> Float) {
    Row(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(MaterialTheme.colorScheme.onBackground)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(14) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(heightAt(i))
                    .clip(asmrShape(999.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
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
) {
    if (playlist.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("播放列表为空", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val index = playlist.indexOfFirst { it.path == currentPath }
    val listState = rememberLazyListState()
    LaunchedEffect(index, autoScroll) {
        if (autoScroll && index >= 0) listState.animateScrollToItem(index)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(playlist, key = { _, t -> t.path }) { i, t ->
            val active = i == index
            ListItem(
                headlineContent = {
                    Text(
                        t.baseName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                supportingContent = {
                    val ext = t.name.substringAfterLast('.', "").uppercase()
                    Text(
                        ext + " · " + (if (t.hasScript) "有台本" else "无台本"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = {
                    Text(
                        (i + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (active) {
                            Icon(
                                Icons.Default.PlayArrow,
                                "正在播放",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        onRemove?.let { remove ->
                            IconButton(onClick = { remove(i) }) {
                                Icon(Icons.Default.Close, "从播放列表移除", Modifier.size(18.dp))
                            }
                        }
                    }
                },
                modifier = Modifier.clickable { onPick(i) },
            )
        }
    }
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
            shape = asmrShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.84f)
                .then(asmrBorder(asmrShape(16.dp))),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("加入播放列表", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("搜索曲目名 / 文件夹") },
                )
                Spacer(Modifier.height(6.dp))
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        // 参考图是「细轨道 + 单点」：M3 默认那套粗轨道加停止点在这里又重又吵，
        // 而且拖动时整条轨道会跟着变厚，视觉上像在晃
        Slider(
            modifier = Modifier.fillMaxWidth().height(30.dp),
            value = sliderValue,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeekTo((it * duration).toLong()) }
                dragging = null
            },
            thumb = {
                Box(
                    Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            },
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(sliderValue)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            },
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
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
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
                        shape = asmrShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.onBackground,
                        ),
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
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (playMode == "shuffle") Icons.Default.Shuffle else Icons.Default.Repeat,
                    when (playMode) {
                        "shuffle" -> "随机播放（点按切换）"
                        "loop" -> "列表循环（点按切换）"
                        else -> "顺序播放（点按切换）"
                    },
                    Modifier.size(21.dp),
                    tint = if (playMode == "sequence") {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPrev, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(27.dp))
            }
            Spacer(Modifier.weight(1f))
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(56.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "播放/暂停",
                    Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipNext, "下一首", Modifier.size(27.dp))
            }
            Spacer(Modifier.weight(1f))
            // A-B 用文字而不是图标：Material 没有「区间循环」的现成图标，
            // 用一个含义模糊的箭头图标反而不如两个字母说得清楚
            Box {
                TextButton(
                    onClick = { abMenu = true },
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        com.kiite.player.playback.AbLoopMath.buttonLabel(abState),
                        style = MaterialTheme.typography.labelSmall,
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
                    shape = asmrShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.onBackground,
                    ),
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
        Color(0xFFF6F1E7), Color(0xFF4A3F31), Color(0xFF8D6E3F),
    )
    ScriptReadTheme.NIGHT -> ScriptThemeColors(
        Color(0xFF14161A), Color(0xFFC9CDC4), Color(0xFFE8C87A),
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
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) +
            androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(180)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
            androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(140)),
        modifier = modifier,
    ) {
        FilledTonalIconButton(onClick = onResume, modifier = Modifier.padding(14.dp)) {
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
            itemsIndexed(lines) { i, line ->
                val active = i == index
                val marked = i in bookmarkedLines
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        line.text,
                        fontSize = ((if (active) 18f else 16f) * fontScale).sp,
                        lineHeight = (26 * fontScale * lineHeightScale).sp,
                        // 字体跟随阅读器设置（默认无衬线；可切衬线/等宽）
                        fontFamily = fontFamily,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) activeColor else idleColor,
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
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleLineBookmark(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (marked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                if (marked) "取消这一句的书签" else "给这一句加书签",
                                Modifier.size(16.dp),
                                tint = if (marked) activeColor else idleColor.copy(alpha = 0.35f),
                            )
                        }
                    } else {
                        // 占位，保持行高与有无书签时一致，避免整段台本随滚动轻微跳动
                        Spacer(Modifier.size(28.dp))
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text,
                fontSize = (15 * fontScale).sp,
                lineHeight = (26 * fontScale * lineHeightScale).sp,
                fontFamily = fontFamily,
                color = themeColors.text ?: MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))
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
