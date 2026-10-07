package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
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
import com.kiite.player.ui.theme.LocalIsBright
import com.kiite.player.util.Permissions
import java.io.File

@Composable
fun PlayerScreen(vm: MainViewModel) {
    val track by vm.currentTrack.collectAsStateWithLifecycle()
    val ui by vm.player.collectAsStateWithLifecycle()
    val script by vm.script.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val manualLinks by vm.manualLinks.collectAsStateWithLifecycle()
    val cover by vm.currentCover.collectAsStateWithLifecycle()
    val activePath by vm.activePath.collectAsStateWithLifecycle()
    val images = remember(track) { vm.imagesOfCurrent() }
    val videoFull by vm.videoFullscreen.collectAsStateWithLifecycle()
    // 进入播放页一律非全屏，离开时也复位：
    // 否则全屏状态残留在 ViewModel 里，再进来就是全屏又退不出去
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.setVideoFullscreen(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.setVideoFullscreen(false) }
    }
    var showImages by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val scan by vm.scan.collectAsStateWithLifecycle()
    val sleepRemaining by vm.sleepRemainingMs.collectAsStateWithLifecycle()

    var showPlaylist by remember { mutableStateOf(false) }
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
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = false
                        player = vm.playerForView
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

    val playlist: List<TrackEntry> = if (scan == null) emptyList() else vm.currentPlaylist()

    viewerIndex?.let { idx ->
        if (images.isNotEmpty()) {
            ImageViewer(images, idx) { viewerIndex = null }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PlayerHeader(
            bigCover = settings.playerLayout != "classic",
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

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                showImages -> {
                    ImagePanel(images) { viewerIndex = it }
                }
                showPlaylist -> {
                    PlaylistPanel(
                        playlist = playlist,
                        currentPath = current.path,
                        autoScroll = settings.playlistAutoScroll,
                        onPick = { vm.playFromPlaylist(it) },
                    )
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
        )
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
                Spacer(Modifier.width(8.dp))
                if (imagesCount > 0) {
                    FilterChip(
                        selected = showImages,
                        onClick = onShowImages,
                        label = { Text("图片 " + imagesCount) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                }
                // 沉浸与布局是高频操作，留在外面；其余按功能收进「更多」
                IconButton(onClick = onToggleImmersive) {
                    Icon(
                        if (immersive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        "沉浸模式",
                    )
                }
                IconButton(onClick = onToggleLayout) {
                    Icon(
                        Icons.Default.AspectRatio,
                        if (bigCover) "切换到紧凑界面" else "切换到大封面界面",
                    )
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
                        shape = RoundedCornerShape(20.dp),
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
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(asmrBorder(RoundedCornerShape(corner))),
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
                .clip(RoundedCornerShape(corner))
                .then(asmrBorder(RoundedCornerShape(corner))),
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
            TextButton(
                onClick = { onVideoFullscreen() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            ) { Text("全屏") }
        }
    } else {
        CoverBox(cover, size, corner, playing)
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
                            .clip(RoundedCornerShape(14.dp))
                            .then(asmrBorder(RoundedCornerShape(14.dp)))
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
private fun ImageViewer(images: List<String>, start: Int, onClose: () -> Unit) {
    var index by remember { mutableStateOf(start.coerceIn(0, (images.size - 1).coerceAtLeast(0))) }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.94f))
                .padding(12.dp),
        ) {
            AsyncImage(
                model = File(images[index]),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                    .clip(RoundedCornerShape(50))
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
                    if (active) {
                        Icon(
                            Icons.Default.PlayArrow,
                            "正在播放",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                modifier = Modifier.clickable { onPick(i) },
            )
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
            .clip(RoundedCornerShape(12.dp))
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
                    shape = RoundedCornerShape(16.dp),
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
            IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, "上一首") }
            IconButton(onClick = { onSeekBy(-10_000) }) { Icon(Icons.Default.Replay10, "后退 10 秒") }
            FilledIconButton(onClick = onToggle, Modifier.size(56.dp)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "播放/暂停")
            }
            IconButton(onClick = { onSeekBy(10_000) }) { Icon(Icons.Default.Forward10, "前进 10 秒") }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "下一首") }
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
                fontSize = (16 * fontScale).sp,
                lineHeight = (26 * fontScale).sp,
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
