package com.asmrplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrplayer.AppInfo
import com.asmrplayer.ui.theme.BackgroundPreset
import com.asmrplayer.ui.theme.SkinStyle
import com.asmrplayer.ui.theme.ThemeMode
import com.asmrplayer.util.Permissions
import java.io.File

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
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

        SectionCard("外观与皮肤") {
            Text("深色模式", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
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
            Spacer(Modifier.height(10.dp))
            Text("配色皮肤", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
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
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(10.dp))
            Text("自定义背景图（优先于预设）", style = MaterialTheme.typography.labelMedium)
            Text(
                settings.backgroundImage ?: "未设置",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = { backgroundPicker.launch(arrayOf("image/*", "application/octet-stream")) }) {
                    Text("选择背景图")
                }
                if (settings.backgroundImage != null) {
                    TextButton(onClick = { vm.setBackgroundImage(null) }) { Text("清除背景图") }
                }
            }
            Spacer(Modifier.height(10.dp))
            LabeledSlider(
                "UI 卡不透明度 " + "%.2f".format(settings.cardAlpha),
                settings.cardAlpha, 0.35f..1f,
            ) { vm.setCardAlpha(it) }
            LabeledSlider(
                "背景模糊 " + "%.0f".format(settings.backgroundBlur) + " dp",
                settings.backgroundBlur, 0f..25f,
            ) { vm.setBackgroundBlur(it) }
            Text(
                "背景模糊在 Android 12 及以上生效；调低卡片不透明度可以让背景更多透出来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("曲库") {
            Text("根目录", style = MaterialTheme.typography.labelMedium)
            Text(
                settings.rootPath ?: "未设置",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = { showPicker = true }) { Text("选择目录") }
                TextButton(onClick = { vm.rescan() }) { Text("重新扫描") }
                TextButton(onClick = { vm.setRoot(null) }) { Text("清除") }
            }
            SwitchRow("启动时自动扫描", settings.scanOnStart, vm::setScanOnStart)
        }

        SectionCard("台本 ↔ 音频") {
            Text(
                "默认只在应用内建立对应关系，不改动原始音频。" +
                    "需要让台本跟着音频文件走时，可手动写入标签。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "支持的写入格式：MP3（ID3 USLT）、FLAC（LYRICS）、M4A（©lyr）",
                style = MaterialTheme.typography.bodySmall,
            )
            SwitchRow("扫描后自动写入台本标签", settings.embedOnImport, vm::setEmbedOnImport)
            Spacer(Modifier.height(6.dp))
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

        SectionCard("播放页与播放列表") {
            Text("界面风格", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = settings.playerLayout != "classic",
                    onClick = { vm.setPlayerLayout("new") },
                    label = { Text("大封面（新版）") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = settings.playerLayout == "classic",
                    onClick = { vm.setPlayerLayout("classic") },
                    label = { Text("紧凑（经典）") },
                )
            }
            Text(
                "播放页右下角也有切换按钮，随时可换。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "「播放列表」列出当前总项目里的全部音频（跨章节、跨格式统合）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow("播放列表自动跟随当前曲目", settings.playlistAutoScroll, vm::setPlaylistAutoScroll)
        }

        SectionCard("关于") {
            val uriHandler = LocalUriHandler.current
            Text("作者：" + AppInfo.AUTHOR, style = MaterialTheme.typography.bodyMedium)
            Text(
                "版本：v" + AppInfo.VERSION_NAME,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { uriHandler.openUri(AppInfo.REPO_URL) }) {
                Text("开源页（GitHub）")
            }
            TextButton(onClick = { uriHandler.openUri(AppInfo.BILIBILI_URL) }) {
                Text("B 站主页（UID " + AppInfo.BILIBILI_UID + "）")
            }
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

        SectionCard("支持的台本格式") {
            Text(
                "文本：.txt / .md（可含 LRC 时间轴）\n" +
                    "时间轴：.lrc / .srt / .vtt / .ass（逐句高亮同步）\n" +
                    "文档：.docx / .pdf（自动抽取文字）",
                style = MaterialTheme.typography.bodySmall,
            )
        }

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

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.say("已写入的标签可用任意支持歌词的播放器读取") }) {
            Text("关于标签兼容性")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    AsmrCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
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
