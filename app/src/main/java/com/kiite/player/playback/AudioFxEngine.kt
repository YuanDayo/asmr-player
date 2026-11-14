package com.kiite.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.kiite.player.core.AudioFx
import com.kiite.player.core.AudioFxSettings
import com.kiite.player.data.AppLog

/**
 * 真正把 [AudioFxSettings] 落到 android.media.audiofx 的引擎。
 *
 * 设计要点：
 * - **只作用于播放输出**：效果器挂在 ExoPlayer 的 audioSessionId 上，源文件永远不被改动；
 * - **enabled = false 时全部释放**：不但增益归零，效果器对象也一并 detach，输出链路回到
 *   与没开音效时完全一致的状态（对齐「音效可关闭」的验收标准）；
 * - audioSessionId 变化（换输出设备等）时会从 PlaybackService 通知进来重建。
 */
class AudioFxEngine {

    private var sessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    private var lastApplied: AudioFxSettings = AudioFxSettings.DEFAULT

    /** 挂到指定会话；已有会话则迁移。 */
    fun attach(sessionId: Int) {
        detachAll()
        this.sessionId = sessionId
        apply(lastApplied)
    }

    /** 应用一份设置。settings.disabled 时释放全部效果器。 */
    fun apply(settings: AudioFxSettings) {
        lastApplied = AudioFx.sanitize(settings)
        if (!lastApplied.enabled) {
            detachAll()
            return
        }
        if (sessionId == 0) return
        runCatching { applyEqualizer(lastApplied.gains) }
            .onFailure { AppLog.log("EQ 应用失败：" + it.message) }
        runCatching { applyBass(lastApplied.bassBoost) }
            .onFailure { AppLog.log("低音增强应用失败：" + it.message) }
        runCatching { applyVirtualizer(lastApplied.virtualizer) }
            .onFailure { AppLog.log("环绕声应用失败：" + it.message) }
        runCatching { applyLoudness(lastApplied.loudnessGainMb) }
            .onFailure { AppLog.log("响度增益应用失败：" + it.message) }
    }

    fun release() {
        detachAll()
        sessionId = 0
    }

    private fun detachAll() {
        listOf(equalizer, bassBoost, virtualizer, loudness).forEach { fx ->
            runCatching { fx?.enabled = false }
        }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
    }

    private fun applyEqualizer(gains: List<Int>) {
        if (gains.all { it == 0 }) {
            // 全零 = 均衡器不参与：释放掉而不是挂个平直 EQ（少一层处理，音质更原味）
            runCatching { equalizer?.release() }
            equalizer = null
            return
        }
        val eq = equalizer ?: Equalizer(0, sessionId).also { equalizer = it }
        val bandCount = minOf(AudioFx.BAND_COUNT, eq.numberOfBands.toInt())
        val range = eq.bandLevelRange
        for (band in 0 until bandCount) {
            val milliBel = gains[band] * 100
            val clamped = milliBel.coerceIn(range[0].toInt(), range[1].toInt())
            eq.setBandLevel(band.toShort(), clamped.toShort())
        }
        eq.enabled = true
    }

    private fun applyBass(strength: Int) {
        if (strength <= 0) {
            runCatching { bassBoost?.release() }
            bassBoost = null
            return
        }
        val bb = bassBoost ?: BassBoost(0, sessionId).also { bassBoost = it }
        bb.setStrength(strength.toShort())
        bb.enabled = true
    }

    private fun applyVirtualizer(strength: Int) {
        if (strength <= 0) {
            runCatching { virtualizer?.release() }
            virtualizer = null
            return
        }
        val v = virtualizer ?: Virtualizer(0, sessionId).also { virtualizer = it }
        v.setStrength(strength.toShort())
        v.enabled = true
    }

    private fun applyLoudness(gainMb: Int) {
        if (gainMb <= 0) {
            runCatching { loudness?.release() }
            loudness = null
            return
        }
        val le = loudness ?: LoudnessEnhancer(sessionId).also { loudness = it }
        le.setTargetGain(gainMb)
        le.enabled = true
    }
}
