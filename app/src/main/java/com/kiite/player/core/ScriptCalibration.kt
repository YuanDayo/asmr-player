package com.kiite.player.core

import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * 单句的时间微调量（相对「原文时间 + 全局偏移」的增量）。
 *
 * 存增量而不是绝对值，有两个好处：
 *  - 台本文件重新解析后，只要行数没变，微调依然对得上；
 *  - 调整全局偏移时，已经微调过的句子会跟着一起平移，不会互相打架。
 */
@Serializable
data class LineShift(
    val startDelta: Long = 0L,
    val endDelta: Long = 0L,
) {
    val isZero: Boolean get() = startDelta == 0L && endDelta == 0L
}

/**
 * 批量平移的结果。
 *
 * [appliedMs] 可能小于请求的位移：区间首尾会被区间之外的相邻句夹住，
 * 界面要用它来告诉用户「实际只移动了 0.3 秒」，而不是假装成功。
 */
data class RangeShift(val calibration: ScriptCalibration, val appliedMs: Long)

/**
 * 一份台本的校准结果。**只存在 App 自己的 JSON 里，绝不回写用户的台本文件。**
 *
 * @param offsetMs 全局偏移：整份台本统一平移的量。
 * @param lineCount 校准时台本的行数；行数变了说明台本文件被换过，单句微调作废。
 * @param lines 行号 → 该行的微调量。
 */
@Serializable
data class ScriptCalibration(
    val offsetMs: Long = 0L,
    val lineCount: Int = 0,
    val lines: Map<Int, LineShift> = emptyMap(),
) {
    val isEmpty: Boolean get() = offsetMs == 0L && lines.isEmpty()

    /** 有非零微调的行数。 */
    val adjustedLineCount: Int get() = lines.values.count { !it.isZero }
}

/**
 * 台本时间轴校准的全部计算。纯 JVM，可单测。
 *
 * 约定：
 *  - 所有对外暴露的时间都是「校准后」的绝对毫秒，直接拿去 seek 或比对播放位置；
 *  - 单句起点不允许越过上一句、压到下一句，越界一律夹住而不是拒绝——
 *    用户点「−1s」时只是想往回一点，弹个错误反而更烦；
 *  - 校准结果永远不修改原始 [TimedLine] 列表，只产出新的列表。
 */
object CalibrationMath {

    /** 全局偏移上限（±2 分钟）。台本整体错位超过这个量级，多半是配错了文件。 */
    const val MAX_OFFSET_MS = 120_000L

    /** 相邻两句之间至少保留的间隔，避免两句起点撞在同一毫秒上。 */
    const val MIN_LINE_GAP_MS = 100L

    /** 全局偏移的快捷步长。 */
    val OFFSET_STEPS = longArrayOf(-5_000L, -1_000L, -200L, 200L, 1_000L, 5_000L)

    /** 单句微调的快捷步长。 */
    val LINE_STEPS = longArrayOf(-1_000L, -200L, 200L, 1_000L)

    /**
     * 批量平移的快捷步长。
     *
     * 比单句多两档大位移（±5s）：整段错位往往是「漏了一个停顿」，量级在秒上，
     * 只给 ±1s 要按十几次，而每按一次都是一次整表重算。
     */
    val RANGE_STEPS = longArrayOf(-5_000L, -1_000L, -500L, -100L, 100L, 500L, 1_000L, 5_000L)

    /**
     * 台本文件被换过（行数变化）时，单句微调失去了对应关系，只保留全局偏移。
     * @return 净化后的校准；[ScriptCalibration.lineCount] 同步成当前行数。
     */
    fun pruneStale(cal: ScriptCalibration, lineCount: Int): ScriptCalibration =
        if (cal.lineCount == lineCount) cal else cal.copy(lineCount = lineCount, lines = emptyMap())

    /** 校准数据是否因为台本行数变化而失效（空校准不算失效）。 */
    fun isStale(cal: ScriptCalibration, lineCount: Int): Boolean =
        cal.lineCount != 0 && cal.lineCount != lineCount && cal.lines.isNotEmpty()

    /** 第 [index] 句校准后的起点。 */
    fun effectiveStart(raw: List<TimedLine>, cal: ScriptCalibration, index: Int): Long {
        val line = raw.getOrNull(index) ?: return 0L
        return (line.startMs + cal.offsetMs + (cal.lines[index]?.startDelta ?: 0L)).coerceAtLeast(0L)
    }

    /** 第 [index] 句校准后的终点；原文没有终点时返回 null。 */
    fun effectiveEnd(raw: List<TimedLine>, cal: ScriptCalibration, index: Int): Long? {
        val line = raw.getOrNull(index) ?: return null
        val end = line.endMs ?: return null
        return (end + cal.offsetMs + (cal.lines[index]?.endDelta ?: 0L))
            .coerceAtLeast(effectiveStart(raw, cal, index))
    }

    /**
     * 把校准应用到整份时间轴。
     *
     * 除了平移，还兜两道底：
     *  - 起点不小于 0（LRC 里 `[00:00.00]` 往上挪会变负）；
     *  - 起点单调不减（单句微调可能有历史残留，读回来时不能让顺序乱掉）。
     */
    fun apply(raw: List<TimedLine>, cal: ScriptCalibration): List<TimedLine> {
        if (raw.isEmpty() || cal.isEmpty) return raw
        var prevStart = 0L
        return raw.mapIndexed { i, line ->
            val shift = cal.lines[i]
            val start = (line.startMs + cal.offsetMs + (shift?.startDelta ?: 0L)).coerceAtLeast(prevStart)
            prevStart = start
            val end = line.endMs?.let { e ->
                (e + cal.offsetMs + (shift?.endDelta ?: 0L)).coerceAtLeast(start)
            }
            line.copy(startMs = start, endMs = end)
        }
    }

    /** 直接产出校准后的台本对象；没有时间轴时原样返回。 */
    fun applyToScript(parsed: ParsedScript, cal: ScriptCalibration): ParsedScript {
        if (cal.isEmpty || parsed.timedLines.isEmpty()) return parsed
        return parsed.copy(timedLines = apply(parsed.timedLines, cal))
    }

    fun setOffset(cal: ScriptCalibration, offsetMs: Long, lineCount: Int): ScriptCalibration =
        cal.copy(
            offsetMs = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS),
            lineCount = lineCount,
        )

    fun shiftOffset(cal: ScriptCalibration, deltaMs: Long, lineCount: Int): ScriptCalibration =
        setOffset(cal, cal.offsetMs + deltaMs, lineCount)

    /**
     * 把第 [index] 句的起点挪到音频的 [newStartMs]。
     * 夹在「上一句起点 + 间隔」和「下一句起点 − 间隔」之间；台本本身太挤时原样返回。
     */
    fun withLineStart(
        raw: List<TimedLine>,
        cal: ScriptCalibration,
        index: Int,
        newStartMs: Long,
    ): ScriptCalibration {
        val line = raw.getOrNull(index) ?: return cal
        val lo = if (index > 0) effectiveStart(raw, cal, index - 1) + MIN_LINE_GAP_MS else 0L
        val hi = if (index + 1 < raw.size) {
            effectiveStart(raw, cal, index + 1) - MIN_LINE_GAP_MS
        } else {
            Long.MAX_VALUE / 2
        }
        if (hi < lo) return cal
        val target = newStartMs.coerceIn(lo, hi)
        val delta = target - (line.startMs + cal.offsetMs)
        return putLine(
            cal.copy(lineCount = raw.size),
            index,
            (cal.lines[index] ?: LineShift()).copy(startDelta = delta),
        )
    }

    /**
     * 把第 [index] 句的终点挪到音频的 [newEndMs]。
     *
     * 上界是「下一句起点」而不是「下一句起点 − 间隔」：LRC 里每句的终点本来就等于下一句的起点，
     * 再减一个间隔会把原本正确的终点改早 100ms——用户调不动，还悄悄把数据改坏了。
     */
    fun withLineEnd(
        raw: List<TimedLine>,
        cal: ScriptCalibration,
        index: Int,
        newEndMs: Long,
    ): ScriptCalibration {
        val line = raw.getOrNull(index) ?: return cal
        val start = effectiveStart(raw, cal, index)
        val hi = if (index + 1 < raw.size) effectiveStart(raw, cal, index + 1) else Long.MAX_VALUE / 2
        val lo = start + MIN_LINE_GAP_MS
        if (hi < lo) return cal
        val target = newEndMs.coerceIn(lo, hi)
        val base = (line.endMs ?: start) + cal.offsetMs
        val delta = target - base
        return putLine(
            cal.copy(lineCount = raw.size),
            index,
            (cal.lines[index] ?: LineShift()).copy(endDelta = delta),
        )
    }

    fun nudgeLineStart(raw: List<TimedLine>, cal: ScriptCalibration, index: Int, deltaMs: Long): ScriptCalibration =
        withLineStart(raw, cal, index, effectiveStart(raw, cal, index) + deltaMs)

    fun nudgeLineEnd(raw: List<TimedLine>, cal: ScriptCalibration, index: Int, deltaMs: Long): ScriptCalibration {
        val current = effectiveEnd(raw, cal, index) ?: return cal
        return withLineEnd(raw, cal, index, current + deltaMs)
    }

    /** 清掉某一句的微调。 */
    fun resetLine(cal: ScriptCalibration, index: Int): ScriptCalibration {
        if (!cal.lines.containsKey(index)) return cal
        return cal.copy(lines = cal.lines - index)
    }

    /** 清掉全部单句微调，保留全局偏移。 */
    fun resetLines(cal: ScriptCalibration): ScriptCalibration =
        if (cal.lines.isEmpty()) cal else cal.copy(lines = emptyMap())

    // ---------- 批量（区间）校准 ----------
    // 一集长台本逐句点太慢，而错位往往是「整段一起早/晚」：
    // 中间某个停顿没标时间，之后所有句子都平移同一个量。所以批量做的是「整段一起动」。

    /** 把 [from]..[to] 夹到有效行号并按先后排好；台本为空时返回 null。 */
    fun normalizeRange(lineCount: Int, from: Int, to: Int): IntRange? {
        if (lineCount <= 0) return null
        val a = from.coerceIn(0, lineCount - 1)
        val b = to.coerceIn(0, lineCount - 1)
        return if (a <= b) a..b else b..a
    }

    /** 区间内被单独微调过的句数（界面显示「已微调 N 句」用）。 */
    fun adjustedInRange(cal: ScriptCalibration, range: IntRange): Int =
        range.count { i -> cal.lines[i]?.isZero == false }

    /**
     * 把 [range] 这段句子**整段一起**平移 [deltaMs]。
     *
     * 与逐句 [nudgeLineStart] 的区别：逐句夹的是「这一句 vs 它的邻居」，
     * 整段平移夹的是「这一段 vs 区间外那一句」。如果错位是整段的，
     * 用逐句方式一段段点会一直被内部邻居挡住，用户按半天看不到变化。
     *
     * 位移还会被夹到「首句不早于 0」，并且**不会反向**：
     * 区间已经贴到边界时宁可不动，也不能因为夹取反而把句子推回去。
     */
    fun shiftRange(
        raw: List<TimedLine>,
        cal: ScriptCalibration,
        range: IntRange,
        deltaMs: Long,
    ): RangeShift {
        if (deltaMs == 0L || raw.isEmpty() || range.isEmpty()) return RangeShift(cal, 0L)
        val firstStart = effectiveStart(raw, cal, range.first)
        // 区间内最靠后的时间：通常是末句的终点，但 LRC 允许中间某句的终点更晚，取 max 更稳
        val lastTime = range.maxOf { i ->
            effectiveEnd(raw, cal, i) ?: effectiveStart(raw, cal, i)
        }
        var lo = -firstStart
        var hi = Long.MAX_VALUE / 2
        if (range.first > 0) {
            lo = maxOf(lo, effectiveStart(raw, cal, range.first - 1) + MIN_LINE_GAP_MS - firstStart)
        }
        if (range.last + 1 < raw.size) {
            hi = effectiveStart(raw, cal, range.last + 1) - MIN_LINE_GAP_MS - lastTime
        }
        if (hi < lo) return RangeShift(cal, 0L)
        val applied = if (deltaMs > 0) {
            minOf(deltaMs, hi).coerceAtLeast(0L)
        } else {
            maxOf(deltaMs, lo).coerceAtMost(0L)
        }
        if (applied == 0L) return RangeShift(cal, 0L)
        var next = cal.copy(lineCount = raw.size)
        for (i in range) {
            val old = next.lines[i] ?: LineShift()
            next = putLine(next, i, LineShift(old.startDelta + applied, old.endDelta + applied))
        }
        return RangeShift(next, applied)
    }

    /** 清掉区间内的单句微调，区间外原样保留。 */
    fun resetRange(cal: ScriptCalibration, range: IntRange): ScriptCalibration {
        if (cal.lines.keys.none { it in range }) return cal
        return cal.copy(lines = cal.lines.filterKeys { it !in range })
    }

    /** 全清（回到原始台本时间轴）。 */
    fun reset(cal: ScriptCalibration): ScriptCalibration = ScriptCalibration()

    private fun putLine(cal: ScriptCalibration, index: Int, shift: LineShift): ScriptCalibration =
        if (shift.isZero) cal.copy(lines = cal.lines - index) else cal.copy(lines = cal.lines + (index to shift))

    /** `+1.2 秒` / `−0.2 秒`（负号用真正的减号，等宽时不会看成连字符）/ `0.0 秒`。 */
    fun formatSignedSeconds(ms: Long): String {
        if (ms == 0L) return "0.0 秒"
        val abs = kotlin.math.abs(ms)
        val sign = if (ms > 0) "+" else "−"
        val text = if (abs % 1_000L == 0L) {
            String.format(Locale.US, "%.0f", abs / 1000.0)
        } else {
            String.format(Locale.US, "%.1f", abs / 1000.0)
        }
        return sign + text + " 秒"
    }

    /** 给按钮/状态条用的一句话摘要。 */
    fun describe(cal: ScriptCalibration): String {
        if (cal.isEmpty) return "未校准"
        val parts = ArrayList<String>(2)
        if (cal.offsetMs != 0L) parts.add("偏移 " + formatSignedSeconds(cal.offsetMs))
        val adjusted = cal.adjustedLineCount
        if (adjusted > 0) parts.add("微调 " + adjusted + " 句")
        return parts.joinToString(" · ")
    }

    /**
     * 匹配来源的置信度分级。计划书要求「显示台本匹配状态，避免把不确定匹配误认为准确结果」，
     * 所以这里不只是给个分数，而是给出用户能直接判断的等级。
     */
    enum class MatchConfidence(val label: String) {
        EXACT("精确匹配"),
        STRONG("较可靠"),
        LOOSE("低置信"),
        MANUAL("手动指定"),
        EMBEDDED("音频内嵌"),
        NONE("未匹配"),
    }

    fun confidenceOf(manual: Boolean, fromEmbedded: Boolean, score: Int): MatchConfidence = when {
        fromEmbedded -> MatchConfidence.EMBEDDED
        manual -> MatchConfidence.MANUAL
        score >= 880 -> MatchConfidence.EXACT
        score >= 700 -> MatchConfidence.STRONG
        score > 0 -> MatchConfidence.LOOSE
        else -> MatchConfidence.NONE
    }

    /** 低置信才提示「建议核对」，避免每个页面都在喊狼来了。 */
    fun confidenceHint(confidence: MatchConfidence, hasTimed: Boolean): String? = when {
        confidence == MatchConfidence.LOOSE -> "台本可能不是这一集，时间对不上时可用「校准」整体平移"
        confidence == MatchConfidence.EMBEDDED && !hasTimed -> "内嵌歌词没有时间轴，只能按比例跟随"
        confidence == MatchConfidence.NONE -> null
        !hasTimed -> "这份台本没有时间轴，无法逐句同步"
        else -> null
    }
}
