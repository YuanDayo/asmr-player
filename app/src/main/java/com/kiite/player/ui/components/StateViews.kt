package com.kiite.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.asmrShape
import com.kiite.player.ui.theme.AsmrTheme
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Motion
import com.kiite.player.ui.theme.Radius
import com.kiite.player.ui.theme.Spacing

/**
 * StateViews 独有的控件尺寸。
 *
 * 这些不进全局令牌：它们是本页特有的控件大小（图标按钮、进度条、封面档位），
 * 同值不同义，硬塞进 Spacing / IconSize 会让令牌表变成一张数字表。
 * 集中在这里的目的是可搜、可一处改；真正的归一化（图标按钮统一到 48dp
 * 触控目标）留给单独一批，因为会改变布局。
 */
private object StateDim {
    val box32 = 32.dp
    val box420 = 420.dp
    val height9 = 9.dp
    val height12 = 12.dp
    val width90 = 90.dp
    val width120 = 120.dp
    val width150 = 150.dp
    val width200 = 200.dp
}


/**
 * 空 / 加载 / 错误三态的统一骨架（计划书 4.3）。
 *
 * 以前三态各写各的：有的页面留空白，有的弹 Toast，有的把「没有曲库」和
 * 「目录被删了」写成同一句话。现在三态共用同一套居中布局与文案层级，
 * 唯一的差别是图标、语气和有没有按钮 —— 用户一眼能分清「没有内容」
 * 和「出错了」。
 */
@Composable
fun StatePanel(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(IconSize.hero), tint = iconTint)
        Spacer(Modifier.height(Spacing.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = StateDim.box420),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Spacing.section))
            action()
        }
    }
}

/** 空态：没有内容，但一切正常。给一个「接下来能做什么」的主按钮。 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    action: (@Composable () -> Unit)? = null,
) {
    StatePanel(icon = icon, title = title, modifier = modifier, description = description, action = action)
}

/** 错误态：说明发生了什么 + 一个重试入口。红色图标表示「需要处理」。 */
@Composable
fun ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.WifiOff,
    action: (@Composable () -> Unit)? = null,
) {
    StatePanel(
        icon = icon,
        title = title,
        modifier = modifier,
        description = description,
        iconTint = MaterialTheme.colorScheme.error,
        action = action,
    )
}

/**
 * 加载态：骨架屏优先于转圈（计划书 4.3）。
 *
 * 骨架直接画成「列表行的形状」，界面从加载到有内容不会跳版；
 * 呼吸动画只是给「还在转」一个视觉信号，不做流光，低端机上零成本。
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
    rows: Int = 6,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.LONG * 2, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        repeat(rows) { i ->
            SkeletonRow(alpha, wide = i % 3 == 0)
        }
    }
}

/** 单条骨架行：缩略块 + 两行文字块。 */
@Composable
private fun SkeletonRow(alpha: Float, wide: Boolean) {
    val block = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IconSize.hero)
            .clip(asmrShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.35f))
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(StateDim.box32).clip(asmrShape(Radius.xs)).background(block))
        Spacer(Modifier.width(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(
                Modifier
                    .width(if (wide) StateDim.width200 else StateDim.width150)
                    .height(StateDim.height12)
                    .clip(asmrShape(Radius.pill))
                    .background(block),
            )
            Box(
                Modifier
                    .width(if (wide) StateDim.width120 else StateDim.width90)
                    .height(StateDim.height9)
                    .clip(asmrShape(Radius.pill))
                    .background(block.copy(alpha = alpha * 0.6f)),
            )
        }
    }
}

// ---------------- 预览 ----------------

@Preview(name = "空态", showBackground = true, backgroundColor = 0xFFFDFBF7, heightDp = 300)
@Composable
private fun EmptyStatePreview() {
    AsmrTheme(themeMode = com.kiite.player.ui.theme.ThemeMode.LIGHT) {
        EmptyState(
            title = "还没有音频",
            description = "把作品解压到根目录后重新扫描，或换一个目录。",
            action = { Button(onClick = {}) { Text("重新扫描") } },
        )
    }
}

@Preview(name = "错误态", showBackground = true, backgroundColor = 0xFF101418, heightDp = 300)
@Composable
private fun ErrorStatePreview() {
    AsmrTheme(themeMode = com.kiite.player.ui.theme.ThemeMode.DARK) {
        ErrorState(
            title = "目录已失效",
            description = "上次的曲库目录不存在了（可能被移动或拔掉了存储卡）。",
            action = { Button(onClick = {}) { Text("选择新目录") } },
        )
    }
}

@Preview(name = "骨架屏", showBackground = true, backgroundColor = 0xFFFDFBF7, heightDp = 320)
@Composable
private fun LoadingStatePreview() {
    AsmrTheme(themeMode = com.kiite.player.ui.theme.ThemeMode.LIGHT) {
        LoadingState(message = "正在扫描目录…", rows = 5)
    }
}
