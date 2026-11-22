package com.kiite.player.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    /**
     * 原作编号。用户本地文件夹里的编号通常是原作（RJ01275416），但翻译版的页面
     * 会把翻译编号（RJ01275417）当主编号 —— 两者不能混用：原作编号是身份，
     * 翻译编号只是「这一页显示的版本」。没识别出来时退回 [code]。
     */
    val originalCode: String? = null,
    /** 页面上附带标注的翻译版本编号（没有就是 null）。 */
    val translationCode: String? = null,
    /** 上一次解析的页面状态，见 [DlsitePageState]；仅用于诊断。 */
    val pageState: String? = null,
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

    fun parse(html: String, code: String, pageUrl: String? = null): DlsiteWork? {
        if (html.isBlank()) return null
        // 页面里必须出现这个编号：年龄确认页 / 首页 / 风控页都不含，却同样带 og:title 与 og:image，
        // 直接解析会把 DLsite 站内 banner 当成每一部作品的封面（真机踩过：一堆作品同一张头图）。
        if (!looksLikeProductPage(html, code)) return null
        val metas = metaMap(html)
        val raw = metas["og:title"]
            ?: TITLE_TAG.find(html)?.groupValues?.get(1)?.let(::unescape)
        val title = raw?.substringBefore(" | DLsite")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val ids = ids(html, code, pageUrl)
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
            originalCode = ids.originalCode,
            translationCode = ids.translationCode,
            pageState = DlsitePageState.SUCCESS.name,
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

    // ---------- 原作编号 / 翻译编号 ----------
    //
    // DLsite 的翻译作品有独立的 product_id（如原作 RJ01275416、译文 RJ01275417），页面形态是
    // `/maniax/work/=/product_id/RJ01275416.html?translation=RJ01275417` —— **主编号是原作**，
    // 翻译编号只出现在参数里。把两者混用会出两种错：拿翻译编号去请求原作页、或者拿翻译编号
    // 拼 CDN 地址（译文常常没有独立的封面文件，下回来是 CDN 的 XML 错误）。

    private val PRODUCT_ID = Regex("""product_id[=/]([a-zA-Z]{2}\d{6,8})""", RegexOption.IGNORE_CASE)
    private val TRANSLATION_PARAM = Regex("""translation=([a-zA-Z]{2}\d{6,8})""", RegexOption.IGNORE_CASE)
    private val CANONICAL = Regex(
        """<link\b[^>]*rel\s*=\s*["']canonical["'][^>]*href\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    /** 页面地址里的主作品编号（`…/product_id/RJ01275416.html`）。 */
    fun pageProductId(url: String?): String? =
        PRODUCT_ID.find(url.orEmpty())?.groupValues?.get(1)?.uppercase()

    /**
     * 拆出「用户请求的编号 / 原作编号 / 翻译编号」。
     *
     * 判定顺序刻意这样排：
     *  1. 页面主编号 == 请求编号 → 请求的就是这一页的身份，`translation=` 里那个是译文；
     *  2. 请求编号出现在 `translation=` 里 → 用户给的是译文编号，页面主编号才是原作；
     *  3. 页面没给任何关联信息 → original 退回请求编号，translation 为 null。
     *
     * 第 1、2 条都能对上真实的翻译页形态，第 3 条保证不猜。
     */
    fun ids(html: String, requested: String, pageUrl: String? = null): DlsiteIds {
        val req = requested.trim().uppercase()
        val translations = LinkedHashSet<String>()
        TRANSLATION_PARAM.findAll(pageUrl.orEmpty()).forEach { translations.add(it.groupValues[1].uppercase()) }
        TRANSLATION_PARAM.findAll(html).forEach { translations.add(it.groupValues[1].uppercase()) }
        val pageMain = pageProductId(pageUrl)
            ?: CANONICAL.find(html)?.groupValues?.get(1)?.let(::pageProductId)
        val other = translations.firstOrNull { it != req }
        return when {
            pageMain == null -> DlsiteIds(requested = req, originalCode = req, translationCode = other, pageProductCode = null)
            pageMain == req -> DlsiteIds(requested = req, originalCode = req, translationCode = other, pageProductCode = pageMain)
            req in translations -> DlsiteIds(requested = req, originalCode = pageMain, translationCode = req, pageProductCode = pageMain)
            else -> DlsiteIds(requested = req, originalCode = req, translationCode = other ?: pageMain, pageProductCode = pageMain)
        }
    }

    /**
     * 这一页到底是「作品页」还是别的东西。
     *
     * 以前只有一个笼统的「解析失败」，于是「作品不存在」「要过年龄确认」「要登录」「风控验证码」
     * 「页面改版」全挤在同一句话里，用户和日志都看不出该做什么。这里分开，交给
     * [DlsiteClient][com.kiite.player.data.DlsiteClient] 转成具体提示。
     */
    fun classify(html: String, code: String, httpStatus: Int = 0): DlsitePageState {
        if (httpStatus == 404) return DlsitePageState.NOT_FOUND
        if (httpStatus == 429) return DlsitePageState.CAPTCHA
        if (html.isBlank()) {
            return if (httpStatus == 0) DlsitePageState.NETWORK_ERROR else DlsitePageState.PAGE_CHANGED
        }
        if (isAgeGate(html)) return DlsitePageState.AGE_CONFIRMATION
        if (CAPTCHA_MARKERS.any { html.contains(it) }) return DlsitePageState.CAPTCHA
        if (looksLoginPage(html)) return DlsitePageState.LOGIN_REQUIRED
        if (MISSING_MARKERS.any { html.contains(it) }) return DlsitePageState.NOT_FOUND
        if (!html.contains(code.trim(), ignoreCase = true)) return DlsitePageState.PAGE_CHANGED
        return DlsitePageState.SUCCESS
    }

    private val CAPTCHA_MARKERS = listOf(
        "captcha",
        "アクセスが集中",
        "しばらく時間をおいてから",
        "アクセス制限",
    )

    private val LOGIN_MARKERS = listOf("ログイン", "login", "パスワード")

    /** 登录页：既有登录字样、又没有作品页的结构。 */
    private fun looksLoginPage(html: String): Boolean {
        if (html.contains("product_id", ignoreCase = true)) return false
        return LOGIN_MARKERS.count { html.contains(it, ignoreCase = true) } >= 2
    }

    private val MISSING_MARKERS = listOf(
        "該当する作品はありません",
        "お探しのページは見つかりません",
        "ページが見つかりません",
        "指定されたページは存在しません",
    )

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

    /** JSON-LD 里的缩略图（部分模板只给 thumbnailUrl）。 */
    private val JSON_LD_THUMB = Regex(
        """"thumbnailUrl"\s*:\s*(?:"([^"]+)"|\[\s*"([^"]+)")""",
        RegexOption.IGNORE_CASE,
    )

    /** `srcset="a.jpg 1x, b.jpg 2x"`：懒加载轮播常把真地址只放在这里。 */
    private val SRCSET_ATTR = Regex(
        """(data-srcset|data-lazy-srcset|srcset)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    /** 内联 style 里的 `background-image:url(…)`：少数模板的轮播首图只在这。 */
    private val STYLE_URL = Regex(
        """background(?:-image)?\s*:\s*url\(\s*['"]?([^'")]+)['"]?\s*\)""",
        RegexOption.IGNORE_CASE,
    )

    /** `<source srcset=…>`（picture 元素）。 */
    private val SOURCE_TAG = Regex("""<source\b[^>]*>""", RegexOption.IGNORE_CASE)

    /** 解析 srcset：每项形如 `url 2x` 或 `url 640w`。 */
    fun srcsetUrls(value: String): List<String> = value.split(",")
        .map { it.trim().substringBefore(' ').trim() }
        .filter { it.isNotEmpty() }

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

    private val ID_ONLY = Regex("""^[A-Za-z]{2}\d{6,8}$""")
    private val ID_PARTS = Regex("""^([A-Za-z]{2})(\d{6,8})$""")
    private val ID_IN_TEXT = Regex("""[A-Za-z]{2}\d{6,8}""", RegexOption.IGNORE_CASE)

    /** 封面图 CDN 根路径：同人作品（RJ/VJ/BJ）都在 doujin 目录下。 */
    private const val CDN_WORK_ROOT = "https://img.dlsite.jp/modpub/images2/work/doujin/"

    /**
     * 直接按编号拼出封面地址 —— **完全不依赖作品页**。
     *
     * 为什么必须有这条路：作品页会被年龄确认页、登录墙、风控页、地区限制挡掉，
     * 真机上的表现就是整批作品一起 404 / 一起「解析失败」，可那些作品的封面在 CDN 上好好的。
     * 图片 CDN 的文件名是纯规则：`<编号目录>/<编号>_img_main.jpg`，目录是「编号向上取整到千」，
     * 只要知道编号就能算出来。页面拿不到时这是唯一还能出图的路。
     *
     * 同时给「向上取整」和「向下取整」两种目录：绝大多数作品在前者，少数早期作品落在后者，
     * 多试一个地址的代价只是一次 404。最后补一张样图（`_img_smp1`）—— 它同样是这部作品
     * 自己的图，主图确实不存在时至少不空着（2.4.0 及更早版本拿到的就是它）。
     */
    fun coverUrlsForCode(code: String): List<String> {
        val c = code.trim().uppercase()
        val m = ID_PARTS.find(c) ?: return emptyList()
        val prefix = m.groupValues[1]
        val digits = m.groupValues[2]
        val n = digits.toLongOrNull() ?: return emptyList()
        fun dir(v: Long): String = prefix + v.toString().padStart(digits.length, '0')
        val dirs = listOf(dir((n + 999) / 1000 * 1000), dir(n / 1000 * 1000)).distinct()
        val out = ArrayList<String>(dirs.size + 1)
        dirs.forEach { out.add(CDN_WORK_ROOT + it + "/" + c + "_img_main.jpg") }
        out.add(CDN_WORK_ROOT + dirs.first() + "/" + c + "_img_smp1.jpg")
        return out
    }

    /** 编号向上取整到千，得到 CDN 目录名（RJ01275416 → RJ01276000）。 */
    fun bucketDir(code: String): String? {
        val m = ID_PARTS.find(code.trim()) ?: return null
        val digits = m.groupValues[2]
        val n = digits.toLongOrNull() ?: return null
        val bucket = ((n + 999) / 1000) * 1000
        return m.groupValues[1].uppercase() + bucket.toString().padStart(digits.length, '0')
    }

    /**
     * 同一张封面在**另一个编号**下的地址（原作 ↔ 译文互备）。
     *
     * 译文作品经常没有独立的封面文件，用译文编号拼出来的地址在 CDN 上就是空的（回
     * `NoSuchKey` 的 XML），而原作编号那张图是好的。换算必须连目录一起改：
     * 目录名是「编号向上取整到千」（RJ01275416 → RJ01276000），只换文件名会指到不存在的目录。
     *
     * 只对「目录本身就是编号目录」的地址做换算 —— 其它形态（时间戳目录、hash 目录）一律
     * 返回 null，宁可没有候选，也不虚构一个地址。
     */
    fun coverUrlFor(url: String?, newCode: String): String? {
        val u = normalizeUrl(url) ?: return null
        val code = newCode.trim().uppercase()
        if (!ID_ONLY.matches(code)) return null
        val slash = u.lastIndexOf('/')
        if (slash <= 0) return null
        val dirStart = u.lastIndexOf('/', slash - 1)
        val dir = u.substring(dirStart + 1, slash)
        if (!ID_ONLY.matches(dir)) return null
        val file = u.substring(slash + 1)
        val newDir = bucketDir(code) ?: return null
        val newFile = ID_IN_TEXT.replaceFirst(file, code)
        if (!ID_IN_TEXT.containsMatchIn(file)) return null
        return u.substring(0, dirStart + 1) + newDir + "/" + newFile
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
        val out = ArrayList<String>(10)
        fun add(raw: String?) {
            val u = normalizeUrl(raw) ?: return
            if (u !in out) out.add(u)
        }
        // meta / 结构化数据：页面自己声明的主图，最可信
        add(metas["og:image"])
        add(metas["og:image:secure_url"])
        add(metas["twitter:image"])
        add(metas["twitter:image:src"])
        JSON_LD_IMAGE.findAll(html).forEach { add(it.groupValues[1].ifBlank { it.groupValues[2] }) }
        JSON_LD_THUMB.findAll(html).forEach { add(it.groupValues[1].ifBlank { it.groupValues[2] }) }
        // 正文图片：把 img 的 src / 懒加载属性 / srcset / 内联背景图，以及 picture 的 source 全收一遍。
        // 只认 src 会漏掉两代懒加载写法，而封面恰好常常只存在于其中一个属性里。
        val body = ArrayList<String>(16)
        for (tag in IMG_TAG.findAll(html)) {
            imgUrl(tag.value)?.let(body::add)
            for (m in SRCSET_ATTR.findAll(tag.value)) body.addAll(srcsetUrls(m.groupValues[2]))
            for (m in STYLE_URL.findAll(tag.value)) body.add(m.groupValues[1])
        }
        for (tag in SOURCE_TAG.findAll(html)) {
            for (m in SRCSET_ATTR.findAll(tag.value)) body.addAll(srcsetUrls(m.groupValues[2]))
        }
        // 先只看带 modpub 的商品图，减少噪声；后面的兜底再放宽
        body.filter { it.contains("modpub", ignoreCase = true) }.forEach(::add)
        if (out.none { isPlausibleCover(it, code) }) {
            // 兜底：正文里任何地址里带作品编号的图（部分旧作品不在 modpub 目录下）
            body.forEach(::add)
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

    /**
     * 成人向作品会先跳年龄确认页。
     *
     * 判定要**先看这一页是不是「有内容的页」**：DLsite 的正常页面（作品页、搜索结果页、
     * 下载页）页脚与导航里本来就有「年齢確認」这几个字，只按关键词判会把正常页面当成
     * 年龄确认页 —— 用户看到的就是「搜索一直提示需要年龄认证」，而且点重试永远是同一句。
     * 真正的确认页很小：没有商品链接、没有 work_name、没有「購入履歴 / 検索結果」这类区块。
     */
    fun isAgeGate(html: String): Boolean {
        if (html.isBlank()) return false
        val looksLikeContent = html.contains("/product_id/", ignoreCase = true) ||
            html.contains("work_name", ignoreCase = true) ||
            html.contains("検索結果", ignoreCase = true) ||
            html.contains("購入履歴", ignoreCase = true)
        if (looksLikeContent) return false
        return html.contains("年齢確認") ||
            html.contains("age_verification") ||
            html.contains("Are you over 18") ||
            html.contains("adult_check") ||
            html.contains("adultchecked")
    }

    fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}

/** 一次页面请求的结论分类：每种都要能对应到「用户该做什么」。 */
enum class DlsitePageState {
    /** 正常拿到作品页。 */
    SUCCESS,

    /** DLsite 明确说没有这个作品（只有确认过才用这一档）。 */
    NOT_FOUND,

    /** 被年龄确认页挡住（未登录 / 未过确认）。 */
    AGE_CONFIRMATION,

    /** 被换成登录页。 */
    LOGIN_REQUIRED,

    /** 风控 / 验证码 / 限流。 */
    CAPTCHA,

    /** 连不上（网络层失败）。 */
    NETWORK_ERROR,

    /** 页面结构变了：页面在，但认不出作品信息。 */
    PAGE_CHANGED,

    /** 作品信息正常，但封面拿不到。 */
    COVER_UNAVAILABLE,
}

/**
 * 一部作品的身份三件套。
 *
 * [requested] 是用户本地文件夹（或用户输入）里的编号，永远不改写；
 * [originalCode] 是原作编号，通常与 [requested] 相同；[translationCode] 是页面标注的译文编号。
 */
data class DlsiteIds(
    val requested: String,
    val originalCode: String,
    val translationCode: String? = null,
    val pageProductCode: String? = null,
) {
    /** 除 [originalCode] 之外、还可能挂着同一张封面的编号（译文编号 / 页面主编号）。 */
    fun alternates(): List<String> =
        listOfNotNull(translationCode, pageProductCode)
            .map { it.uppercase() }
            .filter { it != originalCode.uppercase() }
            .distinct()
}

/**
 * 抓取失败的统一异常：带上状态分类，调用方据此决定提示文案与是否重试。
 * `message` 始终是给用户看的中文说明。
 */
class DlsiteFetchException(
    val state: DlsitePageState,
    override val message: String,
) : Exception(message)

/**
 * DLsite 图片 CDN 失败时回的是 XML（S3 风格），不是图片。
 *
 * `<?xml version="1.0"?><Error><Code>NoSuchKey</Code><Message>The specified key does not exist.</Message></Error>`
 *
 * 以前一律记成「返回的不是图片」，等于什么都没说 —— 而 `NoSuchKey` 恰恰是「这个地址的图不存在」
 * 的直接证据，可以据此换编号、换候选，而不是傻等超时。
 */
object DlsiteCdnError {

    private val CODE = Regex("""<Code>([^<]{1,80})</Code>""", RegexOption.IGNORE_CASE)
    private val MESSAGE = Regex("""<Message>([^<]{1,200})</Message>""", RegexOption.IGNORE_CASE)
    private val ERROR_ROOT = Regex("""<Error\b""", RegexOption.IGNORE_CASE)

    /** 解析出 `Code: Message`；不是 XML 错误就返回 null。 */
    fun parse(body: String): String? {
        val b = body.trim()
        if (b.isEmpty()) return null
        val looksXml = b.startsWith("<?xml", ignoreCase = true) ||
            b.startsWith("<Error", ignoreCase = true) ||
            ERROR_ROOT.containsMatchIn(b)
        if (!looksXml) return null
        val code = CODE.find(b)?.groupValues?.get(1)?.trim()
        val message = MESSAGE.find(b)?.groupValues?.get(1)?.trim()
        if (code == null && message == null) return "XML 错误（未含 Code/Message）"
        return listOfNotNull(code, message).joinToString(": ")
    }

    /** 这个 XML 错误是否代表「地址上的文件真的不存在」。 */
    fun isMissing(body: String): Boolean {
        val text = parse(body)?.lowercase() ?: return false
        return text.contains("nosuchkey") || text.contains("not found") || text.startsWith("404")
    }
}

/**
 * DLsite 作品 JSON 接口（`/maniax/api/=/product.json?workno=RJxxxxxx`）的响应解析。
 *
 * 为什么优先用它而不是 HTML：这是 **DLsite 自己页面在用的数据源**，不经过那些
 * 「年龄确认页 / 登录墙 / 风控页」，也不随页面改版失效；返回里直接给封面地址、
 * 分类、社团，翻译作品还带 `translation_info.original_workno`（原作编号），
 * 比从 HTML 里猜编号可靠。
 *
 * 响应是一个数组（查不到时是空数组 `[]`），字段形如：
 * ```json
 * [{"workno":"RJ01275416","work_name":"…","maker_name":"…",
 *   "image_main":{"url":"//img.dlsite.jp/modpub/images2/work/doujin/RJ01276000/RJ01275416_img_main.jpg"},
 *   "genres":[{"name":"ASMR"}],"intro_s":"…","regist_date":"2024-08-30 00:00:00",
 *   "translation_info":{"original_workno":"RJ01275416"}}]
 * ```
 * 字段缺失/改名时只是少几项，不影响封面与标题（这是它比 HTML 解析更抗改版的地方）。
 */
object DlsiteProductJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 接口返回的数组里第一个作品的编号（用于判断「查不到」与「接口变了」）。 */
    fun isEmptyArray(body: String): Boolean = body.trim() == "[]"

    /**
     * 作品有几个可下载文件（分卷作品 > 1）。
     * 下载页本身不给编号时用它兜底：分卷入口固定是 `/number/1..N/`。
     */
    fun contentsCount(body: String): Int {
        if (body.isBlank()) return 0
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return 0
        val obj = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject
            is JsonObject -> root
            else -> null
        } ?: return 0
        return (obj["contents"] as? JsonArray)?.size ?: 0
    }

    fun parse(body: String, requested: String): DlsiteWork? {
        if (body.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        val obj = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject
            is JsonObject -> root
            else -> null
        } ?: return null
        val title = str(obj, "work_name", "work_name_s") ?: return null
        val req = requested.trim().uppercase()
        val workno = str(obj, "workno", "product_id")?.uppercase()
        val main = workno ?: req
        val genres = (obj["genres"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { g -> str(g, "name") } }
            .orEmpty()
        val original = (obj["translation_info"] as? JsonObject)
            ?.let { str(it, "original_workno") }
            ?.uppercase()
        val voices = (obj["creaters"] as? JsonObject)
            ?.let { it["voice_by"] as? JsonArray }
            ?.mapNotNull { (it as? JsonObject)?.let { v -> str(v, "name") } }
            .orEmpty()
        return DlsiteWork(
            // 用户请求的编号是身份（本地文件夹就按它命名），接口给的主编号只作参考
            code = req.ifEmpty { main },
            title = DlsiteParse.unescape(title),
            circle = str(obj, "maker_name")?.let { DlsiteParse.unescape(it) },
            coverUrl = coverUrl(obj),
            releaseDate = str(obj, "regist_date", "release_date")?.substringBefore(' '),
            tags = genres.map { DlsiteParse.unescape(it) }.distinct().take(8),
            voiceActors = voices.map { DlsiteParse.unescape(it) }.distinct().take(8),
            fetchedAtMs = System.currentTimeMillis(),
            productUrl = DlsiteParse.productUrl(req.ifEmpty { main }),
            r18 = looksR18(obj, genres),
            synopsis = str(obj, "intro_s", "intro")?.let(::plainText)?.takeIf { it.length >= 12 },
            originalCode = original ?: main,
            translationCode = if (original != null && original != main) main else null,
            pageState = DlsitePageState.SUCCESS.name,
        )
    }

    /** 封面地址：`image_main.url` 是「//img.dlsite.jp/…」，要补协议；没有主图就退到第一张样图。 */
    private fun coverUrl(obj: JsonObject): String? {
        val main = (obj["image_main"] as? JsonObject)?.let { str(it, "url", "url_webp") }
        val sample = (obj["image_samples"] as? JsonArray)
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.let { s -> str(s, "url") } }
        return DlsiteParse.normalizeUrl(main ?: sample)
    }

    /**
     * 成人向判定只用「分类名 / age_category_string」这类明确标记。
     * **不看数字型的 age_category**：猜错会触发「隐藏 R18」，把正常作品藏起来，代价太大。
     */
    private fun looksR18(obj: JsonObject, genres: List<String>): Boolean {
        val age = str(obj, "age_category_string", "age_category_name")
        if (age != null && (age.contains("R18", true) || age.contains("adult", true) || age.contains("成人"))) return true
        return RatingParse.looksR18("", genres)
    }

    private fun str(obj: JsonObject, vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
        (obj[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 简介里可能带 HTML，去掉标签与实体（详情面板只显示纯文本）。 */
    private fun plainText(s: String): String = DlsiteParse.unescape(
        s.replace(Regex("""<[^>]+>"""), " ").replace(Regex("""\s+"""), " ").trim(),
    )
}

/** DLsite 站内搜索结果条目（从搜索结果页解析）。 */
data class DlsiteSearchHit(
    val code: String,
    val title: String? = null,
    val circle: String? = null,
    /** 标价文本（如「880円」），解析不到为 null。 */
    val price: String? = null,
)

/**
 * 本地文件夹名 → DLsite 搜索词的归一化，以及搜索结果的排序。
 *
 * 本地目录常带编号、分卷后缀和扩展名（「RJ01234567 作品名 第2部分」），
 * 搜索前要去掉这些噪声；排序时完全相同的标题优先，再按相似度降序，
 * 这样「按标题识别」第一次就挑对的概率最高。
 */
object DlsiteTitleKey {
    private val CODE = Regex("""(?i)[\(\[【]?\s*(?:RJ|VJ|BJ|RE|DL|KUI)\s?\d{4,}\s*[\)\]】]?""")
    private val PART = Regex("""(?i)\s*第?\s*\d+\s*(?:部分|卷|碟|disc|cd)\s*$""")
    private val EXT = Regex("""\.[A-Za-z0-9]{1,5}$""")
    private val SEP = Regex("""[\s_・＋+\-—–]+""")

    fun of(raw: String): String {
        var s = TextUtils.toHalfWidth(raw.trim())
        s = EXT.replace(s, "")
        s = CODE.replace(s, " ")
        s = PART.replace(s, "")
        return SEP.replace(s, " ").trim().lowercase()
    }

    /**
     * 从文件夹名 / 标题里抽第一个作品编号（如 `RJ01275416`），没有返回 null。
     *
     * 编号是最可靠的识别入口：官方 JSON 接口按编号直查，不做年龄确认、不依赖登录，
     * 也不用赌站内搜索的召回。所以「按标题识别」先试它，抽不到才去搜。
     * [of] 会把编号删掉（那是给站内搜索用的关键词），两者必须分开。
     */
    fun extractCode(raw: String): String? {
        val hit = CODE.find(TextUtils.toHalfWidth(raw)) ?: return null
        return hit.value.filter { it.isLetterOrDigit() }.uppercase().ifBlank { null }
    }

    /** 最佳匹配排最前；搜索结果为空或标题缺失时原样返回。 */
    fun rank(query: String, hits: List<DlsiteSearchHit>): List<DlsiteSearchHit> {
        val q = of(query)
        if (q.isBlank()) return hits
        return hits.sortedWith(
            compareByDescending<DlsiteSearchHit> { hit -> hit.title?.let { of(it) == q } == true }
                .thenByDescending { hit -> hit.title?.let { TextUtils.similarity(q, of(it)) } ?: 0.0 },
        )
    }
}

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
