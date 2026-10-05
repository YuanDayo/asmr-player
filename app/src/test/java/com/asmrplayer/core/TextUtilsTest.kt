package com.asmrplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUtilsTest {

    @Test
    fun normalizesFullWidthAndNoise() {
        val a = TextUtils.normalizeForMatch("０１　深海の台本.txt")
        assertTrue("全角数字应转成半角：$a", a.startsWith("01"))
        assertTrue("噪声词「台本」应被剥离：$a", !a.contains("台本"))
        assertTrue("正文「深海」应保留：$a", a.contains("深海"))
        assertEquals("01", TextUtils.normalizeForMatch("０１"))
        assertEquals("track 01", TextUtils.normalizeForMatch("track 01 (附台本).mp3"))
        // 英文词不应被误伤
        assertEquals("cover", TextUtils.normalizeForMatch("cover"))
    }

    @Test
    fun stripsBracketContent() {
        assertEquals("track 01", TextUtils.normalizeForMatch("track 01 (附台本).mp3"))
    }

    @Test
    fun extractsLeadingNumbers() {
        assertEquals(1, TextUtils.leadingNumber("01 序章"))
        assertEquals(12, TextUtils.leadingNumber("track12"))
        assertEquals(3, TextUtils.leadingNumber("第3章 台本"))
        assertNull(TextUtils.leadingNumber("序章"))
    }

    @Test
    fun similarityBehaves() {
        assertEquals(1.0, TextUtils.similarity("abc", "abc"), 0.0001)
        assertTrue(TextUtils.similarity("深海の女仆", "深海の女仆 台本".let { TextUtils.normalizeForMatch(it) }) >= 0.0)
        assertTrue(TextUtils.similarity("abcdef", "zzzzzz") < 0.2)
    }

    @Test
    fun detectsLrc() {
        assertTrue(TextUtils.looksLikeLrc("[00:12.34]你好"))
        assertTrue(!TextUtils.looksLikeLrc("普通文本，没有时间轴"))
        assertEquals(2, TextUtils.lrcLineCount("[00:01.00]第一句\n[00:05.50]第二句\n没有时间轴的一行"))
    }

    @Test
    fun parsesFlexibleTime() {
        assertEquals(754_000L, TextUtils.parseFlexibleTime("12:34"))
        assertEquals(12_340L, TextUtils.parseFlexibleTime("00:12.34"))
        assertEquals(3_500L, TextUtils.parseFlexibleTime("00:03.5"))
        assertEquals(3_723_500L, TextUtils.parseFlexibleTime("1:02:03.5"))
        assertEquals(60_000L, TextUtils.parseFlexibleTime("[01:00]"))
        assertNull(TextUtils.parseFlexibleTime("abc"))
        assertNull(TextUtils.parseFlexibleTime("123"))
    }

    @Test
    fun extractsInlineTimestampFromPlainTextLine() {
        assertEquals(12_000L to "台词", TextUtils.inlineTimestamp("00:12 台词"))
        assertEquals(12_000L to "台词", TextUtils.inlineTimestamp("[00:12] 台词"))
        assertEquals(12_000L to "台词", TextUtils.inlineTimestamp("（00:12）台词"))
        assertEquals(12_500L to "台词", TextUtils.inlineTimestamp("00:12.5 - 台词"))
        assertEquals(3_723_000L to "台词", TextUtils.inlineTimestamp("1:02:03：台词"))
        assertNull(TextUtils.inlineTimestamp("这一行没有时间"))
        assertNull(TextUtils.inlineTimestamp("12 个苹果"))
    }

    @Test
    fun parsesLrcTimestamps() {
        assertEquals(12_340L, TextUtils.parseLrcTimestamp("[00:12.34]"))
        assertEquals(60_000L, TextUtils.parseLrcTimestamp("[01:00]"))
        assertEquals(3_500L, TextUtils.parseLrcTimestamp("[00:03.5]"))
    }
}
