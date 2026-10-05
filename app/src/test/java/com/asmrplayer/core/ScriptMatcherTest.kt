package com.asmrplayer.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptMatcherTest {

    private val sep = File.separator
    private val root = "C:" + sep + "lib"

    private fun folder(vararg parts: String): String = (listOf(root) + parts).joinToString(sep)

    private fun track(folderPath: String, name: String) = DiscoveredTrack(
        path = folderPath + sep + name,
        name = name,
        folderPath = folderPath,
        folderName = folderPath.substringAfterLast(sep),
        sizeBytes = 1000,
        modifiedMs = 0,
    )

    private fun script(folderPath: String, name: String) = ScriptRef(
        path = folderPath + sep + name,
        name = name,
        folderPath = folderPath,
        format = ScriptFormat.of(name),
        sizeBytes = 100,
        modifiedMs = 0,
    )

    private fun run(tracks: List<DiscoveredTrack>, scripts: List<ScriptRef>): List<TrackEntry> =
        ScriptMatcher().associate(tracks, scripts, root).first

    // ---- 音频与台本位于同一总项目的不同子文件夹（用户反馈的 bug）----

    @Test
    fun matchesSiblingFoldersInSameProject() {
        val audio = folder("RJ123456", "audio")
        val scriptDir = folder("RJ123456", "script")
        val entries = run(
            listOf(track(audio, "01 序章.wav")),
            listOf(script(scriptDir, "01 序章.txt")),
        )
        assertEquals("同项目不同子文件夹也应匹配", "01 序章.txt", entries[0].primaryScript!!.scriptName)
    }

    @Test
    fun matchesSiblingFolderByNumber() {
        val audio = folder("RJ123456", "音频")
        val scriptDir = folder("RJ123456", "台本")
        val entries = run(
            listOf(track(audio, "01 序章.wav")),
            listOf(script(scriptDir, "01 台本.txt")),
        )
        assertEquals("01 台本.txt", entries[0].primaryScript!!.scriptName)
    }

    @Test
    fun sharesProjectWideScriptAcrossSiblingFolders() {
        val audio = folder("RJ123456", "audio")
        val scriptDir = folder("RJ123456", "台本")
        val entries = run(
            listOf(track(audio, "track one.wav"), track(audio, "track two.wav")),
            listOf(script(scriptDir, "台本.txt")),
        )
        assertTrue("整项目共用台本应摊给所有曲目", entries.all { it.hasScript })
        assertTrue(entries.all { it.primaryScript!!.scriptName == "台本.txt" })
    }

    @Test
    fun doesNotMatchAcrossDifferentProjects() {
        val a = folder("RJ111111", "audio")
        val b = folder("RJ222222", "script")
        val entries = run(
            listOf(track(a, "01 序章.wav")),
            listOf(script(b, "01 序章.txt")),
        )
        assertEquals("不同总项目之间不应匹配", 0, entries[0].scripts.size)
    }

    @Test
    fun readmeIsNotSharedAsScript() {
        val audio = folder("RJ123456", "audio")
        val projectRoot = folder("RJ123456")
        val entries = run(
            listOf(track(audio, "track one.wav")),
            listOf(script(projectRoot, "readme.txt")),
        )
        assertEquals("readme 不应作为台本摊给曲目", 0, entries[0].scripts.size)
    }

    @Test
    fun matchesSameBaseName() {
        val f = folder("album")
        val entries = run(listOf(track(f, "01 深海.wav")), listOf(script(f, "01 深海.txt")))
        assertEquals(1, entries[0].scripts.size)
        assertEquals("01 深海.txt", entries[0].primaryScript!!.scriptName)
    }

    @Test
    fun matchesByNumber() {
        val f = folder("album")
        val entries = run(listOf(track(f, "01 序章.wav")), listOf(script(f, "01 台本.txt")))
        assertEquals("01 台本.txt", entries[0].primaryScript!!.scriptName)
    }

    @Test
    fun matchesScriptSubfolder() {
        val f = folder("album")
        val sub = folder("album", "台本")
        val entries = run(listOf(track(f, "01 序章.wav")), listOf(script(sub, "01 序章.txt")))
        assertEquals("01 序章.txt", entries[0].primaryScript!!.scriptName)
    }

    @Test
    fun prefersTimedFormat() {
        val f = folder("album")
        val entries = run(
            listOf(track(f, "01 序章.wav")),
            listOf(script(f, "01 序章.txt"), script(f, "01 序章.lrc")),
        )
        assertEquals(ScriptFormat.LRC, entries[0].primaryScript!!.format)
        assertEquals(2, entries[0].scripts.size)
    }

    @Test
    fun sharesSingleFolderScriptWithAllTracks() {
        val f = folder("album")
        val entries = run(
            listOf(track(f, "01 序章.wav"), track(f, "02 中章.wav"), track(f, "03 终章.wav")),
            listOf(script(f, "台本.txt")),
        )
        assertEquals(3, entries.count { it.hasScript })
        assertTrue(entries.all { it.primaryScript!!.scriptName == "台本.txt" })
    }

    @Test
    fun doesNotShareNumberedScriptToSiblings() {
        val f = folder("album")
        val entries = run(
            listOf(track(f, "01 序章.wav"), track(f, "02 中章.wav")),
            listOf(script(f, "01 序章.txt")),
        )
        val first = entries.first { it.name.startsWith("01") }
        val second = entries.first { it.name.startsWith("02") }
        assertEquals(1, first.scripts.size)
        assertEquals(0, second.scripts.size)
    }

    @Test
    fun doesNotCrossMatchWhenNumbersDifferButNamesAreSimilar() {
        val f = folder("album")
        val entries = run(
            listOf(track(f, "01 deep sea.wav"), track(f, "02 deep sea.wav")),
            listOf(script(f, "01 deep sea.lrc"), script(f, "02 deep sea.txt")),
        )
        val first = entries.first { it.name.startsWith("01") }
        val second = entries.first { it.name.startsWith("02") }
        assertEquals("01 只应匹配到自己的台本", 1, first.scripts.size)
        assertEquals("01 deep sea.lrc", first.primaryScript!!.scriptName)
        assertEquals("02 只应匹配到自己的台本", 1, second.scripts.size)
        assertEquals("02 deep sea.txt", second.primaryScript!!.scriptName)
    }

    @Test
    fun reportsOrphanScripts() {
        val f = folder("album")
        val orphan = script(folder("other"), "无关.txt")
        val (_, orphans) = ScriptMatcher().associate(listOf(track(f, "01.wav")), listOf(orphan))
        assertEquals(1, orphans.size)
        assertEquals("无关.txt", orphans[0].name)
    }

    @Test
    fun folderLevelFallbackAttachesMultipleScripts() {
        val f = folder("album")
        val entries = run(
            listOf(track(f, "01 序章.wav"), track(f, "02 中章.wav")),
            listOf(script(f, "台本.txt"), script(f, "翻译.txt")),
        )
        assertTrue(entries.all { it.scripts.size == 2 })
    }
}
