package com.kiite.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiite.player.util.SafPath
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 内置文件夹选择器；也可交给系统文件管理器（用其他应用打开）。 */
@Composable
fun FolderPickerDialog(
    start: File,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(start) }
    var dirs by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf<String?>(null) }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = SafPath.fromTreeUri(uri)
        val dir = path?.let(::File)
        if (dir != null && dir.isDirectory) {
            onPick(dir)
        } else {
            hint = "这个位置无法还原成文件夹路径（可能是云盘），请换一个"
        }
    }

    LaunchedEffect(current) {
        loading = true
        dirs = withContext(Dispatchers.IO) {
            current.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith('.') }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择文件夹") },
        text = {
            Column {
                Text(
                    current.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { current.parentFile?.let { current = it } },
                        enabled = current.parentFile != null,
                    ) {
                        Icon(Icons.Default.Upload, null, Modifier.width(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("上一级")
                    }
                    TextButton(onClick = { onPick(current) }) {
                        Icon(Icons.Default.Folder, null, Modifier.width(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("选此文件夹")
                    }
                }
                val pickHint = hint
                TextButton(onClick = { systemPicker.launch(null) }) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.width(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("用其他应用打开")
                }
                if (pickHint != null) {
                    Text(pickHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(dirs, key = { it.absolutePath }) { dir ->
                        ListItem(
                            headlineContent = { Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.Folder, null) },
                            modifier = Modifier.clickable { current = dir },
                        )
                    }
                    if (dirs.isEmpty() && !loading) {
                        item {
                            Text(
                                "（没有子文件夹）",
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
