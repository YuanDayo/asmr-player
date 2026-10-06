package com.kiite.player.core

/** 登录态：未验证成功前绝不显示「已登录」。 */
enum class DlsiteLoginState { LOGGED_IN, LOGGED_OUT, UNKNOWN }

/** 已购买作品条目。 */
data class DlsitePurchase(
    val code: String,
    val title: String? = null,
)

/**
 * DLsite 登录态与已购作品的判定。
 *
 * 注意：不能只看「有没有 cookie」——访问 DLsite 首页本身就会种下会话 / 年龄确认 cookie，
 * 未登录也会有一堆 cookie，所以必须拿一个「只有登录后才能打开」的页面去验证。
 */
object DlsiteAuth {

    /** 购买记录页，未登录会被重定向到登录页。 */
    const val PURCHASE_URL = "https://www.dlsite.com/maniax/mypage/trade/=/mode/list"

    /** 最终落到的地址里有 login，或页面里有密码输入框，就说明没登录。 */
    fun looksLoggedOut(finalUrl: String, html: String): Boolean {
        val u = finalUrl.lowercase()
        if (u.contains("/login")) return true
        val lower = html.lowercase()
        return lower.contains("name=\"login_id\"") ||
            lower.contains("id=\"login_id\"") ||
            (lower.contains("パスワード") && lower.contains("<form"))
    }
}

/** 从购买记录页里抽出作品编号与标题。 */
object DlsitePurchaseParse {

    private val LINK = Regex(
        """href="([^"]*?/work/=/product_id/(RJ\d{6,8})\.html[^"]*)"[^>]*>([^<]{0,140})<""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(html: String): List<DlsitePurchase> =
        LINK.findAll(html)
            .map { m ->
                DlsitePurchase(
                    code = m.groupValues[2].uppercase(),
                    title = DlsiteParse.unescape(m.groupValues[3]).trim().ifBlank { null },
                )
            }
            .distinctBy { it.code }
            .toList()
}
