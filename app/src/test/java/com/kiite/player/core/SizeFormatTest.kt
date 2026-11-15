package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 容量显示格式（下载前「已选内容要占多少」全靠它，三个位置必须是同一个口径）。
 */
class SizeFormatTest {

    @Test
    fun `小于 1KB 显示字节`() {
        assertEquals("1 B", SizeFormat.text(1L))
        assertEquals("845 B", SizeFormat.text(845L))
    }

    @Test
    fun `KB 与 MB 保留合适精度`() {
        assertEquals("1 KB", SizeFormat.text(1024L))
        assertEquals("512 KB", SizeFormat.text(512L * 1024L))
        assertEquals("1.5 MB", SizeFormat.text(1024L * 1024L * 3 / 2))
        assertEquals("45.3 MB", SizeFormat.text(47_500_000L))
    }

    @Test
    fun `GB 保留两位小数`() {
        // 1.5 GB
        assertEquals("1.50 GB", SizeFormat.text(1024L * 1024L * 1024L * 3 / 2))
        // 12.35 GB
        assertEquals("12.35 GB", SizeFormat.text((12.35 * 1073741824.0).toLong()))
    }

    @Test
    fun `未知体积显示破折号而不是 0B`() {
        assertEquals("—", SizeFormat.text(0L))
        assertEquals("—", SizeFormat.text(-1L))
        assertEquals("—", SizeFormat.approx(0L))
    }

    @Test
    fun `估算口径 100MB 以上不显示小数`() {
        assertEquals("1.2 MB", SizeFormat.approx(1_258_291L))
        assertEquals("356 MB", SizeFormat.approx(356L * 1024L * 1024L))
        assertEquals("1.24 GB", SizeFormat.approx((1.24 * 1073741824.0).toLong()))
    }

    @Test
    fun `大文件求和不会溢出`() {
        val each = 700L * 1024L * 1024L
        assertEquals("3.42 GB", SizeFormat.approx(each * 5))
    }
}
