package com.kiite.player.data

import com.kiite.player.core.AsmrOneAuthResponse
import com.kiite.player.core.AsmrOneSearchResponse
import com.kiite.player.core.AsmrOneTreeNode
import com.kiite.player.core.AsmrOneWork
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json

/**
 * asmr.one 搜索客户端。公开接口、无需登录（与 [DlsiteClient] 的抓取同源风格）。
 * 搜索端点把关键词放在路径里：/api/search/{关键词}?order=...&sort=...&page=...
 *
 * 文件树 / 媒体下载需要 Bearer token：访客账号 guest/guest 登录即可拿到（见 login）。
 * 官方 API 域名会轮换（api.asmr.one → api.asmr-300.com…），登录/树接口按列表依次尝试。
 */
object AsmrOneClient {

    private val BASES = listOf("https://api.asmr.one", "https://api.asmr-300.com")

    /** 登录成功时记住可用的 API 域名，后续请求直接用它。 */
    @Volatile
    var activeBase: String = BASES.first()
        private set

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
        val url = activeBase + "/api/search/" + URLEncoder.encode(kw, "UTF-8").replace("+", "%20") +
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

    /**
     * 访客登录拿 Bearer token（guest/guest，免费站的公开约定）。
     * 注意端点是 POST /api/auth/me（kikoeru 的登录路由就叫 me，/api/auth/login 不存在，
     * 打过去会被 JWT 中间件挡成 401）；依次尝试各官方域名，成功的那个记进 [activeBase]。
     * @return Pair(token, base)
     */
    fun login(name: String = "guest", password: String = "guest"): Result<Pair<String, String>> {
        var lastError: Throwable? = null
        for (base in BASES) {
            val outcome = runCatching {
                val url = URL(base + "/api/auth/me")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    doOutput = true
                    setRequestProperty("User-Agent", UA)
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    conn.outputStream.use {
                        it.write(json.encodeToString(
                            kotlinx.serialization.json.JsonObject.serializer(),
                            kotlinx.serialization.json.buildJsonObject {
                                put("name", kotlinx.serialization.json.JsonPrimitive(name))
                                put("password", kotlinx.serialization.json.JsonPrimitive(password))
                            },
                        ).toByteArray(Charsets.UTF_8))
                    }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        // 把服务端的错误说明带上（如 401 = 用户名或密码错误）
                        val detail = runCatching {
                            conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        }.getOrNull()?.take(120)
                        throw IOException("登录响应 " + code + (detail?.let { "：" + it } ?: ""))
                    }
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val resp = json.decodeFromString(AsmrOneAuthResponse.serializer(), body)
                    resp.token?.takeIf { it.isNotBlank() }
                        ?: throw IOException("登录响应里没有 token")
                } finally {
                    conn.disconnect()
                }
            }
            if (outcome.isSuccess) {
                activeBase = base
                return outcome.map { it to base }
            }
            lastError = outcome.exceptionOrNull()
        }
        return Result.failure(lastError ?: IOException("所有 asmr.one 域名都登录失败"))
    }

    /**
     * 拉作品的文件树（需 Bearer token）。端点是 /api/tracks/{id}（/api/tree 不存在，会 404）。
     * 注意真实响应顶层是**数组**（作品根目录是数组第一个元素），个别情况也可能直接给对象——
     * 按 JsonElement 分流：数组只有一个元素时取它当根，多个元素包一层合成根。
     */
    fun tree(workId: Long, token: String): Result<AsmrOneTreeNode> = runCatching {
        val url = activeBase + "/api/tracks/" + workId
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer " + token)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = runCatching {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()?.take(120)
                throw IOException("文件树响应 " + code + (detail?.let { "：" + it } ?: ""))
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            com.kiite.player.core.AsmrOneTree.parseRoot(body, json)
        } finally {
            conn.disconnect()
        }
    }

    /** 媒体下载直链：/api/media/{workId}/{相对路径}（路径按段 URL 编码，保留 /）。 */
    fun mediaUrl(workId: Long, source: String): String =
        activeBase + "/api/media/" + workId + "/" +
            source.trim('/').split('/').joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
}
