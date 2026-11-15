package com.kiite.player.core

import kotlinx.serialization.Serializable

/** DLsite 作品元数据（按 RJ/VJ/BJ 编号抓取）。 */@Serializable
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
    /** 作品简介：优先 og:description / description，其次从作品详情块里抽。 */
    val synopsis: String? = null,
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
            synopsis = extractSynopsis(metas, html),
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

    /** 作品简介：meta 描述 → 作品详情块 → 都不行就返回 null。 */
    private fun extractSynopsis(metas: Map<String, String>, html: String): String? {
        val fromMeta = listOf("og:description", "description", "twitter:description")
            .firstNotNullOfOrNull { metas[it] }
        val body = WORK_DESC.findAll(html)
            .map { unescape(it.groupValues[1]) }
            .maxByOrNull { it.length }
        val text = (listOfNotNull(fromMeta, body).maxByOrNull { it.length }) ?: return null
        val cleaned = cleanText(text)
        return cleaned.takeIf { it.length >= 12 }
    }

    /** 去掉 HTML 残留与多余空白。 */
    private fun cleanText(s: String): String = s
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val WORK_DESC = Regex(
        """(?:itemprop\s*=\s*["']description["'][^>]*>|class\s*=\s*["'][^"']*(?:work_parts|work_article|work_text)[^"']*["'][^>]*>)([\s\S]{20,4000}?)</""",
        RegexOption.IGNORE_CASE,
    )

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

/** DLsite 站内搜索结果条目（从搜索结果页解析）。 */
data class DlsiteSearchHit(
    val code: String,
    val title: String? = null,
    val circle: String? = null,
    /** 标价文本（如「880円」），解析不到为 null。 */
    val price: String? = null,
)

/** 站内搜索地址（DLsite 的路径式筛选格式：/fsr/=/keyword/<关键词>）。 */
object DlsiteSearch {

    fun url(keyword: String): String =
        "https://www.dlsite.com/maniax/fsr/=/keyword/" +
            java.net.URLEncoder.encode(keyword.trim(), "UTF-8") + "/order/trend"
}

/**
 * 从搜索结果页 HTML 里抽出作品。
 * 解析策略与购买记录页相同：先按结构化块（work_name）解析，失败再退到「按 product_id 链接兜底」。
 */
object DlsiteSearchParse {

    /** 结果条目的标题块：<dl class="work_name"><dt><a href="…product_id/RJ….html">标题</a></dt>…</dl> */
    private val WORK_NAME = Regex(
        """class\s*=\s*["'][^"']*work_name[^"']*["'][^>]*>([\s\S]{10,3000}?)</dl>""",
        RegexOption.IGNORE_CASE,
    )
    private val LINK = Regex(
        """<a\s[^>]*href="([^"]*?/product_id/(RJ\d{6,8})\.html[^"]*)"[^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val CIRCLE = Regex(
        """href\s*=\s*["'][^"']*?/circle/[^\s"']*["'][^>]*>([^<]+)<""",
        RegexOption.IGNORE_CASE,
    )
    private val PRICE = Regex("""([\d,]+\s*(?:円|JPY|pt|ポイント))""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")

    fun clean(raw: String): String =
        DlsiteParse.unescape(TAG.replace(raw, " ")).replace(Regex("""\s+"""), " ").trim()

    /** 搜索页的「0 件」提示；出现它就说明搜索成功但无结果（不是解析失败）。 */
    private val EMPTY_MARKERS = listOf("検索結果は0件", "該当する作品は見つかりません", "検索結果：0件")

    fun looksEmpty(html: String): Boolean = EMPTY_MARKERS.any { html.contains(it) }

    fun parse(html: String): List<DlsiteSearchHit> {
        if (html.isBlank()) return emptyList()
        val out = LinkedHashMap<String, DlsiteSearchHit>()
        for (block in WORK_NAME.findAll(html)) {
            val body = block.value
            val m = LINK.find(body) ?: continue
            val code = m.groupValues[2].uppercase()
            val title = clean(m.groupValues[3]).ifBlank { null }
            val circle = CIRCLE.find(body)?.groupValues?.get(1)?.let { clean(it) }?.ifBlank { null }
            val price = PRICE.find(body)?.value?.replace(Regex("""\s+"""), "")?.ifBlank { null }
            out[code] = DlsiteSearchHit(code = code, title = title, circle = circle, price = price)
        }
        if (out.isEmpty()) {
            // 兜底：整页按链接抓（标题可能是缩略图链接的空白，允许为 null）
            for (m in LINK.findAll(html)) {
                val code = m.groupValues[2].uppercase()
                if (out.containsKey(code)) continue
                out[code] = DlsiteSearchHit(code = code, title = clean(m.groupValues[3]).ifBlank { null })
            }
        }
        return out.values.take(50).toList()
    }
}
