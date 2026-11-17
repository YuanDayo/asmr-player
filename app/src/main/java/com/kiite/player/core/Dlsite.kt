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
        // 页面里必须出现这个编号：年龄确认页 / 首页 / 风控页都不含，却同样带 og:title 与 og:image，
        // 直接解析会把 DLsite 站内 banner 当成每一部作品的封面（真机踩过：一堆作品同一张头图）。
        if (!looksLikeProductPage(html, code)) return null
        val metas = metaMap(html)
        val raw = metas["og:title"]
            ?: TITLE_TAG.find(html)?.groupValues?.get(1)?.let(::unescape)
        val title = raw?.substringBefore(" | DLsite")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return DlsiteWork(
            code = code.uppercase(),
            title = title,
            circle = MAKER.find(html)?.groupValues?.get(1)?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() },
            coverUrl = coverCandidates(html, metas, code).firstOrNull(),
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

    /**
     * 这一页到底是不是「这个编号的作品页」。
     *
     * DLsite 在未登录 / 未过年龄确认时会把作品页换成年龄确认页或引导页，这些页面
     * 同样有 og:title、og:image，直接解析就会得到「标题像样、封面是站内 banner」的假数据，
     * 而且会被缓存下来。作品页一定会在 URL / canonical / 面包屑里带上作品编号，据此判定。
     */
    fun looksLikeProductPage(html: String, code: String): Boolean {
        val c = code.trim()
        if (c.isEmpty()) return false
        if (isAgeGate(html)) return false
        return html.contains(c, ignoreCase = true)
    }

    /** 站内 banner / logo / 通用 OGP 图，绝不能当作品封面。 */
    private val SITE_ASSET_HINTS = listOf(
        "logo",
        "banner",
        "ogp",
        "common/",
        "adult_check",
        "age_verification",
        "noimage",
        "no_image",
        "now_printing",
        "dummy",
    )

    /** 作品页里的图片标签，按出现顺序取候选封面。 */
    private val IMG_TAG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    /**
     * 懒加载的页面里 `src` 是占位图、真地址在 `data-src` 里，所以不能只取第一个属性：
     * 先收全部 data-*，再退回 src。
     */
    private val IMG_SRC = Regex(
        """(data-src|data-original|data-lazy-src|src)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    /** 取一张 img 标签里最可能是真实地址的属性。 */
    private fun imgUrl(tag: String): String? {
        var plain: String? = null
        val lazy = ArrayList<String>(2)
        for (m in IMG_SRC.findAll(tag)) {
            val value = m.groupValues[2]
            if (value.isBlank()) continue
            if (m.groupValues[1].equals("src", ignoreCase = true)) {
                if (plain == null) plain = value
            } else {
                lazy.add(value)
            }
        }
        return lazy.firstOrNull() ?: plain
    }
    /** JSON-LD 里的 "image": "…" 或 "image": ["…"]。 */
    private val JSON_LD_IMAGE = Regex(""""image"\s*:\s*(?:"([^"]+)"|\[\s*"([^"]+)")""", RegexOption.IGNORE_CASE)

    /**
     * 样本图（试听 / 截图）的命名：`RJxxxxxxx_img_smp1.jpg`。
     *
     * 作品页的图片轮播里，第 1 张是封面主图 `_img_main`，第 2 张起全是样本图。
     * **og:image 在部分模板（尤其翻译页）指向的是第一张样本图**，于是整条链路都拿它当封面，
     * 界面上就出现一张试听截图。这类地址带作品编号、能正常下载、尺寸也不夸张，
     * 靠「像不像封面」的老判据完全拦不住，只能认文件名。
     */
    private val SAMPLE_URL = Regex("""_smp\d*|img_sample|/sample/""", RegexOption.IGNORE_CASE)

    /** 样本图地址 → 同作品封面主图地址，用来把历史上存错的封面救回来。 */
    private val SAMPLE_TO_MAIN = Regex("""_(?:img_)?smp\d*""", RegexOption.IGNORE_CASE)

    /** 是不是「样本图」地址（不是封面）。 */
    fun isSampleCover(url: String?): Boolean = SAMPLE_URL.containsMatchIn(url.orEmpty())

    /**
     * 把样本图地址改写成同一作品的封面主图地址；不是样本图时返回 null。
     *
     * 少数旧作品的页面里压根没有 `_img_main`，但按 DLsite 的命名规则同一目录下一定有主图，
     * 所以「`RJ01439468_img_smp1.jpg` → `RJ01439468_img_main.jpg`」这条改写是可靠的兜底。
     */
    fun mainFromSample(url: String?): String? {
        val u = normalizeUrl(url) ?: return null
        if (!SAMPLE_URL.containsMatchIn(u)) return null
        return u.replace(SAMPLE_TO_MAIN, "_img_main")
    }

    /**
     * 按可信度列出候选封面地址，只留「像这部作品封面」的那些。
     *
     * 只认 og:image 是不够的：DLsite 在未登录 / 未过年龄确认时给的 og:image 常常是站内
     * 宣传图，而正文里的商品图仍然是对的。所以这里把页面里所有可能的来源都收一遍，
     * 再按 [coverRank] 排序 —— 封面主图（`_img_main`）永远排在样本图与其他图前面。
     * 顺序：og:image → twitter:image → JSON-LD image → 正文 img（含懒加载属性）。
     */
    fun coverCandidates(html: String, metas: Map<String, String>, code: String): List<String> {
        val out = ArrayList<String>(8)
        fun add(raw: String?) {
            val u = normalizeUrl(raw) ?: return
            if (u !in out) out.add(u)
        }
        add(metas["og:image"])
        add(metas["twitter:image"])
        add(metas["twitter:image:src"])
        JSON_LD_IMAGE.findAll(html).forEach { add(it.groupValues[1].ifBlank { it.groupValues[2] }) }
        for (tag in IMG_TAG.findAll(html)) {
            val src = imgUrl(tag.value) ?: continue
            // 先只看带 modpub 的商品图，减少噪声；后面的兜底再放宽
            if (src.contains("modpub", ignoreCase = true)) add(src)
        }
        if (out.none { isPlausibleCover(it, code) }) {
            // 兜底：正文里任何地址里带作品编号的图（部分旧作品不在 modpub 目录下）
            for (tag in IMG_TAG.findAll(html)) {
                val src = imgUrl(tag.value) ?: continue
                add(src)
            }
        }
        // 样本地址先推导出主图地址再加进来：页面只给了样本图时，这是唯一的正确线索
        for (u in out.toList()) {
            mainFromSample(u)?.let { add(it) }
        }
        return out
            .filter { coverRank(it, code) >= 0 }
            .sortedByDescending { coverRank(it, code) }
    }

    /**
     * 封面地址的可信度，越大越像封面；负数表示不可能是封面。
     *
     * 100 = 封面主图、80 = 主图缩略版、60 = 同作品的其他商品图、40 = 兜底匹配、
     * 30 = 样本图（试听截图）。
     *
     * 样本图给正分而不是排除：它是这部作品自己的图、能正常下载，作为**最后的兜底**
     * 比一片空白好（2.4.0 及更早版本就是直接用它）。排序上永远排在封面主图之后。
     */
    fun coverRank(url: String?, code: String): Int {
        val u = normalizeUrl(url) ?: return -1
        if (!isPlausibleCover(u, code)) return -1
        val lower = u.lowercase()
        return when {
            lower.contains("_img_main") && !lower.contains("thumb") -> 100
            lower.contains("_img_main") -> 80
            isSampleCover(lower) -> 30
            lower.contains("/modpub/") -> 60
            else -> 40
        }
    }

    /** 补全协议、去实体转义；相对地址一律丢弃（拼不准反而更糟）。 */
    fun normalizeUrl(raw: String?): String? {
        val u = unescape(raw?.trim().orEmpty())
        if (u.isEmpty()) return null
        if (u.startsWith("//")) return "https:" + u
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return null
        return u
    }

    /**
     * 封面地址是否像「这部作品的封面」。
     *
     * 作品封面在 img.dlsite.jp 的 modpub 目录下，路径里带作品编号
     * （…/work/doujin/RJ01234567/RJ01234567_img_main.jpg）。站内 banner 的路径里没有编号，
     * 靠这一条就能把它们排除掉；宁可没有封面（退回本地图片 / 用户自选），
     * 也不要把别的图贴上去。
     *
     * 注意：这一条只是**地址层**的过滤。DLsite 还会「地址对、内容错」——同一串地址在
     * 没有会话时回一张站内横幅，所以下载后还要用 [CoverGuard] 看真实尺寸。
     *
     * 样本图（`_img_smpN`，试听截图）同样带作品编号、能下载、尺寸正常，所以也算「这部
     * 作品的图」，只是可信度低（见 [coverRank]）。不能因为它是样图就判整条记录无效 ——
     * 那样界面上一张图都没有，比显示一张不够理想的图更糟。
     */
    fun isPlausibleCover(url: String?, code: String): Boolean {
        val u = normalizeUrl(url) ?: return false
        val lower = u.lowercase()
        if (SITE_ASSET_HINTS.any { lower.contains(it) }) return false
        return lower.contains(code.trim().lowercase())
    }

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
