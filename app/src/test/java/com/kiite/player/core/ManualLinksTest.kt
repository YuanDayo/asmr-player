package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLinksTest {

    private fun track(path: String, scriptPath: String) = TrackEntry(
        path = path,
        name = path.substringAfterLast('/'),
        folderPath = path.substringBeforeLast('/'),
        folderName = "d",
        sizeBytes = 1,
        modifiedMs = 0,
        scripts = listOf(ScriptAttachment(scriptPath, scriptPath.substringAfterLast('/'), ScriptFormat.TXT, 1000, "同名文件")),
    )

    @Test
    fun manualLinkIsPrependedAndMarked() {
        val applied = ManualLinks.apply(
            listOf(track("/a/01.mp3", "/a/01.txt")),
            mapOf("/a/01.mp3" to "/a/other.lrc"),
        )
        val t = applied[0]
        assertEquals(2, t.scripts.size)
        val primary = t.primaryScript!!
        assertEquals("/a/other.lrc", primary.scriptPath)
        assertTrue(primary.manual)
        assertEquals("手动指定", primary.reason)
        assertEquals(ScriptFormat.LRC, primary.format)
    }

    @Test
    fun manualLinkReplacesSamePathToAvoidDuplication() {
        val applied = ManualLinks.apply(
            listOf(track("/a/01.mp3", "/a/01.txt")),
            mapOf("/a/01.mp3" to "/a/01.txt"),
        )
        assertEquals(1, applied[0].scripts.size)
        assertTrue(applied[0].scripts[0].manual)
    }

    @Test
    fun emptyLinksAreNoop() {
        val src = listOf(track("/a/01.mp3", "/a/01.txt"))
        assertEquals(src, ManualLinks.apply(src, emptyMap()))
    }

    @Test
    fun recomputingOnRawTracksDropsManualWhenLinkGone() {
        val raw = listOf(track("/a/01.mp3", "/a/01.txt"))
        val withManual = ManualLinks.apply(raw, mapOf("/a/01.mp3" to "/a/other.lrc"))
        assertTrue(withManual[0].primaryScript!!.manual)
        // 正确用法：每次都在「原始自动匹配结果」上重算，清除链接后手动项应消失
        val cleared = ManualLinks.apply(raw, emptyMap())
        assertEquals("01.txt", cleared[0].primaryScript!!.scriptName)
        assertTrue(!cleared[0].primaryScript!!.manual)
    }
}
