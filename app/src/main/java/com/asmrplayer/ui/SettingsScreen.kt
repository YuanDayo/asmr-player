package com.asmrplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.asmrplayer.AppInfo
import com.asmrplayer.data.AppSettings
import com.asmrplayer.ui.theme.AccentColor
import com.asmrplayer.ui.theme.BackgroundPreset
import com.asmrplayer.ui.theme.SkinStyle
import com.asmrplayer.ui.theme.ThemeMode
import com.asmrplayer.util.Permissions
import java.io.File

/** 设置里的子页面。 */
private enum class SettingsPage(val label: String, val mono: String, val icon: ImageVector) {
    APPEARANCE("外观与皮肤", "APPEARANCE", Icons.Default.Palette),
    LIBRARY("曲库与扫描", "LIBRARY", Icons.Default.Folder),
    SCRIPT("台本与音频", "SCRIPT", Icons.Default.Description),
    PLAYBACK("播放与列表", "PLAYBACK", Icons.Default.PlayCircle),
    DLSITE("DLsite", "DL SITE", Icons.Default.Language),
    ABOUT("关于", "ABOUT", Icons.Default.Info),
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    val open = page
    if (open == null) {
        SettingsHome(vm, settings) { page = it }
    } else {
        SettingsSubPage(vm, settings, open) { page = null }
    }
}

// ---------------- 主页：分组入口 ----------------

@Composable
private fun SettingsHome(vm: MainViewModel, settings: AppSettings, onOpen: (SettingsPage) -> Unit) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val dlsiteLoggedIn by vm.dlsiteLoggedIn.collectAsStateWithLifecycle()
    val dlsiteCount by remember(vm) { mutableStateOf(vm.dlsiteCount) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
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
        SettingsEntry(
            SettingsPage.PLAYBACK,
            if (settings.playerLayout == "classic") "紧凑布局" else "大封面布局",
        ) { onOpen(it) }
        SettingsEntry(
            SettingsPage.DLSITE,
            (if (dlsiteLoggedIn) "已登录" else "未登录") + " · 已识别 " + dlsiteCount + " 部",
        ) { onOpen(it) }
        SettingsEntry(SettingsPage.ABOUT, "v" + AppInfo.VERSION_NAME) { onOpen(it) }
        Spacer(Modifier.height(24.dp))
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
                    .clip(RoundedCornerShape(14.dp))
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
    onBack: () -> Unit,
) {
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
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
            SettingsPage.DLSITE -> DlsitePage(vm)
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
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            ThemeMode.values().forEach { mode ->
                FilterChip(
                    selected = ThemeMode.of(settings.themeMode) == mode,
                    onClick = { vm.setThemeMode(mode.id) },
                    label = { Text(mode.label) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
    SectionCard("配色皮肤") {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            SkinStyle.values().forEach { style ->
                FilterChip(
                    selected = SkinStyle.of(settings.skinStyle) == style,
                    onClick = { vm.setSkinStyle(style.id) },
                    label = { Text(style.label) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
    SectionCard("主题色（强调色）") {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            AccentColor.values().forEach { accent ->
                FilterChip(
                    selected = AccentColor.of(settings.accentColor) == accent,
                    onClick = { vm.setAccentColor(accent.id) },
                    label = { Text(accent.label) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
    SectionCard("背景") {
        Text("背景预设", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            BackgroundPreset.values().forEach { preset ->
                FilterChip(
                    selected = BackgroundPreset.of(settings.backgroundPreset) == preset,
                    onClick = { vm.setBackgroundPreset(preset.id) },
                    label = { Text(preset.label) },
                )
                Spacer(Modifier.width(8.dp))
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
    }
    SectionCard("写入音频标签") {
        Text(
            "默认只在应用内建立对应关系，不改动原始音频。支持的写入格式：" +
                "MP3（ID3 USLT）、FLAC（LYRICS）、M4A（©lyr）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow("扫描后自动写入台本标签", settings.embedOnImport, vm::setEmbedOnImport)
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
    SectionCard("界面风格") {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = settings.playerLayout != "classic",
                onClick = { vm.setPlayerLayout("new") },
                label = { Text("大封面") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = settings.playerLayout == "classic",
                onClick = { vm.setPlayerLayout("classic") },
                label = { Text("紧凑") },
            )
        }
        Text(
            "播放页右上角也有切换按钮，随时可换。播放倍数在播放页的「1.0×」胶囊里调。",
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
}

@Composable
private fun DlsitePage(vm: MainViewModel) {
    val loggedIn by vm.dlsiteLoggedIn.collectAsStateWithLifecycle()
    var showLogin by remember { mutableStateOf(false) }
    if (showLogin) {
        DlsiteLoginDialog {
            showLogin = false
            vm.refreshDlsiteLogin()
        }
    }
    SectionCard("账号") {
        Text(
            if (loggedIn) "已登录：可读取成人向作品页，并识别「已购买」状态" else "未登录：只能读取公开页面，成人向作品需要登录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = { showLogin = true }) {
                Text(if (loggedIn) "重新登录" else "登录 DLsite")
            }
            if (loggedIn) TextButton(onClick = { vm.dlsiteLogout() }) { Text("退出登录") }
        }
    }
    SectionCard("作品识别") {
        Text(
            "按项目名里的 RJ / VJ / BJ 编号抓取作品名、社团、封面与标签；" +
                "封面会自动下载到本地作为专辑封面，并可直接跳到作品详情页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { vm.fetchDlsiteForAll() }) { Text("识别全部项目") }
        Text(
            "单曲项目可在项目详情页点「DLsite 识别」。已识别过的会跳过。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutPage(vm: MainViewModel) {
    val uriHandler = LocalUriHandler.current
    var showChangelog by remember { mutableStateOf(false) }
    if (showChangelog) ChangelogDialog { showChangelog = false }
    SectionCard("关于") {
        Text("作者：" + AppInfo.AUTHOR, style = MaterialTheme.typography.bodyMedium)
        Text(
            "版本：v" + AppInfo.VERSION_NAME,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { showChangelog = true }) { Text("更新日志") }

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
