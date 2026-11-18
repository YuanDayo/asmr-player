package com.kiite.player.core

import kotlinx.serialization.Serializable

/** 登录态：未验证成功前绝不显示「已登录」。 */
enum class DlsiteLoginState { LOGGED_IN, LOGGED_OUT, UNKNOWN }

/** 已购买作品条目。 */
@Serializable
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

    /** 官方在线播放器（用户提供：https://play.dlsite.com/work/RJ01688387）。 */
    fun playUrl(code: String): String = "https://play.dlsite.com/work/" + code.uppercase()

    /** 下载页：登录后在此页点下载，应用会拦截下载请求并保存到本地。 */
    fun downloadUrl(code: String): String =
        "https://www.dlsite.com/maniax/download/=/product_id/" + code.uppercase() + ".html"

    /**
     * 原生下载要打的入口（按顺序试）。
     *
     * 购买记录是全站通用的，`/home/download` 对 maniax 作品同样有效，而且它**不做年龄确认**，
     * 是三个分区里最稳的一个。`/maniax/download` 作为退路（个别作品只在分区内可下）。
     */
    fun downloadPageUrls(code: String): List<String> {
        val c = code.uppercase()
        return listOf(
            "https://www.dlsite.com/home/download/=/product_id/$c.html",
            "https://www.dlsite.com/maniax/download/=/product_id/$c.html",
        )
    }

    /** 分卷作品第 [index] 个文件（从 1 开始）的下载入口；同样返回 302 + 临时 Cookie。 */
    fun downloadFileUrl(code: String, index: Int): String =
        "https://www.dlsite.com/home/download/=/number/" + index +
            "/product_id/" + code.uppercase() + ".html"

    /** 作品页（详情/购买页）：直链下载页对未购买作品会 403，未购作品一律从作品页进。 */
    fun workUrl(code: String): String =
        "https://www.dlsite.com/maniax/work/=/product_id/" + code.uppercase() + ".html"

    /**
     * 补上「已确认年龄」的 Cookie。
     *
     * DLsite 的年龄门槛读的是 `adultchecked`，登录/浏览过成人区之后浏览器里才会出现。
     * 应用里的 WebView 只用来登录，用户可能从没进过 maniax，于是搜索和识别会被年龄确认页
     * 挡住 —— 用户看到的就是「一直提示需要年龄认证」。这个 Cookie 只是「我确认已成年」的
     * 自我声明，与我们自己的账号无关，可以安全地替用户带上。
     */
    fun withAdultChecked(cookie: String?): String {
        val base = cookie?.trim().orEmpty()
        if (Regex("""(^|;\s*)adultchecked\s*=""").containsMatchIn(base)) return base
        return if (base.isEmpty()) ADULT_COOKIE else base.trimEnd(';') + "; " + ADULT_COOKIE
    }

    const val ADULT_COOKIE = "adultchecked=1"

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

/**
 * 一次下载任务里的一个文件。
 *
 * DLsite 的下载入口会 **302 到 CDN 上的真实直链**，同时在响应里 `Set-Cookie` 一个临时令牌
 * （JWT）。这个令牌与登录会话是两回事：真实直链**只认它**，带上登录 Cookie 去拉反而会被
 * 拒（用户反馈的「已购作品下载 403」就是这么来的）。
 */
data class DlsiteDownloadFile(
    /** 分卷编号，从 1 开始；单文件作品固定为 1。 */
    val index: Int,
    /** CDN 上的真实直链（带签名参数）。 */
    val url: String,
    /** 这条直链专用的临时 Cookie；可能为空。 */
    val cookie: String?,
    /** 期望的文件名（拿不到时为 null，交给 Content-Disposition 猜）。 */
    val fileName: String?,
    /** 取到这条直链的页面：CDN 会看 Referer，缺了容易被当盗链。 */
    val referer: String? = null,
)

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
