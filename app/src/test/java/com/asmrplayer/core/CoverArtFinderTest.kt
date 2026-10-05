package com.asmrplayer.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoverArtFinderTest {

    private val dir: File = Files.createTempDirectory("asmr-cover").toFile()

    private fun project(name: String): File = File(dir, name).apply { mkdirs() }

    @Test
    fun prefersCoverNamedImage() {
        val p = project("p1")
        File(p, "random.png").writeBytes(ByteArray(5000))
        File(p, "cover.jpg").writeBytes(ByteArray(100))
        assertEquals(File(p, "cover.jpg").absolutePath, CoverArtFinder.find(p))
    }

    @Test
    fun fallsBackToLargestImage() {
        val p = project("p2")
        File(p, "a.png").writeBytes(ByteArray(100))
        File(p, "b.png").writeBytes(ByteArray(5000))
        assertEquals(File(p, "b.png").absolutePath, CoverArtFinder.find(p))
    }

    @Test
    fun findsImageInSubfolder() {
        val p = project("p3")
        val sub = File(p, "img").apply { mkdirs() }
        File(sub, "封面.png").writeBytes(ByteArray(100))
        assertNotNull(CoverArtFinder.find(p, 2))
    }

    @Test
    fun ignoresNonImages() {
        val p = project("p4")
        File(p, "readme.txt").writeBytes(ByteArray(5000))
        File(p, "track.mp3").writeBytes(ByteArray(5000))
        assertNull(CoverArtFinder.find(p))
    }

    @Test
    fun missingFolderYieldsNull() {
        assertNull(CoverArtFinder.find(File(dir, "nope")))
    }
}
