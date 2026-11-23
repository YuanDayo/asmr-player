package com.kiite.player.data

import android.content.Context
import com.kiite.player.core.DlsitePurchase
import com.kiite.player.core.DlsiteWork
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 把抓到的 DLsite 作品信息缓存到本地，避免每次重扫都联网。 */
class DlsiteStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "dlsite-works.json")
    private val purchasesFile = File(context.filesDir, "dlsite-purchases.json")
    private val titleLinksFile = File(context.filesDir, "dlsite-title-links.json")
    /** 登录态快照：见 [loadCookies] 上的说明。 */
    private val cookiesFile = File(context.filesDir, "dlsite-cookies.json")

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

    /**
     * 「按标题识别」的映射：归一化标题 → 作品编号。
     *
     * 不记项目路径：用户整理文件夹时目录会移动/改名，只要文件夹标题还在就能继续认出来。
     */
    fun loadTitleLinks(): Map<String, String> {
        if (!titleLinksFile.isFile) return emptyMap()
        return runCatching {
            json.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                titleLinksFile.readText(),
            )
        }.getOrDefault(emptyMap())
    }

    fun saveTitleLinks(links: Map<String, String>) {
        runCatching {
            titleLinksFile.writeText(
                json.encodeToString(MapSerializer(String.serializer(), String.serializer()), links),
            )
        }
    }

    /** 已购列表持久化：重启后主页能直接显示「已购未下载」，不用每次手动同步。 */
    fun loadPurchases(): List<DlsitePurchase> {
        if (!purchasesFile.isFile) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(DlsitePurchase.serializer()), purchasesFile.readText())
        }.getOrDefault(emptyList())
    }

    fun savePurchases(list: List<DlsitePurchase>) {
        runCatching {
            purchasesFile.writeText(
                json.encodeToString(ListSerializer(DlsitePurchase.serializer()), list),
            )
        }
    }

    fun clearPurchases() {
        runCatching { purchasesFile.delete() }
    }

    // ---------- 登录态快照 ----------

    /**
     * 上次登录有效时的 Cookie 快照（url → 原始 Cookie 头）。
     *
     * 为什么要自己存一份：登录态完全活在 WebView 的 `CookieManager` 里，而 DLsite 的
     * 会话 Cookie 很可能是 **session cookie**（没有过期时间）—— 系统清理 WebView 数据、
     * 部分机型重启后直接丢，表现就是「每次退出应用都要重新登录」。
     * 存到 filesDir 之后，启动时先写回 CookieManager 再联网验证，丢一次也能自己长回来。
     *
     * 只存 dlsite.com 及其子域的 Cookie，和备份导出用的是同一份结构。
     */
    fun loadCookies(): Map<String, String> {
        if (!cookiesFile.isFile) return emptyMap()
        return runCatching {
            json.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                cookiesFile.readText(),
            )
        }.getOrDefault(emptyMap())
    }

    fun saveCookies(snapshot: Map<String, String>) {
        if (snapshot.isEmpty()) return
        runCatching {
            cookiesFile.writeText(
                json.encodeToString(MapSerializer(String.serializer(), String.serializer()), snapshot),
            )
        }
    }

    fun clearCookies() {
        runCatching { cookiesFile.delete() }
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

/** 备份用：把 DLsite 各子域下的 Cookie 原样抓成 url → 头。 */
fun dlsiteCookiesSnapshot(): Map<String, String> {
    val cm = android.webkit.CookieManager.getInstance()
    runCatching { cm.flush() }
    val out = LinkedHashMap<String, String>()
    for (u in DLsiteCookieUrls) {
        val raw = runCatching { cm.getCookie(u) }.getOrNull()?.takeIf { it.isNotBlank() } ?: continue
        out[u] = raw
    }
    return out
}

/** 恢复用：把备份里的 Cookie 逐条写回 WebView 的 CookieManager。 */
fun restoreDlsiteCookies(snapshot: Map<String, String>) {
    val cm = android.webkit.CookieManager.getInstance()
    snapshot.forEach { (url, header) ->
        header.split(';').forEach { part ->
            val kv = part.trim()
            if (kv.isEmpty() || !kv.contains('=')) return@forEach
            runCatching { cm.setCookie(url, kv) }
        }
    }
    runCatching { cm.flush() }
}
