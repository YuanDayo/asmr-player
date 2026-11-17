package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原作 / 译文编号的区分、CDN 目录换算、CDN XML 错误解析、页面状态分类。
 *
 * 这些用例对应真机日志里的几种「解析标题成功、封面却是 XML」的情况：
 * 译文编号拿去拼 CDN 地址、页面被换成年龄确认页、CDN 回 NoSuchKey。
 */
class DlsiteIdentityTest {

    private val translationPageUrl =
        "https://www.dlsite.com/maniax-touch/work/=/product_id/RJ01275416.html?translation=RJ01275417"

    @Test
    fun originalPageKeepsOriginalAndRecordsTranslation() {
        val ids = DlsiteParse.ids(
            "<html><body>RJ01275416 → /work/=/product_id/RJ01275416.html?translation=RJ01275417</body></html>",
            "RJ01275416",
            translationPageUrl,
        )
        assertEquals("RJ01275416", ids.originalCode)
        assertEquals("RJ01275417", ids.translationCode)
        assertEquals("RJ01275416", ids.requested)
        assertEquals(listOf("RJ01275417"), ids.alternates())
    }

    @Test
    fun requestingTranslationCodeResolvesToOriginal() {
        // 用户本地文件夹用的是译文编号：这一页的主编号才是原作，不能拿译文编号当身份
        val ids = DlsiteParse.ids("<html>RJ01275416</html>", "rj01275417", translationPageUrl)
        assertEquals("RJ01275416", ids.originalCode)
        assertEquals("RJ01275417", ids.translationCode)
        assertEquals("RJ01275417", ids.requested)
    }

    @Test
    fun noTranslationInfoKeepsRequestedAsOriginal() {
        val ids = DlsiteParse.ids("<html>nothing here</html>", "rj01439468", null)
        assertEquals("RJ01439468", ids.originalCode)
        assertNull(ids.translationCode)
        assertTrue(ids.alternates().isEmpty())
    }

    @Test
    fun bucketDirectoryRoundsUpToThousand() {
        assertEquals("RJ01276000", DlsiteParse.bucketDir("RJ01275416"))
        assertEquals("RJ01301000", DlsiteParse.bucketDir("RJ01300112"))
        assertEquals("RJ01295000", DlsiteParse.bucketDir("RJ01294377"))
        assertNull(DlsiteParse.bucketDir("RJ12"))
    }

    @Test
    fun coverUrlForSwapsCodeInsideSameBucket() {
        val src = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ01276000/RJ01275417_img_main.jpg"
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ01276000/RJ01275416_img_main.jpg",
            DlsiteParse.coverUrlFor(src, "RJ01275416"),
        )
    }

    @Test
    fun coverUrlForMovesDirectoryWhenCrossingBucket() {
        val src = "https://img.dlsite.jp/modpub/images2/work/doujin/RJ01276000/RJ01275417_img_main.jpg"
        val out = DlsiteParse.coverUrlFor(src, "RJ01300112")
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ01301000/RJ01300112_img_main.jpg",
            out,
        )
    }

    @Test
    fun coverUrlForRefusesUnrelatedPaths() {
        // 目录不是编号目录（站内资源 / 别的站点）时宁可没有候选，也不虚构地址
        assertNull(DlsiteParse.coverUrlFor("https://img.dlsite.jp/common/logo.png", "RJ01275416"))
        assertNull(DlsiteParse.coverUrlFor("https://example.com/a/b.jpg", "RJ01275416"))
        assertNull(DlsiteParse.coverUrlFor(null, "RJ01275416"))
    }

    @Test
    fun parsesCdnXmlError() {
        val body = """<?xml version="1.0" encoding="UTF-8"?>
            <Error><Code>NoSuchKey</Code><Message>The specified key does not exist.</Message></Error>"""
        assertEquals("NoSuchKey: The specified key does not exist.", DlsiteCdnError.parse(body))
        assertTrue(DlsiteCdnError.isMissing(body))
        assertNull(DlsiteCdnError.parse("<html><body>not an image</body></html>"))
        assertNull(DlsiteCdnError.parse(""))
    }

    @Test
    fun classifySeparatesGateStates() {
        assertEquals(
            DlsitePageState.AGE_CONFIRMATION,
            DlsiteParse.classify("<html>年齢確認が必要です</html>", "RJ01275416"),
        )
        assertEquals(DlsitePageState.NOT_FOUND, DlsiteParse.classify("", "RJ01275416", 404))
        assertEquals(DlsitePageState.NETWORK_ERROR, DlsiteParse.classify("", "RJ01275416", 0))
        assertEquals(
            DlsitePageState.CAPTCHA,
            DlsiteParse.classify("<html>アクセスが集中しています</html>", "RJ01275416"),
        )
        assertEquals(
            DlsitePageState.LOGIN_REQUIRED,
            DlsiteParse.classify("<html><body>ログイン パスワード</body></html>", "RJ01275416"),
        )
        assertEquals(
            DlsitePageState.PAGE_CHANGED,
            DlsiteParse.classify("<html><body>something else</body></html>", "RJ01275416", 200),
        )
        assertEquals(
            DlsitePageState.SUCCESS,
            DlsiteParse.classify("<html>RJ01275416</html>", "RJ01275416", 200),
        )
    }

    @Test
    fun parseRecordsTranslationFromPageUrl() {
        val page = """
            <html><head>
            <meta property="og:title" content="作品 [RJ01275416] | DLsite">
            <meta property="og:image" content="https://img.dlsite.jp/modpub/images2/work/doujin/RJ01276000/RJ01275416_img_main.jpg">
            </head><body>RJ01275416</body></html>
        """.trimIndent()
        val w = DlsiteParse.parse(page, "RJ01275416", translationPageUrl)!!
        assertEquals("RJ01275416", w.originalCode)
        assertEquals("RJ01275417", w.translationCode)
        assertEquals(DlsitePageState.SUCCESS.name, w.pageState)
    }

    @Test
    fun coverCandidatesReachSrcsetOnlyImages() {
        val page = """
            <html><head>
            <meta property="og:title" content="作品 [RJ01275416] | DLsite">
            </head><body>
            <img class="work_thumb" srcset="//img.dlsite.jp/modpub/images2/work/doujin/RJ01276000/RJ01275416_img_main.jpg 1x">
            RJ01275416
            </body></html>
        """.trimIndent()
        val w = DlsiteParse.parse(page, "RJ01275416")!!
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ01276000/RJ01275416_img_main.jpg",
            w.coverUrl,
        )
    }

    @Test
    fun srcsetUrlsSplitsDescriptors() {
        assertEquals(
            listOf("a.jpg", "b.jpg"),
            DlsiteParse.srcsetUrls("a.jpg 1x, b.jpg 2x"),
        )
        assertEquals(listOf("c.jpg"), DlsiteParse.srcsetUrls("c.jpg 640w,"))
    }
}
