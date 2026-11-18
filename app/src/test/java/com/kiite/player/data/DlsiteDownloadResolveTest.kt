package com.kiite.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DlsiteDownloadResolveTest {

    @Test
    fun keepsOnlyTokenCookieForCdn() {
        // 302 会同时下发临时令牌与一些无关键；CDN 只认令牌，且不要把会话 Cookie 带出去
        val header = listOf(
            "JWT=eyJhbGciOi.payload.sig; Path=/; Secure; HttpOnly",
            "dlsite_cookie=abc; path=/; domain=.dlsite.com",
        )
        assertEquals("JWT=eyJhbGciOi.payload.sig", DlsiteClient.downloadCookie(header))
    }

    @Test
    fun fallsBackToAllPairsWithoutToken() {
        val header = listOf("dl_token=xyz; Path=/; Expires=Wed, 21 Oct 2026 07:28:00 GMT")
        assertEquals("dl_token=xyz", DlsiteClient.downloadCookie(header))
    }

    @Test
    fun noSetCookieMeansNoCookie() {
        assertNull(DlsiteClient.downloadCookie(emptyList()))
    }

    @Test
    fun countsFilesFromProductJson() {
        val single = """[{"workno":"RJ01234567","contents":[{"type":"audio"}]}]"""
        assertEquals(1, com.kiite.player.core.DlsiteProductJson.contentsCount(single))
        val three = """[{"workno":"RJ01234567","contents":[{},{},{}]}]"""
        assertEquals(3, com.kiite.player.core.DlsiteProductJson.contentsCount(three))
        assertEquals(0, com.kiite.player.core.DlsiteProductJson.contentsCount("[]"))
        assertEquals(0, com.kiite.player.core.DlsiteProductJson.contentsCount("not json"))
    }
}
