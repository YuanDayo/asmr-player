package com.asmrplayer.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagIoTest {

    private val dir: File = Files.createTempDirectory("asmr-tags").toFile()
    private val lyrics = "第一句台词\n第二句台词\n第三句台词"

    @Test
    fun mp3RoundTripKeepsOtherFramesAndAudio() {
        val f = File(dir, "a.mp3")
        TestFiles.writeMp3(f)
        val before = f.length()

        val result = TagIO.writeLyrics(f, lyrics)
        assertTrue("应写入成功：$result", result is TagWriteResult.Written)

        assertEquals(lyrics, TagIO.readLyrics(f))

        val bytes = f.readBytes()
        val latin = String(bytes, Charsets.ISO_8859_1)
        assertTrue("TIT2 帧应保留", latin.contains("TIT2"))
        assertTrue("TPE1 帧应保留", latin.contains("TPE1"))
        assertTrue("音频字节应保留在末尾", latin.endsWith("AUDIO-FRAMES-0123456789"))
        assertTrue("文件应变大（新增歌词帧）", f.length() > before)
    }

    @Test
    fun mp3RewriteReplacesOldLyrics() {
        val f = File(dir, "b.mp3")
        TestFiles.writeMp3(f)
        TagIO.writeLyrics(f, "旧歌词")
        TagIO.writeLyrics(f, "新歌词")
        assertEquals("新歌词", TagIO.readLyrics(f))
        assertTrue("不应残留旧歌词", !String(f.readBytes(), Charsets.ISO_8859_1).contains("旧歌词"))
    }

    @Test
    fun flacInPlaceWriteKeepsMetadataSizeAndFrames() {
        val f = File(dir, "a.flac")
        TestFiles.writeFlac(f, withComment = true, withPadding = true, paddingBytes = 4096)
        val before = f.length()

        val result = TagIO.writeLyrics(f, lyrics)
        assertTrue("应写入成功：$result", result is TagWriteResult.Written)
        assertEquals(lyrics, TagIO.readLyrics(f))
        assertEquals("有 padding 时应原地写入，长度不变", before, f.length())
        assertTrue("音频帧应保留", String(f.readBytes(), Charsets.US_ASCII).endsWith("FLACFRAMES-abcdefghij"))
    }

    @Test
    fun flacWithoutCommentBlockIsCreated() {
        val f = File(dir, "b.flac")
        TestFiles.writeFlac(f, withComment = false, withPadding = true, paddingBytes = 4096)
        val result = TagIO.writeLyrics(f, lyrics)
        assertTrue("应写入成功：$result", result is TagWriteResult.Written)
        assertEquals(lyrics, TagIO.readLyrics(f))
        assertTrue(String(f.readBytes(), Charsets.US_ASCII).endsWith("FLACFRAMES-abcdefghij"))
    }

    @Test
    fun flacWithoutPaddingFallsBackToRewrite() {
        val f = File(dir, "c.flac")
        TestFiles.writeFlac(f, withComment = false, withPadding = false)
        val result = TagIO.writeLyrics(f, lyrics)
        assertTrue("应写入成功：$result", result is TagWriteResult.Written)
        assertEquals(lyrics, TagIO.readLyrics(f))
        assertTrue(String(f.readBytes(), Charsets.US_ASCII).endsWith("FLACFRAMES-abcdefghij"))
    }

    @Test
    fun mp4RoundTripFixesChunkOffsets() {
        val f = File(dir, "a.m4a")
        TestFiles.writeMp4(f)
        val originalMdatStart = TestFiles.mdatPayloadStart(f)
        assertEquals("样本自身应一致", originalMdatStart, TestFiles.readFirstStcoOffset(f))

        val result = TagIO.writeLyrics(f, lyrics)
        assertTrue("应写入成功：$result", result is TagWriteResult.Written)

        assertEquals(lyrics, TagIO.readLyrics(f))
        assertTrue("应新增 ©lyr", TestFiles.mp4HasAtom(f, "\u00A9lyr"))
        assertTrue("原有 ©nam 应保留", TestFiles.mp4HasAtom(f, "\u00A9nam"))

        val newMdatStart = TestFiles.mdatPayloadStart(f)
        assertTrue("moov 变长后 mdat 应后移", newMdatStart > originalMdatStart)
        assertEquals("stco 偏移必须跟着修正", newMdatStart, TestFiles.readFirstStcoOffset(f))

        val payload = String(f.readBytes(), newMdatStart.toInt(), 8, Charsets.US_ASCII)
        assertEquals("AUDIODAT", payload)
    }

    @Test
    fun mp4LyricsCanBeOverwritten() {
        val f = File(dir, "b.m4a")
        TestFiles.writeMp4(f)
        TagIO.writeLyrics(f, "第一次")
        TagIO.writeLyrics(f, "第二次")
        assertEquals("第二次", TagIO.readLyrics(f))
        assertEquals(TestFiles.mdatPayloadStart(f), TestFiles.readFirstStcoOffset(f))
    }

    @Test
    fun unsupportedContainerIsReported() {
        val f = File(dir, "a.ogg")
        f.writeBytes("OggS".toByteArray(Charsets.US_ASCII) + ByteArray(64))
        val result = TagIO.writeLyrics(f, lyrics)
        assertTrue("ogg 应报不支持：$result", result is TagWriteResult.Unsupported)
    }

    @Test
    fun magicBasedDetectionWorks() {
        val mp3 = File(dir, "noext")
        TestFiles.writeMp3(mp3)
        assertTrue(TagIO.canWrite(mp3))
        assertNotNull(TagIO.readLyrics(mp3) ?: "none")
    }
}
