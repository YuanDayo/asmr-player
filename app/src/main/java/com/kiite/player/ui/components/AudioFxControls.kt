package com.kiite.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kiite.player.core.AudioFx
import com.kiite.player.core.AudioFxSettings
import com.kiite.player.ui.theme.Spacing

/**
 * 音效控制（EQ 五段 + 低音 / 环绕 / 响度），设置页与播放页弹层共用这一份。
 *
 * 只负责渲染和回调，不持有状态：状态在 `MainViewModel.audioFx`，写回走 `onApply`。
 * 音效只挂在播放输出上，永远不改音频文件，所以这里不出现任何「保存 / 导出」类操作。
 */
@Composable
fun AudioFxControls(
    fx: AudioFxSettings,
    onApply: (AudioFxSettings) -> Unit,
    /** 打开 / 关闭、重置这类「有结果的动作」用它给一句反馈，弹层里默认不给也行。 */
    say: (String) -> Unit = {},
    /** 弹层里用紧凑排版：少一句说明、间距小一档。 */
    compact: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用音效", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            AsmrSwitch(
                checked = fx.enabled,
                onCheckedChange = { on ->
                    onApply(fx.copy(enabled = on))
                    say(if (on) "音效已启用（只影响播放输出，不改音频文件）" else "音效已关闭，输出恢复原样")
                },
            )
        }
        if (!compact) {
            Text(
                "效果器挂在播放输出上，关闭后与未开音效完全一致；音频文件不会被修改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!fx.enabled) {
            Text(
                "打开上面的开关后可以选预设、拉五段均衡。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Spacer(Modifier.height(if (compact) Spacing.xs else Spacing.sm))
        Text("预设", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AudioFx.PRESETS.forEach { (name, gains) ->
                FilterChip(
                    selected = fx.gains == gains,
                    onClick = { onApply(fx.copy(gains = gains)) },
                    label = { Text(name) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "五段均衡（" + AudioFx.presetNameOf(fx) + "）",
            style = MaterialTheme.typography.labelMedium,
        )
        fx.gains.forEachIndexed { band, gain ->
            val label = AudioFx.BAND_LABELS.getOrElse(band) { band.toString() }
            Text(
                label + " Hz  " + (if (gain > 0) "+" else "") + gain + " dB",
                style = MaterialTheme.typography.bodySmall,
            )
            AsmrSlider(
                value = gain.toFloat(),
                onValueChange = { newGain ->
                    onApply(
                        fx.copy(
                            gains = fx.gains.toMutableList().also { list -> list[band] = newGain.toInt() },
                        ),
                    )
                },
                valueRange = AudioFx.GAIN_MIN.toFloat()..AudioFx.GAIN_MAX.toFloat(),
                steps = AudioFx.GAIN_MAX - AudioFx.GAIN_MIN - 1,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "低音增强 " + (fx.bassBoost * 100 / 1000) + "%",
            style = MaterialTheme.typography.bodyMedium,
        )
        AsmrSlider(
            value = fx.bassBoost.toFloat(),
            onValueChange = { onApply(fx.copy(bassBoost = it.toInt())) },
            valueRange = 0f..1000f,
            steps = 9,
        )
        Text("环绕声 " + (fx.virtualizer * 100 / 1000) + "%", style = MaterialTheme.typography.bodyMedium)
        AsmrSlider(
            value = fx.virtualizer.toFloat(),
            onValueChange = { onApply(fx.copy(virtualizer = it.toInt())) },
            valueRange = 0f..1000f,
            steps = 9,
        )
        Text(
            "响度增益 +" + "%.1f".format(fx.loudnessGainMb / 100f) + " dB（录音偏小声的作品用）",
            style = MaterialTheme.typography.bodyMedium,
        )
        AsmrSlider(
            value = fx.loudnessGainMb.toFloat(),
            onValueChange = { onApply(fx.copy(loudnessGainMb = it.toInt())) },
            valueRange = 0f..3000f,
            steps = 29,
        )
        TextButton(onClick = {
            onApply(AudioFxSettings(enabled = true))
            say("音效已全部重置")
        }) { Text("重置为原声") }
    }
}
