package com.asmrplayer.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 内嵌封面（MP3 APIC / FLAC PICTURE / MP4 covr）的提取。 */
class ArtworkTest {

    private val dir: File = Files.createTempDirectory("asmr-art").toFile()

    private val image: ByteArray = ByteArray(300) { (it % 251).toByte() }.also {
        it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte()
    }

    @Test
    fun mp3ApicIsExtracted() {
        val f = File(dir, "a.mp3")
        TestFiles.writeMp3(f, artwork = image)
        val got = TagIO.readArtwork(f)
        assertNotNull("应能从 APIC 帧读出封面", got)
        assertArrayEquals(image, got)
    }

    @Test
    fun flacPictureIsExtracted() {
        val f = File(dir, "a.flac")
        TestFiles.writeFlac(f, withComment = true, withPadding = true, artwork = image)
        val got = TagIO.readArtwork(f)
        assertNotNull("应能从 PICTURE 块读出封面", got)
        assertArrayEquals(image, got)
    }

    @Test
    fun mp4CovrIsExtracted() {
        val f = File(dir, "a.m4a")
        TestFiles.writeMp4(f, artwork = image)
        val got = TagIO.readArtwork(f)
        assertNotNull("应能从 covr 读出封面", got)
        assertArrayEquals(image, got)
    }

    @Test
    fun noArtworkYieldsNull() {
        val f = File(dir, "none.mp3")
        TestFiles.writeMp3(f)
        assertNull(TagIO.readArtwork(f))
    }

    @Test
    fun artworkSurvivesLyricsWrite() {
        val f = File(dir, "both.mp3")
        TestFiles.writeMp3(f, artwork = image)
        TagIO.writeLyrics(f, "台本内容")
        assertEqualsString("台本内容", TagIO.readLyrics(f))
        assertArrayEquals("写入歌词后封面应仍在", image, TagIO.readArtwork(f))
    }

    private fun assertEqualsString(expected: String, actual: String?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
