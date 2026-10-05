package com.asmrplayer.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptParserTest {

    private val dir: File = Files.createTempDirectory("asmr-parser").toFile()
    private val parser = ScriptParser()

    @Test
    fun parsesPlainTxt() {
        val f = File(dir, "台本.txt")
        f.writeText("第一句台词\n第二句台词", Charsets.UTF_8)
        val p = parser.parse(f)
        assertEquals(ScriptFormat.TXT, p.format)
        assertTrue(p.plainText.contains("第二句台词"))
        assertTrue(!p.isTimed)
    }

    @Test
    fun parsesGbkTxt() {
        val f = File(dir, "gbk.txt")
        f.writeBytes("你好，世界。这里是台本。".toByteArray(charset("GBK")))
        val p = parser.parse(f)
        assertTrue("GBK 应能正确解码：${p.plainText}", p.plainText.contains("你好"))
    }

    @Test
    fun parsesLrcWithTimeline() {
        val f = File(dir, "track01.lrc")
        f.writeText("[ti:测试]\n[00:01.00]第一句\n[00:03.50]第二句\n[00:10.00]第三句", Charsets.UTF_8)
        val p = parser.parse(f)
        assertEquals(ScriptFormat.LRC, p.format)
        assertEquals(3, p.timedLines.size)
        assertEquals(1_000L, p.timedLines[0].startMs)
        assertEquals(3_500L, p.timedLines[1].startMs)
        assertEquals(10_000L, p.timedLines[1].endMs)
        assertEquals("测试", p.title)
    }

    @Test
    fun parsesSrt() {
        val f = File(dir, "sub.srt")
        f.writeText(
            "1\n00:00:01,000 --> 00:00:04,000\n第一句\n\n2\n00:00:05,500 --> 00:00:08,000\n第二句\n",
            Charsets.UTF_8,
        )
        val p = parser.parse(f)
        assertEquals(ScriptFormat.SRT, p.format)
        assertEquals(2, p.timedLines.size)
        assertEquals(1_000L, p.timedLines[0].startMs)
        assertEquals(4_000L, p.timedLines[0].endMs)
        assertEquals(5_500L, p.timedLines[1].startMs)
    }

    @Test
    fun parsesAss() {
        val f = File(dir, "sub.ass")
        f.writeText(
            "[Script Info]\nTitle: 台本测试\n\n[Events]\n" +
                "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                "Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,{\\pos(1,2)}第一句\n" +
                "Dialogue: 0,0:00:05.50,0:00:08.00,Default,,0,0,0,,第二句\\N第二行\n",
            Charsets.UTF_8,
        )
        val p = parser.parse(f)
        assertEquals(ScriptFormat.ASS, p.format)
        assertEquals(2, p.timedLines.size)
        assertEquals(1_000L, p.timedLines[0].startMs)
        assertEquals("第一句", p.timedLines[0].text)
        assertTrue(p.timedLines[1].text.contains("第二行"))
    }

    @Test
    fun parsesDocx() {
        val f = File(dir, "台本.docx")
        TestFiles.writeDocx(f, listOf("第一段", "第二段 & 特殊<字符>"))
        val p = parser.parse(f)
        assertEquals(ScriptFormat.DOCX, p.format)
        assertTrue(p.plainText.contains("第一段"))
        assertTrue(p.plainText.contains("第二段 & 特殊<字符>"))
    }

    @Test
    fun txtContainingLrcIsTreatedAsTimed() {
        val f = File(dir, "伪装.txt")
        f.writeText("[00:01.00]甲\n[00:02.00]乙\n[00:03.00]丙", Charsets.UTF_8)
        val p = parser.parse(f)
        assertEquals(3, p.timedLines.size)
    }

    @Test
    fun parsesPlainTxtWithInlineTimestamps() {
        val f = File(dir, "timed.txt")
        f.writeText("[00:00] 第一句\n[00:12.5] 第二句\n1:02:03 - 第三句", Charsets.UTF_8)
        val p = parser.parse(f)
        assertTrue("应识别为时间轴台本", p.isTimed)
        assertEquals(3, p.timedLines.size)
        assertEquals(0L, p.timedLines[0].startMs)
        assertEquals(12_500L, p.timedLines[1].startMs)
        assertEquals(3_723_000L, p.timedLines[2].startMs)
        assertEquals("第二句", p.timedLines[1].text)
        assertEquals(12_500L, p.timedLines[0].endMs)
    }

    @Test
    fun colophonTimestampsInMarkdownAlsoWork() {
        val f = File(dir, "timed.md")
        f.writeText("# 台本\n\n00:01 第一句\n00:05 第二句\n00:09 第三句", Charsets.UTF_8)
        val p = parser.parse(f)
        assertTrue("md 里的时间标注也应生效", p.isTimed)
        assertEquals(3, p.timedLines.size)
    }

    @Test
    fun plainTxtWithoutTimestampsStaysUntimed() {
        val f = File(dir, "plain2.txt")
        f.writeText("第一句台词\n第二句台词\n第三句台词", Charsets.UTF_8)
        val p = parser.parse(f)
        assertTrue("普通文本不应被误判成时间轴", !p.isTimed)
    }

    @Test
    fun onlyAFewStampedLinesIsNotTreatedAsTimed() {
        val f = File(dir, "mostly_plain.txt")
        f.writeText(
            "开场白\n00:01 第一句\n中间解说\n结尾致辞\n感谢收听",
            Charsets.UTF_8,
        )
        val p = parser.parse(f)
        assertTrue("带时间行太少时不应判定为时间轴", !p.isTimed)
    }

    @Test
    fun stripsMarkdown() {
        val f = File(dir, "台本.md")
        f.writeText("# 标题\n\n- 项目一\n**加粗**与`代码`\n[链接](http://x)", Charsets.UTF_8)
        val p = parser.parse(f)
        assertEquals(ScriptFormat.MD, p.format)
        assertTrue(p.plainText.contains("标题"))
        assertTrue(p.plainText.contains("加粗"))
        assertTrue(!p.plainText.contains("**"))
        assertTrue(!p.plainText.contains("http://x"))
    }
}
