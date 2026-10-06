package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteAuthTest {

    private val purchasePage = """
        <html><body>
        <div class="page_no"><ul>
          <li><a data-value="1">1</a></li><li><a data-value="3">3</a></li>
        </ul></div>
        <table class="work_list_main">
          <tr class="item_name"><th>作品</th></tr>
          <tr><td class="work_name"><a href="https://www.dlsite.com/maniax/work/=/product_id/RJ123456.html">【耳かき】<span>作品A</span></a></td>
              <td class="buy_date">2024/01/02</td></tr>
          <tr><td class="work_name"><a href="/maniax/work/=/product_id/RJ234567.html?locale=zh_CN">作品B</a></td>
              <td class="buy_date">2024/02/03</td></tr>
          <tr><td class="work_name"><a href="https://www.dlsite.com/maniax/work/=/product_id/RJ123456.html">【耳かき】作品A</a></td></tr>
        </table>
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
        assertEquals("【耳かき】 作品A", list[0].title)
        assertEquals("RJ234567", list[1].code)
        assertEquals("作品B", list[1].title)
    }

    @Test
    fun readsLastPageFromPager() {
        assertEquals(3, DlsitePurchaseParse.lastPage(purchasePage))
        assertEquals(1, DlsitePurchaseParse.lastPage("<html></html>"))
    }

    @Test
    fun buildsUserbuyUrl() {
        assertEquals(
            "https://www.dlsite.com/maniax/mypage/userbuy/=/type/all/start/all/sort/1/order/1/page/2",
            DlsiteAuth.purchaseUrl(2),
        )
    }

    @Test
    fun returnsEmptyForPageWithoutWorks() {
        assertTrue(DlsitePurchaseParse.parse("<html><body>nothing</body></html>").isEmpty())
    }
}
