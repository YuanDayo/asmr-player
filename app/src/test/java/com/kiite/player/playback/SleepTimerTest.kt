package com.kiite.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 睡眠定时器纯逻辑测试。
 *
 * 重点覆盖计划书 5.1 里点名的「睡眠定时边界」：
 * 渐停区间起止、渐停时长为 0、渐停时长大于总时长、剩余时间归零时的音量。
 */
class SleepTimerTest {

    // ---------- volumeAt ----------

    @Test
    fun `不渐停时音量恒为满`() {
        assertEquals(1f, SleepMath.volumeAt(10_000L, 0L), 0f)
        assertEquals(1f, SleepMath.volumeAt(1L, 0L), 0f)
        assertEquals(1f, SleepMath.volumeAt(0L, 0L), 0f)
    }

    @Test
    fun `渐停区间之外保持满音量`() {
        // 剩 60 秒、渐停 10 秒：还没进渐停区
        assertEquals(1f, SleepMath.volumeAt(60_000L, 10_000L), 0f)
        // 正好站在渐停区入口
        assertEquals(1f, SleepMath.volumeAt(10_000L, 10_000L), 0f)
    }

    @Test
    fun `渐停区间内线性下降`() {
        assertEquals(0.5f, SleepMath.volumeAt(5_000L, 10_000L), 0.001f)
        assertEquals(0.1f, SleepMath.volumeAt(1_000L, 10_000L), 0.001f)
        assertEquals(0.9f, SleepMath.volumeAt(9_000L, 10_000L), 0.001f)
    }

    @Test
    fun `剩余时间归零时音量为零`() {
        assertEquals(0f, SleepMath.volumeAt(0L, 10_000L), 0f)
        // 负数不该出现，但出现了也不能算出超过 1 的值
        assertEquals(0f, SleepMath.volumeAt(-5_000L, 10_000L), 0f)
    }

    @Test
    fun `渐停时长大于总时长时一开始就在渐停`() {
        // 设 1 分钟定时、渐停 30 分钟：进入定时的那一刻就应当从 1/30 音量开始
        val v = SleepMath.volumeAt(60_000L, 1_800_000L)
        assertTrue("音量应小于 1，实际 $v", v < 1f)
        assertTrue("音量应大于 0，实际 $v", v > 0f)
    }

    @Test
    fun `音量永远不会越界`() {
        val samples = listOf(-1L, 0L, 1L, 999L, 10_000L, 999_999L)
        samples.forEach { rem ->
            listOf(0L, 1L, 10_000L).forEach { fade ->
                val v = SleepMath.volumeAt(rem, fade)
                assertTrue("remaining=$rem fade=$fade -> $v", v in 0f..1f)
            }
        }
    }

    // ---------- step ----------

    @Test
    fun `step 递推剩余时间并给出对应音量`() {
        val first = SleepMath.step(12_000L, 200L, 10_000L)
        assertEquals(11_800L, first.remainingMs)
        assertEquals(1f, first.volume, 0f)
        assertFalse(first.done)

        val inFade = SleepMath.step(10_000L, 200L, 10_000L)
        assertEquals(9_800L, inFade.remainingMs)
        assertEquals(0.98f, inFade.volume, 0.001f)
        assertFalse(inFade.done)
    }

    @Test
    fun `step 不会把剩余时间减成负数`() {
        val last = SleepMath.step(100L, 200L, 10_000L)
        assertEquals(0L, last.remainingMs)
        assertTrue(last.done)
    }

    @Test
    fun `step 在最后一步标出 done 且音量为零`() {
        var remaining = 1_000L
        var guard = 0
        while (!SleepMath.step(remaining, 200L, 1_000L).done) {
            remaining -= 200L
            guard++
            if (guard > 100) break
        }
        val end = SleepMath.step(remaining, 200L, 1_000L)
        assertTrue(end.done)
        assertEquals(0L, end.remainingMs)
        assertEquals(0f, end.volume, 0f)
    }

    // ---------- 显示 ----------

    @Test
    fun `剩余时间向上取整到秒`() {
        assertEquals("1:00", SleepMath.formatRemaining(60_000L))
        assertEquals("0:01", SleepMath.formatRemaining(400L))
        assertEquals("0:00", SleepMath.formatRemaining(0L))
        assertEquals("1:30", SleepMath.formatRemaining(89_100L))
        assertEquals("90:00", SleepMath.formatRemaining(5_400_000L))
    }

    @Test
    fun `渐停文案`() {
        assertEquals("不渐停", SleepMath.fadeLabel(0L))
        assertEquals("10 秒渐停", SleepMath.fadeLabel(10_000L))
        assertEquals("30 秒渐停", SleepMath.fadeLabel(30_000L))
    }

    // ---------- 状态与按钮文案 ----------

    @Test
    fun `未开启时不计为 active`() {
        assertFalse(SleepState.IDLE.active)
        assertFalse(SleepState.IDLE.fading)
    }

    @Test
    fun `fading 只在倒计时进入渐停区时为真`() {
        val inFade = SleepState(SleepStopMode.DURATION, 4_000L, 600_000L, 10_000L)
        assertTrue(inFade.fading)

        val beforeFade = SleepState(SleepStopMode.DURATION, 60_000L, 600_000L, 10_000L)
        assertFalse(beforeFade.fading)

        val noFade = SleepState(SleepStopMode.DURATION, 1_000L, 600_000L, 0L)
        assertFalse(noFade.fading)

        // 「播完本章停」不做渐停，永远不算 fading
        val itemEnd = SleepState(SleepStopMode.END_OF_TRACK, 0L, 0L, 10_000L)
        assertTrue(itemEnd.active)
        assertFalse(itemEnd.fading)
    }

    @Test
    fun `按钮文案随模式变化`() {
        assertEquals("定时停止", SleepMath.buttonLabel(SleepState.IDLE))
        assertEquals(
            "定时 9:30",
            SleepMath.buttonLabel(SleepState(SleepStopMode.DURATION, 570_000L, 600_000L, 0L)),
        )
        assertEquals(
            "渐停 0:08",
            SleepMath.buttonLabel(SleepState(SleepStopMode.DURATION, 8_000L, 600_000L, 10_000L)),
        )
        assertEquals(
            "本章后停",
            SleepMath.buttonLabel(SleepState(SleepStopMode.END_OF_TRACK)),
        )
        assertEquals(
            "本作品后停",
            SleepMath.buttonLabel(SleepState(SleepStopMode.END_OF_WORK)),
        )
    }

    @Test
    fun `档位表非空且渐停档位包含关闭项`() {
        assertTrue(SleepMath.MINUTE_OPTIONS.isNotEmpty())
        assertEquals(0L, SleepMath.FADE_OPTIONS_MS.first())
        assertTrue(SleepMath.FADE_OPTIONS_MS.all { it >= 0L })
    }
}
