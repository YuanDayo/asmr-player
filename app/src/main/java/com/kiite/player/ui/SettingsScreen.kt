@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiite.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiite.player.AppInfo
import com.kiite.player.data.dlsiteCookieCount
import com.kiite.player.data.AppSettings
import com.kiite.player.ui.components.AsmrSlider
import com.kiite.player.ui.components.AudioFxControls
import com.kiite.player.ui.components.AsmrSwitch
import com.kiite.player.ui.components.ThemeGlyph
import com.kiite.player.ui.theme.AccentColor
import com.kiite.player.ui.theme.BackgroundPreset
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.LocalBottomBarInset
import com.kiite.player.ui.theme.LocalTopBarMetrics
import com.kiite.player.ui.theme.LocalTabReselect
import com.kiite.player.ui.theme.Motion
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.SkinStyle
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.ThemeMode
import com.kiite.player.ui.theme.TouchTarget
import com.kiite.player.util.Permissions
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixDivider
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColors
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColors
import java.io.File

/**
 * SettingsScreen 独有的控件尺寸。
 *
 * 这些不进全局令牌：它们是本页特有的控件大小（图标按钮、进度条、封面档位），
 * 同值不同义，硬塞进 Spacing / IconSize 会让令牌表变成一张数字表。
 * 集中在这里的目的是可搜、可一处改；真正的归一化（图标按钮统一到 48dp
 * 触控目标）留给单独一批，因为会改变布局。
 */
private object SettingsDim {
    val box42 = 42.dp
    val height104 = 104.dp

    /** Miuix 卡片默认内边距要清零，行才能通铺到卡片边缘。 */
    val none = 0.dp

    /** 首页图标胶囊的着色浓度。 */
    const val iconTileAlpha = 0.12f

    /** 下载任务那根细进度条的高度。 */
    val thinBar = 4.dp
}


/** 设置里的子页面。 */
private enum class SettingsPage(val label: String, val mono: String, val icon: ImageVector) {
    APPEARANCE("外观与皮肤", "APPEARANCE", Icons.Default.Palette),
    LIBRARY("曲库与扫描", "LIBRARY", Icons.Default.Folder),
    SCRIPT("台本与音频", "SCRIPT", Icons.Default.Description),
    PLAYBACK("播放与列表", "PLAYBACK", Icons.Default.PlayCircle),
    DOWNLOADS("下载管理", "DOWNLOADS", Icons.Default.Download),
    DLSITE("DLsite", "DL SITE", Icons.Default.Language),
    DEBUG("调试日志", "DEBUG LOG", Icons.Default.BugReport),
    ABOUT("关于", "ABOUT", Icons.Default.Info),
}

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    /**
     * 上报「这一页现在盖着整屏内容」（DLsite 登录 WebView）。
     *
     * 没有它，登录页就是 tab 里的普通内容：悬浮磨砂顶栏照旧画在它上面，
     * 「完成」正好落在那条栏 + 状态栏的位置 —— 看得见字、点不到按钮。
     * 由 AppRoot 收进 libraryOverlay 一起管顶栏/底栏/切页手势。
     */
    onOverlayChange: (Boolean) -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    // 登录页开着时把整层 chrome 交出去（顶栏、导航胶囊、左右切页都让位），
    // 否则「完成」被顶栏压住点不到。离场（含整页被销毁）时一定复位。
    LaunchedEffect(showLogin) { onOverlayChange(showLogin) }
    DisposableEffect(Unit) { onDispose { onOverlayChange(false) } }
    // 重复点「设置」标签：返回设置主页（已在主页时由 SettingsHome 自己滚回顶部）
    val tabReselect = LocalTabReselect.current
    LaunchedEffect(tabReselect) {
        if (tabReselect == 0) return@LaunchedEffect
        showLogin = false
        page = null
    }
    val open = page
    // 设置页整页跑在 Miuix 主题里（HyperOS 设计语言）；深浅跟随应用自身的背景亮度，
    // 因为应用的三态主题不走系统夜间模式的 LocalConfiguration。强调色跟随当前皮肤。
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = MaterialTheme.colorScheme.primary
    val accentOn = MaterialTheme.colorScheme.onPrimary
    val miuixColors = if (dark) {
        miuixDarkColors(primary = accent, onPrimary = accentOn)
    } else {
        miuixLightColors(primary = accent, onPrimary = accentOn)
    }
    MiuixTheme(colors = miuixColors) {
        if (showLogin) {
            DlsiteLoginScreen {
                showLogin = false
                vm.refreshDlsiteLogin()
            }
        } else {
            // 主页 ↔ 子页面之间淡切，去掉原来 `if/else` 的硬切；
            // 设置页是整页滚动内容，用 150ms 淡变足够顺，也不像整屏平移那样每帧重绘。
            Crossfade(
                targetState = open,
                animationSpec = Motion.standard(Motion.SHORT),
                label = "settingsPage",
            ) { target ->
                if (target == null) {
                    SettingsHome(vm, settings) { page = it }
                } else {
                    SettingsSubPage(
                        vm,
                        settings,
                        target,
                        onLogin = { showLogin = true },
                    ) { page = null }
                }
            }
        }
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
    // 下载管理那一行的摘要：有任务在跑就显示进度，否则显示落盘目录
    val dlsiteDl by vm.dlsiteDownload.collectAsStateWithLifecycle()
    val asmrDl by vm.asmrDownload.collectAsStateWithLifecycle()
    var showBookmarks by remember { mutableStateOf(false) }
    // 台本匹配报告：与曲库页共用一个弹窗，缺台本的逐首直达手动指定
    val manualLinks by vm.manualLinks.collectAsStateWithLifecycle()
    var showScriptReport by remember { mutableStateOf(false) }
    var scriptPickerTrack by remember { mutableStateOf<com.kiite.player.core.TrackEntry?>(null) }
    // 曲库体检：空作品 / 可疑小文件 / 跨作品重复
    var showAudit by remember { mutableStateOf(false) }

    val settingsScroll = rememberScrollState()
    TrackScroll(settingsScroll)
    // 顶栏不再是固定条：往上滑时它才淡入成磨砂条，这里把滚动进度报上去
    com.kiite.player.ui.theme.TrackTopBar(settingsScroll)
    // 在设置主页时重复点「设置」标签 → 滚回顶部
    val tabReselect = LocalTabReselect.current
    LaunchedEffect(tabReselect) {
        if (tabReselect > 0) settingsScroll.animateScrollTo(0)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(settingsScroll)
            // 设置页没有固定的头部，整页就是滚动内容。和曲库一样只让出状态栏：
            // 大标题落在「顶栏那一条」里（顶栏停在顶部时是透明的），往下滚内容再穿过磨砂条。
            // 让出整条栏高会在大标题上方多出一条空带，看着像漏了一格。
            .padding(
                top = LocalTopBarMetrics.current.safeTop,
                bottom = LocalBottomBarInset.current,
            ),
    ) {
        // 大标题：跟着内容一起滚走，替代以前那条固定的「设置」（参考 HyperOS 的大标题）
        androidx.compose.material3.Text(
            "设置",
            modifier = Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.xs),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.xs))

        MiuixSmallTitle("通用")
        MiuixSettingsGroup {
            MiuixSettingsRow(
                icon = Icons.Default.Palette,
                title = SettingsPage.APPEARANCE.label,
                summary = SkinStyle.of(settings.skinStyle).label + " · " +
                    ThemeMode.of(settings.themeMode).label + " · 遮罩 " +
                    "%.2f".format(settings.backgroundDim),
            ) { onOpen(SettingsPage.APPEARANCE) }
            MiuixDivider()
            MiuixSettingsRow(
                icon = Icons.Default.Folder,
                title = SettingsPage.LIBRARY.label,
                summary = settings.rootPath ?: "未设置根目录",
            ) { onOpen(SettingsPage.LIBRARY) }
            MiuixDivider()
            MiuixSettingsRow(
                icon = Icons.Default.Description,
                title = SettingsPage.SCRIPT.label,
                summary = "已匹配 " + (scan?.withScriptCount ?: 0) + " / " + (scan?.trackCount ?: 0) + " 首",
            ) { onOpen(SettingsPage.SCRIPT) }
        }

        // 台本匹配报告 / 作品体检：不占设置子页，点开直接看面板
        if (scan != null) {
            val unmatchedProjects = scan?.projects?.count { p ->
                vm.tracksOfProject(p.path).any { !it.hasScript }
            } ?: 0
            MiuixSmallTitle("工具")
            MiuixSettingsGroup {
                MiuixSettingsRow(
                    icon = Icons.Default.Description,
                    title = "台本匹配报告",
                    summary = if (unmatchedProjects > 0) {
                        "有 " + unmatchedProjects + " 部作品缺台本，点开逐首指定"
                    } else {
                        "全部音频都匹配到了台本"
                    },
                ) { showScriptReport = true }
                MiuixDivider()
                MiuixSettingsRow(
                    icon = Icons.Default.CheckCircle,
                    title = "作品体检",
                    summary = "空作品 · 可疑小文件 · 跨作品重复检测",
                ) { showAudit = true }
            }
        }

        // 书签与历史不是「设置项」，是用户数据，所以直接在这里开面板
        MiuixSmallTitle("数据")
        MiuixSettingsGroup {
            MiuixSettingsRow(
                icon = Icons.Default.Download,
                title = SettingsPage.DOWNLOADS.label,
                summary = downloadsSummary(dlsiteDl, asmrDl, settings),
            ) { onOpen(SettingsPage.DOWNLOADS) }
            MiuixDivider()
            MiuixSettingsRow(
                icon = Icons.Default.Bookmark,
                title = "书签与历史",
                summary = "书签 " + bookmarks.size + " 条 · 历史 " + history.records.size +
                    " 条 · 今日 " + com.kiite.player.core.HistoryMath.formatDuration(
                    com.kiite.player.core.HistoryMath.todayMs(history.dailyListenedMs, System.currentTimeMillis()),
                ),
            ) { showBookmarks = true }
        }

        MiuixSmallTitle("播放")
        MiuixSettingsGroup {
            MiuixSettingsRow(
                icon = Icons.Default.PlayCircle,
                title = SettingsPage.PLAYBACK.label,
                summary = when (settings.playerLayout) {
                    "classic" -> "紧凑布局"
                    "big" -> "全页大图"
                    else -> "大封面布局"
                },
            ) { onOpen(SettingsPage.PLAYBACK) }
        }

        MiuixSmallTitle("账号")
        MiuixSettingsGroup {
            MiuixSettingsRow(
                icon = Icons.Default.Language,
                title = SettingsPage.DLSITE.label,
                summary = dlsiteStateLabel(loginState) + " · 已识别 " + dlsiteCount + " 部",
            ) { onOpen(SettingsPage.DLSITE) }
        }

        MiuixSmallTitle("其他")
        MiuixSettingsGroup {
            MiuixSettingsRow(
                icon = Icons.Default.BugReport,
                title = SettingsPage.DEBUG.label,
                summary = logLines.size.toString() + " 行记录",
            ) { onOpen(SettingsPage.DEBUG) }
            MiuixDivider()
            MiuixSettingsRow(
                icon = Icons.Default.Info,
                title = SettingsPage.ABOUT.label,
                summary = "v" + AppInfo.VERSION_NAME,
            ) { onOpen(SettingsPage.ABOUT) }
        }

        Spacer(Modifier.height(Spacing.xl))
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

/** 一组设置项 = 一张 Miuix 卡片，行与行之间用 Miuix 分隔线。 */
@Composable
private fun MiuixSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        // 大卡片用 Radius.xl（20dp），与其它页的大卡片一致
        cornerRadius = Radius.xl,
        insideMargin = PaddingValues(SettingsDim.none),
        onClick = null,
    ) {
        content()
    }
}

/**
 * 单行设置项：左侧图标胶囊 + 标题/副标题 + 右侧 chevron，整行可点。
 *
 * 不用 Miuix 的 BasicComponent —— 它内部按 Miuix 自己的字阶排版，
 * 和子页里 MaterialTheme 的 bodyMedium 摆一起会一大一小。这里显式用项目字阶。
 */
@Composable
private fun MiuixSettingsRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    // 深色模式下外层分组已经是深色大卡片，行再贴一张「亮一档的小卡」就成了
    // 大卡包小卡（§7 之后行底被抬亮，观感更明显）。所以深色下一律走「透明行 + 分隔线」，
    // 浅色仍保留原来的行卡（浅色下那层按钮感的底是必要的）。
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(SettingsDim.box42)
                    .clip(asmrShape(Radius.sm))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = SettingsDim.iconTileAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    Modifier.size(IconSize.md),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.gap14))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (dark) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) { content() }
    } else {
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            color = asmrRowColor(),
            insideMargin = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            onClick = onClick,
        ) { content() }
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
        Modifier.fillMaxSize().verticalScroll(pageScroll).padding(Spacing.md)
            .padding(top = LocalTopBarMetrics.current.total, bottom = LocalBottomBarInset.current),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回设置") }
            Column(Modifier.weight(1f)) {
                MiuixText(page.label, style = MiuixTheme.textStyles.title2)
                MiuixText(
                    page.mono,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.itemGap))

        when (page) {
            SettingsPage.APPEARANCE -> AppearancePage(vm, settings, backgroundPicker)
            SettingsPage.LIBRARY -> LibraryPage(vm, settings) { showPicker = true }
            SettingsPage.SCRIPT -> ScriptPage(vm, settings)
            SettingsPage.PLAYBACK -> PlaybackPage(vm, settings)
            SettingsPage.DOWNLOADS -> DownloadsPage(vm, settings)
            SettingsPage.DLSITE -> DlsitePage(vm, onLogin)
            SettingsPage.DEBUG -> DebugPage(vm)
            SettingsPage.ABOUT -> AboutPage(vm)
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

// ---------------- 下载管理 --------------

/** DLsite 的实际落盘目录：与 DlsiteController.downloadBase() 同一套规则（自选目录优先，其次曲库根）。 */
private fun downloadsTarget(settings: AppSettings): String? {
    val base = (settings.downloadDir?.takeIf { it.isNotBlank() } ?: settings.rootPath)
        ?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val clean = base.trimEnd('/')
    return if (clean.endsWith("/DLsite")) clean else clean + "/DLsite"
}

/** 设置首页那一行的小字：有任务就先说任务，空闲就说落盘目录。 */
private fun downloadsSummary(
    dl: com.kiite.player.domain.DlsiteDownloader.State?,
    am: com.kiite.player.domain.AsmrOneController.DownloadState?,
    settings: AppSettings,
): String {
    if (dl?.running == true) return "正在下载 " + (dl.code ?: "DLsite 作品") + " · " + dl.percent + "%"
    if (am?.running == true) return "正在下载 ASMR One 作品 · " + am.percent + "%"
    val target = downloadsTarget(settings)
    return if (target != null) "空闲 · 存到 " + target else "空闲 · 还没设下载目录"
}

/**
 * 下载管理：两个下载器（DLsite 应用内下载、asmr.one 作品）共用一条队列，
 * 同一时刻只跑一个任务，所以这一页就两件事 —— 现在在跑什么、下一份文件落在哪。
 *
 * 台账（已下载过哪些编号）不在这里列：DlsiteController 只把「已下载」当内部状态用，
 * 编号全集不对外暴露，这里不去猜它。
 */
@Composable
private fun DownloadsPage(vm: MainViewModel, settings: AppSettings) {
    val dl by vm.dlsiteDownload.collectAsStateWithLifecycle()
    val am by vm.asmrDownload.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val target = downloadsTarget(settings)
    val running = dl?.running == true || am?.running == true

    SectionCard("进行中的下载") {
        val d = dl
        val a = am
        if (d == null && a == null) {
            Text(
                "现在没有下载任务",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "DLsite 作品页点「下载」、ASMR One 选好作品点下载，任务都会出现在这里；切页、退到后台都不中断。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (d != null) {
            DownloadTaskBlock(
                title = "DLsite · " + (d.code ?: "作品"),
                subtitle = d.fileName.ifBlank { "等待响应" },
                progressText = d.progressText,
                status = d.status,
                progress = d.progress,
                running = d.running,
                failed = d.failed,
                canExtract = d.completed && d.savedPaths.isNotEmpty(),
                extractHint = "已存 " + d.savedPaths.size + " 个文件",
                onExtract = { vm.extractDownloaded() },
                onCancel = { vm.cancelDlsiteDownload() },
                onClear = { vm.clearDownloadStatus() },
            )
        }
        if (a != null) {
            DownloadTaskBlock(
                title = "ASMR One · " + a.title,
                subtitle = a.fileName.ifBlank { "等待响应" },
                progressText = a.countText + " · " + a.percent + "%" +
                    (a.speedText.takeIf { it.isNotBlank() }?.let { " · " + it } ?: "") +
                    (a.etaText.takeIf { it.isNotBlank() }?.let { " · " + it } ?: ""),
                status = a.status,
                progress = a.progress,
                running = a.running,
                failed = a.failed,
                canExtract = false,
                extractHint = "",
                onExtract = {},
                onCancel = { vm.cancelAsmrDownload() },
                onClear = { vm.clearAsmrDownload() },
            )
        }
    }

    SectionCard("保存位置") {
        if (target != null) {
            Text(target, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (settings.downloadDir.isNullOrBlank()) "跟随曲库根目录" else "设置里指定的下载目录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                copyText(context, "下载目录", target)
                vm.say("下载目录已复制")
            }) { Text("复制路径") }
        } else {
            Text(
                "还没设下载目录：先设曲库根目录（或单独指定下载目录），下载才有地方落。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "下载完的压缩包可以在这里直接解压成曲库里的独立作品文件夹；rar/7z 需要外部解压工具。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { vm.extractDownloaded() }) { Text("解压并入库") }
    }

    if (running) {
        Text(
            "同一时刻只跑一个任务：现在取消，下一个排队的任务会接着开始。",
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 一条任务：标题 + 文件 + 进度条 + 一行数字 + 按钮。 */
@Composable
private fun DownloadTaskBlock(
    title: String,
    subtitle: String,
    progressText: String,
    status: String,
    progress: Float,
    running: Boolean,
    failed: Boolean,
    canExtract: Boolean,
    extractHint: String,
    onExtract: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xs))
        // 自绘细进度条：不吃 Material3 版本差异（LinearProgressIndicator 的 progress 参数在各版本里签名不同）
        Box(
            Modifier
                .fillMaxWidth()
                .height(SettingsDim.thinBar)
                .clip(RoundedCornerShape(Radius.xs))
                .background(scheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(SettingsDim.thinBar)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(if (failed) scheme.error else scheme.primary),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            progressText,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        if (status.isNotBlank() && status != progressText) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) scheme.error else scheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                TextButton(onClick = onCancel) { Text("取消") }
            } else {
                if (canExtract) {
                    TextButton(onClick = onExtract) { Text("解压入库") }
                }
                TextButton(onClick = onClear) { Text("清除") }
            }
            if (extractHint.isNotBlank() && canExtract) {
                Text(
                    extractHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppearancePage(
    vm: MainViewModel,
    settings: AppSettings,
    backgroundPicker: androidx.activity.result.ActivityResultLauncher<Array<String>>,
) {
    SectionCard("深色模式") {
        // 参考站点的做法：把「亮/暗」做成一颗会转身的图标，而不是一组纯文字选项。
        // 三态（跟随系统）仍然只能在这里选 —— 图标按钮只负责最常用的那一步：换到另一边。
        val mode = ThemeMode.of(settings.themeMode)
        val darkNow = when (mode) {
            ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { vm.setThemeMode(if (darkNow) "light" else "dark") },
                modifier = Modifier.size(TouchTarget.min),
            ) {
                ThemeGlyph(
                    dark = darkNow,
                    modifier = Modifier.size(Spacing.xxl),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "当前：" + mode.label + "　点这里换到" + if (darkNow) "浅色" else "深色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        Spacer(Modifier.height(Spacing.itemGap))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BackgroundPreset.values().forEach { preset ->
                FilterChip(
                    selected = BackgroundPreset.of(settings.backgroundPreset) == preset,
                    onClick = { vm.setBackgroundPreset(preset.id) },
                    label = { Text(preset.label) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
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
        SwitchRow("简化动效", settings.simplifiedMotion) { vm.setSimplifiedMotion(it) }
        Text(
            "打开后切页、展开、共享元素等动画时长减半。" +
                "如果在系统设置里把「动画时长缩放」关掉，动画本来就是直接切换。",
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
        Spacer(Modifier.height(Spacing.sm))
        Text("台本字体", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        Spacer(Modifier.height(Spacing.sm))
        Text("阅读主题", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        Spacer(Modifier.height(Spacing.sm))
        LabeledSlider(
            "悬浮窗字号 " + "%.1f".format(settings.lyricScale) + "×",
            settings.lyricScale, 0.7f..1.8f,
        ) { vm.setLyricScale(it) }
        Spacer(Modifier.height(Spacing.xs))
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        AudioFxControls(
            fx = audioFx,
            onApply = vm::applyAudioFx,
            say = vm::say,
        )
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
    val dl by vm.dlsiteDownload.collectAsStateWithLifecycle()
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
        Spacer(Modifier.height(Spacing.itemGap))
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
                "列出本机还没有的作品。点「下载」直接存到下载目录（默认 曲库根目录/DLsite），" +
                "不用在网页里操作。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.itemGap))
        TextButton(onClick = { vm.syncPurchases() }) { Text("同步已购作品") }
        SwitchRow("只看音声 / ASMR 作品", settings.purchaseAsmrOnly, vm::setPurchaseAsmrOnly)
        // 下载进度就放在这一页：用户在这里点下载，不需要跳到曲库去确认
        dl?.takeIf { it.running }?.let { s ->
            Spacer(Modifier.height(Spacing.itemGap))
            DownloadProgressBar(
                progress = s.progress,
                modifier = Modifier.fillMaxWidth(),
                height = com.kiite.player.ui.theme.Stroke.thin,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.progressText.ifBlank { s.status },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = { vm.cancelDlsiteDownload() }) {
                    Text("取消", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        dl?.takeIf { !it.running && it.failed }?.let { s ->
            Text(
                s.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (purchases.isNotEmpty()) {
            val missing = vm.missingPurchases()
            Text(
                "共 " + purchases.size + " 部，过滤后 " + missing.size + " 部本机还没有" +
                    if (vm.skippedNonAsmr() > 0) "（已跳过 " + vm.skippedNonAsmr() + " 部非音声作品）" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Spacing.xs))
            missing.take(20).forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
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
                    TextButton(enabled = dl?.running != true, onClick = { vm.downloadDlsiteOwned(item.code) }) {
                        Text("下载")
                    }
                    TextButton(onClick = { uriHandler.openUri(com.kiite.player.core.DlsiteParse.productUrl(item.code)) }) {
                        Text("网页")
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
        Spacer(Modifier.height(Spacing.itemGap))
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
        Spacer(Modifier.height(Spacing.itemGap))
        Text("当前下载目录", style = MaterialTheme.typography.labelMedium)
        Text(
            // 真正落盘时会在 base 下再建 DLsite/，但用户已经选到 DLsite 这一层时不再套——
            // 显示跟着同一套规则走，否则会出现 .../DLsite/DLsite
            com.kiite.player.domain.DlsiteDownloader.targetDir(
                customDir ?: (settings.rootPath ?: "未设置"),
            ).path,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Spacing.xxs))
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
        Spacer(Modifier.height(Spacing.itemGap))
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
                    Spacer(Modifier.height(Spacing.sm))
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
                    Spacer(Modifier.height(Spacing.itemGap))
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
        Spacer(Modifier.height(Spacing.itemGap))
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
            Modifier.fillMaxWidth().height(SettingsDim.height104),
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
    MiuixCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        cornerRadius = Radius.lg,
        insideMargin = PaddingValues(Spacing.lg),
    ) {
        MiuixText(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.sm))
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // 整行可点 + 至少 48dp 高：开关本身只有 32dp，手指按不准
            .heightIn(min = TouchTarget.min)
            .clickable { onChange(!checked) }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        AsmrSwitch(checked = checked, onCheckedChange = onChange)
    }
    MiuixDivider()
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(Spacing.xs))
    AsmrSlider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        modifier = Modifier.fillMaxWidth(),
    )
}
