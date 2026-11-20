package com.kiite.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.kiite.player.ui.asmrBorder
import com.kiite.player.ui.asmrRowColor
import com.kiite.player.ui.theme.AsmrTheme
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.Stroke
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape

/**
 * 统一曲目行：曲库 / 队列 / 搜索结果共用同一个实现（计划书 4.1）。
 *
 * 手势语义（全项目一致，见 docs/UI-SPEC.md）：
 *  - 单击：播放 / 跳转播放
 *  - 长按：弹出操作菜单（调用方给 [onLongClick]）
 *  - 尾部操作按钮由 [trailing] 传入，不再各自写一套
 *
 * 外观走 Miuix 卡片：squircle 圆角 + 按下去会陷一点的反馈，和曲库的作品卡是同一套手感。
 * 当前播放项用「左侧竖条 + 高亮底色」双重标识：只靠底色的话在深色皮肤下
 * 几乎看不出来，只靠竖条则在光线强时不够醒目。
 *
 * @param leadingIcon 行首内容（序号、封面缩略图等），由调用方决定
 * @param active 是否为当前项
 */
@Composable
fun TrackListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    titleStyle: TextStyle? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = SmoothRoundedCornerShape(Radius.md)
    MiuixCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (active) Modifier else asmrBorder(shape))
            // TalkBack 把整行读成一句（标题 + 副标题），而不是逐元素朗读
            .semantics(mergeDescendants = true) {},
        color = if (active) scheme.secondaryContainer else asmrRowColor(),
        cornerRadius = Radius.md,
        insideMargin = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        // 不可点时不给按压反馈，避免「按下去有反应但其实没反应」
        pressFeedbackType = if (enabled) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = if (enabled) onClick else null,
        onLongPress = if (enabled) onLongClick else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                Box(
                    Modifier
                        .width(Stroke.medium)
                        .height(IconSize.md)
                        .clip(SmoothRoundedCornerShape(Radius.xxs))
                        .background(scheme.primary),
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            leadingIcon?.let {
                it()
                Spacer(Modifier.width(Spacing.md))
            }
            Column(Modifier.weight(1f)) {
                MiuixText(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = titleStyle ?: MaterialTheme.typography.bodyLarge,
                    // 颜色取应用主题：这个组件也会出现在没有 MiuixTheme 包裹的页面里
                    color = if (active) scheme.primary else scheme.onSurface,
                )
                if (subtitle != null) {
                    MiuixText(
                        subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    content = trailing,
                )
            }
        }
    }
}

// ---------------- 预览 ----------------
// 覆盖浅色 / 深色 / 大字体三态：列表行最容易出问题的地方就是
// 「深色下行高看不清」「1.3x 字体把副标题截断」，改样式后先看这三个。

@Composable
private fun TrackListItemPreviewContent() {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        TrackListItem(
            title = "第 01 集 · 耳かき",
            subtitle = "MP3 · 有台本",
            leadingIcon = { Text("1", style = MaterialTheme.typography.labelMedium) },
            onClick = {},
            onLongClick = {},
        )
        TrackListItem(
            title = "第 02 集 · 添い寝（这是正在播放的那一行）",
            subtitle = "FLAC · 无台本",
            active = true,
            leadingIcon = { Text("2", style = MaterialTheme.typography.labelMedium) },
            onClick = {},
            trailing = { Text("03:12", style = MaterialTheme.typography.labelSmall) },
        )
        TrackListItem(
            title = "第 03 集 · 囁き",
            subtitle = "MP3 · 有台本",
            enabled = false,
            leadingIcon = { Text("3", style = MaterialTheme.typography.labelMedium) },
            onClick = {},
        )
    }
}

@Preview(name = "浅色", showBackground = true, backgroundColor = 0xFFFDFBF7)
@Composable
private fun TrackListItemPreviewLight() {
    AsmrTheme(themeMode = com.kiite.player.ui.theme.ThemeMode.LIGHT) { TrackListItemPreviewContent() }
}

@Preview(name = "深色", showBackground = true, backgroundColor = 0xFF101418)
@Composable
private fun TrackListItemPreviewDark() {
    AsmrTheme(themeMode = com.kiite.player.ui.theme.ThemeMode.DARK) { TrackListItemPreviewContent() }
}

@Preview(name = "大字体 1.3x", showBackground = true, fontScale = 1.3f, widthDp = 320)
@Composable
private fun TrackListItemPreviewLargeFont() {
    AsmrTheme { TrackListItemPreviewContent() }
}
