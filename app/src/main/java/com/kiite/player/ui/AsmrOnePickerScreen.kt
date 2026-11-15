package com.kiite.player.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.core.content.ContextCompat
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
    // 通知栏进度需要 POST_NOTIFICATIONS（Android 13+）。拒绝了也不影响下载本身，
    // 只是看不到通知——所以「请求归请求，下载照常开始」。
    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 授权与否都继续，下载不依赖通知 */ }
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
            // 「数据」按钮：把 /api/tracks 的原始响应摊开来看。线上字段和本地假设不一致时，
            // 一张截图就能定位（前几轮排查全靠猜字段，绕了很多弯）。
            var showRaw by remember { mutableStateOf(false) }
            if (showRaw) {
                val raw = com.kiite.player.data.AsmrOneClient.lastTreeRaw
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showRaw = false },
                    confirmButton = {
                        TextButton(onClick = { showRaw = false }) { Text("关闭") }
                    },
                    title = { Text("原始文件树（" + raw.length + " 字符）") },
                    text = {
                        Text(
                            raw.take(8000).ifEmpty { "（空）" },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .heightIn(max = 380.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        )
                    },
                )
            }

            // 真机防双触发（beta3~beta7 真机全失败、模拟器全通过；beta8 的 Toast 证明
            // 事件已到达但结果停在 0——「+1 后又被 -1」）。这里改成：
            //  1) 绝对赋值 —— 勾/取消都写目标值而非取反，同一次点击两个回调同收也结果一致；
            //  2) 同行 700ms 去抖 —— 双击回退 / 重复注入的第二次事件直接丢弃
            //     （正常人双击间隔 200~500ms，700ms 能兜住；想反悔等 0.7s 再点即可）；
            //  3) 明确 Toast —— 写清楚勾选/取消了什么、现在共几个，效果一目了然，
            //     也方便远程诊断（截图里的 Toast 就是现场记录）。
            val toastCtx = androidx.compose.ui.platform.LocalContext.current
            var lastToggleKey by remember { mutableStateOf<String?>(null) }
            var lastToggleAt by remember { mutableStateOf(0L) }
            fun toggleRow(rowKey: String, sources: Set<String>, target: Boolean, label: String) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (rowKey == lastToggleKey && now - lastToggleAt < 400L) return
                lastToggleKey = rowKey
                lastToggleAt = now
                selected = if (target) selected + sources else selected - sources
                val verb = if (target) "勾选 " else "取消 "
                android.widget.Toast.makeText(
                    toastCtx,
                    verb + label + " · 共 " + selected.size + " 个",
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
                                        onValueChange = { check ->
                                            if (sources.isEmpty()) {
                                                android.widget.Toast.makeText(
                                                    toastCtx,
                                                    "这个文件夹里没有可下载的文件",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            } else {
                                                toggleRow(
                                                    row.key,
                                                    sources,
                                                    check,
                                                    "文件夹「" + node.displayName + "」",
                                                )
                                            }
                                        },
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
                                    node.displayName,
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
                                        val src = node.source
                                        if (src == null) {
                                            android.widget.Toast.makeText(
                                                toastCtx,
                                                "该文件没有可下载路径（数据缺失）",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            toggleRow(row.key, setOf(src), check, node.displayName.ifEmpty { src })
                                        }
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
                                node.displayName.ifEmpty { node.source ?: "" },
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
                if (s.running || s.completed || s.okCount > 0) {
                    DownloadProgressBar(
                        progress = when {
                            s.running -> s.progress
                            s.completed -> 1f
                            else -> 0f
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        height = 5.dp,
                        bar = if (s.failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    downloadDetailText(s),
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (s.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                if (s.running) {
                    Text(
                        "可以退出本页，下载会继续；通知栏可看进度、也能取消",
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                TextButton(onClick = { showRaw = true }) { Text("数据") }
                when {
                    thisDl?.running == true -> TextButton(onClick = { vm.cancelAsmrDownload() }) {
                        Text("取消", color = MaterialTheme.colorScheme.error)
                    }
                    else -> TextButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    ctx, Manifest.permission.POST_NOTIFICATIONS,
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                runCatching { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            }
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

/** 行的 key：文件用 source（唯一），文件夹用名字/序号。 */
private fun rowKey(node: AsmrOneTreeNode, index: Int): String =
    node.source ?: node.displayName.ifEmpty { index.toString() }

/** 递归拼展开列表：只展开 expanded 里的文件夹；根节点本身不占行。 */
private fun flatten(root: AsmrOneTreeNode, expanded: Set<String>): List<TreeRow> {
    val out = mutableListOf<TreeRow>()
    fun walk(children: List<AsmrOneTreeNode>, parentKey: String, depth: Int) {
        children.forEachIndexed { i, node ->
            val key = parentKey + "/" + rowKey(node, i)
            out.add(TreeRow(key, node, depth))
            if (node.children.isNotEmpty() && key in expanded) {
                walk(node.children, key, depth + 1)
            }
        }
    }
    walk(root.children, root.displayName, 0)
    return out
}

/** 树里所有文件夹的 key（与 flatten 的 key 规则一致），用于默认全部展开。 */
private fun collectFolderKeys(root: AsmrOneTreeNode): Set<String> {
    val out = mutableSetOf<String>()
    fun walk(children: List<AsmrOneTreeNode>, parentKey: String) {
        children.forEachIndexed { i, node ->
            val key = parentKey + "/" + rowKey(node, i)
            if (node.children.isNotEmpty()) {
                out.add(key)
                walk(node.children, key)
            }
        }
    }
    walk(root.children, root.displayName)
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
