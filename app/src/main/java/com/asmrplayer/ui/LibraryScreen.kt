package com.asmrplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.asmrplayer.core.ProjectEntry
import com.asmrplayer.core.ScanResult
import com.asmrplayer.core.TrackEntry
import com.asmrplayer.util.Permissions
import java.io.File

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val selectedProject by vm.selectedProject.collectAsStateWithLifecycle()
    val manualLinks by vm.manualLinks.collectAsStateWithLifecycle()
    val currentTrack by vm.currentTrack.collectAsStateWithLifecycle()
    var showRootPicker by remember { mutableStateOf(false) }
    var scriptPickerTrack by remember { mutableStateOf<TrackEntry?>(null) }

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

    Column(Modifier.fillMaxSize()) {
        RootHeader(
            rootPath = rootPath,
            scanning = scanning,
            projectCount = scan?.projectCount ?: 0,
            trackCount = scan?.trackCount ?: 0,
            matched = scan?.withScriptCount ?: 0,
            onRescan = { vm.rescan() },
            onChangeRoot = { showRootPicker = true },
        )
        if (scanning && scan == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val project = selectedProject
        if (project == null) {
            ProjectList(scan = scan, onOpen = { vm.selectProject(it) })
        } else {
            ProjectDetail(
                vm = vm,
                project = scan?.projects?.firstOrNull { it.path == project },
                currentPath = currentTrack?.path,
                onBack = { vm.selectProject(null) },
                onPickScript = { scriptPickerTrack = it },
            )
        }
    }
}

@Composable
private fun EmptyLibrary(onChoose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("还没有选择曲库目录", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "选一个装着 ASMR 解压文件夹的目录。应用会把每部作品识别成一个「总项目」，" +
                "并把它的全部音频统合在一起展示。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onChoose) { Text("选择文件夹") }
    }
}

@Composable
private fun RootHeader(
    rootPath: String,
    scanning: Boolean,
    projectCount: Int,
    trackCount: Int,
    matched: Int,
    onRescan: () -> Unit,
    onChangeRoot: () -> Unit,
) {
    AsmrCard(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("曲库目录", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                rootPath,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text("项目 " + projectCount) })
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, label = { Text("音频 " + trackCount) })
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, label = { Text("已配台本 " + matched) })
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = onRescan, enabled = !scanning) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (scanning) "扫描中…" else "重新扫描")
                }
                TextButton(onClick = onChangeRoot) {
                    Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("更换目录")
                }
            }
        }
    }
}

@Composable
private fun ProjectList(scan: ScanResult?, onOpen: (String) -> Unit) {
    val projects = scan?.projects ?: emptyList()
    if (projects.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有找到音频文件", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(projects, key = { it.path }) { project ->
            ListItem(
                headlineContent = { Text(projectTitle(project), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        project.chapters.size.toString() + " 章 · " + project.trackCount +
                            " 首 · " + project.scriptCount + " 份台本",
                    )
                },
                leadingContent = {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val cover = project.coverPath
                        if (cover != null) {
                            AsyncImage(
                                model = File(cover),
                                contentDescription = "专辑封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Default.LibraryMusic, null, Modifier.size(22.dp))
                        }
                    }
                },
                modifier = Modifier.clickable { onOpen(project.path) },
            )
        }
    }
}

@Composable
private fun ProjectDetail(
    vm: MainViewModel,
    project: ProjectEntry?,
    currentPath: String?,
    onBack: () -> Unit,
    onPickScript: (TrackEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    projectTitle(project),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (project != null && project.trackCount > 0) {
                TextButton(onClick = {
                    val queue = vm.tracksOfProject(project.path)
                    queue.firstOrNull()?.let { vm.playTrack(it, queue) }
                }) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("播放全部")
                }
                TextButton(onClick = { vm.embedProject(project.path) }) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("写入台本标签")
                }
            }
        }
        HorizontalDivider()

        if (project == null) return@Column

        val chapters = vm.chaptersOf(project)
        LazyColumn(Modifier.fillMaxSize()) {
            for ((chapter, tracks) in chapters) {
                item(key = "h:" + chapter.path) {
                    ChapterHeader(name = chapter.name, count = tracks.size)
                }
                items(tracks, key = { "t:" + it.path }) { track ->
                    TrackRow(
                        track = track,
                        active = track.path == currentPath,
                        onPlay = { vm.playTrack(track, vm.tracksOfProject(project.path)) },
                        onPickScript = onPickScript,
                    )
                }
            }
        }
    }
}

private fun projectTitle(p: ProjectEntry?): String =
    p?.let { if (it.code != null) "[" + it.code + "] " + it.name else it.name } ?: ""

@Composable
private fun ChapterHeader(name: String, count: Int) {
    Text(
        name + " · " + count + " 首",
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TrackRow(
    track: TrackEntry,
    active: Boolean,
    onPlay: () -> Unit,
    onPickScript: (TrackEntry) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                track.baseName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = { ScriptBadge(track) },
        leadingContent = {
            Icon(
                Icons.Default.PlayArrow,
                null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            IconButton(onClick = { onPickScript(track) }) {
                Icon(
                    Icons.Default.Description,
                    "选择台本",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable { onPlay() },
    )
}

@Composable
private fun ScriptBadge(track: TrackEntry) {
    val primary = track.primaryScript
    if (primary == null) {
        Text("未匹配到台本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    } else {
        val manual = if (primary.manual) "手动 · " else ""
        val extra = if (track.scripts.size > 1) " 等 " + track.scripts.size + " 份" else ""
        val timed = if (primary.format.isTimed) " · 可逐句同步" else ""
        Text(
            manual + primary.format.label + " · " + primary.scriptName + extra + timed,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
