@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiite.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiite.player.AppInfo
import com.kiite.player.data.dlsiteCookieCount
import com.kiite.player.data.AppSettings
import com.kiite.player.ui.theme.AccentColor
import com.kiite.player.ui.theme.BackgroundPreset
import com.kiite.player.ui.theme.LocalBottomBarInset
import com.kiite.player.ui.theme.SkinStyle
import com.kiite.player.ui.theme.ThemeMode
import com.kiite.player.util.Permissions
import java.io.File

/** 设置里的子页面。 */
private enum class SettingsPage(val label: String, val mono: String, val icon: ImageVector) {
    APPEARANCE("外观与皮肤", "APPEARANCE", Icons.Default.Palette),
    LIBRARY("曲库与扫描", "LIBRARY", Icons.Default.Folder),
    SCRIPT("台本与音频", "SCRIPT", Icons.Default.Description),
    PLAYBACK("播放与列表", "PLAYBACK", Icons.Default.PlayCircle),
    DLSITE("DLsite", "DL SITE", Icons.Default.Language),
    DEBUG("调试日志", "DEBUG LOG", Icons.Default.BugReport),
    ABOUT("关于", "ABOUT", Icons.Default.Info),
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    val open = page
    if (showLogin) {
        DlsiteLoginScreen {
            showLogin = false
            vm.refreshDlsiteLogin()
        }
    } else if (open == null) {
        SettingsHome(vm, settings) { page = it }
    } else {
        SettingsSubPage(vm, settings, open, onLogin = { showLogin = true }) { page = null }
    }
}

// ---------------- 主页：分组入口 ----------------

@Composable
private fun SettingsHome(vm: MainViewModel, settings: AppSettings, onOpen: (SettingsPage) -> Unit) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val loginState by vm.dlsiteLoginState.collectAsStateWithLifecycle()
    val logLines by com.kiite.player.data.AppLog.lines.collectAsStateWithLifecycle()
    val dlsiteCount by remember(vm) { mutableStateOf(vm.dlsiteCount) }
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var showBookmarks by remember { mutableStateOf(false) }
    // 台本匹配报告：与曲库页共用一个弹窗，缺台本的逐首直达手动指定
    val manualLinks by vm.manualLinks.collectAsStateWithLifecycle()
    var showScriptReport by remember { mutableStateOf(false) }
    var scriptPickerTrack by remember { mutableStateOf<com.kiite.player.core.TrackEntry?>(null) }
    // 曲库体检：空作品 / 可疑小文件 / 跨作品重复
    var showAudit by remember { mutableStateOf(false) }

    val settingsScroll = rememberScrollState()
    TrackScroll(settingsScroll)
    Column(
        Modifier.fillMaxSize().verticalScroll(settingsScroll).padding(12.dp)
            .padding(bottom = LocalBottomBarInset.current),
    ) {
        SettingsEntry(
            SettingsPage.APPEARANCE,
            SkinStyle.of(settings.skinStyle).label + " · " + ThemeMode.of(settings.themeMode).label +
                " · 遮罩 " + "%.2f".format(settings.backgroundDim),
        ) { onOpen(it) }
        SettingsEntry(
            SettingsPage.LIBRARY,
            settings.rootPath ?: "未设置根目录",
        ) { onOpen(it) }
        SettingsEntry(
            SettingsPage.SCRIPT,
            "已匹配 " + (scan?.withScriptCount ?: 0) + " / " + (scan?.trackCount ?: 0) + " 首",
        ) { onOpen(it) }
        // 台本匹配报告：不占设置子页，点开直接看哪些音频没台本
        if (scan != null) {
            val unmatchedProjects = scan?.projects?.count { p ->
                vm.tracksOfProject(p.path).any { !it.hasScript }
            } ?: 0
            QuickEntry(
                icon = Icons.Default.Description,
                title = "台本匹配报告",
                summary = if (unmatchedProjects > 0) {
                    "有 " + unmatchedProjects + " 部作品缺台本，点开逐首指定"
                } else {
                    "全部音频都匹配到了台本"
                },
            ) { showScriptReport = true }
        }
        // 作品体检：与台本报告互补，管的是「文件本身」的完整性
        if (scan != null) {
            QuickEntry(
                icon = Icons.Default.CheckCircle,
                title = "作品体检",
                summary = "空作品 · 可疑小文件 · 跨作品重复检测",
            ) { showAudit = true }
        }
        // 书签与历史不是「设置项」，是用户数据，所以直接在这里开面板，不占一个设置子页
        QuickEntry(
            icon = Icons.Default.Bookmark,
            title = "书签与历史",
            summary = "书签 " + bookmarks.size + " 条 · 历史 " + history.records.size +
                " 条 · 今日 " + com.kiite.player.core.HistoryMath.formatDuration(
                com.kiite.player.core.HistoryMath.todayMs(history.dailyListenedMs, System.currentTimeMillis()),
            ),
        ) { showBookmarks = true }
        SettingsEntry(
            SettingsPage.PLAYBACK,
            when (settings.playerLayout) {
                "classic" -> "紧凑布局"
                "big" -> "全页大图"
                else -> "大封面布局"
            },
        ) { onOpen(it) }
        SettingsEntry(
            SettingsPage.DLSITE,
            dlsiteStateLabel(loginState) + " · 已识别 " + dlsiteCount + " 部",
        ) { onOpen(it) }
        SettingsEntry(SettingsPage.DEBUG, logLines.size.toString() + " 行记录") { onOpen(it) }
        SettingsEntry(SettingsPage.ABOUT, "v" + AppInfo.VERSION_NAME) { onOpen(it) }
        Spacer(Modifier.height(24.dp))
    }

    if (showBookmarks) {
        BookmarksPanel(vm = vm, onDismiss = { showBookmarks = false })
    }

    if (showScriptReport) {
        ScriptReportDialog(
            projects = scan?.projects ?: emptyList(),
            tracksOf = { vm.tracksOfProject(it) },
            onPickTrack = { track ->
                showScriptReport = false
                scriptPickerTrack = track
            },
            onDismiss = { showScriptReport = false },
        )
    }

    if (showAudit) {
        LibraryAuditDialog(
            projects = scan?.projects ?: emptyList(),
            tracksOf = { vm.tracksOfProject(it) },
            onDismiss = { showAudit = false },
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
}

/** 和 [SettingsEntry] 同样的外形，但点开来是一块面板而不是设置子页。 */
@Composable
private fun QuickEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    AsmrCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(asmrShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsEntry(page: SettingsPage, summary: String, onOpen: (SettingsPage) -> Unit) {
    AsmrCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(page) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(asmrShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(page.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(page.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- 子页 ----------------

@Composable
private fun SettingsSubPage(
    vm: MainViewModel,
    settings: AppSettings,
    page: SettingsPage,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }
    var showPicker by remember { mutableStateOf(false) }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.setBackgroundFromUri(uri)
    }
    if (showPicker) {
        FolderPickerDialog(
            start = settings.rootPath?.let { File(it) }?.takeIf { it.isDirectory } ?: Permissions.storageRoot(),
            onPick = {
                showPicker = false
                vm.setRoot(it.absolutePath)
            },
            onDismiss = { showPicker = false },
        )
    }

    val pageScroll = rememberScrollState()
    TrackScroll(pageScroll)
    Column(
        Modifier.fillMaxSize().verticalScroll(pageScroll).padding(12.dp)
            .padding(bottom = LocalBottomBarInset.current),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回设置") }
            Column(Modifier.weight(1f)) {
                Text(page.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    page.mono,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        when (page) {
            SettingsPage.APPEARANCE -> AppearancePage(vm, settings, backgroundPicker)
            SettingsPage.LIBRARY -> LibraryPage(vm, settings) { showPicker = true }
            SettingsPage.SCRIPT -> ScriptPage(vm, settings)
            SettingsPage.PLAYBACK -> PlaybackPage(vm, settings)
            SettingsPage.DLSITE -> DlsitePage(vm, onLogin)
            SettingsPage.DEBUG -> DebugPage(vm)
            SettingsPage.ABOUT -> AboutPage(vm)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppearancePage(
    vm: MainViewModel,
    settings: AppSettings,
    backgroundPicker: androidx.activity.result.ActivityResultLauncher<Array<String>>,
) {
    SectionCard("深色模式") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.values().forEach { mode ->
                FilterChip(
                    selected = ThemeMode.of(settings.themeMode) == mode,
                    onClick = { vm.setThemeMode(mode.id) },
                    label = { Text(mode.label) },
                )
            }
        }
    }
    SectionCard("配色皮肤") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkinStyle.values().forEach { style ->
                FilterChip(
                    selected = SkinStyle.of(settings.skinStyle) == style,
                    onClick = { vm.setSkinStyle(style.id) },
                    label = { Text(style.label) },
                )
            }
        }
    }
    SectionCard("作品详情页") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("new" to "新版（头图 + 简介 + 选集二级页）", "old" to "旧版（改动前的布局）").forEach { (id, label) ->
                FilterChip(
                    selected = settings.detailLayout == id,
                    onClick = { vm.setDetailLayout(id) },
                    label = { Text(label) },
                )
            }
        }
        Text(
            "新版把 DLsite 作品名与简介放在封面对面的位置，选集单独进二级页面；旧版就是这次改动之前的布局。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard("主题色（强调色）") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentColor.values().forEach { accent ->
                FilterChip(
                    selected = AccentColor.of(settings.accentColor) == accent,
                    onClick = { vm.setAccentColor(accent.id) },
                    label = { Text(accent.label) },
                )
            }
        }
    }
    SectionCard("背景") {
        Text("背景预设", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundPreset.values().forEach { preset ->
                FilterChip(
                    selected = BackgroundPreset.of(settings.backgroundPreset) == preset,
                    onClick = { vm.setBackgroundPreset(preset.id) },
                    label = { Text(preset.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("自定义背景图（优先于预设）", style = MaterialTheme.typography.labelMedium)
        Text(
            settings.backgroundImage ?: "未设置",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row {
            TextButton(onClick = { backgroundPicker.launch(arrayOf("image/*", "application/octet-stream")) }) {
                Text("选择背景图")
            }
            if (settings.backgroundImage != null) {
                TextButton(onClick = { vm.setBackgroundImage(null) }) { Text("清除") }
            }
        }
    }
    SectionCard("不透明度与模糊") {
        LabeledSlider(
            "UI 卡不透明度 " + "%.2f".format(settings.cardAlpha),
            settings.cardAlpha, 0.35f..1f,
        ) { vm.setCardAlpha(it) }
        LabeledSlider(
            "背景遮罩 " + "%.2f".format(settings.backgroundDim),
            settings.backgroundDim, 0f..0.95f,
        ) { vm.setBackgroundDim(it) }
        LabeledSlider(
            "背景模糊 " + "%.0f".format(settings.backgroundBlur) + " dp",
            settings.backgroundBlur, 0f..25f,
        ) { vm.setBackgroundBlur(it) }
        SwitchRow("实时毛玻璃（iOS 玻璃皮肤）", settings.liveBlur) { vm.setLiveBlur(it) }
        Text(
            "关闭后顶栏与悬浮底栏改为半透明纯色，滚动更流畅；背景模糊不受影响。" +
                "实时毛玻璃较耗 GPU，掉帧时可关闭。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "背景遮罩越小背景图越清楚（也越容易看不清字）；曲库列表用半透明卡片压一层，保证逐条可辨别。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryPage(vm: MainViewModel, settings: AppSettings, onPickFolder: () -> Unit) {
    SectionCard("根目录") {
        Text(
            settings.rootPath ?: "未设置",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row {
            TextButton(onClick = onPickFolder) { Text("选择目录") }
            TextButton(onClick = { vm.rescan() }) { Text("重新扫描") }
            TextButton(onClick = { vm.setRoot(null) }) { Text("清除") }
        }
        SwitchRow("启动时自动扫描", settings.scanOnStart, vm::setScanOnStart)
    }
    SectionCard("支持的台本格式") {
        Text(
            "文本：.txt / .md（可含 LRC 时间轴）\n" +
                "时间轴：.lrc / .srt / .vtt / .ass（逐句高亮同步）\n" +
                "文档：.docx / .pdf（自动抽取文字）",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    SectionCard("内容显示") {
        SwitchRow("隐藏 R18（成人向）作品", settings.hideR18, vm::setHideR18)
        Text(
            "按 DLsite 识别结果与手动分级判断；隐藏后从曲库列表过滤，主动筛选「成人」时仍会显示。未分类的作品不受影响。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard("压缩包") {
        Text(
            "扫描时会识别 zip / rar / 7z / tar / gz 等；项目页可直接解压 zip，" +
                "rar / 7z 请用其他工具解压后再扫描。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScriptPage(vm: MainViewModel, settings: AppSettings) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    SectionCard("匹配规则") {
        Text(
            "① 同名文件：01 深海.mp3 ↔ 01 深海.txt\n" +
                "② 同开头编号：01 ×××.mp3 ↔ 01 台本.txt\n" +
                "③ 父子目录：音频/ 与 台本/ 相隔一层\n" +
                "④ 同一总项目：音频/ 与 脚本/ 并列时按名字或编号对应\n" +
                "⑤ 整项目共用：项目里只有一份台本时，归给没有强匹配的音频\n" +
                "⑥ 名称相似：以上都不中时按相似度兜底（编号不同不会误配）",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    SectionCard("阅读体验") {
        SwitchRow("台本自动跟随播放", settings.autoScroll, vm::setAutoScroll)
        LabeledSlider(
            "滚动速度 " + "%.1f".format(settings.scrollSpeed) + "×",
            settings.scrollSpeed, 0.3f..3f,
        ) { vm.setScrollSpeed(it) }
        LabeledSlider(
            "台本字号 " + "%.1f".format(settings.scriptFontScale) + "×",
            settings.scriptFontScale, 0.8f..2f,
        ) { vm.setFontScale(it) }
        LabeledSlider(
            "台本行高 " + "%.2f".format(settings.scriptLineHeight) + "×",
            settings.scriptLineHeight, 0.8f..2f,
        ) { vm.setScriptLineHeight(it) }
        Text(
            "行高乘在字号上；长台本建议 1.2–1.4，密集跟读可以调回 1.0。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("台本字体", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "default" to "系统默认",
                "serif" to "衬线（宋体感）",
                "mono" to "等宽",
            ).forEach { (id, label) ->
                FilterChip(
                    selected = settings.scriptFontFamily == id,
                    onClick = { vm.setScriptFontFamily(id) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("阅读主题", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "follow" to "跟随应用",
                "paper" to "纸张",
                "night" to "夜读",
            ).forEach { (id, label) ->
                FilterChip(
                    selected = settings.scriptTheme == id,
                    onClick = { vm.setScriptTheme(id) },
                    label = { Text(label) },
                )
            }
        }
        Text(
            "「纸张」是米色底深字，「夜读」是深底浅字——关灯听的时候不刺眼；" +
                "「跟随应用」保持原来的卡片配色。只影响台本区域，不改全局主题。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard("台本悬浮窗（桌面歌词）") {
        val context = androidx.compose.ui.platform.LocalContext.current
        SwitchRow("播放时在桌面显示台本悬浮窗", settings.lyricOverlay) { on ->
            if (on && !android.provider.Settings.canDrawOverlays(context)) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + context.packageName),
                        ),
                    )
                }
                vm.say("请允许「显示在其他应用上层」，回来后重新打开开关")
            } else {
                vm.setLyricOverlay(on)
            }
        }
        Text(
            "播放带时间轴的台本（LRC / SRT 等）时，当前句子会显示在桌面悬浮窗里；" +
                "无时间轴的台本只显示曲目名。\n" +
                "· 拖右下角 ◢ 直接改大小，字号会跟着一起缩放、正文自动折行；拖正文移动位置；" +
                "轻点正文展开控制条（A− / A+ 在尺寸比例上继续微调字号、收成球、关闭）。\n" +
                "· 「缩为球」后桌面上只留一个圆点，点一下立刻展开回歌词。播放页的「桌面歌词」按钮同样可以开关。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LabeledSlider(
            "悬浮窗字号 " + "%.1f".format(settings.lyricScale) + "×",
            settings.lyricScale, 0.7f..1.8f,
        ) { vm.setLyricScale(it) }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "窗口 %.0f×%.0f dp".format(settings.lyricWidth, settings.lyricHeight) +
                    if (settings.lyricCollapsed) " · 已收成球" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                vm.setLyricSize(240f, 96f)
                vm.setLyricCollapsed(false)
                vm.say("悬浮窗已恢复默认大小并展开")
            }) { Text("恢复默认大小") }
        }
    }
    SectionCard("写入音频标签") {
        Text(
            "默认只在应用内建立对应关系，不改动原始音频。支持的写入格式：" +
                "MP3（ID3 USLT）、FLAC（LYRICS）、M4A（©lyr）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val backupCount by vm.tagBackupCount.collectAsStateWithLifecycle()
        if (backupCount > 0) {
            Text(
                "已自动备份 " + backupCount + " 个原始音频文件（首次写入前保存在应用内部）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 恢复是覆盖性操作，先确认
            var confirmRestore by remember { mutableStateOf(false) }
            TextButton(onClick = { confirmRestore = true }) { Text("恢复全部备份") }
            if (confirmRestore) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmRestore = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("恢复原始音频？") },
                    text = {
                        Text(
                            "会把 " + backupCount + " 个音频文件还原为写入台本标签之前的原始版本" +
                                "（当前写入的标签会被覆盖）。文件被移动或删除的会跳过。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.restoreTagBackups(); confirmRestore = false }) { Text("恢复") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRestore = false }) { Text("取消") }
                    },
                )
            }
        }
        // 开启「自动写入」前先确认：它会批量修改用户的原始音频文件
        var confirmEmbed by remember { mutableStateOf(false) }
        SwitchRow(
            "扫描后自动写入台本标签",
            settings.embedOnImport,
        ) {
            if (it) confirmEmbed = true else vm.setEmbedOnImport(false)
        }
        if (confirmEmbed) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmEmbed = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("开启自动写入？") },
                text = {
                    Text(
                        "开启后，每次扫描完成都会把台本直接写入原始音频文件（MP3 / FLAC / M4A）。" +
                            "首次写入前会自动把原始文件备份到应用内部，之后可随时在设置里一键恢复。确定开启吗？",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.setEmbedOnImport(true); confirmEmbed = false }) { Text("开启") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmEmbed = false }) { Text("取消") }
                },
            )
        }
        TextButton(onClick = { vm.embedAll() }) { Text("为全库写入台本标签") }
        val s = scan
        if (s != null) {
            Text(
                "已匹配 " + s.withScriptCount + " / " + s.trackCount + " 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackPage(vm: MainViewModel, settings: AppSettings) {
    val audioFx by vm.audioFx.collectAsStateWithLifecycle()
    SectionCard("界面风格") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.playerLayout == "new",
                onClick = { vm.setPlayerLayout("new") },
                label = { Text("大封面") },
            )
            FilterChip(
                selected = settings.playerLayout == "big",
                onClick = { vm.setPlayerLayout("big") },
                label = { Text("全页大图") },
            )
            FilterChip(
                selected = settings.playerLayout == "classic",
                onClick = { vm.setPlayerLayout("classic") },
                label = { Text("紧凑") },
            )
        }
        Text(
            "播放页点封面在「紧凑 → 大封面 → 全页大图」之间循环切换；全页大图可侧滑返回退回大封面。" +
                "播放倍数在播放页的「1.0×」胶囊里调。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard("播放列表") {
        Text(
            "「播放列表」列出当前总项目里的全部音频（跨章节、跨格式统合）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow("播放页默认显示播放列表", settings.showPlaylist, vm::setShowPlaylist)
        SwitchRow("播放列表自动跟随当前曲目", settings.playlistAutoScroll, vm::setPlaylistAutoScroll)
    }
    SectionCard("音效（EQ 与增强）") {
        SwitchRow("启用音效", audioFx.enabled) { on ->
            vm.applyAudioFx(audioFx.copy(enabled = on))
            vm.say(if (on) "音效已启用（只影响播放输出，不改音频文件）" else "音效已关闭，输出恢复原样")
        }
        Text(
            "效果器挂在播放输出上，关闭后与未开音效完全一致；音频文件不会被修改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (audioFx.enabled) {
            Spacer(Modifier.height(8.dp))
            Text("预设", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.kiite.player.core.AudioFx.PRESETS.forEach { (name, gains) ->
                    FilterChip(
                        selected = audioFx.gains == gains,
                        onClick = { vm.applyAudioFx(audioFx.copy(gains = gains)) },
                        label = { Text(name) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "五段均衡（" + com.kiite.player.core.AudioFx.presetNameOf(audioFx) + "）",
                style = MaterialTheme.typography.labelMedium,
            )
            audioFx.gains.forEachIndexed { band, gain ->
                val label = com.kiite.player.core.AudioFx.BAND_LABELS.getOrElse(band) { "$band" }
                Text(
                    label + " Hz  " + (if (gain > 0) "+" else "") + gain + " dB",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = gain.toFloat(),
                    onValueChange = { newGain ->
                        vm.applyAudioFx(
                            audioFx.copy(
                                gains = audioFx.gains.toMutableList().also { list -> list[band] = newGain.toInt() },
                            ),
                        )
                    },
                    valueRange = (com.kiite.player.core.AudioFx.GAIN_MIN).toFloat()
                        ..(com.kiite.player.core.AudioFx.GAIN_MAX).toFloat(),
                    steps = com.kiite.player.core.AudioFx.GAIN_MAX - com.kiite.player.core.AudioFx.GAIN_MIN - 1,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("低音增强 " + (audioFx.bassBoost * 100 / 1000) + "%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = audioFx.bassBoost.toFloat(),
                onValueChange = { vm.applyAudioFx(audioFx.copy(bassBoost = it.toInt())) },
                valueRange = 0f..1000f,
                steps = 9,
            )
            Text("环绕声 " + (audioFx.virtualizer * 100 / 1000) + "%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = audioFx.virtualizer.toFloat(),
                onValueChange = { vm.applyAudioFx(audioFx.copy(virtualizer = it.toInt())) },
                valueRange = 0f..1000f,
                steps = 9,
            )
            Text(
                "响度增益 +" + "%.1f".format(audioFx.loudnessGainMb / 100f) + " dB（录音偏小声的作品用）",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = audioFx.loudnessGainMb.toFloat(),
                onValueChange = { vm.applyAudioFx(audioFx.copy(loudnessGainMb = it.toInt())) },
                valueRange = 0f..3000f,
                steps = 29,
            )
            TextButton(onClick = {
                vm.applyAudioFx(com.kiite.player.core.AudioFxSettings(enabled = true))
                vm.say("音效已全部重置")
            }) { Text("重置为原声") }
        }
    }
}

@Composable
private fun dlsiteStateLabel(state: com.kiite.player.core.DlsiteLoginState): String = when (state) {
    com.kiite.player.core.DlsiteLoginState.LOGGED_IN -> "已登录"
    com.kiite.player.core.DlsiteLoginState.LOGGED_OUT -> "未登录"
    com.kiite.player.core.DlsiteLoginState.UNKNOWN -> "无法确认（网络不可用）"
}

@Composable
private fun DlsitePage(vm: MainViewModel, onLogin: () -> Unit) {
    val state by vm.dlsiteLoginState.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var dirDialog by remember { mutableStateOf(false) }

    SectionCard("账号 · " + dlsiteStateLabel(state)) {
        Text(
            when (state) {
                com.kiite.player.core.DlsiteLoginState.LOGGED_IN ->
                    "已登录：可读取成人向作品页、识别「已购买」，并同步已购列表。"
                com.kiite.player.core.DlsiteLoginState.LOGGED_OUT ->
                    "未登录：只能读公开页面，成人向作品需要登录。"
                com.kiite.player.core.DlsiteLoginState.UNKNOWN ->
                    "无法确认登录态：需要联网验证，请检查网络后重试。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "检测到 " + dlsiteCookieCount() + " 个 Cookie" +
                if (dlsiteCookieCount() == 0) "（没取到 Cookie，登录可能没成功）" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row {
            TextButton(onClick = onLogin) { Text("登录 DLsite") }
            TextButton(onClick = { vm.refreshDlsiteLogin() }) { Text("重新验证") }
            if (state == com.kiite.player.core.DlsiteLoginState.LOGGED_IN) {
                TextButton(onClick = { vm.dlsiteLogout() }) { Text("退出登录") }
            }
        }
    }

    SectionCard("已购作品") {
        Text(
            "登录后点「同步已购作品」，会读取你的购买记录并自动与本机曲库比对，" +
                "列出本机还没有的作品。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { vm.syncPurchases() }) { Text("同步已购作品") }
        SwitchRow("只看音声 / ASMR 作品", settings.purchaseAsmrOnly, vm::setPurchaseAsmrOnly)
        if (purchases.isNotEmpty()) {
            val missing = vm.missingPurchases()
            Text(
                "共 " + purchases.size + " 部，过滤后 " + missing.size + " 部本机还没有" +
                    if (vm.skippedNonAsmr() > 0) "（已跳过 " + vm.skippedNonAsmr() + " 部非音声作品）" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            missing.take(20).forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title ?: item.code,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { uriHandler.openUri(com.kiite.player.core.DlsiteParse.productUrl(item.code)) }) {
                        Text("打开")
                    }
                }
            }
            if (missing.size > 20) {
                Text(
                    "（只显示前 20 部）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    SectionCard("作品头图") {
        Text(
            "识别 DLsite 作品后会顺手把封面下载到本地。若有些作品显示的是 DLsite 站内那张宣传图、" +
                "或者你想换成自己的图：点下面重新抓取，或在作品详情页点头图下方的「换头图」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { vm.refreshDlsiteCovers() }) { Text("重新抓取全部头图") }
    }

    SectionCard("下载路径") {
        val customDir = settings.downloadDir?.takeIf { it.isNotBlank() }
        Text(
            "应用内下载（DLsite）落在这里。默认放在曲库根目录下的 DLsite/，" +
                "这样下完重新扫描就能直接播放；改到曲库外面也可以，但不会被自动扫描到。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text("当前下载目录", style = MaterialTheme.typography.labelMedium)
        Text(
            customDir?.let { it.trimEnd('/') + "/DLsite" } ?: ((settings.rootPath ?: "未设置") + "/DLsite"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (customDir == null) "默认（随曲库根目录）" else "自定义",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = { dirDialog = true }) { Text("更改目录") }
            if (customDir != null) {
                TextButton(onClick = {
                    vm.setDownloadDir(null)
                    vm.say("下载目录已恢复默认")
                }) { Text("恢复默认") }
            }
        }
        if (customDir != null && !isUnder(customDir, settings.rootPath)) {
            Text(
                "注意：这个目录不在曲库根目录下，下载完的作品需要手动移动或把曲库指到那里才会出现在曲库。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "浏览器里下载的作品不受这里影响，仍由浏览器的下载目录决定。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (dirDialog) {
        var text by remember { mutableStateOf(settings.downloadDir ?: "") }
        val valid = text.trim().let { it.isEmpty() || it.startsWith("/") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dirDialog = false },
            title = { Text("下载目录") },
            text = {
                Column {
                    Text(
                        "填一个绝对路径。目录不存在会自动创建；留空表示恢复默认（曲库根目录下的 DLsite/）。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text("绝对路径") },
                        isError = !valid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!valid) {
                        Text(
                            "要以 / 开头，例如 /storage/emulated/0/Music/DLsite",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        TextButton(onClick = {
                            text = android.os.Environment
                                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                                .absolutePath + "/DLsite"
                        }) { Text("音乐目录") }
                        TextButton(onClick = {
                            text = android.os.Environment
                                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                .absolutePath + "/DLsite"
                        }) { Text("下载目录") }
                        TextButton(onClick = {
                            settings.rootPath?.let { text = it.trimEnd('/') + "/DLsite" }
                        }) { Text("曲库内") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        val value = text.trim()
                        vm.setDownloadDir(if (value.isEmpty()) null else value)
                        dirDialog = false
                        vm.say(if (value.isEmpty()) "下载目录已恢复默认" else "下载目录已更新")
                    },
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { dirDialog = false }) { Text("取消") } },
        )
    }
}

/** `path` 是否在 `root` 之内（用于提示「下载完不会被扫进曲库」）。 */
private fun isUnder(path: String, root: String?): Boolean {
    val p = path.trimEnd('/')
    val r = root?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return false
    return p == r || p.startsWith(r + "/")
}

private fun copyText(context: android.content.Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }
}

private fun copyEmail(context: android.content.Context) {
    runCatching {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("email", com.kiite.player.AppInfo.EMAIL))
    }
}

/** 调试日志：不用连电脑抓 logcat，直接把现场复制给作者。 */
@Composable
private fun DebugPage(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lines by com.kiite.player.data.AppLog.lines.collectAsStateWithLifecycle()
    SectionCard("调试日志（" + lines.size + " 行）") {
        Text(
            "扫描、DLsite 识别、手动分级/标签等操作都会记到这里，重启后仍保留。出问题时点「复制全部」发给作者即可定位。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row {
            TextButton(onClick = {
                copyText(context, "kiite-log", com.kiite.player.data.AppLog.snapshot())
                vm.say("日志已复制到剪贴板")
            }) { Text("复制全部") }
            TextButton(onClick = {
                com.kiite.player.data.AppLog.clear()
                vm.say("日志已清空")
            }) { Text("清空") }
        }
    }
    SectionCard("最近记录（内存里最多 500 行）") {
        if (lines.isEmpty()) {
            Text("暂无记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                lines.takeLast(200).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AboutPage(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = LocalUriHandler.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showChangelog by remember { mutableStateOf(false) }
    if (showChangelog) ChangelogDialog { showChangelog = false }
    SectionCard("kiite player") {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(104.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.kiite.player.R.drawable.kiite_mascot,
                ),
                contentDescription = "看板娘",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text("作者：" + AppInfo.AUTHOR, style = MaterialTheme.typography.bodyMedium)
        Text(
            "联系作者：" + AppInfo.EMAIL,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "版本：v" + AppInfo.VERSION_NAME + "（" + AppInfo.BUILD_TYPE + "）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { showChangelog = true }) { Text("更新日志") }
        TextButton(onClick = { vm.checkUpdate() }) { Text("检查更新") }
        SwitchRow("启动时不提示更新（仍可手动检查）", settings.noUpdatePrompt, vm::setNoUpdatePrompt)
        TextButton(onClick = { vm.say("邮件地址已复制") ; copyEmail(context) }) { Text("复制联系邮箱") }

        TextButton(onClick = { uriHandler.openUri(AppInfo.REPO_URL) }) { Text("开源页（GitHub）") }
        TextButton(onClick = { uriHandler.openUri(AppInfo.BILIBILI_URL) }) {
            Text("B 站主页（UID " + AppInfo.BILIBILI_UID + "）")
        }
        TextButton(onClick = { vm.say("已写入的标签可用任意支持歌词的播放器读取") }) {
            Text("关于标签兼容性")
        }
    }
}

// ---------------- 通用组件 ----------------

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    AsmrCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider()
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}
