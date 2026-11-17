package com.kiite.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.IndexTag
import com.kiite.player.ui.theme.LocalEditorial
import com.kiite.player.ui.theme.Spacing

/**
 * 分区标题：章节、设置分组、搜索结果区块都用它（计划书 4.4）。
 *
 * 两套皮肤下的排版不同（玻璃皮肤是「竖条 + 宽字距标签 + 右侧序号」，
 * 经典皮肤是「主色小标题」），但结构一致 —— 页面层只传标题、数量、
 * 右侧操作，不用再各写一份 Row。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    /** 数量后缀（「12 首」）；null 表示不显示。 */
    count: Int? = null,
    unit: String = "首",
    trailing: (@Composable () -> Unit)? = null,
) {
    if (LocalEditorial.current) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(13.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                buildString {
                    append(title.uppercase())
                    if (count != null) append("　").append(count).append(" ").append(unit)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.let {
                it()
                Spacer(Modifier.width(Spacing.xs))
            }
            if (count != null) IndexTag(count.toString().padStart(2, '0'))
        }
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                append(title)
                if (count != null) append(" · ").append(count).append(" ").append(unit)
            },
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}
