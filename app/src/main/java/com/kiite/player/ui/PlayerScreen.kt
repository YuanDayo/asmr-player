package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
    val scan by vm.scan.collectAsStateWithLifecycle()

    var showPlaylist by remember { mutableStateOf(false) }
    var showScriptPicker by remember { mutableStateOf(false) }

    val current = track
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有在播放的音频", style = MaterialTheme.typography.bodyMedium)
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

    val playlist: List<TrackEntry> = if (scan == null) emptyList() else vm.currentPlaylist()

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
            activePath = activePath,
            onSwitchVariant = { vm.switchVariant(it) },
            immersive = settings.immersive,
            onToggleImmersive = { vm.setImmersive(!settings.immersive) },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
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
    activePath: String?,
    onSwitchVariant: (String) -> Unit,
    immersive: Boolean,
    onToggleImmersive: () -> Unit,
) {
    AsmrCard(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = if (bigCover) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            if (bigCover) {
                CoverBox(cover, 220.dp, 18.dp)
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
                    CoverBox(cover, 96.dp, 10.dp)
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
                FilterChip(selected = !showPlaylist, onClick = onShowScript, label = { Text("台本") })
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = showPlaylist,
                    onClick = onShowPlaylist,
                    label = { Text("播放列表 " + playlistSize) },
                )
                Spacer(Modifier.width(8.dp))
                // 播放倍数
                var speedMenu by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = speed != 1.0f,
                        onClick = { speedMenu = true },
                        label = { Text(formatSpeed(speed) + "×") },
                    )
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        MainViewModel.SPEED_STEPS.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        formatSpeed(s) + "×" + if (s == 1.0f) "   默认速度" else "",
                                        fontWeight = if (s == speed) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    onSpeed(s)
                                    speedMenu = false
                                },
                                leadingIcon = {
                                    if (s == speed) {
                                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                    } else {
                                        Spacer(Modifier.size(18.dp))
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleLayout) {
                    Icon(
                        Icons.Default.AspectRatio,
                        if (bigCover) "切换到紧凑界面" else "切换到大封面界面",
                    )
                }
                IconButton(onClick = onToggleImmersive) {
                    Icon(
                        if (immersive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        "沉浸模式",
                    )
                }
                IconButton(onClick = onPickScript) { Icon(Icons.Default.Description, "选择台本") }
                IconButton(onClick = onEmbed) { Icon(Icons.Default.Save, "写入标签") }
            }
            if (variants.size > 1) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "同名格式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    variants.forEach { p ->
                        FilterChip(
                            selected = (activePath ?: variants.first()) == p,
                            onClick = { onSwitchVariant(p) },
                            label = { Text(p.substringAfterLast('.').uppercase()) },
                        )
                        Spacer(Modifier.width(6.dp))
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
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = dragging ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Slider(
            value = sliderValue,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeekTo((it * duration).toLong()) }
                dragging = null
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), style = MaterialTheme.typography.labelSmall)
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
