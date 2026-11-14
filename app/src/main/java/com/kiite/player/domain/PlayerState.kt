/**
 * 播放页与台本区的领域状态：控制器持有、界面观察。
 * 纯 Kotlin，不依赖任何 Android 类型——Domain 层保持可测试。
 */
package com.kiite.player.domain

import com.kiite.player.core.ParsedScript
import com.kiite.player.core.ScriptAttachment

data class PlayerUi(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val buffering: Boolean = false,
    val hasMedia: Boolean = false,
    val speed: Float = 1.0f,
)

data class ScriptUi(
    val attachment: ScriptAttachment? = null,
    val parsed: ParsedScript? = null,
    val loading: Boolean = false,
    val fromEmbedded: Boolean = false,
    val error: String? = null,
    /** 校准后实际用于显示/同步的时间轴就在 [parsed] 里；[rawLineCount] 是原文行数。 */
    val rawLineCount: Int = 0,
    /** 当前台本生效的校准（已按行数净化过）。 */
    val calibration: com.kiite.player.core.ScriptCalibration = com.kiite.player.core.ScriptCalibration(),
    val calibrationKey: String? = null,
    /** 台本文件行数变了，单句微调已失效（全局偏移仍保留）。 */
    val staleLines: Boolean = false,
    /** 匹配状态：精确 / 较可靠 / 低置信 / 手动指定 / 内嵌。 */
    val confidence: com.kiite.player.core.CalibrationMath.MatchConfidence =
        com.kiite.player.core.CalibrationMath.MatchConfidence.NONE,
    val confidenceHint: String? = null,
) {
    /** 有时间轴才能做逐句校准。 */
    val calibratable: Boolean get() = parsed?.timedLines?.isNotEmpty() == true
    val hasTimeline: Boolean get() = calibratable
}
