package com.kiite.player.data

import com.kiite.player.core.AsmrOneSearchResponse
import com.kiite.player.core.AsmrOneWork
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json

/**
 * asmr.one 搜索客户端。公开接口、无需登录（与 [DlsiteClient] 的抓取同源风格）。
 * 搜索端点把关键词放在路径里：/api/search/{关键词}?order=...&sort=...&page=...
 */
object AsmrOneClient {

    private const val BASE = "https://api.asmr.one"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 作品页（网页端是 SPA，供 WebView 打开后手动挑选文件下载）。 */
    fun workPageUrl(work: AsmrOneWork): String = "https://www.asmr.one/work/" + work.id

    /**
     * 关键词搜索，按下载量排序（免费站最实用的默认序）。
     * 返回「本页结果 + 总条数」，UI 用总条数判断还能不能加载下一页。
     */
    fun search(keyword: String, page: Int = 1): Result<Pair<List<AsmrOneWork>, Int>> = runCatching {
        val kw = keyword.trim()
        require(kw.isNotEmpty()) { "关键词为空" }
        val url = BASE + "/api/search/" + URLEncoder.encode(kw, "UTF-8").replace("+", "%20") +
            "?order=dl_count&sort=desc&page=" + page + "&subtitle=0"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
        }
        try {
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val resp = json.decodeFromString(AsmrOneSearchResponse.serializer(), body)
            resp.works to (resp.pagination?.totalCount ?: resp.works.size)
        } finally {
            conn.disconnect()
        }
    }
}
