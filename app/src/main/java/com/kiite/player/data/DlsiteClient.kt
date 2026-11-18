package com.kiite.player.data

import com.kiite.player.core.DlsiteAuth
import com.kiite.player.core.DlsiteDownloadFile
import com.kiite.player.core.DlsiteFetchException
import com.kiite.player.core.DlsitePageState
import com.kiite.player.core.DlsiteParse
import com.kiite.player.core.DlsiteSearch
import com.kiite.player.core.DlsiteSearchHit
import com.kiite.player.core.DlsiteSearchParse
import com.kiite.player.core.DlsiteWork
import java.net.HttpURLConnection
import java.net.URL

/** 抓取 DLsite 公开作品页（不需要账号）。 */
object DlsiteClient {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

    private const val ACCEPT_HTML =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

    fun workUrl(code: String): String =
        "https://www.dlsite.com/maniax/work/=/product_id/" + code.uppercase() + ".html"

    /** 成人向在 /maniax，全年龄在 /home（还有 /pro）；逐个试，避免分区不对直接失败。 */
    internal fun candidates(code: String): List<Pair<String, String>> {
        val c = code.uppercase()
        return listOf(
            "maniax" to "https://www.dlsite.com/maniax/work/=/product_id/$c.html",
            "maniax-touch" to "https://www.dlsite.com/maniax-touch/work/=/product_id/$c.html",
            "home" to "https://www.dlsite.com/home/work/=/product_id/$c.html",
            "pro" to "https://www.dlsite.com/pro/work/=/product_id/$c.html",
        )
    }

    /**
     * 按请求编号解析；请求的是译文编号、页面却按原作编号给的时候，用页面主编号再解析一次。
     *
     * DLsite 译文页的主编号是**原作**（`product_id/RJ01275416.html?translation=RJ01275417`）。
     * 如果用户本地文件夹用的是译文编号，直接拿译文编号判「这是不是它的页面」会判成假，
     * 整部作品就变成「解析失败」—— 而标题、封面其实都在这一页上。身份仍然记用户那个编号。
     */
    private fun parseWork(html: String, code: String, pageUrl: String?): DlsiteWork? {
        DlsiteParse.parse(html, code, pageUrl)?.let { return it }
        val main = DlsiteParse.pageProductId(pageUrl) ?: return null
        val upper = code.uppercase()
        if (main == upper) return null
        val work = DlsiteParse.parse(html, main, pageUrl) ?: return null
        return work.copy(
            code = upper,
            originalCode = main,
            translationCode = upper.takeIf { it != main },
        )
    }

    fun fetch(code: String, cookie: String? = null): Result<DlsiteWork> {
        // 1) 先打作品 JSON 接口：与网页同源，但是**DLsite 页面自己在用**的数据源，
        //    不经过年龄确认页 / 登录墙 / 风控页，也不随 HTML 改版失效。
        val api = fetchApi(code, cookie)
        if (api.isSuccess) {
            val w = api.getOrThrow()
            AppLog.log(
                "DLsite 命中 JSON 接口（请求 " + code.uppercase() +
                    "，原作 " + (w.originalCode ?: "-") + "，译文 " + (w.translationCode ?: "-") + "）",
            )
            return api
        }
        val apiErr = api.exceptionOrNull()
        val apiState = (apiErr as? DlsiteFetchException)?.state
        val apiSaysMissing = apiState == DlsitePageState.NOT_FOUND
        AppLog.log("DLsite JSON 接口失败：" + (apiState?.name ?: "ERROR") + " · " + apiErr?.message)

        // 2) 退回抓 HTML 页面（老办法，多个分区挨个试）
        val attempts = ArrayList<Pair<String, DlsiteFetchException>>(4)
        for ((label, url) in candidates(code)) {
            val r = fetchFrom(url, code, cookie)
            if (r.isSuccess) {
                val w = r.getOrThrow()
                AppLog.log(
                    "DLsite 命中分区：" + label +
                        "（请求 " + code.uppercase() +
                        "，原作 " + (w.originalCode ?: "-") +
                        "，译文 " + (w.translationCode ?: "-") + "）",
                )
                return r
            }
            val e = r.exceptionOrNull()
            val typed = e as? DlsiteFetchException
                ?: DlsiteFetchException(DlsitePageState.PAGE_CHANGED, e?.message ?: "未知错误")
            attempts.add(label to typed)
            AppLog.log("DLsite 尝试失败 " + label + "：" + typed.state.name + " · " + typed.message)
        }
        // 以前直接把「最后一次」的错误抛出去：RJ 作品本来就不在 /pro，于是用户永远看到
        // 「这个分区没有该编号（404）」，把前面 maniax 的真实原因（年龄确认 / 风控 / 连不上）盖掉了。
        // 这里改成挑最需要用户处理的那条，并把每个分区的结论一起写进日志。
        AppLog.log(
            "DLsite 各分区结果：" + attempts.joinToString("，") { it.first + "=" + it.second.state.name } +
                "；网络诊断 www.dlsite.com → " + resolve("www.dlsite.com") +
                "，img.dlsite.jp → " + resolve("img.dlsite.jp"),
        )
        // JSON 接口回的是空数组 = 它明确说「没这个作品」，这时不要再怪网络
        if (apiSaysMissing) {
            return Result.failure(
                DlsiteFetchException(
                    DlsitePageState.NOT_FOUND,
                    "DLsite 上没有 " + code.uppercase() + " 这个作品（JSON 接口返回空）",
                ),
            )
        }
        return Result.failure(bestError(attempts))
    }

    /**
     * 作品 JSON 接口：`/maniax/api/=/product.json?workno=RJxxxxxx`。
     *
     * 这是 DLsite 自己的页面脚本在用的接口，返回数组（查不到是 `[]`）。本机实测不到的网络环境
     * 下它和页面一样会被墙挡住 —— 但**页面被年龄确认页顶替、被改成引导页**时它仍然给数据，
     * 所以放在第一位；失败就照旧退回 HTML。
     */
    fun apiUrl(code: String): String =
        "https://www.dlsite.com/maniax/api/=/product.json?workno=" + code.uppercase() + "&locale=ja-JP"

    internal fun fetchApi(code: String, cookie: String? = null): Result<DlsiteWork> = runCatching {
        val conn = (URL(apiUrl(code)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            setRequestProperty("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Cookie", DlsiteAuth.withAdultChecked(cookie))
        }
        try {
            val status = runCatching { conn.responseCode }.getOrDefault(0)
            val body = runCatching {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrElse { e ->
                throw DlsiteFetchException(DlsiteParse.classify("", code, status), httpMessage(status, e))
            }
            val trimmed = body.trim()
            if (trimmed.isEmpty()) {
                throw DlsiteFetchException(
                    DlsiteParse.classify("", code, status),
                    "DLsite JSON 接口返回空（HTTP " + status + "）",
                )
            }
            if (com.kiite.player.core.DlsiteProductJson.isEmptyArray(trimmed)) {
                throw DlsiteFetchException(
                    DlsitePageState.NOT_FOUND,
                    "DLsite 上没有 " + code.uppercase() + " 这个作品（JSON 接口返回空）",
                )
            }
            com.kiite.player.core.DlsiteProductJson.parse(trimmed, code)
                ?: throw DlsiteFetchException(
                    DlsitePageState.PAGE_CHANGED,
                    "JSON 接口返回的内容认不出来（结构可能变了）",
                )
        } finally {
            conn.disconnect()
        }
    }

    /** HTTP 状态 → 给用户看的中文原因。 */
    private fun httpMessage(status: Int, e: Throwable): String = when {
        status == 404 -> "DLsite 这个分区没有该编号（404）"
        status == 403 -> "DLsite 拒绝了请求（403），可能需要登录或触发了风控"
        status == 429 -> "DLsite 请求过于频繁（429），请稍后再试"
        status in 500..599 -> "DLsite 服务器错误（" + status + "）"
        status == 0 -> "连不上 DLsite（网络/被墙？）：" + (e.message ?: e.javaClass.simpleName)
        else -> "DLsite 返回 HTTP " + status
    }

    /** 分区结果里最能说明「该做什么」的那一条；全部 404 时给一段专门的解释。 */
    private fun bestError(attempts: List<Pair<String, DlsiteFetchException>>): DlsiteFetchException {
        if (attempts.isEmpty()) return DlsiteFetchException(DlsitePageState.PAGE_CHANGED, "识别失败")
        if (attempts.all { it.second.state == DlsitePageState.NOT_FOUND }) {
            // 三个分区一起 404，几乎不可能是「作品不存在」——更像是请求根本没到 DLsite
            // （DNS 被劫持 / 运营商拦截 / 地区限制），或者这个编号确实不在公开页。
            return DlsiteFetchException(
                DlsitePageState.NOT_FOUND,
                "DLsite 各分区都返回 404（" + attempts.joinToString("/") { it.first } + "），" +
                    "多半不是作品不存在，而是网络被拦截或地区限制：换个网络（移动数据 / Wi-Fi）或挂代理再试。" +
                    "封面仍会直接从图片 CDN 取，不受这条影响。",
            )
        }
        val rank = mapOf(
            DlsitePageState.AGE_CONFIRMATION to 6,
            DlsitePageState.LOGIN_REQUIRED to 5,
            DlsitePageState.CAPTCHA to 4,
            DlsitePageState.NETWORK_ERROR to 3,
            DlsitePageState.NOT_FOUND to 2,
            DlsitePageState.PAGE_CHANGED to 1,
            DlsitePageState.COVER_UNAVAILABLE to 0,
            DlsitePageState.SUCCESS to -1,
        )
        return attempts.maxByOrNull { rank[it.second.state] ?: 0 }!!.second
    }

    /** 域名解析结果（只用于诊断日志：解析到奇怪地址就说明网络被劫持了）。 */
    private fun resolve(host: String): String = runCatching {
        java.net.InetAddress.getAllByName(host).joinToString(",") { it.hostAddress ?: "?" }
    }.getOrDefault("-")

    /** 通用 GET，返回「最终地址 + 正文」。 */
    internal fun get(url: String, cookie: String? = null): Pair<String, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", ACCEPT_HTML)
            setRequestProperty("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
            setRequestProperty("Cookie", DlsiteAuth.withAdultChecked(cookie))
        }
        return try {
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.url.toString() to html
        } finally {
            conn.disconnect()
        }
    }

    // ---------- 已购作品的原生下载 ----------

    /**
     * 向 DLsite 要「这部作品该下哪些文件、每个文件的真实直链与临时令牌」。
     *
     * 为什么要自己解析而不是让 WebView 点：
     *  - WebView 里点下载会先 302 到 CDN，页面随即变成一块空白（深色主题下就是「黑屏」），
     *    用户只能干等到下载结束；
     *  - 真实直链认的是响应里 `Set-Cookie` 的临时 JWT，不是登录 Cookie，重放登录 Cookie → 403。
     * 这里直接不跟 302，把 `Location` 与 `Set-Cookie` 一起收下，交给应用内下载器。
     */
    fun resolveDownload(code: String, sessionCookie: String?): Result<List<DlsiteDownloadFile>> = runCatching {
        val c = code.trim().uppercase()
        val cookie = DlsiteAuth.withAdultChecked(sessionCookie)
        var firstError: Throwable? = null
        for (page in DlsiteAuth.downloadPageUrls(c)) {
            val attempt = runCatching { resolveFromPage(c, page, cookie) }
            if (attempt.isSuccess) return@runCatching attempt.getOrThrow()
            val e = attempt.exceptionOrNull()
            AppLog.log("DLsite 下载入口失败 " + page.substringAfter("dlsite.com/") + "：" + (e?.message ?: "?"))
            if (firstError == null) firstError = e
            // 换分区只对「这一页没有这个作品」有意义。连不上 / 登录失效 / 风控换分区也一样，
            // 再等一个超时只会让用户多盯 20 秒空白。
            val state = (e as? DlsiteFetchException)?.state
            if (state != DlsitePageState.NOT_FOUND && state != DlsitePageState.PAGE_CHANGED) break
        }
        throw firstError ?: IllegalStateException("无法向 DLsite 请求下载地址")
    }

    private fun resolveFromPage(code: String, pageUrl: String, cookie: String): List<DlsiteDownloadFile> {
        val head = head(pageUrl, cookie, referer = DlsiteAuth.workUrl(code))
        if (head.location != null && head.status in 300..399) {
            // 单文件作品：入口自己就 302 到直链
            return listOf(toFile(1, head, pageUrl))
        }
        when (head.status) {
            403 -> throw DlsiteFetchException(
                DlsitePageState.LOGIN_REQUIRED,
                "DLsite 拒绝了下载（403）：这个账号的购买记录里没有 " + code +
                    "，或者登录已失效。先在设置 → DLsite 里重新登录，再回来点下载。",
            )
            404 -> throw DlsiteFetchException(
                DlsitePageState.NOT_FOUND,
                "DLsite 没有 " + code + " 的下载页（404）：编号可能不对，或作品已下架。",
            )
            !in 200..299 -> throw DlsiteFetchException(
                if (head.status == 0) DlsitePageState.NETWORK_ERROR else DlsitePageState.PAGE_CHANGED,
                if (head.status == 0) {
                    "连不上 DLsite（网络不可用或被拦截）：换网络、或用代理后再点下载。"
                } else {
                    "DLsite 返回 HTTP " + head.status + "，暂时拿不到下载地址。"
                },
            )
        }
        if (com.kiite.player.core.DlsiteParse.isAgeGate(head.body) ||
            DlsiteAuth.looksLoggedOut(pageUrl, head.body)
        ) {
            throw DlsiteFetchException(
                DlsitePageState.LOGIN_REQUIRED,
                "DLsite 要求先登录/确认年龄：去设置 → DLsite 登录一次，再回来点下载。",
            )
        }
        val indices = parseFileIndices(head.body, code, cookie)
        if (indices.isEmpty()) {
            throw DlsiteFetchException(
                DlsitePageState.PAGE_CHANGED,
                "下载页里没有找到可下载的文件（页面结构可能变了）。可以点下面的「在网页里打开」手动下载。",
            )
        }
        val out = ArrayList<DlsiteDownloadFile>(indices.size)
        for (i in indices) {
            val f = head(DlsiteAuth.downloadFileUrl(code, i), cookie, referer = pageUrl)
            if (f.location == null || f.status !in 300..399) {
                throw DlsiteFetchException(
                    DlsitePageState.PAGE_CHANGED,
                    "第 " + i + "/" + indices.size + " 个文件没拿到直链（HTTP " + f.status + "）。",
                )
            }
            out.add(toFile(i, f, pageUrl))
        }
        return out
    }

    /**
     * 下载页里出现的分卷编号（`/number/2/product_id/RJxxx.html`）。
     * 一个都没有时退到作品 JSON 的 `contents` 长度——分卷页一定按 1..N 编号。
     */
    private fun parseFileIndices(html: String, code: String, cookie: String): List<Int> {
        val found = Regex("""/number/(\d+)/product_id/""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toList()
        if (found.isNotEmpty()) return found
        val count = runCatching {
            com.kiite.player.core.DlsiteProductJson.contentsCount(get(apiUrl(code), cookie).second)
        }.getOrDefault(0)
        return (1..count).toList()
    }

    /** 一个不跟 302 的请求：把 `Location` 与 `Set-Cookie` 原样收下来。 */
    private class Head(val status: Int, val location: String?, val cookies: String?, val body: String)

    private fun head(url: String, cookie: String?, referer: String?): Head {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            // 比抓页面短一点：这一步失败要尽快告诉用户，别让人盯着「正在请求地址」发呆
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", ACCEPT_HTML)
            setRequestProperty("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
            setRequestProperty("Cookie", DlsiteAuth.withAdultChecked(cookie))
            if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
        }
        return try {
            val status = runCatching { conn.responseCode }.getOrDefault(0)
            val location = conn.getHeaderField("Location")
            val rawCookies = conn.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values.flatten()
            val body = if (status in 200..299) {
                runCatching {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrDefault("")
            } else ""
            Head(status, location, downloadCookie(rawCookies), body)
        } finally {
            conn.disconnect()
        }
    }

    private fun toFile(index: Int, h: Head, referer: String): DlsiteDownloadFile {
        val url = h.location ?: error("没有拿到直链")
        val name = runCatching {
            val seg = url.substringBefore('?').substringAfterLast('/')
            if (seg.contains('.')) java.net.URLDecoder.decode(seg, "UTF-8") else null
        }.getOrNull()
        return DlsiteDownloadFile(index = index, url = url, cookie = h.cookies, fileName = name, referer = referer)
    }

    /**
     * 从 `Set-Cookie` 里拼出下载直链要的 Cookie 头。
     *
     * 优先只保留令牌类（JWT）——登录会话 Cookie 发给 CDN 既没用也没必要；
     * 一个令牌都没有时退回全部非属性项（Path / Expires 这些不是 Cookie）。
     */
    internal fun downloadCookie(rawSetCookies: List<String>): String? {
        if (rawSetCookies.isEmpty()) return null
        val attrs = setOf("path", "domain", "expires", "max-age", "secure", "httponly", "samesite")
        val pairs = LinkedHashMap<String, String>()
        for (line in rawSetCookies) {
            for (m in Regex("""([A-Za-z0-9_\-]+)=([^;,\s]+)""").findAll(line)) {
                val name = m.groupValues[1]
                if (name.lowercase() in attrs) continue
                pairs[name] = name + "=" + m.groupValues[2]
            }
        }
        if (pairs.isEmpty()) return null
        val token = pairs.filterKeys { it.contains("jwt", true) || it.contains("token", true) }
        return (if (token.isNotEmpty()) token else pairs).values.joinToString("; ")
    }

    /** 真正验证登录态：拿只有登录后才能打开的购买记录页去试。 */
    fun verifyLogin(cookie: String?): com.kiite.player.core.DlsiteLoginState {
        if (cookie.isNullOrBlank()) return com.kiite.player.core.DlsiteLoginState.LOGGED_OUT
        var reached = false
        for (url in com.kiite.player.core.DlsiteAuth.PURCHASE_URLS) {
            val r = runCatching { get(url, cookie) }.getOrNull() ?: continue
            reached = true
            if (!com.kiite.player.core.DlsiteAuth.looksLoggedOut(r.first, r.second)) {
                return com.kiite.player.core.DlsiteLoginState.LOGGED_IN
            }
        }
        // 一个都连不上 = 无法确认；连上了但都被判定未登录 = 未登录
        return if (reached) com.kiite.player.core.DlsiteLoginState.LOGGED_OUT
        else com.kiite.player.core.DlsiteLoginState.UNKNOWN
    }

    /** 拉已购作品列表。 */
    fun fetchPurchases(cookie: String?): Result<List<com.kiite.player.core.DlsitePurchase>> = runCatching {
        // 第一页：顺便确认登录态与总页数
        val first = runCatching { get(com.kiite.player.core.DlsiteAuth.purchaseUrl(1), cookie) }.getOrNull()
            ?: error("连不上 DLsite，请检查网络")
        if (com.kiite.player.core.DlsiteAuth.looksLoggedOut(first.first, first.second)) {
            error("未登录或登录已失效")
        }
        val all = LinkedHashMap<String, com.kiite.player.core.DlsitePurchase>()
        com.kiite.player.core.DlsitePurchaseParse.parse(first.second).forEach { all[it.code] = it }
        val last = com.kiite.player.core.DlsitePurchaseParse.lastPage(first.second)
        for (p in 2..last) {
            val r = runCatching { get(com.kiite.player.core.DlsiteAuth.purchaseUrl(p), cookie) }.getOrNull() ?: continue
            com.kiite.player.core.DlsitePurchaseParse.parse(r.second).forEach { all[it.code] = it }
        }
        if (all.isEmpty()) {
            error("页面里没有解析到作品（结构可能变了，请把购买记录页另存为 HTML 发给作者）")
        }
        all.values.toList()
    }

    /** 站内搜索：关键词直接查 DLsite；输入恰好是 RJ 编号时退化为单作品识别。 */
    fun search(keyword: String, cookie: String? = null): Result<List<DlsiteSearchHit>> = runCatching {
        val trimmed = keyword.trim()
        val direct = Regex("""(?i)\bRJ\d{6,8}\b""").find(trimmed)?.value
        if (direct != null) {
            val work = fetch(direct.uppercase(), cookie).getOrThrow()
            return Result.success(
                listOf(
                    com.kiite.player.core.DlsiteSearchHit(
                        code = work.code,
                        title = work.title,
                        circle = work.circle,
                    ),
                ),
            )
        }
        val (finalUrl, html) = get(DlsiteSearch.url(trimmed), cookie)
        if (DlsiteParse.isAgeGate(html)) {
            error("DLsite 要求年龄确认，请先在设置里登录账号并完成年龄确认")
        }
        if (com.kiite.player.core.DlsiteAuth.looksLoggedOut(finalUrl, html)) {
            error("DLsite 返回了登录页，搜索被拦截（可能触发了风控，稍后再试）")
        }
        DlsiteSearchParse.parse(html)
    }

    /** 抽出来便于用本地 HTTP 服务做离线端到端验证。 */
    internal fun fetchFrom(url: String, code: String, cookie: String? = null): Result<DlsiteWork> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            // 缺 Accept 的请求更容易被 WAF 当成爬虫直接挡掉（真的整批 404 过）
            setRequestProperty("Accept", ACCEPT_HTML)
            setRequestProperty("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
            setRequestProperty("Cookie", DlsiteAuth.withAdultChecked(cookie))
        }
        try {
            val status = runCatching { conn.responseCode }.getOrDefault(0)
            val html = runCatching {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrElse { e ->
                // 连响应体都没拿到：网络层失败，按状态分类交出去
                throw DlsiteFetchException(DlsiteParse.classify("", code, status), httpMessage(status, e))
            }
            // 页面状态分开报：以前全都塞进一句「解析失败」，日志里看不出是作品不存在、
            // 还是被年龄确认页/登录页/风控页挡住，也就没法判断该重试还是该登录。
            when (val state = DlsiteParse.classify(html, code, status)) {
                DlsitePageState.SUCCESS -> parseWork(html, code, conn.url.toString())
                    ?: throw DlsiteFetchException(
                        DlsitePageState.PAGE_CHANGED,
                        "页面里没找到作品信息（编号是否正确？）",
                    )
                DlsitePageState.AGE_CONFIRMATION -> throw DlsiteFetchException(
                    state,
                    "该作品页需要年龄确认，请先在设置里登录 DLsite 账号后重试",
                )
                DlsitePageState.LOGIN_REQUIRED -> throw DlsiteFetchException(
                    state,
                    "DLsite 要求登录后才能查看这个作品",
                )
                DlsitePageState.CAPTCHA -> throw DlsiteFetchException(
                    state,
                    "DLsite 触发了风控（验证码/限流），请稍后再试",
                )
                DlsitePageState.NOT_FOUND -> throw DlsiteFetchException(
                    state,
                    "DLsite 上没有 " + code.uppercase() + " 这个作品",
                )
                DlsitePageState.NETWORK_ERROR -> throw DlsiteFetchException(state, "连不上 DLsite，请检查网络")
                DlsitePageState.PAGE_CHANGED, DlsitePageState.COVER_UNAVAILABLE -> throw DlsiteFetchException(
                    state,
                    "这一页不是 " + code.uppercase() + " 的作品页（可能被 DLsite 换成了引导页），请稍后重试或先登录",
                )
            }
        } finally {
            conn.disconnect()
        }
    }
}
