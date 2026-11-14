package com.kiite.player.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 台本时间轴校准的纯逻辑测试。
 *
 * 出错的表现都很安静：整份台词偏 800ms、最后一句被上一句吞掉、
 * 台本换了之后微调落到错误的句子上。所以边界都单独钉一遍。
 */
class ScriptCalibrationTest {

    private fun line(start: Long, end: Long? = null, text: String = "t") = TimedLine(start, end, text)

    /** 三句：0s / 10s / 20s，每句长 10 秒。 */
    private val raw = listOf(
        line(0, 10_000, "第一句"),
        line(10_000, 20_000, "第二句"),
        line(20_000, null, "第三句"),
    )

    // ---------- 全局偏移 ----------

    @Test
    fun `没有校准时原样返回`() {
        val applied = CalibrationMath.apply(raw, ScriptCalibration())
        assertEquals(raw, applied)
    }

    @Test
    fun `正偏移把整份台本一起往后推`() {
        val cal = ScriptCalibration(offsetMs = 1_500L, lineCount = 3)
        val applied = CalibrationMath.apply(raw, cal)
        assertEquals(1_500L, applied[0].startMs)
        assertEquals(11_500L, applied[1].startMs)
        assertEquals(21_500L, applied[2].startMs)
        // 终点也要跟着走，否则每句的有效区间会变短
        assertEquals(11_500L, applied[0].endMs)
    }

    @Test
    fun `负偏移不会把第一句推到负数`() {
        val cal = ScriptCalibration(offsetMs = -5_000L, lineCount = 3)
        val applied = CalibrationMath.apply(raw, cal)
        assertEquals(0L, applied[0].startMs)
        assertEquals(5_000L, applied[1].startMs)
    }

    @Test
    fun `偏移被限制在正负两分钟内`() {
        assertEquals(
            CalibrationMath.MAX_OFFSET_MS,
            CalibrationMath.setOffset(ScriptCalibration(), 999_999L, 3).offsetMs,
        )
        assertEquals(
            -CalibrationMath.MAX_OFFSET_MS,
            CalibrationMath.setOffset(ScriptCalibration(), -999_999L, 3).offsetMs,
        )
    }

    @Test
    fun `偏移步进累加后仍然被夹住`() {
        val cal = ScriptCalibration(offsetMs = 119_000L, lineCount = 3)
        val next = CalibrationMath.shiftOffset(cal, 5_000L, 3)
        assertEquals(CalibrationMath.MAX_OFFSET_MS, next.offsetMs)
    }

    // ---------- 单句微调 ----------

    @Test
    fun `单句起点可以单独往后挪`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 1, 12_000L)
        assertEquals(2_000L, cal.lines[1]?.startDelta)
        val applied = CalibrationMath.apply(raw, cal)
        assertEquals(12_000L, applied[1].startMs)
        assertEquals(0L, applied[0].startMs)
        assertEquals(20_000L, applied[2].startMs)
    }

    @Test
    fun `单句起点不能越过上一句`() {
        // 想把第二句挪到 0s，会撞上第一句（起点 0s），夹到 0s + 100ms
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 1, 0L)
        assertEquals(100L, CalibrationMath.effectiveStart(raw, cal, 1))
    }

    @Test
    fun `单句起点不能压到下一句`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 1, 25_000L)
        // 下一句在 20s，最多只能到 19.9s
        assertEquals(19_900L, CalibrationMath.effectiveStart(raw, cal, 1))
    }

    @Test
    fun `最后一句往后挪不受下一句限制但不会变负`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 2, 60_000L)
        assertEquals(60_000L, CalibrationMath.effectiveStart(raw, cal, 2))
        // 往回挪最多到上一句之后，不会越到第二句上面
        val back = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 2, -9_000L)
        assertEquals(10_100L, CalibrationMath.effectiveStart(raw, back, 2))
    }

    @Test
    fun `第一句往回挪不会变负`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 0, -3_000L)
        assertEquals(0L, CalibrationMath.effectiveStart(raw, cal, 0))
    }

    @Test
    fun `微调到零增量时把这一行从记录里剔掉`() {
        val shifted = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 1, 12_000L)
        assertTrue(shifted.lines.containsKey(1))
        val back = CalibrationMath.withLineStart(raw, shifted, 1, 10_000L)
        assertFalse(back.lines.containsKey(1))
    }

    @Test
    fun `单句终点可以设到当前位置`() {
        val cal = CalibrationMath.withLineEnd(raw, ScriptCalibration(lineCount = 3), 0, 8_000L)
        assertEquals(8_000L, CalibrationMath.effectiveEnd(raw, cal, 0))
    }

    @Test
    fun `单句终点不能早于起点`() {
        val cal = CalibrationMath.withLineEnd(raw, ScriptCalibration(lineCount = 3), 0, -1_000L)
        assertEquals(
            CalibrationMath.effectiveStart(raw, cal, 0) + CalibrationMath.MIN_LINE_GAP_MS,
            CalibrationMath.effectiveEnd(raw, cal, 0),
        )
    }

    @Test
    fun `终点微调作用在原文终点上`() {
        val base = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 1, 12_000L)
        val cal = CalibrationMath.nudgeLineEnd(raw, base, 1, -500L)
        // 第二句原文终点（= 第三句起点）20s，减 500ms
        assertEquals(19_500L, CalibrationMath.effectiveEnd(raw, cal, 1))
    }

    @Test
    fun `终点不会越过下一句起点`() {
        // 原文终点本来就等于下一句起点，再往后推只能停在原地，不能压到下一句头上
        val cal = CalibrationMath.nudgeLineEnd(raw, ScriptCalibration(lineCount = 3), 1, 500L)
        assertEquals(20_000L, CalibrationMath.effectiveEnd(raw, cal, 1))
        assertEquals(20_000L, CalibrationMath.effectiveStart(raw, cal, 2))
    }

    @Test
    fun `没有终点的句子做终点微调时原样返回`() {
        val cal = CalibrationMath.nudgeLineEnd(raw, ScriptCalibration(lineCount = 3), 2, 500L)
        assertTrue(cal.isEmpty)
    }

    // ---------- 与全局偏移的组合 ----------

    @Test
    fun `调整全局偏移后单句微调跟着一起平移`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 1, 12_000L)
        val shifted = CalibrationMath.shiftOffset(cal, 1_000L, 3)
        // 原文 10s + 全局 1s + 本句 +2s
        assertEquals(13_000L, CalibrationMath.effectiveStart(raw, shifted, 1))
    }

    @Test
    fun `偏移与微调同时存在时摘要说得清楚`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(offsetMs = 1_000L, lineCount = 3), 1, 14_000L)
        assertEquals("偏移 +1 秒 · 微调 1 句", CalibrationMath.describe(cal))
    }

    @Test
    fun `未校准的摘要`() {
        assertEquals("未校准", CalibrationMath.describe(ScriptCalibration()))
    }

    @Test
    fun `带小数的偏移显示到一位小数`() {
        assertEquals("+1.5 秒", CalibrationMath.formatSignedSeconds(1_500L))
        assertEquals("−0.2 秒", CalibrationMath.formatSignedSeconds(-200L))
        assertEquals("0.0 秒", CalibrationMath.formatSignedSeconds(0L))
        assertEquals("+2 秒", CalibrationMath.formatSignedSeconds(2_000L))
    }

    // ---------- 重置 ----------

    @Test
    fun `重置单句保留全局偏移`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(offsetMs = 1_000L, lineCount = 3), 1, 12_000L)
        val reset = CalibrationMath.resetLine(cal, 1)
        assertEquals(1_000L, reset.offsetMs)
        assertTrue(reset.lines.isEmpty())
    }

    @Test
    fun `全部重置回到空状态`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(offsetMs = 1_000L, lineCount = 3), 1, 12_000L)
        assertTrue(CalibrationMath.reset(cal).isEmpty)
    }

    // ---------- 台本被换掉 ----------

    @Test
    fun `行数变化时丢弃单句微调但保留全局偏移`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(offsetMs = 2_000L, lineCount = 3), 1, 14_000L)
        assertTrue(CalibrationMath.isStale(cal, 5))
        val pruned = CalibrationMath.pruneStale(cal, 5)
        assertEquals(2_000L, pruned.offsetMs)
        assertTrue(pruned.lines.isEmpty())
        assertEquals(5, pruned.lineCount)
    }

    @Test
    fun `行数没变不算失效`() {
        val cal = CalibrationMath.withLineStart(raw, ScriptCalibration(lineCount = 3), 1, 12_000L)
        assertFalse(CalibrationMath.isStale(cal, 3))
    }

    @Test
    fun `空校准不会被当成失效`() {
        assertFalse(CalibrationMath.isStale(ScriptCalibration(), 999))
    }

    // ---------- 序列化 ----------

    @Test
    fun `校准能往返序列化`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val cal = ScriptCalibration(
            offsetMs = -1_200L,
            lineCount = 3,
            lines = mapOf(1 to LineShift(2_000L, -300L)),
        )
        val text = json.encodeToString(cal)
        val back = json.decodeFromString<ScriptCalibration>(text)
        assertEquals(cal, back)
        // 键是行号，读回来必须还是数字键（JSON 里是字符串，靠序列化器转回去）
        assertEquals(2_000L, back.lines[1]?.startDelta)
    }

    @Test
    fun `缺字段的旧数据能读出来`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val back = json.decodeFromString<ScriptCalibration>("""{"offsetMs":800}""")
        assertEquals(800L, back.offsetMs)
        assertEquals(0, back.lineCount)
        assertTrue(back.lines.isEmpty())
    }

    // ---------- 匹配置信度 ----------

    @Test
    fun `置信度分级`() {
        assertEquals(
            CalibrationMath.MatchConfidence.EXACT,
            CalibrationMath.confidenceOf(manual = false, fromEmbedded = false, score = 880),
        )
        assertEquals(
            CalibrationMath.MatchConfidence.STRONG,
            CalibrationMath.confidenceOf(manual = false, fromEmbedded = false, score = 700),
        )
        assertEquals(
            CalibrationMath.MatchConfidence.LOOSE,
            CalibrationMath.confidenceOf(manual = false, fromEmbedded = false, score = 401),
        )
        assertEquals(
            CalibrationMath.MatchConfidence.MANUAL,
            CalibrationMath.confidenceOf(manual = true, fromEmbedded = false, score = 0),
        )
        assertEquals(
            CalibrationMath.MatchConfidence.EMBEDDED,
            CalibrationMath.confidenceOf(manual = true, fromEmbedded = true, score = 1000),
        )
        assertEquals(
            CalibrationMath.MatchConfidence.NONE,
            CalibrationMath.confidenceOf(manual = false, fromEmbedded = false, score = 0),
        )
    }

    @Test
    fun `只有低置信才提示核对`() {
        assertNull(
            CalibrationMath.confidenceHint(CalibrationMath.MatchConfidence.EXACT, hasTimed = true),
        )
        assertEquals(
            "台本可能不是这一集，时间对不上时可用「校准」整体平移",
            CalibrationMath.confidenceHint(CalibrationMath.MatchConfidence.LOOSE, hasTimed = true),
        )
        assertEquals(
            "这份台本没有时间轴，无法逐句同步",
            CalibrationMath.confidenceHint(CalibrationMath.MatchConfidence.EXACT, hasTimed = false),
        )
    }

    // ---------- 应用到 ParsedScript ----------

    @Test
    fun `applyToScript 只动时间轴不动正文`() {
        val parsed = ParsedScript(ScriptFormat.LRC, "第一句\n第二句", raw)
        val applied = CalibrationMath.applyToScript(parsed, ScriptCalibration(offsetMs = 500L, lineCount = 3))
        assertEquals(500L, applied.timedLines[0].startMs)
        assertEquals("第一句\n第二句", applied.plainText)
    }

    @Test
    fun `没有时间轴的台本不受校准影响`() {
        val parsed = ParsedScript(ScriptFormat.TXT, "纯文本")
        val applied = CalibrationMath.applyToScript(parsed, ScriptCalibration(offsetMs = 500L, lineCount = 0))
        assertEquals(parsed, applied)
    }

    @Test
    fun `乱序的微调读回来仍然单调不减`() {
        // 手工构造一份「第一句被推到第三句之后」的脏数据
        val cal = ScriptCalibration(lineCount = 3, lines = mapOf(0 to LineShift(startDelta = 30_000L)))
        val applied = CalibrationMath.apply(raw, cal)
        assertEquals(30_000L, applied[0].startMs)
        // 后两句被顶到这个起点之后，不会出现「当前句往回跳」
        assertTrue(applied[1].startMs >= applied[0].startMs)
        assertTrue(applied[2].startMs >= applied[1].startMs)
    }
}
