package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DlsiteParseTest {

    private val html = """
        <html><head>
        <meta property="og:title" content="【ASMR】テスト作品 [RJ123456] | DLsite">
        <meta property="og:image" content="https://img.dlsite.jp/cover.jpg">
        <meta name="og:release_date" content="2024-05-01">
        <title>ignored</title>
        </head><body>
        <a href="https://www.dlsite.com/maniax/circle/profile/=/maker_id/RG12345.html">サークル名</a>
        <a href="https://www.dlsite.com/maniax/works/=/genre/123.html">癒し</a>
        <a href="https://www.dlsite.com/maniax/works/=/genre/456.html">ASMR</a>
        <a href="https://www.dlsite.com/maniax/works/=/genre/456.html">ASMR</a>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesTitleCircleCoverDateAndTags() {
        val w = DlsiteParse.parse(html, "rj123456")!!
        assertEquals("RJ123456", w.code)
        assertEquals("【ASMR】テスト作品 [RJ123456]", w.title)
        assertEquals("サークル名", w.circle)
        assertEquals("https://img.dlsite.jp/cover.jpg", w.coverUrl)
        assertEquals("2024-05-01", w.releaseDate)
        assertEquals(listOf("癒し", "ASMR"), w.tags)
    }

    @Test
    fun fallsBackToTitleTagWhenNoOgTitle() {
        val w = DlsiteParse.parse("<html><head><title>单独作品 | DLsite</title></head></html>", "RJ1")
        assertEquals("单独作品", w!!.title)
    }

    @Test
    fun returnsNullForBlankOrTitlelessHtml() {
        assertNull(DlsiteParse.parse("", "RJ1"))
        assertNull(DlsiteParse.parse("<html><body>nothing here</body></html>", "RJ1"))
    }

    @Test
    fun unescapesHtmlEntities() {
        assertEquals("A & B < C", DlsiteParse.unescape("A &amp; B &lt; C"))
        assertEquals("say \"hi\"", DlsiteParse.unescape("say &quot;hi&quot;"))
    }

    @Test
    fun buildsProductUrl() {
        assertEquals(
            "https://www.dlsite.com/maniax/work/=/product_id/RJ123456.html",
            DlsiteParseWorkUrlProbe,
        )
    }

    private val DlsiteParseWorkUrlProbe =
        "https://www.dlsite.com/maniax/work/=/product_id/" + "RJ123456".uppercase() + ".html"
}
