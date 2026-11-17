package com.kiite.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.asmrBorder
import com.kiite.player.ui.asmrRowColor
import com.kiite.player.ui.asmrShape
import com.kiite.player.ui.theme.AsmrTheme
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing
import com.kiite.player.ui.theme.TouchTarget

/**
 * 统一曲目行：曲库 / 队列 / 搜索结果共用同一个实现（计划书 4.1）。
 *
 * 手势语义（全项目一致，见 docs/UI-SPEC.md）：
 *  - 单击：播放 / 跳转播放
 *  - 长按：弹出操作菜单（调用方给 [onLongClick]）
 *  - 尾部操作按钮由 [trailing] 传入，不再各自写一套
 *
 * 当前播放项用「左侧竖条 + 高亮底色」双重标识：只靠底色的话在深色皮肤下
 * 几乎看不出来，只靠竖条则在光线强时不够醒目。
 *
 * @param leadingIcon 行首内容（序号、封面缩略图等），由调用方决定
 * @param active 是否为当前项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = asmrShape(Radius.md)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.row)
            .clip(shape)
            .background(if (active) scheme.secondaryContainer else asmrRowColor())
            .then(if (active) Modifier else asmrBorder(shape))
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            // TalkBack 把整行读成一句（标题 + 副标题），而不是逐元素朗读
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .clip(asmrShape(2.dp))
                    .background(scheme.primary),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        leadingIcon?.let {
            it()
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = titleStyle ?: MaterialTheme.typography.bodyLarge,
                color = if (active) scheme.primary else scheme.onSurface,
            )
            if (subtitle != null) {
                Text(
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
