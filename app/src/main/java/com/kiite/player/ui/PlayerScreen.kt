package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun PlayerScreen(vm: MainViewModel, onExit: () -> Unit = {}) {
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
    // 全屏状态改在 AppRoot 里「切走播放页」时复位：
    // 放在这里会自我抵消——进全屏要隐藏顶栏/底栏，Scaffold 重排又让本 composable 重进，
    // LaunchedEffect(Unit)/onDispose 立刻把状态改回 false，于是永远进不去。
    var showImages by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val scan by vm.scan.collectAsStateWithLifecycle()
    val sleepRemaining by vm.sleepRemainingMs.collectAsStateWithLifecycle()

    var showPlaylist by remember { mutableStateOf(false) }
    var showAddToQueue by remember { mutableStateOf(false) }
    var showScriptPicker by remember { mutableStateOf(false) }

    val current = track
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
        val hostView = androidx.compose.ui.platform.LocalView.current
        val hostCtx = androidx.compose.ui.platform.LocalContext.current
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
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        // 之前是 useController=false，全屏里根本没法暂停/拖进度
                        useController = true
                        showController()
                        keepScreenOn = true
                        player = vm.playerForView
                        installVideoGestures(this)
                    }
                },
                update = { it.player = vm.playerForView },
            )
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
        // 「又宽又矮」（横屏手机）：左栏放不下大封面 → 强制紧凑并隐藏布局切换；
        // 平板竖屏这类高度足够的宽屏仍可切大封面，按钮保留
        val tightHeight = maxHeight < 600.dp

        // 头部、内容、控制拆成三段：竖屏上下排，横屏左右排
        val header: @Composable (Boolean, Boolean) -> Unit = { bigCover, showLayoutToggle ->
            PlayerHeader(
                bigCover = bigCover,
                track = current,
                cover = cover,
                scriptText = scriptLabel(
                    script.fromEmbedded,
                    script.attachment?.let { it.format.label },
                    script.attachment?.reason,
                ),
                showPlaylist = showPlaylist,
                playlistSize = playlist.size,
                onShowScript = { showPlaylist = false },
                onShowPlaylist = { showPlaylist = true },
                onPickScript = { showScriptPicker = true },
                onEmbed = { vm.embedCurrent() },
                onToggleLayout = {
                    vm.setPlayerLayout(if (settings.playerLayout == "classic") "new" else "classic")
                },
                showLayoutToggle = showLayoutToggle,
                speed = ui.speed,
                onSpeed = { vm.setSpeed(it) },
                variants = vm.variantsOfCurrent(),
                variantLabels = vm.variantLabels(),
                activePath = activePath,
                onSwitchVariant = { vm.switchVariant(it) },
                immersive = settings.immersive,
                onToggleImmersive = { vm.setImmersive(!settings.immersive) },
                playing = ui.isPlaying,
                isVideo = vm.currentIsVideo(),
                videoPlayer = vm.playerForView,
                imagesCount = images.size,
                showImages = showImages,
                onShowImages = { showImages = !showImages },
                onVideoFullscreen = { vm.setVideoFullscreen(true) },
            )
        }

        // 播放列表：弹出窗口（覆盖大部分区域），而不是挤在页面里
        if (showAddToQueue) {
            QueueAddDialog(
                vm = vm,
                onDismiss = { showAddToQueue = false },
            )
        }
        if (showPlaylist) {
            Dialog(
                onDismissRequest = { showPlaylist = false },
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
                            Text("播放列表", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                playlist.size.toString() + " 首",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.queueReset() }) { Text("重置") }
                            TextButton(onClick = { showAddToQueue = true }) { Text("＋ 加入") }
                            IconButton(onClick = { showPlaylist = false }) {
                                Icon(Icons.Default.Close, "关闭播放列表")
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        )
                        Box(Modifier.weight(1f)) {
                            PlaylistPanel(
                                playlist = playlist,
                                currentPath = current.path,
                                autoScroll = settings.playlistAutoScroll,
                                onPick = { vm.playFromPlaylist(it) },
                                onRemove = { vm.queueRemove(it) },
                            )
                        }
                        Text(
                            "点每行右侧的 ✕ 可移除；「＋ 加入」从曲库挑歌；「重置」按当前项目的曲库还原。从曲库直接点歌会整队重置。",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val content: @Composable () -> Unit = {
            when {
                showImages -> {
                    ImagePanel(images) { viewerIndex = it }
                }
                script.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                script.parsed != null && !script.parsed!!.isEmpty -> {
                    ScriptPanel(
                        parsed = script.parsed!!,
                        positionMs = ui.positionMs,
                        durationMs = ui.durationMs,
                        autoScroll = settings.autoScroll,
                        speed = settings.scrollSpeed,
                        fontScale = settings.scriptFontScale,
                        onSeek = { vm.seekTo(it) },
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            script.error ?: "没有可显示的台本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val controls: @Composable () -> Unit = {
            Controls(
                isPlaying = ui.isPlaying,
                positionMs = ui.positionMs,
                durationMs = ui.durationMs,
                onSeekTo = { vm.seekTo(it) },
                onSeekBy = { vm.seekBy(it) },
                onToggle = { vm.playPause() },
                onPrev = { vm.previous() },
                onNext = { vm.next() },
                sleepRemainingMs = sleepRemaining,
                onSetSleep = { vm.setSleepTimer(it) },
                playMode = playMode,
                onCyclePlayMode = { vm.cyclePlayMode() },
            )
        }

        if (wide) {
            // 横屏双栏：左 = 封面 + 控制，右 = 台本 / 播放列表 / 图片
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    header(!tightHeight && settings.playerLayout != "classic", !tightHeight)
                    Spacer(Modifier.weight(1f))
                    controls()
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    content()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header(settings.playerLayout != "classic", true)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content()
                }
                controls()
            }
        }
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

@Composable
private fun PlayerHeader(
    bigCover: Boolean,
    track: TrackEntry,
    cover: String?,
    scriptText: String,
    showPlaylist: Boolean,
    playlistSize: Int,
    onShowScript: () -> Unit,
    onShowPlaylist: () -> Unit,
    onPickScript: () -> Unit,
    onEmbed: () -> Unit,
    onToggleLayout: () -> Unit,
    showLayoutToggle: Boolean = true,
    speed: Float,
    onSpeed: (Float) -> Unit,
    variants: List<String>,
    variantLabels: List<Pair<String, String>>,
    activePath: String?,
    onSwitchVariant: (String) -> Unit,
    immersive: Boolean,
    onToggleImmersive: () -> Unit,
    playing: Boolean,
    isVideo: Boolean,
    videoPlayer: androidx.media3.common.Player?,
    imagesCount: Int,
    showImages: Boolean,
    onShowImages: () -> Unit,
    onVideoFullscreen: () -> Unit,
) {
    AsmrCard(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = if (bigCover) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            if (bigCover) {
                // 「版式」皮肤：封面之上的报头带（竖条 + 宽字距标签 + 套色块）
                if (LocalEditorial.current) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).height(13.dp).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        EditorialTicks()
                    }
                }
                MediaBox(cover, 220.dp, 18.dp, playing, isVideo, videoPlayer, onVideoFullscreen)
                Spacer(Modifier.height(12.dp))
                Text(
                    track.baseName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track.folderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    scriptText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row {
                    MediaBox(cover, 96.dp, 10.dp, playing, isVideo, videoPlayer, onVideoFullscreen)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.baseName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            track.folderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            scriptText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                var moreMenu by remember { mutableStateOf(false) }
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                FilterChip(selected = !showPlaylist && !showImages, onClick = onShowScript, label = { Text("台本") })
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = showPlaylist,
                    onClick = onShowPlaylist,
                    label = { Text("播放列表 " + playlistSize) },
                )

                if (imagesCount > 0) {
                    FilterChip(
                        selected = showImages,
                        onClick = onShowImages,
                        label = { Text("图片 " + imagesCount) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                }
                // 视频「全屏」：必须是图标行的独立按钮。之前放在视频画面里会被
                // SurfaceView 盖住点不到，塞进胶囊行又会被挤扁
                if (isVideo) {
                    IconButton(onClick = onVideoFullscreen) {
                        Icon(Icons.Default.OpenInFull, "全屏播放")
                    }
                }
                // 沉浸与布局是高频操作，留在外面；其余按功能收进「更多」
                IconButton(onClick = onToggleImmersive) {
                    Icon(
                        if (immersive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        "沉浸模式",
                    )
                }
                if (showLayoutToggle) {
                    IconButton(onClick = onToggleLayout) {
                        Icon(
                            Icons.Default.AspectRatio,
                            if (bigCover) "切换到紧凑界面" else "切换到大封面界面",
                        )
                    }
                }
                Box {
                    FilterChip(
                        selected = false,
                        onClick = { moreMenu = true },
                        label = { Text("更多") },
                    )
                    DropdownMenu(
                        expanded = moreMenu,
                        onDismissRequest = { moreMenu = false },
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        shape = asmrShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            MaterialTheme.colorScheme.onBackground,
                        ),
                    ) {
                        MenuLabel("显示")
                        if (isVideo) {
                            DropdownMenuItem(
                                text = { Text("视频全屏") },
                                onClick = { onVideoFullscreen(); moreMenu = false },
                            )
                        }
                        MenuLabel("播放倍数")
                        DropdownMenuItem(
                            text = {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MainViewModel.SPEED_STEPS.forEach { s ->
                                        FilterChip(
                                            selected = s == speed,
                                            onClick = { onSpeed(s) },
                                            label = { Text(formatSpeed(s)) },
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                }
                            },
                            onClick = {},
                        )
                        if (variants.size > 1) {
                            MenuLabel("格式（同一音频的不同版本）")
                            variantLabels.forEach { pair ->
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
                                    onClick = { onSwitchVariant(pair.first); moreMenu = false },
                                )
                            }
                        }
                        MenuLabel("台本")
                        DropdownMenuItem(
                            text = { Text("选择台本") },
                            onClick = { onPickScript(); moreMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("写入标签") },
                            onClick = { onEmbed(); moreMenu = false },
                        )
                    }
                }
            }
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
        if (LocalIsBright.current) {
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
private fun installVideoGestures(view: androidx.media3.ui.PlayerView) {
    val ctx = view.context
    var boostFrom = 0f
    val gd = android.view.GestureDetector(
        ctx,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                val p = view.player ?: return false
                val delta = if (e.x < view.width / 2f) -10_000L else 10_000L
                p.seekTo((p.currentPosition + delta).coerceIn(0L, p.duration.coerceAtLeast(0L)))
                return true
            }

            override fun onLongPress(e: android.view.MotionEvent) {
                val p = view.player ?: return
                boostFrom = p.playbackParameters.speed
                p.setPlaybackSpeed(2f)
            }

            override fun onScroll(
                e1: android.view.MotionEvent?,
                e2: android.view.MotionEvent,
                dx: Float,
                dy: Float,
            ): Boolean {
                if (e1 == null || view.height <= 0) return false
                val delta = (e1.y - e2.y) / view.height // 上滑为正
                if (e1.x < view.width / 2f) {
                    val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE)
                        as? android.media.AudioManager ?: return true
                    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    val next = (cur + delta * max * 2f).toInt().coerceIn(0, max)
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, 0)
                } else {
                    val act = ctx as? android.app.Activity ?: return true
                    val attrs = act.window.attributes
                    val base = if (attrs.screenBrightness >= 0f) attrs.screenBrightness else 0.5f
                    attrs.screenBrightness = (base + delta * 2f).coerceIn(0.05f, 1f)
                    act.window.attributes = attrs
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
    val duration = ui.durationMs.coerceAtLeast(1L)
    val sliderValue = dragging ?: (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 2.dp, end = 6.dp, top = 2.dp),
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
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = true
                        showController()
                        keepScreenOn = true
                        setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        player = vm.playerForView
                        installVideoGestures(this)
                    }
                },
                update = {
                    it.player = vm.playerForView
                    it.resizeMode = resizeMode
                },
            )
        }
        // 走带 + 工具条
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
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
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.94f))
                .padding(12.dp),
        ) {
            AsyncImage(
                model = File(images[index]),
                contentDescription = "图片，双击放大",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { index = (index - 1 + images.size) % images.size },
                ) { Text("上一张") }
                Text(
                    (index + 1).toString() + " / " + images.size,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                )
                TextButton(onClick = { index = (index + 1) % images.size }) { Text("下一张") }
                if (scale > 1.01f) {
                    TextButton(onClick = { resetZoom() }) { Text("复位") }
                }
                TextButton(onClick = onClose) { Text("关闭") }
            }
        }
    }
}

/** 范例里封面底部那条黑色均衡器。 */
@Composable
private fun EqStrip(playing: Boolean, modifier: Modifier = Modifier) {
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
            val h = if (playing) {
                val t = (phase + i * 0.17f) % 1f
                (0.15f + 0.75f * kotlin.math.abs(kotlin.math.sin(t * 3.14159f))).coerceIn(0.15f, 0.9f)
            } else {
                0.25f
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .clip(asmrShape(999.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )
        }
    }
}

private fun scriptLabel(fromEmbedded: Boolean, formatLabel: String?, reason: String?): String = when {
    fromEmbedded -> "台本来自音频内嵌标签"
    formatLabel != null -> formatLabel + " · " + (reason ?: "")
    else -> "未匹配到台本"
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

@Composable
private fun Controls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    sleepRemainingMs: Long?,
    onSetSleep: (Long?) -> Unit,
    playMode: String,
    onCyclePlayMode: () -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    var timerMenu by remember { mutableStateOf(false) }
    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = dragging ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)

    // 进度、时间、走带、定时合并成同一张卡片
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(asmrShape(12.dp))
            .background(asmrRowColor())
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Slider(
            value = sliderValue,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeekTo((it * duration).toLong()) }
                dragging = null
            },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatTime(positionMs), style = MaterialTheme.typography.labelSmall)
            Box {
                TextButton(onClick = { timerMenu = true }) {
                    Text(
                        if (sleepRemainingMs != null) "定时 " + formatTime(sleepRemainingMs) else "定时停止",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sleepRemainingMs != null) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
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
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
                ) {
                    DropdownMenuItem(
                        text = { Text("关闭定时") },
                        onClick = { onSetSleep(null); timerMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("10 秒后停止") },
                        onClick = { onSetSleep(10L); timerMenu = false },
                    )
                    listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.toString() + " 分钟后停止") },
                            onClick = { onSetSleep(m * 60L); timerMenu = false },
                        )
                    }
                }
            }
            Text(formatTime(if (durationMs > 0) durationMs else 0L), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (playMode == "shuffle") Icons.Default.Shuffle else Icons.Default.Repeat,
                    when (playMode) {
                        "shuffle" -> "随机播放（点按切换）"
                        "loop" -> "列表循环（点按切换）"
                        else -> "顺序播放（点按切换）"
                    },
                    Modifier.size(24.dp),
                    tint = if (playMode == "sequence") {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(28.dp))
            }
            IconButton(onClick = { onSeekBy(-10_000) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Replay10, "后退 10 秒", Modifier.size(28.dp))
            }
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(70.dp),
                // 「版式」皮肤：参考图里是实心墨黑圆钮
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (LocalEditorial.current) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (LocalEditorial.current) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                ),
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "播放/暂停",
                    Modifier.size(38.dp),
                )
            }
            IconButton(onClick = { onSeekBy(10_000) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Forward10, "前进 10 秒", Modifier.size(28.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipNext, "下一首", Modifier.size(28.dp))
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
) {
    if (parsed.timedLines.isNotEmpty()) {
        TimedScript(parsed.timedLines, positionMs, autoScroll, fontScale, onSeek)
    } else {
        PlainScript(parsed.plainText, positionMs, durationMs, autoScroll, speed, fontScale)
    }
}

@Composable
private fun TimedScript(
    lines: List<TimedLine>,
    positionMs: Long,
    autoScroll: Boolean,
    fontScale: Float,
    onSeek: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    val index = lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    LaunchedEffect(index, autoScroll) {
        if (autoScroll && index < lines.size) listState.animateScrollToItem(index)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lines) { i, line ->
            val active = i == index
            Text(
                line.text,
                fontSize = ((if (active && LocalEditorial.current) 18f else 16f) * fontScale).sp,
                lineHeight = (26 * fontScale).sp,
                // 「版式」皮肤：当前句用衬线（参考图的歌词面板那种编辑排版感）
                fontFamily = if (active && LocalEditorial.current) FontFamily.Serif else FontFamily.SansSerif,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.startMs) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
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
) {
    val scrollState = rememberScrollState()
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    LaunchedEffect(progress, autoScroll, speed, scrollState.maxValue) {
        if (autoScroll && scrollState.maxValue > 0) {
            val target = (scrollState.maxValue * progress * speed).toInt().coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(target)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            fontSize = (15 * fontScale).sp,
            lineHeight = (26 * fontScale).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
