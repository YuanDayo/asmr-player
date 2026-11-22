package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 站内搜索结果解析：结构化块优先，链接兜底；空结果页不当作解析失败。 */
class DlsiteSearchParseTest {

    /** 仿搜索结果页：work_name 块里有标题链接 / 社团 / 价格。 */
    private val structured = """
        <html><body>
        <div class="search_result_img_box_inner">
          <a href="https://www.dlsite.com/maniax/work/=/product_id/RJ01123456.html"><img src="x.jpg"></a>
          <dl class="work_name">
            <dt><a href="https://www.dlsite.com/maniax/work/=/product_id/RJ01123456.html">【耳かき】夜の耳かき [RJ01123456]</a></dt>
            <dd><a href="https://www.dlsite.com/maniax/circle/profile/=/maker_id/RG0001.html">てすとサークル</a>
                <span class="work_price"><b>880円</b></span></dd>
          </dl>
        </div>
        <div class="search_result_img_box_inner">
          <dl class="work_name">
            <dt><a href="https://www.dlsite.com/maniax/work/=/product_id/RJ123456.html">テスト音声2</a></dt>
            <dd><a href="https://www.dlsite.com/maniax/circle/profile/=/maker_id/RG0002.html">第二サークル</a>
                <span class="work_price"><b>1,320円</b></span></dd>
          </dl>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesStructuredBlocks() {
        val hits = DlsiteSearchParse.parse(structured)
        assertEquals(2, hits.size)
        assertEquals("RJ01123456", hits[0].code)
        assertEquals("【耳かき】夜の耳かき [RJ01123456]", hits[0].title)
        assertEquals("てすとサークル", hits[0].circle)
        assertEquals("880円", hits[0].price)
        assertEquals("RJ123456", hits[1].code)
        assertEquals("1,320円", hits[1].price)
    }

    /** 结构变了（没有 work_name 块）也要能靠 product_id 链接兜底。 */
    @Test
    fun fallsBackToBareLinks() {
        val html = """
            <html><body>
            <a href="https://www.dlsite.com/maniax/work/=/product_id/RJ333333.html">兜底标题</a>
            <a href="https://www.dlsite.com/maniax/work/=/product_id/RJ333333.html"><img src="a.jpg"></a>
            <a href="https://www.dlsite.com/home/work/=/product_id/VJ444444.html">全年龄</a>
            </body></html>
        """.trimIndent()
        val hits = DlsiteSearchParse.parse(html)
        // RJ333333 出现两次只算一次；VJ 编号不在 RJ 正则里、不应混进结果
        assertEquals(1, hits.size)
        assertEquals("RJ333333", hits[0].code)
        assertEquals("兜底标题", hits[0].title)
        assertNull(hits[0].circle)
    }

    @Test
    fun emptyPageIsRecognizedAsEmpty() {
        assertTrue(DlsiteSearchParse.looksEmpty("<div>検索結果は0件です</div>"))
        assertEquals(emptyList<DlsiteSearchHit>(), DlsiteSearchParse.parse("<html><body> nothing </body></html>"))
    }

    @Test
    fun dedupesAndCapsResults() {
        val many = (1..80).joinToString("") {
            """<dl class="work_name"><dt><a href="https://www.dlsite.com/maniax/work/=/product_id/RJ0%06d.html">作品%d</a></dt></dl>"""
                .format(100000 + it, it)
        }
        val hits = DlsiteSearchParse.parse("<html>$many</html>")
        assertEquals(50, hits.size)
    }

    @Test
    fun urlEncodesKeyword() {
        val url = DlsiteSearch.url("彼女 okayu")
        assertTrue(url.startsWith("https://www.dlsite.com/maniax/fsr/=/keyword/"))
        assertTrue(url.endsWith("/order/trend"))
        assertTrue("中文与空格要被编码", !url.contains("彼女 "))
    }

    @Test
    fun titleKeyStripsCodeAndPartSuffix() {
        assertEquals("夜の耳かき", DlsiteTitleKey.of("RJ01123456 夜の耳かき 第2部分"))
        assertEquals("夜の耳かき", DlsiteTitleKey.of("夜の耳かき.zip"))
        assertEquals("作品", DlsiteTitleKey.of("作品 [RJ01234567]"))
    }

    @Test
    fun titleRankPutsExactMatchFirst() {
        val hits = listOf(
            DlsiteSearchHit("RJ00000002", "夜の耳かき 別バージョン"),
            DlsiteSearchHit("RJ00000001", "夜の耳かき"),
        )
        val ranked = DlsiteTitleKey.rank("RJ01123456 夜の耳かき 第2部分", hits)
        assertEquals("RJ00000001", ranked.first().code)
    }

    @Test
    fun extractCodeFindsCodeInFolderName() {
        // 「按标题识别」的第一步：文件夹名里有编号就直接按编号走 JSON 接口，
        // 不去搜站内搜索页（那一步容易被年龄确认/风控挡住）。
        assertEquals("RJ01123456", DlsiteTitleKey.extractCode("RJ01123456 夜の耳かき 第2部分"))
        assertEquals("RJ01234567", DlsiteTitleKey.extractCode("作品 [RJ01234567]"))
        assertEquals("RJ01275416", DlsiteTitleKey.extractCode("(rj01275416) 耳かき"))
        // 全角/半角混排、编号与字母之间有空格也要认出来
        assertEquals("RJ01275416", DlsiteTitleKey.extractCode("RJ 01275416 作品"))
        // 纯标题没有编号：返回 null，交给站内搜索兜底
        assertEquals(null, DlsiteTitleKey.extractCode("夜の耳かき喫茶"))
    }
}
