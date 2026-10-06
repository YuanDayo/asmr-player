package com.kiite.player.core

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
    /** 声优（DLsite 的 /fsr/=/voice/ 链接文本）。 */
    val voiceActors: List<String> = emptyList(),
    val fetchedAtMs: Long = 0L,
    /** 作品详情页地址，用于「跳转到 DLsite」。 */
    val productUrl: String? = null,
    /** 登录状态下页面是否标记为已购买。 */
    val owned: Boolean = false,
    /** 已下载到本地的封面路径（作为专辑封面用）。 */
    val coverLocalPath: String? = null,
    /** 是否成人向（根据作品页标记判断）。 */
    val r18: Boolean = false,
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
    private val GENRE = Regex("""/(?:fsr/=/genre|works/=/genre)/[^"']*["'][^>]*>([^<]+)<""", RegexOption.IGNORE_CASE)
    // 分类链接实测是 /fsr/=/genre/，声优按同一体系推断为 /fsr/=/voice/
    private val VOICE = Regex("""/(?:fsr/=/voice|works/=/voice)/[^"']*["'][^>]*>([^<]+)<""", RegexOption.IGNORE_CASE)

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
            voiceActors = VOICE.findAll(html)
                .map { unescape(it.groupValues[1]).trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(8)
                .toList(),
            fetchedAtMs = System.currentTimeMillis(),
            productUrl = productUrl(code),
            owned = looksOwned(html),
            r18 = RatingParse.looksR18(html, GENRE.findAll(html).map { it.groupValues[1] }.toList()),
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

    fun productUrl(code: String): String =
        "https://www.dlsite.com/maniax/work/=/product_id/" + code.uppercase() + ".html"

    /** 已登录且买过时，作品页会出现「購入済み」之类的标记。 */
    fun looksOwned(html: String): Boolean = OWNED_MARKERS.any { html.contains(it) }

    private val OWNED_MARKERS = listOf("購入済み", "ご購入済", "purchased", "already_purchased")

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
