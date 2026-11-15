package com.kiite.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 下载进度换算的单元测试：进度条 / 百分比 / 速度 / 剩余时间都是纯计算，
 * 算错会让界面显示「99% 卡住」或「已下 0 字节」这类假象。
 */
class DownloadStateTest {

    private fun st(
        running: Boolean = true,
        completed: Boolean = false,
        index: Int = 0,
        total: Int = 0,
        fileDone: Long = 0L,
        fileTotal: Long = -1L,
        speed: Long = 0L,
    ) = AsmrOneController.DownloadState(
        workId = 1L,
        title = "RJ123456 测试",
        status = "s",
        running = running,
        completed = completed,
        index = index,
        total = total,
        fileName = "01.mp3",
        fileDoneBytes = fileDone,
        fileTotalBytes = fileTotal,
        speedBps = speed,
    )

    @Test
    fun `progress combines finished files and current file fraction`() {
        // 共 4 个文件，第 2 个下了一半 → (1 + 0.5) / 4 = 37.5%
        val s = st(index = 2, total = 4, fileDone = 50, fileTotal = 100)
        assertEquals(0.375f, s.progress, 0.0001f)
        assertEquals(37, s.percent)
    }

    @Test
    fun `progress is zero before first file starts`() {
        assertEquals(0f, st(index = 0, total = 10).progress, 0.0001f)
    }

    @Test
    fun `completed download shows full bar`() {
        val s = st(running = false, completed = true, index = 3, total = 3, fileDone = 10, fileTotal = 10)
        assertEquals(1f, s.progress, 0.0001f)
        assertEquals(100, s.percent)
    }

    @Test
    fun `unknown content length still keeps progress monotonic within file`() {
        // 服务端没给 Content-Length：当前文件按极小进度计，不会跳到 100%
        val s = st(index = 1, total = 4, fileDone = 999, fileTotal = -1)
        val p = s.progress
        assertEquals(true, p in 0f..0.25f)
    }

    @Test
    fun `speed text switches units`() {
        assertEquals("", st(speed = 0).speedText)
        assertEquals("900 B/s", st(speed = 900).speedText)
        assertEquals("512 KB/s", st(speed = 512L * 1024).speedText)
        assertEquals("1.5 MB/s", st(speed = (1.5 * 1048576).toLong()).speedText)
    }

    @Test
    fun `eta only when running and size known`() {
        assertNull(st(running = false, fileDone = 1, fileTotal = 100, speed = 10).etaSeconds)
        assertNull(st(fileDone = 1, fileTotal = -1, speed = 10).etaSeconds)
        assertEquals(9L, st(fileDone = 10, fileTotal = 100, speed = 10).etaSeconds)
        assertEquals("约 1 分 40 秒", st(fileDone = 0, fileTotal = 1000, speed = 10).etaText)
    }

    @Test
    fun `count text is capped at total`() {
        assertEquals("2/4", st(index = 2, total = 4).countText)
        assertEquals("", st(index = 0, total = 0).countText)
    }
}
