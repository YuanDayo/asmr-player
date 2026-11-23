package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 桌面歌词浮窗的几何算术。
 *
 * 这里钉的是「浮窗不会再丢」的判据：越界坐标必须被拉回可视区、状态栏区域压不进去、
 * 松手贴边之后仍然合法；以及「还剩几秒」在句末 / 无终点时不会给出荒唐值。
 */
class OverlayGeometryTest {

    private val screenW = 1080
    private val screenH = 2400
    private val w = 660
    private val h = 264

    @Test
    fun `可视区内的位置原样返回`() {
        val p = OverlayGeometry.clamp(60, 400, screenW, screenH, w, h)
        assertEquals(60, p.x)
        assertEquals(400, p.y)
    }

    @Test
    fun `负坐标拉回原点`() {
        val p = OverlayGeometry.clamp(-300, -50, screenW, screenH, w, h)
        assertEquals(0, p.x)
        assertEquals(0, p.y)
    }

    @Test
    fun `超出右下角时贴住右边缘与下边缘`() {
        val p = OverlayGeometry.clamp(99_999, 99_999, screenW, screenH, w, h)
        assertEquals(screenW - w, p.x)
        assertEquals(screenH - h, p.y)
    }

    @Test
    fun `窗口比屏幕还宽时贴左沿而不是抛异常`() {
        // 横向放不下（maxX = 0），纵向还剩 100：x 贴 0、y 收到 100，绝不能因为 max<min 崩掉
        val p = OverlayGeometry.clamp(500, 500, 400, 300, 800, 200)
        assertEquals(0, p.x)
        assertEquals(100, p.y)
    }

    @Test
    fun `状态栏与刘海区域压不进去`() {
        // y 原本要落在状态栏里（0..120），钳制后必须从安全区下沿开始
        val p = OverlayGeometry.clamp(100, 10, screenW, screenH, w, h, safeTop = 120)
        assertEquals(120, p.y)
        assertEquals(100, p.x)
    }

    @Test
    fun `窗口比可用高度更高时贴住上边界`() {
        // 2400 - 2300 = 100 已经比安全区 120 还小：这时没了「安全区」，只能贴着屏幕上沿
        // （宁可被状态栏压一点，也不能把窗口算到屏幕外）
        val p = OverlayGeometry.clamp(0, 999, screenW, screenH, w, 2300, safeTop = 120)
        assertEquals(100, p.y)
    }

    @Test
    fun `松手时靠近左右边缘就吸附过去`() {
        assertEquals(0, OverlayGeometry.snapToEdge(5, screenW, w, 30))
        assertEquals(screenW - w, OverlayGeometry.snapToEdge(screenW - w - 5, screenW, w, 30))
        // 中间位置不动
        assertEquals(200, OverlayGeometry.snapToEdge(200, screenW, w, 30))
        // 阈值关掉（0）时不吸附
        assertEquals(5, OverlayGeometry.snapToEdge(5, screenW, w, 0))
    }

    @Test
    fun `单句进度在区间内线性、区间外夹住`() {
        assertEquals(0f, OverlayGeometry.lineProgress(1_000, 1_000, 5_000)!!, 0.001f)
        assertEquals(0.5f, OverlayGeometry.lineProgress(3_000, 1_000, 5_000)!!, 0.001f)
        assertEquals(1f, OverlayGeometry.lineProgress(9_000, 1_000, 5_000)!!, 0.001f)
    }

    @Test
    fun `没有结束时间就不画进度`() {
        assertNull(OverlayGeometry.lineProgress(3_000, 1_000, null))
        // 终点早于起点（脏数据）同样不画，避免除零
        assertNull(OverlayGeometry.lineProgress(3_000, 1_000, 1_000))
    }

    @Test
    fun `剩余秒数向上取整，过点归零`() {
        assertEquals(4L, OverlayGeometry.remainingSec(1_000, 5_000))
        assertEquals(4L, OverlayGeometry.remainingSec(1_001, 5_000))
        assertEquals(0L, OverlayGeometry.remainingSec(5_000, 5_000))
        assertEquals(0L, OverlayGeometry.remainingSec(5_500, 5_000))
        assertNull(OverlayGeometry.remainingSec(5_500, null))
    }
}
