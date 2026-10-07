package com.kiite.player.data

import com.kiite.player.AppInfo
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UpdateInfo(val tag: String, val url: String, val notes: String = "")

/**
 * 检查新版本。国内网络对 api.github.com 干扰严重（常见 connect closed），
 * 所以按以下顺序多源回退，任意一个成功即返回：
 *  1. jsDelivr CDN 上的 version.json（国内可达性最好）
 *  2. raw.githubusercontent.com 上的 version.json
 *  3. GitHub Releases API
 *  4. GitHub Releases 网页的 302 跳转（从 Location 里取版本号）
 */
object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }
    private const val REPO = "YuanDayo/asmr-player"
    private const val FALLBACK_URL = "https://github.com/YuanDayo/asmr-player/releases/latest"

    data class Result(val info: UpdateInfo?, val error: String?)

    @Serializable
    private data class Release(val tag_name: String = "", val html_url: String = "", val body: String = "")

    @Serializable
    private data class VersionFile(val version: String = "", val url: String = "", val notes: String = "")

    fun check(): UpdateInfo? = checkVerbose().info

    fun checkVerbose(): Result {
        val errors = mutableListOf<String>()
        val sources: List<Pair<String, () -> UpdateInfo?>> = listOf(
            "jsDelivr" to { fromVersionFile("https://cdn.jsdelivr.net/gh/" + REPO + "@main/version.json") },
            "raw.github" to { fromVersionFile("https://raw.githubusercontent.com/" + REPO + "/main/version.json") },
            "api" to { fromApi() },
            "web" to { fromWeb() },
        )
        for ((name, src) in sources) {
            try {
                return Result(src(), null)
            } catch (t: Throwable) {
                errors += name + ": " + (t.message ?: t.javaClass.simpleName)
            }
        }
        return Result(null, errors.joinToString(" / ").take(220))
    }

    private fun http(url: String, accept: String? = null, follow: Boolean = true): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = follow
            setRequestProperty("User-Agent", "kiite-player")
            if (accept != null) setRequestProperty("Accept", accept)
        }
        try {
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun decide(latestTag: String, url: String, notes: String): UpdateInfo? {
        val latest = latestTag.removePrefix("v").trim()
        if (latest.isBlank()) throw IllegalStateException("版本号为空")
        if (!isNewer(latest, AppInfo.VERSION_NAME)) return null
        return UpdateInfo("v" + latest, url.ifBlank { FALLBACK_URL }, notes)
    }

    private fun fromVersionFile(url: String): UpdateInfo? {
        val vf = json.decodeFromString(VersionFile.serializer(), http(url))
        return decide(vf.version, vf.url, vf.notes)
    }

    private fun fromApi(): UpdateInfo? {
        val text = http(
            "https://api.github.com/repos/" + REPO + "/releases/latest",
            accept = "application/vnd.github+json",
        )
        val rel = json.decodeFromString(Release.serializer(), text)
        return decide(rel.tag_name, rel.html_url, rel.body.take(400))
    }

    /** 不跟随跳转，直接从 302 的 Location 取 tag（网页版比 API 更容易连通）。 */
    private fun fromWeb(): UpdateInfo? {
        val conn = (URL(FALLBACK_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "kiite-player")
        }
        val tag = try {
            val loc = conn.getHeaderField("Location") ?: throw IllegalStateException("无跳转")
            loc.substringAfterLast("/tag/", "").trim()
        } finally {
            conn.disconnect()
        }
        return decide(tag, FALLBACK_URL, "")
    }

    /** 逐段比较版本号；a 比 b 新返回 true。 */
    fun isNewer(a: String, b: String): Boolean {
        val pa = a.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
