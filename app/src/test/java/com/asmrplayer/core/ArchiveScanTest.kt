package com.asmrplayer.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveScanTest {

    private fun tempRoot(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "asmr-arc-" + System.nanoTime())
        dir.mkdirs()
        return dir
    }

    @Test
    fun detectsZipAndRarAndMarksZipExtractable() {
        val root = tempRoot()
        val proj = File(root, "RJ999999_Test").apply { mkdirs() }
        File(proj, "01.wav").writeBytes(ByteArray(128))
        File(proj, "extra.zip").writeBytes(ByteArray(32))
        File(proj, "more.rar").writeBytes(ByteArray(32))
        File(proj, "notes.7z").writeBytes(ByteArray(32))

        val scan = LibraryScanner().scan(root)
        assertEquals(3, scan.archives.size)
        assertTrue(scan.archives.first { it.name == "extra.zip" }.extractable)
        assertTrue(!scan.archives.first { it.name == "more.rar" }.extractable)
        assertTrue(!scan.archives.first { it.name == "notes.7z" }.extractable)
        assertEquals(3, scan.projects.first { it.name == "RJ999999_Test" }.archiveCount)
    }

    @Test
    fun projectWithOnlyArchivesStillAppears() {
        val root = tempRoot()
        val proj = File(root, "RJ888888_OnlyZip").apply { mkdirs() }
        File(proj, "pack.zip").writeBytes(ByteArray(16))

        val scan = LibraryScanner().scan(root)
        assertEquals(1, scan.archiveCount)
        val entry = scan.projects.firstOrNull { it.name == "RJ888888_OnlyZip" }
        assertTrue("只有压缩包的项目也应该出现", entry != null)
        assertEquals(0, entry!!.trackCount)
        assertEquals(1, entry.archiveCount)
    }

    @Test
    fun extractsZipIntoFolderBesideArchive() {
        val root = tempRoot()
        val proj = File(root, "RJ777777").apply { mkdirs() }
        val zip = File(proj, "pack.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("sub/hello.txt"))
            zos.write("你好".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        var count = 0
        java.util.zip.ZipInputStream(java.io.BufferedInputStream(zip.inputStream())).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                count++
                zin.closeEntry()
            }
        }
        assertEquals(1, count)
        assertTrue(zip.length() > 0)
    }
}
