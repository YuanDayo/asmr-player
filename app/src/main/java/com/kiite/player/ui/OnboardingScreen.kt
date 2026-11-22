package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.components.IconTileSize
import com.kiite.player.ui.components.TintedIconTile
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.TouchTarget
import kotlinx.coroutines.launch

/**
 * 首启引导的控件尺寸。
 *
 * 命名常量而不是散在布局里的字面量：`scripts/check_ui_tokens.py` 拦裸 dp，
 * 而「圆点直径 8dp」这种值本来也该有个名字，否则改一处要全页搜。
 */
private val DotSize = 8.dp
private val DotActiveWidth = 20.dp

/** 引导页的文案与配图（只用现有图标，不造图）。 */
private data class OnboardingPage(val title: String, val body: String, val icon: ImageVector)

private val Pages = listOf(
    OnboardingPage(
        "欢迎使用 Kiite",
        "一个只属于你自己的 ASMR 播放器。音频放在哪、怎么分类、怎么听，全都在本机完成，不上传任何内容。",
        Icons.Default.LibraryMusic,
    ),
    OnboardingPage(
        "先选一个曲库目录",
        "在「曲库」里选一个文件夹，应用会自己认出作品、章节与台本。文件不用改名，也不用挪位置。",
        Icons.Default.FolderOpen,
    ),
    OnboardingPage(
        "在线获取：DLsite 与 asmr.one",
        "首页两个入口分别通往 DLsite（已购作品 / 在线搜索）与 asmr.one（免费资源站）。搜到作品后可以直接下载入库。",
        Icons.Default.CloudDownload,
    ),
    OnboardingPage(
        "台本、下载与解压",
        "扫描到的台本会自动匹配到对应音轨，播放时跟读高亮；压缩包下载完可以直接解压入库，不用离开应用。",
        Icons.Default.Description,
    ),
    OnboardingPage(
        "按你的习惯来",
        "主题、皮肤、字号、播放速度与均衡器都在设置里。换设备时用「备份与恢复」把数据带走。",
        Icons.Default.Tune,
    ),
)

/**
 * 首启引导的内容版本。
 *
 * 只在这个流程**结构**变了的时候 +1（加一步、删一步）。改文案、调间距不用动它 ——
 * 不然每次小改都让所有人再看一遍引导。
 *
 * - 0：5 页功能说明（beta14 及更早）
 * - 1：末尾加「使用须知 + 免责声明」，必须勾选同意才算走完
 */
const val ONBOARDING_GUIDE_VERSION = 1

/**
 * 首次启动引导：5 页功能说明 → 使用须知与免责声明（勾选同意）。
 *
 * 盖在最上层（AppRoot 的 `onboardingOpen`），所以自己负责背景与「吃掉点击」：
 * 不加那颗空 `clickable` 的话，点空白处会穿透到下面的曲库/设置上。
 *
 * 两条入口：
 * - `legalOnly = false`：全新安装（`onboardingSeen == false`），功能页 + 条款页都走；
 * - `legalOnly = true`：用户已经看过功能页、但记录的内容版本落后（`onboardingVersion`
 *   小于 [ONBOARDING_GUIDE_VERSION]），只补条款这一段 —— 不把人按回第 1 页重看 5 屏。
 *
 * 「跳过」只跳过功能页，条款仍然要读、要勾 —— 免责声明不能靠一次误触就算同意过。
 * 想重看功能页走「设置 → 关于 → 功能介绍」（`vm.replayOnboarding()`，不写盘）。
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, legalOnly: Boolean = false) {
    // 从功能页点「跳过 / 继续」都到这里；legalOnly 时一进来就是这里
    var showLegal by remember { mutableStateOf(legalOnly) }
    if (showLegal) {
        OnboardingLegalStep(
            canGoBack = !legalOnly,
            onBack = { showLegal = false },
            onAgree = onDone,
        )
    } else {
        OnboardingFeaturePages(onFinish = { showLegal = true })
    }
}

/** 功能页：横向翻页 + 圆点指示器 + 「跳过 / 下一页 / 继续」。 */
@Composable
private fun OnboardingFeaturePages(onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { Pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == Pages.lastIndex
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* 只为止住穿透，不做事 */ },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.lg),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                // 跳过 = 跳过这 5 页功能说明，接着去读条款；不是「关掉引导」
                TextButton(onClick = onFinish) { Text("跳过") }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                val page = Pages[index]
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 底块与设置分组行、空态共用同一个组件：尺寸/圆角在这里指定，
                    // 浓度统一走 AccentFill.tile（以前这里是硬写的 0.12f）
                    TintedIconTile(
                        icon = page.icon,
                        size = IconTileSize.lg,
                        iconSize = IconSize.hero,
                        corner = Radius.xxl,
                        tint = scheme.primary,
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    Text(
                        page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        page.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pages.indices.forEach { i ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = Spacing.xxs)
                            .size(width = if (active) DotActiveWidth else DotSize, height = DotSize)
                            .clip(CircleShape)
                            .background(if (active) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.3f)),
                    )
                }
            }
            Button(
                onClick = {
                    if (last) {
                        onFinish()
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(TouchTarget.row),
            ) {
                Text(if (last) "继续" else "下一页")
            }
        }
    }
}

/**
 * 条款页：使用须知 + 免责声明（正文直接复用设置里那两页，见 [LegalTextPage]）。
 *
 * 正文很长，所以整段放在可滚动区里，「同意」按钮固定在底部 —— 不能让人为了点按钮
 * 才滑到底，也不能让人在没看见正文的情况下点到按钮。按钮在勾选前是禁用的。
 */
@Composable
private fun OnboardingLegalStep(canGoBack: Boolean, onBack: () -> Unit, onAgree: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var agreed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* 只为止住穿透，不做事 */ },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.lg),
        ) {
            // 只从功能页过来时给「返回」；legalOnly 没有上一步，别留一行空白
            if (canGoBack) {
                Row(
                    Modifier.fillMaxWidth().height(TouchTarget.row),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            }
            Text(
                "使用须知与免责声明",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "开始使用前请先读完这两段：它们说明这个应用做什么、不做什么，以及出问题时责任在哪。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                LegalSection("使用须知", com.kiite.player.R.string.terms_of_use)
                Spacer(Modifier.height(Spacing.xl))
                LegalSection("免责声明", com.kiite.player.R.string.disclaimer)
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier
                    .fillMaxWidth()
                    // 整行可点：勾选框只有 20dp，单点它太考验手指
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { agreed = !agreed }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // onCheckedChange = null：勾选状态由整行统一管，避免行与框各翻一次
                Checkbox(checked = agreed, onCheckedChange = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "我已阅读并同意《使用须知》与《免责声明》",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onAgree,
                enabled = agreed,
                modifier = Modifier.fillMaxWidth().height(TouchTarget.row),
            ) {
                Text("同意并开始使用")
            }
        }
    }
}

/** 条款页里的一节：小标题 + 正文。 */
@Composable
private fun LegalSection(title: String, textRes: Int) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(Spacing.xs))
    LegalTextPage(textRes)
}
