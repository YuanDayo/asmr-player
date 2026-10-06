package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.kiite.player.ui.theme.LocalIsBright
import com.kiite.player.util.Permissions

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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }

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

    var tab by remember { mutableIntStateOf(0) }
    val current by vm.currentTrack.collectAsStateWithLifecycle()
    val playerUi by vm.player.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val coverPath by vm.currentCover.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!videoFull) Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = asmrBarColor()),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (LocalIsBright.current) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
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
                    }
                },
            )
            }
        },
        bottomBar = {
            if (videoFull) {
                // 视频沉浸全屏：顶栏与底栏都不显示
            } else if (settings.immersive && tab == 1) {
                // 沉浸模式：播放页不显示下方的功能切换卡片
            } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                current?.let { track ->
                    MiniPlayer(
                        title = track.baseName,
                        subtitle = track.folderName,
                        cover = coverPath,
                        isPlaying = playerUi.isPlaying,
                        progress = if (playerUi.durationMs > 0) {
                            (playerUi.positionMs.toFloat() / playerUi.durationMs).coerceIn(0f, 1f)
                        } else 0f,
                        onToggle = { vm.playPause() },
                        onOpen = { tab = 1 },
                    )
                }
                // HyperOS 风格：悬浮圆角底栏 + 选中胶囊
                NavigationBar(
                    containerColor = asmrBarColor(),
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.LibraryMusic, null) },
                        label = { Text("曲库") },
                        colors = navItemColors(),
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.PlayCircle, null) },
                        label = { Text("播放") },
                        colors = navItemColors(),
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("设置") },
                        colors = navItemColors(),
                    )
                }
            }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.fillMaxHeight().widthIn(max = 760.dp)) {
            when (tab) {
                0 -> LibraryScreen(vm)
                1 -> PlayerScreen(vm)
                else -> SettingsScreen(vm)
            }
            }
            busy?.let { text ->
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsmrCard {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            Text(text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun navItemColors() = if (LocalIsBright.current) {
    // 范例风格：选中项填黄，图标与文字用墨黑
    NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
} else {
    NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Surface(
        color = asmrBarColor(),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpen() },
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        Icon(Icons.Default.LibraryMusic, null, Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onToggle) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "播放/暂停")
                }
            }
        }
    }
}
