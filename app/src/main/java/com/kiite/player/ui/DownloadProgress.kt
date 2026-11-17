package com.kiite.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiite.player.domain.AsmrOneController

/**
 * DownloadProgress 独有的控件尺寸。
 *
 * 这些不进全局令牌：它们是本页特有的控件大小（图标按钮、进度条、封面档位），
 * 同值不同义，硬塞进 Spacing / IconSize 会让令牌表变成一张数字表。
 * 集中在这里的目的是可搜、可一处改；真正的归一化（图标按钮统一到 48dp
 * 触控目标）留给单独一批，因为会改变布局。
 */
private object ProgressDim {
    val box4 = 4.dp
}


/**
 * 细进度条：自己用两个 Box 画。
 *
 * 不用 Material3 的 LinearProgressIndicator 是有原因的——它的 `progress` 参数在
 * 1.2 / 1.3 之间从 Float 变成了 `() -> Float`，不同 BOM 版本编译不过（项目里
 * BookmarksPanel 也踩过同一个坑）。自绘既能控制高度（3~4dp 才像下载条），
 * 又不受版本影响。
 */
@Composable
fun DownloadProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = ProgressDim.box4,
    track: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    bar: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val p = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(height)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(track),
    ) {
        Box(
            Modifier
                .width(maxWidth * p)
                .fillMaxHeight()
                .clip(shape)
                .background(bar),
        )
    }
}

/** 下载状态的一行小字：状态 + 百分比 + 速度 + 预计剩余。 */
fun downloadDetailText(s: AsmrOneController.DownloadState): String = buildString {
    append(s.status)
    if (s.running) {
        if (s.total > 0) append(" · ").append(s.percent).append("%")
        s.speedText.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        s.etaText.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }
}
