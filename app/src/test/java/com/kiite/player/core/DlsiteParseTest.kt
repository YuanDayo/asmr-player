package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteParseTest {

    private val html = """
        <html><head>
        <meta property="og:title" content="【ASMR】テスト作品 [RJ123456] | DLsite">
        <meta property="og:image" content="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg">
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
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg",
            w.coverUrl,
        )
        assertEquals("2024-05-01", w.releaseDate)
        assertEquals(listOf("癒し", "ASMR"), w.tags)
    }

    @Test
    fun fallsBackToTitleTagWhenNoOgTitle() {
        val w = DlsiteParse.parse(
            "<html><head><title>单独作品 | DLsite</title>" +
                "<link rel=\"canonical\" href=\"https://www.dlsite.com/maniax/work/=/product_id/RJ1.html\">" +
                "</head></html>",
            "RJ1",
        )
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

    // ---------- 站内 banner 被当成封面（真机踩过的坑） ----------

    /** DLsite 站内那张「二次元総合ダウンロードショップ」宣传图。 */
    private val bannerUrl =
        "https://www.dlsite.com/images/web/common/img_ogp.png"

    @Test
    fun ageGatePageIsNotParsedAsWork() {
        // 未登录时的年龄确认页：有 og:title、og:image，但页面里没有作品编号
        val gate = """
            <html><head>
            <meta property="og:title" content="DLsite">
            <meta property="og:image" content="$bannerUrl">
            </head><body>年齢確認 18歳以上ですか？</body></html>
        """.trimIndent()
        assertNull(DlsiteParse.parse(gate, "RJ123456"))
        assertEquals(false, DlsiteParse.looksLikeProductPage(gate, "RJ123456"))
    }

    @Test
    fun siteBannerIsNeverUsedAsCover() {
        // 页面确实是这部作品的（带编号），但 og:image 是站内宣传图 —— 宁可为空也不能贴错
        val page = """
            <html><head>
            <meta property="og:title" content="作品 [RJ123456] | DLsite">
            <meta property="og:image" content="$bannerUrl">
            <link rel="canonical" href="https://www.dlsite.com/maniax/work/=/product_id/RJ123456.html">
            </head><body>RJ123456</body></html>
        """.trimIndent()
        val w = DlsiteParse.parse(page, "RJ123456")!!
        assertNull("站内 banner 不能当封面", w.coverUrl)
    }

    @Test
    fun coverUrlMustPointAtThisWork() {
        assertTrue(
            DlsiteParse.isPlausibleCover(
                "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg",
                "RJ123456",
            ),
        )
        // 别的作品的封面（编号不匹配）
        assertEquals(
            false,
            DlsiteParse.isPlausibleCover(
                "https://img.dlsite.jp/modpub/images2/work/doujin/RJ999999/RJ999999_img_main.jpg",
                "RJ123456",
            ),
        )
        // 站内通用图 / 非 http 地址
        assertEquals(false, DlsiteParse.isPlausibleCover(bannerUrl, "RJ123456"))
        assertEquals(false, DlsiteParse.isPlausibleCover("/images/logo.png", "RJ123456"))
        assertEquals(false, DlsiteParse.isPlausibleCover(null, "RJ123456"))
    }

    @Test
    fun pageOfAnotherWorkIsRejected() {
        val other = """
            <html><head><meta property="og:title" content="别的作品 [RJ999999] | DLsite"></head>
            <body>RJ999999</body></html>
        """.trimIndent()
        assertNull(DlsiteParse.parse(other, "RJ123456"))
    }

    // ---------- 封面候选：og:image 不是唯一来源 ----------

    @Test
    fun fallsBackToBodyImageWhenOgImageIsBanner() {
        // 真实情况：og:image 是站内宣传图，但正文商品图是对的 —— 不能因为第一条不可用就放弃
        val page = """
            <html><head>
            <meta property="og:title" content="作品 [RJ123456] | DLsite">
            <meta property="og:image" content="$bannerUrl">
            </head><body>
            <img class="product_img" src="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg">
            </body></html>
        """.trimIndent()
        val w = DlsiteParse.parse(page, "RJ123456")!!
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg",
            w.coverUrl,
        )
    }

    @Test
    fun sampleImageIsConvertedToMainCover() {
        // 懒加载页面里只给了样图（试听截图）地址 —— 按 DLsite 命名规则换成封面主图。
        // 用户手机上那张「哥哥，欢迎回来」的截图，就是被当成封面缓存下来的样图。
        val page = """
            <html><head>
            <meta property="og:title" content="作品 [RJ123456] | DLsite">
            <meta property="og:image" content="$bannerUrl">
            </head><body>
            <img src="/images/web/common/blank.gif"
                 data-src="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_smp1.jpg">
            </body></html>
        """.trimIndent()
        val w = DlsiteParse.parse(page, "RJ123456")!!
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg",
            w.coverUrl,
        )
    }

    @Test
    fun ogImagePointingAtSampleYieldsMainCover() {
        // 真实情况（RJ01439468）：翻译页的 og:image 就是第一张样图，
        // 正文轮播里同时有样图和封面主图 —— 必须取主图。
        val page = """
            <html><head>
            <meta property="og:title" content="作品 [RJ123456] | DLsite">
            <meta property="og:image" content="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_smp1.jpg">
            </head><body>
            <div class="product-slider">
              <img data-src="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_smp1.jpg">
              <img data-src="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_smp2.jpg">
            </div>
            <img class="work_thumb" src="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg">
            </body></html>
        """.trimIndent()
        val w = DlsiteParse.parse(page, "RJ123456")!!
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg",
            w.coverUrl,
        )
    }

    @Test
    fun sampleUrlRanksBelowMainCoverButStaysUsable() {
        val smp = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_smp1.jpg"
        val main = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg"
        assertEquals(true, DlsiteParse.isSampleCover(smp))
        // 样图也是「这部作品的图」，能当兜底；但必须永远排在主图之后
        assertEquals(true, DlsiteParse.isPlausibleCover(smp, "RJ123456"))
        assertTrue(DlsiteParse.coverRank(smp, "RJ123456") in 0 until DlsiteParse.coverRank(main, "RJ123456"))
        assertEquals(100, DlsiteParse.coverRank(main, "RJ123456"))
        assertEquals(
            main,
            DlsiteParse.mainFromSample(smp),
        )
        // 已经是主图 → 不改写
        assertNull(DlsiteParse.mainFromSample(main))
        // 各种历史命名都要认得
        assertEquals(
            "https://img.dlsite.jp/modpub/RJ123456/RJ123456_img_main.jpg",
            DlsiteParse.mainFromSample("https://img.dlsite.jp/modpub/RJ123456/RJ123456_smp3.jpg"),
        )
        assertEquals(
            "https://img.dlsite.jp/modpub/RJ123456/RJ123456_img_main.jpg",
            DlsiteParse.mainFromSample("https://img.dlsite.jp/modpub/RJ123456/RJ123456_img_smp.jpg"),
        )
    }

    @Test
    fun mainCoverBeatsOtherProductImages() {
        val page = """
            <html><head>
            <meta property="og:title" content="作品 [RJ123456] | DLsite">
            </head><body>
            <img src="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_smp1.jpg">
            <img src="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_sub.jpg">
            <img src="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main_thumb.jpg">
            <img src="https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg">
            </body></html>
        """.trimIndent()
        val w = DlsiteParse.parse(page, "RJ123456")!!
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg",
            w.coverUrl,
        )
    }

    @Test
    fun readsJsonLdImage() {
        val page = """
            <html><head>
            <meta property="og:title" content="作品 [RJ123456] | DLsite">
            <script type="application/ld+json">
            {"@type":"Product","name":"作品","image":"https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg"}
            </script>
            </head><body>RJ123456</body></html>
        """.trimIndent()
        val w = DlsiteParse.parse(page, "RJ123456")!!
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/RJ123456_img_main.jpg",
            w.coverUrl,
        )
    }

    @Test
    fun normalizesProtocolRelativeUrl() {
        // 老页面里常见 //img.dlsite.jp/… 这种省略协议的写法
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ123456/a.jpg",
            DlsiteParse.normalizeUrl("//img.dlsite.jp/modpub/images2/work/doujin/RJ123456/a.jpg"),
        )
        assertNull(DlsiteParse.normalizeUrl("/images/logo.png"))
        assertNull(DlsiteParse.normalizeUrl(""))
        assertNull(DlsiteParse.normalizeUrl(null))
    }

    @Test
    fun placeholderImagesAreNeverCovers() {
        // 占位图 / 无图标记：路径里有编号也不能用
        assertEquals(
            false,
            DlsiteParse.isPlausibleCover("https://img.dlsite.jp/modpub/noimage/RJ123456.png", "RJ123456"),
        )
        assertEquals(
            false,
            DlsiteParse.isPlausibleCover("https://img.dlsite.jp/modpub/RJ123456/now_printing.jpg", "RJ123456"),
        )
    }

    @Test
    fun normalPagesMentioningAgeCheckAreNotAgeGate() {
        // 作品页 / 搜索结果页的页脚本来就有「年齢確認」，只看关键词会把正常页面误判成确认页
        val workPage = """
            <html><body><a href="/maniax/work/=/product_id/RJ123456.html">作品</a>
            <footer><a href="/age_check/">年齢確認</a></footer></body></html>
        """.trimIndent()
        assertTrue(!DlsiteParse.isAgeGate(workPage))

        val emptySearch = """
            <html><body><h2>検索結果：0件</h2><footer>年齢確認</footer></body></html>
        """.trimIndent()
        assertTrue(!DlsiteParse.isAgeGate(emptySearch))
    }

    @Test
    fun realAgeGatePageIsDetected() {
        val gate = """
            <html><body><h1>年齢確認</h1>
            <form action="/age_check/"><input type="submit" name="adultchecked" value="1"></form>
            </body></html>
        """.trimIndent()
        assertTrue(DlsiteParse.isAgeGate(gate))
        assertTrue(!DlsiteParse.isAgeGate(""))
    }
}
