package com.kiite.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kiite.player.ui.theme.IconSize
import com.kiite.player.ui.theme.Stroke
import com.kiite.player.ui.theme.TouchTarget
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

/**
 * 项目统一的开关。
 *
 * 外观沿用 Miuix 的 HyperOS 开关（50 × 28.5dp），但**外框固定成 52 × 32dp** ——
 * 这正是 M3 Switch 的历史尺寸。换库时行高、右边缘对齐、点按位置都不变，
 * 不会出现「同一张设置页里开关忽大忽小」。
 *
 * 尺寸写死在这里而不是 Miuix 的默认值：Miuix 的开关没有尺寸参数，
 * 只能靠外层盒子统一占位。
 */
private object SwitchTokens {
    val width = 52.dp
    val height = 32.dp
}

@Composable
fun AsmrSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier.size(SwitchTokens.width, SwitchTokens.height),
        contentAlignment = Alignment.Center,
    ) {
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * 项目统一的滑杆：**细轨道 + 单点**。
 *
 * 这套外观不是新发明的 —— 播放页走带（`PlayerScreen` 的 `Transport`）早就是
 * 4dp 轨道 + 15dp 圆点，注释里写明「M3 默认那套粗轨道加停止点又重又吵」。
 * 把它提成公共控件，设置页、音效页、播放页从此是同一根滑杆。
 *
 * 为什么不用 Miuix 的 Slider：它的轨道高度**就是**可拖拽区域
 * （`SliderDefaults.MinHeight = 30dp`），调细就没人按得住；保持 30dp 又粗一圈。
 *
 * 触控高度仍是 [TouchTarget.min]（48dp）：轨道只有 4dp，但手指按在轨道上下
 * 24dp 内都能拖动。
 */
private object SliderTokens {
    val height = TouchTarget.min
    val thumb = IconSize.xs
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsmrSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(SliderTokens.height),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        thumb = {
            Box(
                Modifier
                    .size(SliderTokens.thumb)
                    .clip(CircleShape)
                    .background(if (enabled) accent else accent.copy(alpha = 0.38f)),
            )
        },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Stroke.thick)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(Stroke.thick)
                        .clip(CircleShape)
                        .background(if (enabled) accent else accent.copy(alpha = 0.38f)),
                )
            }
        },
    )
}
