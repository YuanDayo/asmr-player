package com.asmrplayer.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmrplayer.core.LibraryScanner
import com.asmrplayer.util.SafPath
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 手动选择台本文件：可浏览目录、可交给系统文件管理器打开，也可清除已手动指定的台本。 */
@Composable
fun ScriptPickerDialog(
    start: File,
    hasManual: Boolean,
    onPick: (File) -> Unit,
    onPickUri: (android.net.Uri) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(start) }
    var dirs by remember { mutableStateOf<List<File>>(emptyList()) }
    var scripts by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf<String?>(null) }

    // 「用其他应用打开」：交给系统文件管理器，先跳文件夹再选文件
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = SafPath.fromDocumentUri(context, uri)
        val file = path?.let(::File)
        if (file != null && file.isFile) {
            onPick(file)
        } else {
            // 云盘等 provider 拿不到路径，直接复制进应用内部存储
            onPickUri(uri)
        }
    }

    LaunchedEffect(current) {
        loading = true
        val d = ArrayList<File>()
        val s = ArrayList<File>()
        withContext(Dispatchers.IO) {
            val children = current.listFiles() ?: emptyArray()
            for (f in children) {
                if (f.isDirectory && !f.name.startsWith('.')) d.add(f)
                else if (f.isFile && f.name.substringAfterLast('.', "").lowercase() in LibraryScanner.DEFAULT_SCRIPT_EXT) {
                    s.add(f)
                }
            }
        }
        d.sortBy { it.name.lowercase() }
        s.sortBy { it.name.lowercase() }
        dirs = d
        scripts = s
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择台本文件") },
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
                    if (onClear != null && hasManual) {
                        TextButton(onClick = onClear) {
                            Icon(Icons.Default.Delete, null, Modifier.width(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("清除手动指定")
                        }
                    }
                }
                TextButton(onClick = { systemPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.width(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("用其他应用打开")
                }
                val pickHint = hint
                if (pickHint != null) {
                    Text(pickHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(dirs, key = { "d:" + it.absolutePath }) { dir ->
                        ListItem(
                            headlineContent = { Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.Folder, null) },
                            modifier = Modifier.clickable { current = dir },
                        )
                    }
                    items(scripts, key = { "f:" + it.absolutePath }) { file ->
                        ListItem(
                            headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("台本文件") },
                            leadingContent = { Icon(Icons.Default.Description, null) },
                            modifier = Modifier.clickable { onPick(file) },
                        )
                    }
                    if (dirs.isEmpty() && scripts.isEmpty() && !loading) {
                        item {
                            Text(
                                "（这里没有台本文件）",
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
