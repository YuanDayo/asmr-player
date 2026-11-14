package com.kiite.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-B 区间循环纯逻辑测试。
 *
 * 这里是「循环区间边界」最容易出错的地方：A 越过 B、B 越过曲尾、
 * 区间短到会让播放器疯狂 seek、以及微调到 0 或负值。
 */
class AbLoopTest {

    private val duration = 60_000L

    // ---------- 设置 A 点 ----------

    @Test
    fun `未设置时设 A 点只留下起点`() {
        val s = AbLoopMath.setStart(AbState.IDLE, 12_000L, duration)
        assertEquals(12_000L, s.startMs)
        assertNull(s.endMs)
        assertFalse(s.enabled)
        assertTrue(s.waitingForEnd)
        assertFalse(s.complete)
    }

    @Test
    fun `重设 A 点时若原 B 仍然够远就保留 B`() {
        val before = AbState(startMs = 1_000L, endMs = 20_000L, enabled = true)
        val after = AbLoopMath.setStart(before, 12_000L, duration)
        assertEquals(12_000L, after.startMs)
        assertEquals(20_000L, after.endMs)
        assertTrue("保留 B 时不应停掉循环", after.enabled)
    }

    @Test
    fun `重设 A 点贴到 B 上会连 B 一起清掉`() {
        val before = AbState(startMs = 1_000L, endMs = 20_000L, enabled = true)
        val after = AbLoopMath.setStart(before, 19_900L, duration)
        assertEquals(19_900L, after.startMs)
        assertNull(after.endMs)
        assertFalse(after.enabled)
    }

    @Test
    fun `负的播放位置会被夹到 0`() {
        val s = AbLoopMath.setStart(AbState.IDLE, -5_000L, duration)
        assertEquals(0L, s.startMs)
    }

    @Test
    fun `超过曲目时长的位置会被夹到时长`() {
        val s = AbLoopMath.setStart(AbState.IDLE, 99_000L, duration)
        assertEquals(duration, s.startMs)
    }

    // ---------- 设置 B 点 ----------

    @Test
    fun `没有 A 点时设 B 点被拒绝`() {
        assertNull(AbLoopMath.setEnd(AbState.IDLE, 20_000L, duration))
    }

    @Test
    fun `B 与 A 的间隔不足时被拒绝`() {
        val s = AbState(startMs = 10_000L)
        assertNull(AbLoopMath.setEnd(s, 10_100L, duration))
        assertNull(AbLoopMath.setEnd(s, 9_000L, duration))
        assertEquals("被拒绝时不能改动原状态", 10_000L, s.startMs)
    }

    @Test
    fun `间隔正好等于下限时接受`() {
        val s = AbState(startMs = 10_000L)
        val after = AbLoopMath.setEnd(s, 10_000L + AbLoopMath.MIN_GAP_MS, duration)
        assertEquals(10_000L + AbLoopMath.MIN_GAP_MS, after?.endMs)
    }

    @Test
    fun `设好 B 点后自动开始循环`() {
        val s = AbLoopMath.setEnd(AbState(startMs = 10_000L), 20_000L, duration)
        assertEquals(20_000L, s?.endMs)
        assertTrue(s!!.enabled)
        assertTrue(s.complete)
        assertTrue(s.active)
        assertEquals(10_000L, s.range?.durationMs)
    }

    // ---------- 微调 ----------

    @Test
    fun `A 点向左微调不会低于 0`() {
        val s = AbState(startMs = 1_000L, endMs = 20_000L)
        assertEquals(0L, AbLoopMath.nudgeStart(s, -2_000L).startMs)
    }

    @Test
    fun `A 点向右微调不会压到 B 点`() {
        val s = AbState(startMs = 19_000L, endMs = 20_000L)
        val after = AbLoopMath.nudgeStart(s, 1_000L)
        assertEquals(20_000L - AbLoopMath.MIN_GAP_MS, after.startMs)
    }

    @Test
    fun `B 点向左微调不会压到 A 点`() {
        val s = AbState(startMs = 10_000L, endMs = 11_000L)
        val after = AbLoopMath.nudgeEnd(s, -5_000L, duration)
        assertEquals(10_000L + AbLoopMath.MIN_GAP_MS, after.endMs)
    }

    @Test
    fun `B 点向右微调不会超过曲目时长`() {
        val s = AbState(startMs = 10_000L, endMs = 59_000L)
        val after = AbLoopMath.nudgeEnd(s, 5_000L, duration)
        assertEquals(duration, after.endMs)
    }

    @Test
    fun `时长未知时微调 B 点不设上限`() {
        val s = AbState(startMs = 10_000L, endMs = 20_000L)
        val after = AbLoopMath.nudgeEnd(s, 1_000L, 0L)
        assertEquals(21_000L, after.endMs)
    }

    @Test
    fun `没设点位时微调是空操作`() {
        assertEquals(AbState.IDLE, AbLoopMath.nudgeStart(AbState.IDLE, 1_000L))
        assertEquals(AbState.IDLE, AbLoopMath.nudgeEnd(AbState.IDLE, 1_000L, duration))
    }

    // ---------- 跳回判定 ----------

    @Test
    fun `位置到达 B 点就要跳回`() {
        val range = AbRange(10_000L, 20_000L)
        assertTrue(AbLoopMath.shouldJumpBack(20_000L, range))
        assertTrue(AbLoopMath.shouldJumpBack(20_050L, range))
        assertFalse(AbLoopMath.shouldJumpBack(19_999L, range))
    }

    @Test
    fun `没有区间或区间无效时不跳`() {
        assertFalse(AbLoopMath.shouldJumpBack(30_000L, null))
        // B 贴着 A，属于无效区间
        assertFalse(AbLoopMath.shouldJumpBack(30_000L, AbRange(10_000L, 10_050L)))
    }

    // ---------- 显示 ----------

    @Test
    fun `秒数保留到 0 点 1 秒`() {
        assertEquals("0:12.4", AbLoopMath.formatClock(12_400L))
        assertEquals("0:00.0", AbLoopMath.formatClock(0L))
        assertEquals("1:01.5", AbLoopMath.formatClock(61_500L))
        assertEquals("1:30.0", AbLoopMath.formatClock(90_000L))
        // 负数不该出现，但也不能显示成 0:-1
        assertEquals("0:00.0", AbLoopMath.formatClock(-1_000L))
    }

    @Test
    fun `按钮文案随设置进度变化`() {
        assertEquals("A-B", AbLoopMath.buttonLabel(AbState.IDLE))
        assertEquals(
            "设 B",
            AbLoopMath.buttonLabel(AbState(startMs = 10_000L)),
        )
        assertEquals(
            "A-B 循环",
            AbLoopMath.buttonLabel(AbState(startMs = 10_000L, endMs = 20_000L, enabled = true)),
        )
        assertEquals(
            "A-B 暂停",
            AbLoopMath.buttonLabel(AbState(startMs = 10_000L, endMs = 20_000L, enabled = false)),
        )
    }

    @Test
    fun `状态说明文案覆盖四种阶段`() {
        assertEquals("未设置区间", AbLoopMath.statusLabel(AbState.IDLE))
        assertTrue(AbLoopMath.statusLabel(AbState(startMs = 1_000L)).contains("A 点"))
        assertTrue(AbLoopMath.statusLabel(AbState(startMs = 1_000L, endMs = 1_100L)).contains("太短"))
        assertTrue(
            AbLoopMath.statusLabel(AbState(1_000L, 2_000L, true)).startsWith("循环中"),
        )
        assertTrue(
            AbLoopMath.statusLabel(AbState(1_000L, 2_000L, false)).startsWith("已暂停"),
        )
    }
}
