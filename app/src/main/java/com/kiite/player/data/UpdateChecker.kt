package com.kiite.player.data

import com.kiite.player.AppInfo
import com.kiite.player.core.SemVer
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一条更新提示。
 *
 * [url] 是给人看的 Release 页面；[apkUrl] 只在能确认是真实 Release 资产时才有值
 * （GitHub API 的 asset 列表 / version.json 显式声明），不允许拼接猜测。
 * [sha256] 是 **APK 文件摘要**，用来校验下载完整性，跟签名证书指纹不是一回事。
 */
@Serializable
data class UpdateInfo(
    val tag: String,
    val url: String,
    val notes: String = "",
    val apkUrl: String? = null,
    val sha256: String? = null,
    val prerelease: Boolean = false,
)

/**
 * 检查新版本。国内网络对 api.github.com 干扰严重（常见 connect closed），
 * 所以按以下顺序多源回退，任意一个成功即返回：
 *  1. jsDelivr CDN 上的 version.json（国内可达性最好）
 *  2. raw.githubusercontent.com 上的 version.json
 *  3. GitHub Releases API（能顺带拿到 APK 直链与哈希）
 *  4. GitHub Releases 网页的 302 跳转（从 Location 里取版本号）
 *
 * 版本比较走 [SemVer]；正式版用户不会收到 beta/alpha/rc，预发布用户仍能升到正式版。
 */
object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }
    private const val REPO = "YuanDayo/asmr-player"
    private const val FALLBACK_URL = "https://github.com/YuanDayo/asmr-player/releases/latest"

    data class Result(val info: UpdateInfo?, val error: String?)

    @Serializable
    private data class Asset(
        val name: String = "",
        val browser_download_url: String = "",
        val digest: String = "",
    )

    @Serializable
    private data class Release(
        val tag_name: String = "",
        val html_url: String = "",
        val body: String = "",
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class VersionFile(
        val version: String = "",
        val versionCode: Int = 0,
        val url: String = "",
        val notes: String = "",
        val apkUrl: String = "",
        val sha256: String = "",
    )

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

    private fun decide(
        latestTag: String,
        pageUrl: String,
        notes: String,
        apkUrl: String? = null,
        sha256: String? = null,
        latestCode: Int = 0,
        prereleaseFlag: Boolean = false,
    ): UpdateInfo? {
        val latest = SemVer.normalize(latestTag)
        if (latest.isBlank()) throw IllegalStateException("版本号为空")
        if (!shouldUpdate(latest, latestCode, prereleaseFlag)) return null
        return UpdateInfo(
            tag = "v" + latest,
            url = pageUrl.ifBlank { FALLBACK_URL },
            notes = notes,
            apkUrl = apkUrl?.takeIf { it.isNotBlank() },
            sha256 = sha256?.takeIf { it.isNotBlank() },
            prerelease = prereleaseFlag || SemVer.isPrerelease(latest),
        )
    }

    /**
     * 是否应该提示更新。
     *
     * - 正在用正式版的人不接收预发布包（beta/alpha/rc），避免普通用户被推到测试版；
     * - 正在用预发布的人可以升到正式版，也可以升到更高的 beta；
     * - 两个来源都有 versionCode 时以它为准，否则退回 SemVer 比较。
     */
    internal fun shouldUpdate(
        latest: String,
        latestCode: Int,
        prereleaseFlag: Boolean,
        current: String = AppInfo.VERSION_NAME,
        currentCode: Int = AppInfo.VERSION_CODE,
    ): Boolean {
        val candidateIsPre = prereleaseFlag || SemVer.isPrerelease(latest)
        if (candidateIsPre && !SemVer.isPrerelease(current)) return false
        if (latestCode > 0) return latestCode > currentCode
        return SemVer.compare(latest, current) > 0
    }

    private fun fromVersionFile(url: String): UpdateInfo? {
        val vf = json.decodeFromString(VersionFile.serializer(), http(url))
        return decide(
            latestTag = vf.version,
            pageUrl = vf.url,
            notes = vf.notes,
            apkUrl = vf.apkUrl,
            sha256 = vf.sha256,
            latestCode = vf.versionCode,
        )
    }

    private fun fromApi(): UpdateInfo? {
        val text = http(
            "https://api.github.com/repos/" + REPO + "/releases/latest",
            accept = "application/vnd.github+json",
        )
        val rel = json.decodeFromString(Release.serializer(), text)
        val apk = rel.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        return decide(
            latestTag = rel.tag_name,
            pageUrl = rel.html_url,
            notes = rel.body.take(400),
            apkUrl = apk?.browser_download_url,
            sha256 = apk?.digest?.removePrefix("sha256:"),
            prereleaseFlag = rel.prerelease,
        )
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

    /** 纯版本比较（不含更新通道策略）；a 比 b 新返回 true。 */
    fun isNewer(a: String, b: String): Boolean = SemVer.compare(a, b) > 0
}
