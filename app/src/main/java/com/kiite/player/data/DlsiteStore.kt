package com.kiite.player.data

import android.content.Context
import com.kiite.player.core.DlsiteWork
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 把抓到的 DLsite 作品信息缓存到本地，避免每次重扫都联网。 */
class DlsiteStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "dlsite-works.json")

    fun load(): Map<String, DlsiteWork> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            json.decodeFromString(ListSerializer(DlsiteWork.serializer()), file.readText())
                .associateBy { it.code }
        }.getOrDefault(emptyMap())
    }

    fun save(works: Map<String, DlsiteWork>) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(DlsiteWork.serializer()), works.values.toList()))
        }
    }
}

/** WebView 登录后 Cookie 可能散落在这些子域上，收集时逐一查。 */
val DLsiteCookieUrls = listOf(
    "https://www.dlsite.com",
    "https://login.dlsite.com",
    "https://ssl.dlsite.com",
)

/**
 * 收集 DLsite 的 Cookie 头。
 * WebView 的 Cookie 存在系统的 CookieManager 里，HttpURLConnection 不会自动带上，必须手动转发；
 * 而且登录 Cookie 可能落在其它子域 / 路径上，只查 www 根路径经常取不到。
 */
fun dlsiteCookieHeader(): String? {
    val cm = android.webkit.CookieManager.getInstance()
    runCatching { cm.flush() }
    val pairs = LinkedHashMap<String, String>()
    for (u in DLsiteCookieUrls) {
        val raw = runCatching { cm.getCookie(u) }.getOrNull() ?: continue
        for (part in raw.split(";")) {
            val kv = part.trim()
            if (kv.isEmpty() || !kv.contains('=')) continue
            pairs[kv.substringBefore('=')] = kv
        }
    }
    return if (pairs.isEmpty()) null else pairs.values.joinToString("; ")
}

/** 诊断用：当前收集到多少个 Cookie。 */
fun dlsiteCookieCount(): Int =
    dlsiteCookieHeader()?.split(";")?.count { it.isNotBlank() } ?: 0

/** 退出登录：清掉所有 Cookie。 */
fun clearDlsiteSession() {
    android.webkit.CookieManager.getInstance().removeAllCookies(null)
    android.webkit.CookieManager.getInstance().flush()
}
