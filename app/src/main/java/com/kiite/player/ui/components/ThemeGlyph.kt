package com.kiite.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.theme.AsmrTheme
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Motion
import com.kiite.player.ui.theme.ThemeMode

/**
 * ThemeGlyph 独有的尺寸。
 *
 * 模糊半径不进全局令牌：它只服务这一个「图标换态」的小把戏，
 * 塞进 Spacing / Radius 里只会让令牌表变成一张数字表。
 */
private object GlyphDim {
    /** 换态时失焦的模糊半径；再大就像「糊掉了」而不是「转走了」。 */
    val blurOut = 3.dp

    /** 缩小到多小：0.45 刚好还认得出是太阳，再小就只剩一个点。 */
    const val shrink = 0.45f

    /** 旋转角度：75° 足够读出「转走」，90° 会让射线与十字对齐，看起来像没转。 */
    const val spin = 75f
}

/**
 * 白天 / 黑夜图标：切换时**旋转 + 缩小 + 失焦**地交叉淡出淡入。
 *
 * 参考站点的 `.theme-icon-stack` 就是这么做的（sun 从 `rotate(0) scale(1) blur(0)`
 * 转到 `rotate(-75deg) scale(0.45) blur(3px)`，moon 反向），观感上像一颗图标「转个身
 * 换成另一个」，比直接换图标或纯淡入强得多 —— 纯淡入在深色底上会有一帧两个图标都
 * 半透明，看起来像重影。
 *
 * 两者的差异必须**互补**：一个的 alpha 等于 1 减另一个，否则中间会出现「两个都没有」
 * 的空档。旋转方向也相反，视觉上才是「换位」而不是「原地消失」。
 *
 * 模糊走 `Modifier.blur`，Android 12 以下不生效（退化成纯淡入 + 旋转 + 缩放），
 * 不影响可用性。
 */
@Composable
fun ThemeGlyph(
    dark: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val p by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = Motion.standard(Motion.LONG),
        label = "themeGlyph",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.LightMode,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(IconSize.md)
                .blur(GlyphDim.blurOut * p)
                .graphicsLayer {
                    alpha = 1f - p
                    rotationZ = -GlyphDim.spin * p
                    val s = 1f - (1f - GlyphDim.shrink) * p
                    scaleX = s
                    scaleY = s
                },
        )
        Icon(
            Icons.Filled.DarkMode,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(IconSize.md)
                .blur(GlyphDim.blurOut * (1f - p))
                .graphicsLayer {
                    alpha = p
                    rotationZ = GlyphDim.spin * (1f - p)
                    val s = GlyphDim.shrink + (1f - GlyphDim.shrink) * p
                    scaleX = s
                    scaleY = s
                },
        )
    }
}

@Preview(name = "主题图标 · 浅色", showBackground = true, backgroundColor = 0xFFF2F2F7, widthDp = 120)
@Composable
private fun ThemeGlyphPreviewLight() {
    AsmrTheme(themeMode = ThemeMode.LIGHT) {
        ThemeGlyph(dark = false, modifier = Modifier.size(IconSize.hero))
    }
}

@Preview(name = "主题图标 · 深色", showBackground = true, backgroundColor = 0xFF000000, widthDp = 120)
@Composable
private fun ThemeGlyphPreviewDark() {
    AsmrTheme(themeMode = ThemeMode.DARK) {
        ThemeGlyph(dark = true, modifier = Modifier.size(IconSize.hero))
    }
}
