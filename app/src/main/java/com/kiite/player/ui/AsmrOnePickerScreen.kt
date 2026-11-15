package com.kiite.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiite.player.core.AsmrOneTreeNode
import com.kiite.player.core.AsmrOneTree

/**
 * asmr.one 文件选择页：像网站里那样勾选要下载的具体文件。
 *
 * 树形列表 + 复选框；进入时默认勾好「推荐音频」（同目录 mp3>wav>flac 去重，见
 * [AsmrOneTree.audioFiles]），不想挑直接点「下载所选」也能全下。文件夹的复选框
 * 作用于整个子树；下载进行中显示进度，退出页面下载继续。
 */
@Composable
fun AsmrOnePickerScreen(
    vm: MainViewModel,
    work: com.kiite.player.core.AsmrOneWork,
    onBack: () -> Unit,
) {
    val dl by vm.asmrDownload.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tree by remember { mutableStateOf<AsmrOneTreeNode?>(null) }

    LaunchedEffect(work.id) {
        val outcome = vm.fetchAsmrTree(work)
        tree = outcome.getOrNull()
        error = outcome.exceptionOrNull()?.message
        loading = false
    }

    // 全屏二级页：藏起迷你播放条与导航胶囊
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.setChromeHidden(true)
        onDispose { vm.setChromeHidden(false) }
    }
    androidx.activity.compose.BackHandler { onBack() }

    // 当前包版本名（选择页标题里显示，便于确认装的是哪个构建）
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val appVersion = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    "选择要下载的文件",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    (work.code ?: ("#" + work.id)) + " · 默认勾选推荐音频 · v" + appVersion,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(
                "文件列表获取失败：" + it,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        tree?.let { t ->
            // 展开的文件夹（按完整路径 key）；默认全部展开，勾选一目了然
            var expanded by remember(t) { mutableStateOf(collectFolderKeys(t)) }
            // 已勾选的文件 source 集合；默认 = 推荐音频（去重后）
            var selected by remember(t) {
                mutableStateOf(AsmrOneTree.audioFiles(t).mapNotNull { it.source }.toSet())
            }
            val rows = remember(t, expanded) { flatten(t, expanded) }

            // 真机防双触发（beta3~beta7 真机全失败、模拟器全通过的最大嫌疑：
            // 部分手机高报点率/触控固件会把一次点击注入两次事件，任何「切换式」
            // 逻辑都会被自己抵消）。这里改成：
            //  1) 绝对赋值 —— 勾/取消都写目标值而非取反，同一次点击两个回调同收也结果一致；
            //  2) 同行 350ms 去抖 —— 重复注入的第二次事件直接丢弃；
            //  3) Toast 反馈 —— 每次生效的勾选都弹「已选 N」，用户点没点中一目了然。
            val toastCtx = androidx.compose.ui.platform.LocalContext.current
            var lastToggleKey by remember { mutableStateOf<String?>(null) }
            var lastToggleAt by remember { mutableStateOf(0L) }
            fun toggleRow(rowKey: String, sources: Set<String>, target: Boolean) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (rowKey == lastToggleKey && now - lastToggleAt < 350L) return
                lastToggleKey = rowKey
                lastToggleAt = now
                selected = if (target) selected + sources else selected - sources
                android.widget.Toast.makeText(
                    toastCtx,
                    "已选 " + selected.size + " 个文件",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(rows, key = { _, r -> r.key }) { _, row ->
                    val node = row.node
                    val isFolder = node.children.isNotEmpty()
                    val sources = allSources(node)
                    if (isFolder) {
                        // 文件夹行拆成两个平级手势区：左侧勾选框独立 toggleable = 子树选中/取消；
                        // 其余区域点击 = 展开/收起。不能把勾选框嵌进 clickable 行——外层
                        // clickable 会先消费点击，内层 toggleable 收不到（真机踩了三次）。
                        val folderChecked = sources.isNotEmpty() && selected.containsAll(sources)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .toggleable(
                                        value = folderChecked,
                                        role = androidx.compose.ui.semantics.Role.Checkbox,
                                        onValueChange = { check -> toggleRow(row.key, sources, check) },
                                    )
                                    // 48dp 最小触控目标：真机上勾选框太小容易点偏
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .padding(start = (row.depth * 16).dp + 4.dp, end = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Checkbox(checked = folderChecked, onCheckedChange = null)
                            }
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        expanded = if (row.key in expanded) expanded - row.key else expanded + row.key
                                    }
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (row.key in expanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    Icons.Default.Folder,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    node.title ?: node.name ?: "",
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        // 文件：整行 toggleable，绝对赋值写目标值；Checkbox 的
                        // onCheckedChange 仍传 null，避免它嵌在可点击行里双触发
                        // （toggleRow 内部的去抖兜底真机重复注入事件）。
                        val checked = node.source != null && node.source in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    role = androidx.compose.ui.semantics.Role.Checkbox,
                                    onValueChange = { check ->
                                        node.source?.let { toggleRow(row.key, setOf(it), check) }
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .padding(start = (row.depth * 16).dp + 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (AsmrOneTree.isAudio(node)) Icons.Default.Audiotrack else Icons.Default.InsertDriveFile,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                node.title ?: node.name ?: node.source ?: "",
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // 底部操作条
            val thisDl = dl?.takeIf { it.workId == work.id }
            thisDl?.let { s ->
                Text(
                    s.status,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (s.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已选 " + selected.size + " 个文件",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = {
                    selected = AsmrOneTree.audioFiles(t).mapNotNull { it.source }.toSet()
                }) { Text("推荐音频") }
                TextButton(onClick = { selected = allSources(t) }) { Text("全选") }
                TextButton(onClick = { selected = emptySet() }) { Text("清空") }
                when {
                    thisDl?.running == true -> TextButton(onClick = { vm.cancelAsmrDownload() }) {
                        Text("取消", color = MaterialTheme.colorScheme.error)
                    }
                    else -> TextButton(
                        onClick = {
                            vm.clearAsmrDownload()
                            val files = collectSelected(t, selected)
                            if (files.isEmpty()) {
                                vm.say("没有勾选任何文件")
                            } else {
                                vm.downloadAsmrOne(work, files, t.name)
                            }
                        },
                        enabled = dl?.running != true,
                    ) { Text(if (dl?.running == true) "下载中…" else "下载所选") }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** 展开后的树行。 */
private data class TreeRow(
    val key: String,
    val node: AsmrOneTreeNode,
    val depth: Int,
)

/** 递归拼展开列表：只展开 expanded 里的文件夹；根节点本身不占行。 */
private fun flatten(root: AsmrOneTreeNode, expanded: Set<String>): List<TreeRow> {
    val out = mutableListOf<TreeRow>()
    fun walk(children: List<AsmrOneTreeNode>, parentKey: String, depth: Int) {
        children.forEachIndexed { i, node ->
            val key = parentKey + "/" + (node.source ?: node.name ?: i.toString())
            out.add(TreeRow(key, node, depth))
            if (node.children.isNotEmpty() && key in expanded) {
                walk(node.children, key, depth + 1)
            }
        }
    }
    walk(root.children, root.name ?: "", 0)
    return out
}

/** 树里所有文件夹的 key（与 flatten 的 key 规则一致），用于默认全部展开。 */
private fun collectFolderKeys(root: AsmrOneTreeNode): Set<String> {
    val out = mutableSetOf<String>()
    fun walk(children: List<AsmrOneTreeNode>, parentKey: String) {
        children.forEachIndexed { i, node ->
            val key = parentKey + "/" + (node.source ?: node.name ?: i.toString())
            if (node.children.isNotEmpty()) {
                out.add(key)
                walk(node.children, key)
            }
        }
    }
    walk(root.children, root.name ?: "")
    return out
}

/** 节点子树下所有叶子文件的 source（含自身如果是文件）。 */
private fun allSources(node: AsmrOneTreeNode): Set<String> {
    if (node.children.isEmpty()) return node.source?.let { setOf(it) } ?: emptySet()
    return node.children.flatMap { allSources(it) }.toSet()
}

/** 按勾选集合挑出文件节点（保持树内顺序）。 */
private fun collectSelected(root: AsmrOneTreeNode, selected: Set<String>): List<AsmrOneTreeNode> {
    val out = mutableListOf<AsmrOneTreeNode>()
    fun walk(node: AsmrOneTreeNode) {
        if (node.children.isEmpty()) {
            if (node.source != null && node.source in selected) out.add(node)
            return
        }
        node.children.forEach { walk(it) }
    }
    walk(root)
    return out
}
