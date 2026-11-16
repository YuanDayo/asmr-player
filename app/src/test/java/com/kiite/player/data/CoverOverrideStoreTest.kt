package com.kiite.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自选头图的键与文件名：脏字符不能进文件名，编号稳定时换目录也要能命中。 */
class CoverOverrideStoreTest {

    @Test
    fun codeKeyIsPreferredAndUpperCased() {
        assertEquals("code:RJ123456", CoverOverrideStore.keyOf("rj123456", "/storage/x/A")!!)
        assertEquals("code:VJ010101", CoverOverrideStore.keyOf(" VJ010101 ", "/storage/x/A")!!)
    }

    @Test
    fun fallsBackToPathWhenNoCode() {
        assertEquals(
            "path:/storage/emulated/0/Music/某作品",
            CoverOverrideStore.keyOf(null, "/storage/emulated/0/Music/某作品")!!,
        )
        assertEquals("path:/a", CoverOverrideStore.keyOf("   ", "/a")!!)
        assertEquals(null, CoverOverrideStore.keyOf(null, "   "))
    }

    @Test
    fun sameCodeDifferentPathHitsSameKey() {
        // 作品挪了目录，自选头图仍然跟着作品而不是路径
        assertEquals(
            CoverOverrideStore.keyOf("RJ123456", "/old/place"),
            CoverOverrideStore.keyOf("RJ123456", "/new/place"),
        )
        assertNotEquals(
            CoverOverrideStore.keyOf(null, "/old/place"),
            CoverOverrideStore.keyOf(null, "/new/place"),
        )
    }

    @Test
    fun fileNameIsStableAndSafe() {
        val k = CoverOverrideStore.keyOf("RJ123456", "/storage/x")!!
        val a = CoverOverrideStore.fileNameOf(k, "jpg")
        val b = CoverOverrideStore.fileNameOf(k, "jpg")
        assertEquals(a, b)
        assertTrue("不该带路径分隔符: " + a, !a.contains('/') && !a.contains('\\'))
        assertTrue("扩展名要保留: " + a, a.endsWith(".jpg"))
        // 奇怪的扩展名退到 jpg，避免写出可执行/无法解码的文件名
        assertTrue(CoverOverrideStore.fileNameOf(k, "apk").endsWith(".jpg"))
        assertTrue(CoverOverrideStore.fileNameOf(k, ".PNG").endsWith(".png"))
    }
}
