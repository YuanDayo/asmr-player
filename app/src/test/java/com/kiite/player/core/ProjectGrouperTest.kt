package com.kiite.player.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectGrouperTest {

    private val sep = File.separator
    private val root = "C:" + sep + "lib"

    private fun f(vararg parts: String): String = (listOf(root) + parts).joinToString(sep)

    private fun track(name: String, folder: String) = TrackEntry(
        path = folder + sep + name,
        name = name,
        folderPath = folder,
        folderName = folder.substringAfterLast(sep),
        sizeBytes = 1,
        modifiedMs = 0,
    )

    @Test
    fun groupsByTopLevelProject() {
        val tracks = listOf(
            track("a.mp3", f("RJ123456", "01 第一章")),
            track("b.mp3", f("RJ123456", "02 第二章")),
            track("c.mp3", f("RJ654321")),
        )
        val projects = ProjectGrouper.group(root, tracks)
        assertEquals(2, projects.size)

        val rj123 = projects.first { it.code == "RJ123456" }
        assertEquals(2, rj123.trackCount)
        assertEquals(2, rj123.chapters.size)
        assertEquals(listOf("01 第一章", "02 第二章"), rj123.chapters.map { it.name })

        val rj654 = projects.first { it.code == "RJ654321" }
        assertEquals(1, rj654.trackCount)
        assertEquals(1, rj654.chapters.size)
        assertEquals("本目录", rj654.chapters[0].name)
    }

    @Test
    fun rootLevelFilesBecomeSingleProject() {
        val tracks = listOf(track("a.mp3", root), track("b.mp3", root))
        val projects = ProjectGrouper.group(root, tracks)
        assertEquals(1, projects.size)
        assertEquals(2, projects[0].trackCount)
        assertEquals("lib", projects[0].name)
    }

    @Test
    fun deepNestingStillRollsUpToOneProject() {
        val tracks = listOf(
            track("a.mp3", f("作品A", "本編", "トラック1")),
            track("b.mp3", f("作品A", "特典", "voice")),
        )
        val projects = ProjectGrouper.group(root, tracks)
        assertEquals(1, projects.size)
        assertEquals(2, projects[0].trackCount)
        // 章节按最内层文件夹切分
        assertEquals(setOf("トラック1", "voice"), projects[0].chapters.map { it.name }.toSet())
    }

    @Test
    fun emptyYieldsNothing() {
        assertEquals(0, ProjectGrouper.group(root, emptyList()).size)
    }

    @Test
    fun projectRootOfHandlesPrefixCollision() {
        assertEquals(
            f("RJ123456"),
            ProjectGrouper.projectRootOf(root, f("RJ123456", "sub")),
        )
        // 不以分隔符结尾的相似前缀不能算同一项目
        assertEquals(
            f("RJ123456x"),
            ProjectGrouper.projectRootOf(root, f("RJ123456x", "sub")),
        )
    }
}
