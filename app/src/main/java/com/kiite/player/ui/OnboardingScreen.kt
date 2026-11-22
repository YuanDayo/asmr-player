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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
private val BadgeSize = 104.dp
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
 * 首次启动的功能介绍：5 页横向翻页 + 圆点指示器 + 「跳过 / 下一页 / 开始使用」。
 *
 * 盖在最上层（AppRoot 的 `onboardingOpen`），所以自己负责背景与「吃掉点击」：
 * 不加那颗空 `clickable` 的话，点空白处会穿透到下面的曲库/设置上。
 * 看过一次后由 `AppSettings.onboardingSeen` 记住，之后不再自动出现；
 * 想重看走「设置 → 关于 → 功能介绍」。
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
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
                // 最后一页只剩「开始使用」，跳过就没意义了，提前收掉避免两个按钮抢注意力
                if (!last) {
                    TextButton(onClick = onDone) { Text("跳过") }
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                val page = Pages[index]
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(BadgeSize)
                            .clip(asmrShape(Radius.xxl))
                            .background(scheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(page.icon, null, Modifier.size(IconSize.hero), tint = scheme.primary)
                    }
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
                        onDone()
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(TouchTarget.row),
            ) {
                Text(if (last) "开始使用" else "下一页")
            }
        }
    }
}
