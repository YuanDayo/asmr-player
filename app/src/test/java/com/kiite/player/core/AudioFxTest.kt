package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 音效（EQ）设置的纯逻辑：校正、预设匹配、持久化编码。 */
class AudioFxTest {

    @Test
    fun `sanitize clamps gains and strengths`() {
        val s = AudioFx.sanitize(
            AudioFxSettings(
                enabled = true,
                gains = listOf(-99, 0, 99, 3, -4, 7), // 长度也不对
                bassBoost = 5000,
                virtualizer = -10,
                loudnessGainMb = 9999,
            ),
        )
        assertEquals(AudioFxSettings.DEFAULT_GAINS, s.gains) // 长度不对整体回落
        assertEquals(AudioFx.STRENGTH_MAX, s.bassBoost) // 超上限被夹住
        assertEquals(0, s.virtualizer) // 负值被夹到 0
        assertEquals(AudioFx.LOUDNESS_MAX_MB, s.loudnessGainMb)
    }

    @Test
    fun `sanitize keeps valid gains within range`() {
        val s = AudioFx.sanitize(
            AudioFxSettings(enabled = true, gains = listOf(-12, -5, 0, 6, 12)),
        )
        assertEquals(listOf(-12, -5, 0, 6, 12), s.gains)
    }

    @Test
    fun `preset matching returns exact name or custom`() {
        val preset = AudioFx.PRESETS[1]
        val s = AudioFxSettings(enabled = true, gains = preset.second)
        assertEquals(preset.first, AudioFx.presetNameOf(s))
        val custom = AudioFxSettings(enabled = true, gains = listOf(1, 2, 3, 4, 5))
        assertEquals(AudioFx.CUSTOM_PRESET, AudioFx.presetNameOf(custom))
        assertTrue(AudioFx.isCustom(custom))
        assertFalse(AudioFx.isCustom(s))
    }

    @Test
    fun `unknown preset falls back to flat`() {
        assertEquals(AudioFx.PRESETS.first().second, AudioFx.gainsForPreset("不存在的预设"))
    }

    @Test
    fun `gains encode and decode roundtrip`() {
        val gains = listOf(-3, 0, 2, 7, -12)
        assertEquals(gains, AudioFx.decodeGains(AudioFx.encodeGains(gains)))
        assertEquals(AudioFxSettings.DEFAULT_GAINS, AudioFx.decodeGains(null))
        assertEquals(AudioFxSettings.DEFAULT_GAINS, AudioFx.decodeGains(""))
        assertEquals(AudioFxSettings.DEFAULT_GAINS, AudioFx.decodeGains("1,2"))
    }

    @Test
    fun `audible only when enabled and something nonzero`() {
        assertFalse(AudioFxSettings.DEFAULT.audible)
        assertFalse(AudioFxSettings(enabled = true).audible) // 全零也等于没开
        assertTrue(AudioFxSettings(enabled = true, gains = listOf(1, 0, 0, 0, 0)).audible)
        assertTrue(AudioFxSettings(enabled = true, bassBoost = 500).audible)
        assertFalse(AudioFxSettings(enabled = false, gains = listOf(6, 6, 6, 6, 6)).audible)
    }

    @Test
    fun `every preset has five bands within range`() {
        AudioFx.PRESETS.forEach { (name, gains) ->
            assertEquals("预设 $name 应有 5 段", AudioFx.BAND_COUNT, gains.size)
            gains.forEach { g ->
                assertTrue("预设 $name 增益 $g 超范围", g in AudioFx.GAIN_MIN..AudioFx.GAIN_MAX)
            }
        }
    }
}
