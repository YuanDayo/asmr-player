package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批量（区间）校准的纯逻辑测试。
 *
 * 批量做的事和逐句不同：逐句是「这一句相对邻居还能动多少」，
 * 批量是「这一段整体相对区间外那两句还能动多少」。两者的夹取边界容易写混，
 * 表现出来的却是「按了没反应」或「一按就冲到隔壁句子上」，所以每条边界都单钉一遍。
 */
class BatchCalibrationTest {

    private fun line(start: Long, end: Long? = null, text: String = "t") = TimedLine(start, end, text)

    /** 三句，中间留 2 秒空隙：0–8s / 10–18s / 20s 起（末句无终点）。 */
    private val raw = listOf(
        line(0, 8_000, "第一句"),
        line(10_000, 18_000, "第二句"),
        line(20_000, null, "第三句"),
    )

    // ---------- 范围归一 ----------

    @Test
    fun `起止写反也按先后处理`() {
        assertEquals(1..3, CalibrationMath.normalizeRange(5, 3, 1))
    }

    @Test
    fun `越界行号被夹到有效范围`() {
        assertEquals(0..2, CalibrationMath.normalizeRange(3, -9, 99))
    }

    @Test
    fun `空台本没有范围`() {
        assertNull(CalibrationMath.normalizeRange(0, 0, 0))
    }

    // ---------- 整段平移 ----------

    @Test
    fun `整段往后平移只影响区间内`() {
        val out = CalibrationMath.shiftRange(raw, ScriptCalibration(lineCount = 3), 0..1, 500L)
        assertEquals(500L, out.appliedMs)
        val applied = CalibrationMath.apply(raw, out.calibration)
        assertEquals(500L, applied[0].startMs)
        assertEquals(10_500L, applied[1].startMs)
        // 区间外的第三句一动不动
        assertEquals(20_000L, applied[2].startMs)
    }

    @Test
    fun `终点跟着一起走，句子长度不变`() {
        val out = CalibrationMath.shiftRange(raw, ScriptCalibration(lineCount = 3), 1..1, 300L)
        val applied = CalibrationMath.apply(raw, out.calibration)
        assertEquals(10_300L, applied[1].startMs)
        assertEquals(18_300L, applied[1].endMs)
    }

    @Test
    fun `区间内的相对关系保持不变`() {
        // 第二句先被单独微调 +200，整段再平移 +300：两句的差仍是 200
        val cal = ScriptCalibration(lineCount = 3, lines = mapOf(1 to LineShift(startDelta = 200L)))
        val out = CalibrationMath.shiftRange(raw, cal, 0..1, 300L)
        val applied = CalibrationMath.apply(raw, out.calibration)
        assertEquals(300L, applied[0].startMs)
        assertEquals(10_500L, applied[1].startMs)
    }

    @Test
    fun `往后平移被下一句挡住时只走到边界`() {
        // 区间末句终点 8s，下一句 10s 起 → 最多再往后 1.9 秒（还要留 100ms 间隔）
        val out = CalibrationMath.shiftRange(raw, ScriptCalibration(lineCount = 3), 0..0, 5_000L)
        assertEquals(1_900L, out.appliedMs)
        val applied = CalibrationMath.apply(raw, out.calibration)
        assertEquals(1_900L, applied[0].startMs)
        assertTrue(applied[0].startMs + CalibrationMath.MIN_LINE_GAP_MS <= applied[1].startMs)
    }

    @Test
    fun `往前平移被上一句挡住时只走到边界`() {
        // 第二句 10s，第一句 0s（还需留 100ms）→ 最多往前 9.9 秒
        val out = CalibrationMath.shiftRange(raw, ScriptCalibration(lineCount = 3), 1..1, -50_000L)
        assertEquals(-9_900L, out.appliedMs)
        val applied = CalibrationMath.apply(raw, out.calibration)
        assertEquals(100L, applied[1].startMs)
    }

    @Test
    fun `第一句往前平移不会变成负数`() {
        // 第一句起点 0，已经贴到音频开头
        val out = CalibrationMath.shiftRange(raw, ScriptCalibration(lineCount = 3), 0..0, -3_000L)
        assertEquals(0L, out.appliedMs)
        assertEquals(0L, CalibrationMath.apply(raw, out.calibration)[0].startMs)
    }

    @Test
    fun `贴到边界时不会因为夹取反向移动`() {
        // 三句首尾相接、区间已无空间：请求往后，绝不能变成往回推
        val tight = listOf(line(0, 5_000), line(5_000, 10_000), line(10_000, null))
        val out = CalibrationMath.shiftRange(tight, ScriptCalibration(lineCount = 3), 0..0, 1_000L)
        assertEquals(0L, out.appliedMs)
        assertEquals(tight, CalibrationMath.apply(tight, out.calibration))
    }

    @Test
    fun `整份台本一起平移等价于全局偏移`() {
        val out = CalibrationMath.shiftRange(raw, ScriptCalibration(lineCount = 3), 0..2, 700L)
        assertEquals(700L, out.appliedMs)
        val byRange = CalibrationMath.apply(raw, out.calibration)
        val byOffset = CalibrationMath.apply(raw, ScriptCalibration(offsetMs = 700L, lineCount = 3))
        assertEquals(byOffset, byRange)
    }

    @Test
    fun `与已有全局偏移叠加`() {
        val cal = ScriptCalibration(offsetMs = 1_000L, lineCount = 3)
        val out = CalibrationMath.shiftRange(raw, cal, 2..2, 400L)
        val applied = CalibrationMath.apply(raw, out.calibration)
        assertEquals(21_400L, applied[2].startMs)
        // 全局偏移本身不被批量改写，两者是叠加关系
        assertEquals(1_000L, out.calibration.offsetMs)
    }

    @Test
    fun `反复平移是累加的`() {
        var cal = ScriptCalibration(lineCount = 3)
        repeat(3) {
            cal = CalibrationMath.shiftRange(raw, cal, 0..1, 100L).calibration
        }
        assertEquals(300L, CalibrationMath.apply(raw, cal)[0].startMs)
    }

    @Test
    fun `平移后时间轴仍然单调不减`() {
        val tight = listOf(line(0, 3_000), line(3_000, 6_000), line(6_000, null))
        var cal = ScriptCalibration(lineCount = 3)
        cal = CalibrationMath.shiftRange(tight, cal, 0..1, 500L).calibration
        cal = CalibrationMath.shiftRange(tight, cal, 1..2, -500L).calibration
        val applied = CalibrationMath.apply(tight, cal)
        assertTrue(applied[0].startMs <= applied[1].startMs)
        assertTrue(applied[1].startMs <= applied[2].startMs)
    }

    @Test
    fun `位移为零时原样返回`() {
        val cal = ScriptCalibration(lineCount = 3)
        assertEquals(cal, CalibrationMath.shiftRange(raw, cal, 0..2, 0L).calibration)
    }

    // ---------- 清除区间 ----------

    @Test
    fun `清除只影响区间内的微调`() {
        val cal = ScriptCalibration(
            lineCount = 3,
            lines = mapOf(
                0 to LineShift(startDelta = 100L),
                1 to LineShift(startDelta = 200L),
                2 to LineShift(startDelta = 300L),
            ),
        )
        val next = CalibrationMath.resetRange(cal, 0..1)
        assertEquals(setOf(2), next.lines.keys)
        assertEquals(0L, CalibrationMath.apply(raw, next)[1].startMs - raw[1].startMs)
    }

    @Test
    fun `清除区间不影响全局偏移`() {
        val cal = ScriptCalibration(offsetMs = 900L, lineCount = 3, lines = mapOf(0 to LineShift(startDelta = 100L)))
        val next = CalibrationMath.resetRange(cal, 0..0)
        assertEquals(900L, next.offsetMs)
        assertTrue(next.lines.isEmpty())
    }

    @Test
    fun `清除没有微调的区间不改动数据`() {
        val cal = ScriptCalibration(offsetMs = 900L, lineCount = 3)
        assertEquals(cal, CalibrationMath.resetRange(cal, 0..2))
    }

    @Test
    fun `统计区间内已微调的句数`() {
        val cal = ScriptCalibration(
            lineCount = 3,
            lines = mapOf(0 to LineShift(startDelta = 100L), 2 to LineShift(startDelta = 50L)),
        )
        assertEquals(1, CalibrationMath.adjustedInRange(cal, 0..1))
        assertEquals(2, CalibrationMath.adjustedInRange(cal, 0..2))
        assertEquals(0, CalibrationMath.adjustedInRange(cal, 1..1))
    }
}
