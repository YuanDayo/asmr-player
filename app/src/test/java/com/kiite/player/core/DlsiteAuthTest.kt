package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteAuthTest {

    private val purchasePage = """
        <html><body>
        <h1>購入履歴</h1>
        <a href="https://www.dlsite.com/maniax/work/=/product_id/RJ123456.html">【耳かき】作品A</a>
        <a href="/maniax/work/=/product_id/RJ234567.html?locale=zh_CN">作品B</a>
        <a href="https://www.dlsite.com/maniax/work/=/product_id/RJ123456.html">【耳かき】作品A（重复）</a>
        </body></html>
    """.trimIndent()

    private val loginPage = """
        <html><body><form action="/login/"><input name="login_id"><input type="password"></form>
        <p>パスワード</p></body></html>
    """.trimIndent()

    @Test
    fun detectsLoggedOutByRedirectedUrl() {
        assertTrue(DlsiteAuth.looksLoggedOut("https://www.dlsite.com/maniax/login/", purchasePage))
    }

    @Test
    fun detectsLoggedOutByLoginForm() {
        assertTrue(DlsiteAuth.looksLoggedOut(DlsiteAuth.PURCHASE_URL, loginPage))
    }

    @Test
    fun treatsPurchasePageWithoutLoginMarkersAsLoggedIn() {
        assertFalse(DlsiteAuth.looksLoggedOut(DlsiteAuth.PURCHASE_URL, purchasePage))
    }

    @Test
    fun parsesPurchasesAndDeduplicates() {
        val list = DlsitePurchaseParse.parse(purchasePage)
        assertEquals(2, list.size)
        assertEquals("RJ123456", list[0].code)
        assertEquals("【耳かき】作品A", list[0].title)
        assertEquals("RJ234567", list[1].code)
        assertEquals("作品B", list[1].title)
    }

    @Test
    fun returnsEmptyForPageWithoutWorks() {
        assertTrue(DlsitePurchaseParse.parse("<html><body>nothing</body></html>").isEmpty())
    }
}
