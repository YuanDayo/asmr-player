package com.kiite.player.data

import com.kiite.player.core.DlsiteParse
import com.kiite.player.core.DlsiteWork
import java.net.HttpURLConnection
import java.net.URL

/** 抓取 DLsite 公开作品页（不需要账号）。 */
object DlsiteClient {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

    fun workUrl(code: String): String =
        "https://www.dlsite.com/maniax/work/=/product_id/" + code.uppercase() + ".html"

    fun fetch(code: String, cookie: String? = null): Result<DlsiteWork> =
        fetchFrom(workUrl(code), code, cookie)

    /** 通用 GET，返回「最终地址 + 正文」。 */
    internal fun get(url: String, cookie: String? = null): Pair<String, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
            if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
        }
        return try {
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.url.toString() to html
        } finally {
            conn.disconnect()
        }
    }

    /** 真正验证登录态：拿只有登录后才能打开的购买记录页去试。 */
    fun verifyLogin(cookie: String?): com.kiite.player.core.DlsiteLoginState {
        if (cookie.isNullOrBlank()) return com.kiite.player.core.DlsiteLoginState.LOGGED_OUT
        return runCatching {
            val (finalUrl, html) = get(com.kiite.player.core.DlsiteAuth.PURCHASE_URL, cookie)
            if (com.kiite.player.core.DlsiteAuth.looksLoggedOut(finalUrl, html)) {
                com.kiite.player.core.DlsiteLoginState.LOGGED_OUT
            } else {
                com.kiite.player.core.DlsiteLoginState.LOGGED_IN
            }
        }.getOrDefault(com.kiite.player.core.DlsiteLoginState.UNKNOWN)
    }

    /** 拉已购作品列表。 */
    fun fetchPurchases(cookie: String?): Result<List<com.kiite.player.core.DlsitePurchase>> = runCatching {
        val (finalUrl, html) = get(com.kiite.player.core.DlsiteAuth.PURCHASE_URL, cookie)
        if (com.kiite.player.core.DlsiteAuth.looksLoggedOut(finalUrl, html)) error("未登录或登录已失效")
        com.kiite.player.core.DlsitePurchaseParse.parse(html)
    }

    /** 抽出来便于用本地 HTTP 服务做离线端到端验证。 */
    internal fun fetchFrom(url: String, code: String, cookie: String? = null): Result<DlsiteWork> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
            if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
        }
        try {
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            DlsiteParse.parse(html, code) ?: error(
                if (DlsiteParse.isAgeGate(html)) {
                    "该作品页需要年龄确认，请先在设置里登录 DLsite 账号"
                } else {
                    "页面里没找到作品信息（编号是否正确？）"
                },
            )
        } finally {
            conn.disconnect()
        }
    }
}
