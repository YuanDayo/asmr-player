package com.kiite.player.core

/** 登录态：未验证成功前绝不显示「已登录」。 */
enum class DlsiteLoginState { LOGGED_IN, LOGGED_OUT, UNKNOWN }

/** 已购买作品条目。 */
data class DlsitePurchase(
    val code: String,
    val title: String? = null,
    /** 行内有音声 / ASMR / ボイス 等字样。 */
    val asmr: Boolean = false,
)

/**
 * DLsite 登录态与已购作品的判定。
 *
 * 注意：不能只看「有没有 cookie」——访问 DLsite 首页本身就会种下会话 / 年龄确认 cookie，
 * 未登录也会有一堆 cookie，所以必须拿一个「只有登录后才能打开」的页面去验证。
 */
object DlsiteAuth {

    /**
     * 真实购买记录页：/maniax/mypage/userbuy/=/type/all/start/all/sort/1/order/1/page/N
     * （原来的 /mypage/trade/... 会 404。）
     */
    fun purchaseUrl(page: Int = 1): String =
        "https://www.dlsite.com/maniax/mypage/userbuy/=/type/all/start/all/sort/1/order/1/page/" + page

    /** 按顺序尝试，取第一个能打开且已登录的。 */
    val PURCHASE_URLS = listOf(
        purchaseUrl(1),
        "https://www.dlsite.com/maniax/mypage/userbuy",
        "https://www.dlsite.com/maniax/mypage/",
    )

    const val PURCHASE_URL = "https://www.dlsite.com/maniax/mypage/userbuy"

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

    /** 购买记录页里每行都是 .work_name > a，指向作品页。 */
    private val LINK = Regex(
        """<a\s[^>]*href="([^"]*?/product_id/(RJ\d{6,8})\.html[^"]*)"[^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val TAG = Regex("""<[^>]+>""")
    private val WS = Regex("""\s+""")

    fun clean(raw: String): String =
        DlsiteParse.unescape(TAG.replace(raw, " ")).let { WS.replace(it, " ") }.trim()

    /** 一行 = 一部作品；整行文本里出现音声相关词就当作 ASMR 作品。 */
    private val ROW = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
    private val ASMR_WORDS = listOf("asmr", "音声", "ボイス", "voiced", "バイノーラル", "オーディオ")

    fun looksAsmr(rowText: String): Boolean = ASMR_WORDS.any { rowText.contains(it, ignoreCase = true) }

    fun parse(html: String): List<DlsitePurchase> {
        val out = ArrayList<DlsitePurchase>()
        for (row in ROW.findAll(html)) {
            val block = row.value
            val m = LINK.find(block) ?: continue
            out.add(
                DlsitePurchase(
                    code = m.groupValues[2].uppercase(),
                    title = clean(m.groupValues[3]).ifBlank { null },
                    asmr = looksAsmr(clean(block)),
                ),
            )
        }
        if (out.isEmpty()) {
            // 兜底：分不出行时按链接抓，asmr 未知（按 true 处理，避免漏掉）
            for (m in LINK.findAll(html)) {
                out.add(
                    DlsitePurchase(
                        code = m.groupValues[2].uppercase(),
                        title = clean(m.groupValues[3]).ifBlank { null },
                        asmr = true,
                    ),
                )
            }
        }
        return out.distinctBy { it.code }
    }

    /** 页码导航里的最大页码；拿不到就当 1。 */
    fun lastPage(html: String): Int =
        Regex("""data-value="(\d+)"""")
            .findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull()
            ?.coerceIn(1, 20)
            ?: 1
}
