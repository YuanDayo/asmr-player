package com.kiite.player.core

import kotlinx.serialization.Serializable

/**
 * 音效（EQ / 低音增强 / 环绕声 / 响度增益）设置。
 *
 * 纯数据 + 纯逻辑，不含任何 Android 类型：预览、单元测试、UI 状态都能直接用。
 * 真正把它落到 android.media.audiofx 的是 playback 包里的 AudioFxEngine；
 * **音效只作用于播放输出，永远不修改源文件**。
 */
@Serializable
data class AudioFxSettings(
    /** 总开关：关掉时引擎会把全部音效释放掉，输出与不开音效完全一致。 */
    val enabled: Boolean = false,
    /** 五段均衡增益（dB），顺序对应频段从低到高（约 60Hz–10kHz）。 */
    val gains: List<Int> = DEFAULT_GAINS,
    /** 低音增强强度 0–1000（android.media.audiofx.BassBoost 的量纲）。 */
    val bassBoost: Int = 0,
    /** 环绕声（虚拟器）强度 0–1000（android.media.audiofx.Virtualizer 的量纲）。 */
    val virtualizer: Int = 0,
    /** 响度增益（mB，0–3000；1000 mB = 10 dB），适合「录音偏小声」的音声作品。 */
    val loudnessGainMb: Int = 0,
) {
    companion object {
        val DEFAULT_GAINS = listOf(0, 0, 0, 0, 0)
        val DEFAULT = AudioFxSettings()
    }

    /** 是否真的会改变声音（开着但全零等于没开）。 */
    val audible: Boolean
        get() = enabled && (gains.any { it != 0 } || bassBoost > 0 || virtualizer > 0 || loudnessGainMb > 0)
}

/**
 * EQ 预设与量纲约束。全部是纯函数，方便单元测试。
 *
 * 频段与 Android 系统均衡器默认一致（60Hz / 230Hz / 950Hz / 3.6kHz / 10kHz）。
 * 预设值都往「人声清晰、低频不轰头」的方向调，贴合 ASMR / 音声内容。
 */
object AudioFx {

    const val BAND_COUNT = 5
    const val GAIN_MIN = -12
    const val GAIN_MAX = 12
    const val STRENGTH_MAX = 1000
    const val LOUDNESS_MAX_MB = 3000

    /** 频段标签（从低到高）。 */
    val BAND_LABELS = listOf("60", "230", "950", "3.6k", "10k")

    /** 预设：名称 → 五段增益。 */
    val PRESETS: List<Pair<String, List<Int>>> = listOf(
        "原声" to listOf(0, 0, 0, 0, 0),
        "人声清晰" to listOf(-2, -1, 3, 3, 1),
        "低语增强" to listOf(1, 0, -1, 2, 3),
        "温暖" to listOf(3, 2, 0, -1, -2),
        "低音质感" to listOf(4, 2, -1, 0, 1),
        "明亮通透" to listOf(-1, 0, 1, 2, 4),
    )

    const val CUSTOM_PRESET = "自定义"

    /** 把增益夹进安全范围，长度不对时回落到默认。 */
    fun clampGain(gain: Int): Int = gain.coerceIn(GAIN_MIN, GAIN_MAX)

    /** 校正一份设置：长度、范围、强度全部夹紧，坏数据不会把引擎弄崩。 */
    fun sanitize(settings: AudioFxSettings): AudioFxSettings {
        val gains = if (settings.gains.size != BAND_COUNT) {
            AudioFxSettings.DEFAULT_GAINS
        } else {
            settings.gains.map { clampGain(it) }
        }
        return settings.copy(
            gains = gains,
            bassBoost = settings.bassBoost.coerceIn(0, STRENGTH_MAX),
            virtualizer = settings.virtualizer.coerceIn(0, STRENGTH_MAX),
            loudnessGainMb = settings.loudnessGainMb.coerceIn(0, LOUDNESS_MAX_MB),
        )
    }

    /** 当前增益精确等于某个预设时返回该预设名，否则是「自定义」。 */
    fun presetNameOf(settings: AudioFxSettings): String =
        PRESETS.firstOrNull { it.second == settings.gains }?.first ?: CUSTOM_PRESET

    /** 取预设增益；未知名字回落到原声。 */
    fun gainsForPreset(name: String): List<Int> =
        PRESETS.firstOrNull { it.first == name }?.second ?: PRESETS.first().second

    /** 预设选择器只把「自定义」标中，当且仅当增益不等于任何预设。 */
    fun isCustom(settings: AudioFxSettings): Boolean = presetNameOf(settings) == CUSTOM_PRESET

    /** 增益列表 ↔ 持久化字符串（逗号分隔）。空串 = 没存过（全零）。 */
    fun encodeGains(gains: List<Int>): String = gains.joinToString(",")

    fun decodeGains(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return AudioFxSettings.DEFAULT_GAINS
        val parts = raw.split(',').mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size != BAND_COUNT) AudioFxSettings.DEFAULT_GAINS else parts
    }
}
