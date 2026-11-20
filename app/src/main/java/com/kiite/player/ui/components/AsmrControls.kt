package com.kiite.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 * 项目统一的滑杆。
 *
 * 这里**没有**用 Miuix 的 Slider：它的「轨道高度」和「可拖拽区域」是同一个值
 * （`SliderDefaults.MinHeight = 30dp`），调到 4～8dp 的细轨道就没有手指能按住的区域了；
 * 反过来保持 30dp，又会比页面里其它控件粗一大圈（实测一屏里三条 30dp 实心蓝条）。
 *
 * 所以滑杆用 M3 的实现（4dp 轨道 + 20dp 滑块 + 48dp 触控），外面包一层，
 * 以后要换自定义轨道只改这里。
 */
@Composable
fun AsmrSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
    )
}
