package com.asmrplayer.data

import com.asmrplayer.core.DlsiteParse
import com.asmrplayer.core.DlsiteWork
import java.net.HttpURLConnection
import java.net.URL

/** 抓取 DLsite 公开作品页（不需要账号）。 */
object DlsiteClient {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

    fun workUrl(code: String): String =
        "https://www.dlsite.com/maniax/work/=/product_id/" + code.uppercase() + ".html"

    fun fetch(code: String): Result<DlsiteWork> = runCatching {
        val conn = (URL(workUrl(code)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
        }
        try {
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            DlsiteParse.parse(html, code) ?: error("页面里没找到作品信息")
        } finally {
            conn.disconnect()
        }
    }
}
