package com.kiite.player.data

import com.kiite.player.AppInfo
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UpdateInfo(val tag: String, val url: String, val notes: String = "")

/** 通过 GitHub Releases 检查新版本。失败一律静默。 */
object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Release(val tag_name: String = "", val html_url: String = "", val body: String = "")

    fun check(): UpdateInfo? = runCatching {
        val conn = (URL("https://api.github.com/repos/YuanDayo/asmr-player/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "kiite-player")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        conn.disconnect()
        val rel = json.decodeFromString(Release.serializer(), text)
        val latest = rel.tag_name.removePrefix("v").trim()
        if (latest.isBlank() || !isNewer(latest, AppInfo.VERSION_NAME)) null else UpdateInfo(rel.tag_name, rel.html_url, rel.body.take(400))
    }.getOrNull()

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
