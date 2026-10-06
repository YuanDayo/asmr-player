package com.asmrplayer.core

import kotlinx.serialization.Serializable

/** DLsite 作品元数据（按 RJ/VJ/BJ 编号抓取）。 */
@Serializable
data class DlsiteWork(
    val code: String,
    val title: String? = null,
    val circle: String? = null,
    val coverUrl: String? = null,
    val releaseDate: String? = null,
    val tags: List<String> = emptyList(),
    val fetchedAtMs: Long = 0L,
)

/**
 * 从 DLsite 作品页 HTML 里抽元数据。
 * 不用官方 API（需要单独申请 api_key），也不涉及账号登录，只看公开页面。
 */
object DlsiteParse {

    private val META_TAG = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""(property|name|content)\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val TITLE_TAG = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)
    private val MAKER = Regex("""/circle/profile/[^"']*["'][^>]*>([^<]+)<""", RegexOption.IGNORE_CASE)
    private val GENRE = Regex("""/works/=/genre/[^"']*["'][^>]*>([^<]+)<""", RegexOption.IGNORE_CASE)

    fun parse(html: String, code: String): DlsiteWork? {
        if (html.isBlank()) return null
        val metas = metaMap(html)
        val raw = metas["og:title"]
            ?: TITLE_TAG.find(html)?.groupValues?.get(1)?.let(::unescape)
        val title = raw?.substringBefore(" | DLsite")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return DlsiteWork(
            code = code.uppercase(),
            title = title,
            circle = MAKER.find(html)?.groupValues?.get(1)?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() },
            coverUrl = metas["og:image"],
            releaseDate = metas["og:release_date"] ?: metas["product:release_date"],
            tags = GENRE.findAll(html)
                .map { unescape(it.groupValues[1]).trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(8)
                .toList(),
            fetchedAtMs = System.currentTimeMillis(),
        )
    }

    /** meta 标签的属性顺序不固定，先切标签再取属性。 */
    fun metaMap(html: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (tag in META_TAG.findAll(html)) {
            var key: String? = null
            var value: String? = null
            for (a in ATTR.findAll(tag.value)) {
                when (a.groupValues[1].lowercase()) {
                    "property", "name" -> key = a.groupValues[2].lowercase()
                    "content" -> value = a.groupValues[2]
                }
            }
            if (key != null && value != null) out[key] = unescape(value)
        }
        return out
    }

    /** 成人向作品会先跳年龄确认页。 */
    fun isAgeGate(html: String): Boolean = html.contains("年齢確認") ||
        html.contains("age_verification") ||
        html.contains("Are you over 18") ||
        html.contains("adult_check")

    fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
