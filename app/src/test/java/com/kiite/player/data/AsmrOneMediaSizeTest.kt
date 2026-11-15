package com.kiite.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 体积探测的兜底解析：不认 HEAD 的端点只给 `Content-Range: bytes 0-0/N`，
 * N 就是文件总长（下载前的容量估算靠它）。
 */
class AsmrOneMediaSizeTest {

    @Test
    fun `标准 Range 响应取出总长`() {
        assertEquals(12_345L, AsmrOneClient.totalFromContentRange("bytes 0-0/12345"))
        assertEquals(12_345L, AsmrOneClient.totalFromContentRange("bytes 0-1/12345"))
    }

    @Test
    fun `空格与大小写差异不影响解析`() {
        assertEquals(7_340_032L, AsmrOneClient.totalFromContentRange("bytes 0-0/7340032"))
        assertEquals(7_340_032L, AsmrOneClient.totalFromContentRange("bytes 0-0 / 7340032 "))
    }

    @Test
    fun `未知总长或缺失头部返回 null`() {
        assertNull(AsmrOneClient.totalFromContentRange(null))
        assertNull(AsmrOneClient.totalFromContentRange(""))
        assertNull(AsmrOneClient.totalFromContentRange("bytes 0-0/*"))
        assertNull(AsmrOneClient.totalFromContentRange("garbage"))
        assertNull(AsmrOneClient.totalFromContentRange("bytes 0-0/0"))
    }
}
