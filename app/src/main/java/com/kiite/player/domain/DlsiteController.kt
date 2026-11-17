package com.kiite.player.domain

import android.app.Application
import com.kiite.player.core.DlsiteLoginState
import com.kiite.player.core.DlsitePurchase
import com.kiite.player.core.DlsiteWork
import com.kiite.player.core.ProjectEntry
import com.kiite.player.data.AppLog
import com.kiite.player.data.clearDlsiteSession
import com.kiite.player.data.dlsiteCookieHeader
import com.kiite.player.data.dlsiteCookieCount
import com.kiite.player.data.DlsiteClient
import com.kiite.player.data.DlsiteStore
import com.kiite.player.data.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DLsite 相关的一切：识别作品信息、抓封面、登录态、已购同步、应用内下载。
 *
 * 抽出来的原因：这一整块有 300 行左右，且**与播放、台本、曲库结构完全无关**——
 * 它只对外部世界（网络）和几个回调（项目列表、根目录、是否只看音声）负责。
 *
 * 两条安全约定（原样保留，勿改）：
 *  - 直链可能指向第三方 CDN，**绝不把 DLsite 会话 Cookie 发过去**（只有 dlsite.com 及其子域才带）；
 *  - 登录态必须联网验证过才算已登录，绝不只看 cookie 是否存在。
 */
class DlsiteController(
    private val app: Application,
    private val repo: LibraryRepository,
    private val store: DlsiteStore,
    private val scope: CoroutineScope,
    /** 当前扫描到的项目（批量识别、已购比对用）。 */
    private val projects: () -> List<ProjectEntry>,
    /** 曲库根目录（未单独设置下载目录时的落盘位置）。 */
    private val rootPath: () -> String?,
    /** 用户自选的下载目录（设置里可改）；为空表示用曲库根目录。 */
    private val downloadDir: () -> String? = { null },
    /** 「已购列表只看音声」开关。 */
    private val purchaseAsmrOnly: () -> Boolean,
    /** 「隐藏 R18」开关（识别出 R18 作品时在通知里说明它会被曲库隐藏）。 */
    private val hideR18: () -> Boolean,
    /** 解压完成后重新扫描曲库。 */
    private val rescan: () -> Unit,
    private val notify: (String) -> Unit,
    private val setBusy: (String, String?) -> Unit,
) {
    private val _works = MutableStateFlow<Map<String, DlsiteWork>>(emptyMap())
    val works: StateFlow<Map<String, DlsiteWork>> = _works.asStateFlow()

    val count: Int get() = _works.value.size

    fun load() {
        runCatching {
            // 老版本把 DLsite 站内 banner 当成过封面（一批作品同一张图）。那些记录的
            // coverUrl 指向的不是本作品，启动时一并清掉，界面立刻回退到本地图片。
            val loaded = store.load()
            val cleaned = sanitizeCovers(loaded)
            _works.value = cleaned.first
            if (cleaned.first !== loaded) {
                runCatching { store.save(cleaned.first) }
            }
            // 地址被改写过的作品顺手补下一遍（替换成真封面），用户不用自己点「重新抓取」。
            // 只在本次被修正时触发，改完地址就定格了，不会每次启动都重试失败的作品。
            refetchCovers(cleaned.second)
            // 已购列表持久化装载：重启后主页「已购未下载」直接可用。
            // 注意：登录态仍保持 UNKNOWN，必须等 refreshLogin 联网验证（项目安全约定）。
            val saved = store.loadPurchases()
            if (saved.isNotEmpty()) {
                _purchases.value = saved
                fetchPurchaseMeta()
            }
        }
    }

    /**
     * 清掉「封面不是这部作品」的历史缓存，并返回需要后台重下封面的编号。
     *
     * 三道判据，从确定到保守：
     *  1. coverUrl 本身不像本作品（站内横幅地址里没有作品编号）→ 地址一定是错的；
     *  2. 同一份文件内容被**多个作品**共用 → 典型的「一堆作品同一张头图」，一定是错的；
     *  3. 文件根本不是图片 / 小到不可能是封面 → 显示出来也没意义。
     *
     * 这里**不按宽高比一刀切**，也**不再因为「地址不理想」就删文件**：真机上出现过
     * 「启动清理删掉全部封面 → 后台补下又全失败 → 一整屏占位图」。能显示的图先留着，
     * 重下成功自然替换。
     */
    private fun sanitizeCovers(map: Map<String, DlsiteWork>): Pair<Map<String, DlsiteWork>, Set<String>> {
        val refetch = LinkedHashSet<String>()
        val out = LinkedHashMap<String, DlsiteWork>(map.size)

        // 按内容分组，找出被多个作品共用的封面文件
        val byDigest = HashMap<String, MutableList<String>>()
        for ((code, work) in map) {
            val file = work.coverLocalPath?.let { java.io.File(it) }?.takeIf { it.isFile } ?: continue
            val digest = runCatching { digestOf(file) }.getOrNull() ?: continue
            byDigest.getOrPut(digest) { ArrayList(2) }.add(code)
        }
        // 注意：原作与译文**本来就共用同一张封面**（译文常常没有独立封面文件），
        // 这种共用是正常的，不能当成「一堆作品同一张头图」的错误。只有彼此毫无编号关联的
        // 作品共用同一张图，才说明缓存里那张是站内横幅。
        val shared = HashSet<String>()
        for (codes in byDigest.values) {
            if (codes.size < 2) continue
            val unrelated = codes.filter { c ->
                val related = map[c]?.let { relatedCodes(it) }.orEmpty().toSet()
                codes.none { other -> other != c && other in related }
            }
            if (unrelated.size > 1) shared.addAll(unrelated)
        }
        if (shared.isNotEmpty()) {
            AppLog.log("发现共用封面（同一张图被 " + shared.size + " 个作品使用）：" + shared.take(8))
        }

        for ((code, work) in map) {
            val file = work.coverLocalPath?.let { java.io.File(it) }?.takeIf { it.isFile }

            // 曾经把「样本图」（试听截图）当封面缓存下来：这种图地址合法、能下载、尺寸正常，
            // 只有按文件名才认得出来。这里**只改地址、保留已有图片**：以前顺手把文件删了再后台补下，
            // 补下一次（网络被挡 / 页面 404）就会一整屏作品全变占位图 —— 真机上就是这么白的。
            val mainUrl = com.kiite.player.core.DlsiteParse.mainFromSample(work.coverUrl)
            if (mainUrl != null && mainUrl != work.coverUrl) {
                out[code] = work.copy(coverUrl = mainUrl)
                refetch.add(code)
                AppLog.log("修正样本图封面：" + code + " → " + mainUrl + "（保留旧图，后台替换）")
                continue
            }

            val urlOk = com.kiite.player.core.DlsiteParse.isPlausibleCover(work.coverUrl, code)
            val verdict = file?.let { coverVerdictOf(it) }
            val broken = verdict == com.kiite.player.core.CoverGuard.Verdict.UNREADABLE ||
                verdict == com.kiite.player.core.CoverGuard.Verdict.TOO_SMALL
            when {
                // 同一张图被一堆毫无关联的作品共用 = 站内横幅，文件和地址本身都是错的
                code in shared || broken -> {
                    runCatching { file?.delete() }
                    out[code] = work.copy(coverLocalPath = null)
                    AppLog.log(
                        "清掉错封面：" + code +
                            (if (code in shared) "（与别的作品共用同一张图）" else "") +
                            "（" + (work.coverUrl ?: "无地址") + "）",
                    )
                }
                // 地址不像本作品（站内图），但本地文件本身合格：只把地址丢掉，图留着 ——
                // 宁可显示一张旧图，也不要因为「地址不理想」白屏
                !urlOk && work.coverUrl != null -> {
                    out[code] = work.copy(coverUrl = null)
                    refetch.add(code)
                    AppLog.log("丢弃可疑封面地址：" + code + "（" + work.coverUrl + "），稍后按编号重取")
                }
                else -> out[code] = work
            }
        }
        return out to refetch
    }

    /**
     * 启动清扫改动过地址的作品，静默重下一遍（把样图 / 缺失的封面换成真封面）。
     *
     * 触发条件是「本次启动改了地址」，改完就定格：下次 `mainFromSample` 返回 null、
     * 可疑地址也已置空，不会每次启动都重试一批永远下不下来的封面。
     * `force = true` 是必要的：本地已有旧图（样图）时也要换成主图。
     */
    private fun refetchCovers(codes: Set<String>) {
        if (codes.isEmpty()) return
        scope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                val next = LinkedHashMap(_works.value)
                var ok = 0
                for (code in codes) {
                    val work = next[code] ?: continue
                    val done = downloadCover(work, force = true)
                    next[code] = done
                    if (done.coverLocalPath != null) ok++
                }
                _works.value = next
                runCatching { store.save(next) }
                AppLog.log("封面修正：" + codes.size + " 个作品，补下成功 " + ok + " 个")
            }
        }
    }

    private fun digestOf(file: java.io.File): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    /** 读一张本地图片的宽高（只读文件头，不解码像素）。 */
    private fun boundsOf(file: java.io.File): Pair<Int, Int>? {
        val opt = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opt)
        return if (opt.outWidth > 0 && opt.outHeight > 0) opt.outWidth to opt.outHeight else null
    }

    private fun coverVerdictOf(file: java.io.File): com.kiite.player.core.CoverGuard.Verdict {
        val wh = boundsOf(file) ?: return com.kiite.player.core.CoverGuard.Verdict.UNREADABLE
        return com.kiite.player.core.CoverGuard.judge(wh.first, wh.second)
    }

    private fun put(work: DlsiteWork) {
        val merged = _works.value + (work.code to work)
        _works.value = merged
        runCatching { store.save(merged) }
    }

    // ---------- 识别 ----------

    /**
     * 批量识别所有带 RJ 编号的项目（已识别过的跳过）。
     *
     * @param force 已识别的也重新抓一遍并重下封面——用于修复历史上被站内 banner
     *   污染成同一张头图的作品（每部作品的封面缓存按编号命名，只能重下才能覆盖）。
     */
    fun fetchAll(force: Boolean = false, codeOf: (ProjectEntry) -> String?) {
        val targets = projects().filter { codeOf(it) != null }
        if (targets.isEmpty()) {
            notify("没有找到带 RJ/VJ/BJ 编号的项目")
            return
        }
        scope.launch {
            var ok = 0
            var failed = 0
            var coverOnly = 0
            var hiddenR18 = 0
            var firstError: String? = null
            targets.forEachIndexed { i, project ->
                val code = codeOf(project)!!.uppercase()
                if (!force && _works.value.containsKey(code)) {
                    ok++
                    // 已识别但封面缺失（上次下载失败 / 被判成横幅清掉了）时补一次；
                    // 本次运行里失败过的不再重试，避免每轮空跑。
                    // 注意：不再要求 coverUrl 非空 —— 地址可以按编号算出来。
                    val cached = _works.value.getValue(code)
                    val hasCover = cached.coverLocalPath?.let { java.io.File(it).isFile } == true
                    if (!hasCover && code !in coverFailures) {
                        val retried = withContext(kotlinx.coroutines.Dispatchers.IO) { downloadCover(cached) }
                        if (retried != cached) put(retried)
                    }
                    return@forEachIndexed
                }
                setBusy("dlsite_batch", "DLsite 识别 " + (i + 1) + "/" + targets.size + "：" + code)
                val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
                val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    DlsiteClient.fetch(code, cookie)
                }
                outcome.onSuccess { work ->
                    val enriched = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        downloadCover(work, force = force)
                    }
                    put(enriched)
                    ok++
                    if (enriched.r18 && hideR18()) hiddenR18++
                }.onFailure { e ->
                    failed++
                    if (firstError == null) firstError = e.message
                    AppLog.log("DLsite 识别失败：" + code + " · " + stateOf(e) + " · " + e.message)
                    // 作品页打不开（404 / 年龄确认 / 风控）不代表没有封面：CDN 的地址是按编号
                    // 拼出来的，单独再试一次。这条让「页面全被挡」的用户至少还有头图。
                    val rescued = withContext(kotlinx.coroutines.Dispatchers.IO) { coverOnly(code) }
                    if (rescued != null) {
                        put(rescued)
                        coverOnly++
                    }
                }
            }
            setBusy("dlsite_batch", null)
            notify(
                "DLsite 批量识别完成：成功 " + ok + "，失败 " + failed +
                    (if (coverOnly > 0) "（其中 " + coverOnly + " 个只补到封面，作品信息仍取不到）" else "") +
                    (firstError?.takeIf { failed > 0 }?.let { "：" + it } ?: "") +
                    (if (hiddenR18 > 0) "；其中 " + hiddenR18 + " 部 R18 因「隐藏 R18」设置暂不显示（设置里可关闭）" else ""),
            )
        }
    }

    /**
     * 只补封面：作品页拿不到时按编号直算 CDN 地址硬试。
     * 成功才返回记录（避免把「只有编号、没有标题」的空壳写进缓存）。
     */
    private fun coverOnly(code: String): DlsiteWork? {
        val existing = _works.value[code]
            ?: DlsiteWork(code = code, productUrl = com.kiite.player.core.DlsiteParse.productUrl(code))
        val done = downloadCover(existing, force = false)
        return done.takeIf { it.coverLocalPath != null }
    }

    /** 按编号抓一次公开信息并缓存。 */
    fun fetchOne(code: String) {
        scope.launch {
            setBusy("dlsite", "正在从 DLsite 获取 " + code + " …")
            AppLog.log("DLsite 识别开始：" + code + "（cookie " + (if (dlsiteCookieHeader().isNullOrBlank()) "无" else "有") + "）")
            val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                DlsiteClient.fetch(code, cookie)
            }
            setBusy("dlsite", null)
            outcome
                .onSuccess { work ->
                    // 用户手动点「重新识别」：封面也重下，才有机会覆盖掉历史上错的缓存
                    val withCover = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        downloadCover(work, force = true)
                    }
                    put(withCover)
                    AppLog.log("DLsite 识别成功：" + code + " · " + (withCover.title ?: "（无标题）"))
                    notify(
                        "已识别：" + (withCover.title ?: withCover.code) +
                            (if (withCover.owned) "（已购买）" else "") +
                            (if (withCover.r18 && hideR18()) "；该作品为 R18，因「隐藏 R18」设置暂不显示（设置里可关闭）" else ""),
                    )
                }
                .onFailure { e ->
                    AppLog.log("DLsite 识别失败：" + code + " · " + stateOf(e) + " · " + e.message)
                    // 页面被挡也先试着按编号把封面拿下来：能出图就别让界面一片空白
                    val rescued = withContext(kotlinx.coroutines.Dispatchers.IO) { coverOnly(code.uppercase()) }
                    if (rescued != null) {
                        put(rescued)
                        notify("作品信息获取失败（" + e.message + "），但已按编号补到封面")
                    } else {
                        notify("获取失败：" + e.message)
                    }
                }
        }
    }

    /**
     * 把作品封面下载到应用内部，作为专辑封面。
     *
     * 分两层尝试，顺序是刻意的：
     *
     *  1. **地址**：`_img_main`（封面主图）优先；页面只给了样图（试听截图）时按命名规则
     *     换算成主图再试；主图确实不存在才退回原地址。2.4.0 及更早版本拿到的其实就是
     *     样图 —— 不理想，但至少「有图」，比一片占位好。
     *     **页面完全拿不到时**（404 / 年龄确认 / 风控 / 没登录），再按编号把 CDN 地址直接
     *     算出来试（见 [com.kiite.player.core.DlsiteParse.coverUrlsForCode]）—— 这条不依赖
     *     任何页面，是"不登录也能有头图"的最后一道保险。
     *  2. **请求方式**：先按老版本的做法裸请求（只带 UA），失败再带 Referer 与 DLsite
     *     会话 Cookie 重试。老版本不登录也能下到图，说明图片 CDN 对裸请求是放行的；带上
     *     来源信息反而可能被回一张站内横幅。反过来，登录后少数图确实需要会话，两轮都留着。
     *
     * 拿到字节后必须解一次真实尺寸（[com.kiite.player.core.CoverGuard]）：DLsite 会用一张
     * 超宽横幅顶替封面，而地址完全合法。先写 `.part` 再改名，半截文件不会冒充封面。
     *
     * @param force true 时无视本地缓存重新下载（「重新识别」/「重抓全部头图」用）。
     */
    private fun downloadCover(work: DlsiteWork, force: Boolean = false): DlsiteWork {
        val dir = coversDir()
        val out = java.io.File(dir, work.code + ".jpg")
        // 理想地址：样图换算成同作品封面主图，顺手把旧数据里的样图地址修正掉
        val fromPage = work.coverUrl?.let { com.kiite.player.core.DlsiteParse.mainFromSample(it) ?: it }
        // 按编号直接算出来的 CDN 地址（封面主图 / 样图兜底）——页面 404、被年龄确认挡住、
        // 根本没登录时，这是唯一还能出图的路。
        val derived = com.kiite.player.core.DlsiteParse.coverUrlsForCode(work.code)
        val ideal = fromPage ?: derived.firstOrNull()
        val candidates = LinkedHashSet<String>(8)
        fromPage?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        work.coverUrl?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        derived.forEach { candidates.add(it) }
        // 跨编号候选：译文作品常常没有独立的封面文件，用译文编号拼地址在 CDN 上就是空的
        // （回 NoSuchKey 的 XML），而原作编号那张图是好的。反过来同理。
        // 只做「规则内」的换算（同一 CDN 目录 + 文件名编号），不虚构路径，下不到就正常失败。
        for (base in candidates.toList()) {
            for (alt in relatedCodes(work)) {
                com.kiite.player.core.DlsiteParse.coverUrlFor(base, alt)?.let { candidates.add(it) }
            }
        }
        if (candidates.isEmpty()) {
            // 这次没有可用地址：保留已有且合格的本地图，绝不因为「没地址」把封面删了
            val keep = out.takeIf { it.isFile && it.length() > 0 && coverVerdictOf(it) == com.kiite.player.core.CoverGuard.Verdict.OK }
            return work.copy(coverLocalPath = keep?.absolutePath)
        }
        val path = runCatching {
            dir.mkdirs()
            if (!force && out.isFile && out.length() > 0 &&
                coverVerdictOf(out) == com.kiite.player.core.CoverGuard.Verdict.OK
            ) {
                return@runCatching out.absolutePath
            }
            // 注意：这里**不再先删旧图**。旧图先留着，新图下到 .part 成功后才顶替；
            // 一次网络失败不该让原本能显示的封面消失。
            for (src in candidates) {
                // false = 裸请求（只带 UA，2.4.0 的做法，不登录也能下）；true = 带 Referer / 会话
                for (withIdentity in booleanArrayOf(false, true)) {
                    when (fetchCover(src, out, work, withIdentity)) {
                        CoverFetch.OK -> return@runCatching out.absolutePath
                        // 主机根本连不上：换候选地址也是一样的结局，别让用户干等 N 次超时
                        CoverFetch.NETWORK -> return@runCatching null
                        CoverFetch.FAILED -> Unit
                    }
                }
            }
            null
        }.getOrNull()
        // 成功与否都把 coverUrl 定成理想地址：本地可能暂时是降级的图，
        // 但地址指向主图，下次重新抓取还能再试，不会被样图地址永久钉住。
        return if (path == null) {
            coverFailures.add(work.code)
            val keep = out.takeIf { it.isFile && it.length() > 0 && coverVerdictOf(it) == com.kiite.player.core.CoverGuard.Verdict.OK }
            AppLog.log(
                "封面下载失败 " + work.code + "：候选 " + candidates.size + " 个全部未采用" +
                    (if (keep != null) "（保留原有封面）" else ""),
            )
            work.copy(coverUrl = ideal, coverLocalPath = keep?.absolutePath)
        } else {
            coverFailures.remove(work.code)
            work.copy(coverUrl = ideal, coverLocalPath = path)
        }
    }

    /** 与本作品可能共用同一张封面的其它编号（原作 / 译文），不含自己。 */
    private fun relatedCodes(work: DlsiteWork): List<String> {
        val self = work.code.uppercase()
        return listOfNotNull(work.originalCode, work.translationCode)
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it != self }
            .distinct()
    }

    /**
     * 试下一次封面地址，合格才落到 [out]。
     *
     * @param withIdentity 是否带 `Referer` 与 DLsite 会话 Cookie。
     */
    private fun fetchCover(
        url: String,
        out: java.io.File,
        work: DlsiteWork,
        withIdentity: Boolean,
    ): CoverFetch {
        val tmp = java.io.File(out.parentFile, work.code + ".part")
        runCatching { tmp.delete() }
        var status = 0
        var contentType = ""
        var length = -1L
        var finalUrl = url
        var xmlError: String? = null
        var failure: String? = null
        val ok = runCatching {
            val referer = work.productUrl ?: com.kiite.player.core.DlsiteParse.productUrl(work.code)
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", BROWSER_UA)
                if (withIdentity) {
                    setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                    runCatching { setRequestProperty("Referer", referer) }
                    if (isDlsiteHost(url)) {
                        runCatching { dlsiteCookieHeader() }.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { setRequestProperty("Cookie", it) }
                    }
                }
            }
            try {
                status = runCatching { conn.responseCode }.getOrDefault(0)
                contentType = conn.contentType.orEmpty()
                length = runCatching { conn.contentLengthLong }.getOrDefault(-1L)
                finalUrl = conn.url?.toString() ?: url
                if (status >= 400) {
                    // CDN 对不存在的 key 回的是 XML（NoSuchKey / AccessDenied），
                    // 里面写着明确原因，比「HTTP 404」有用
                    xmlError = readXmlError(conn)
                    val msg = "HTTP " + status + (xmlError?.let { "（" + it + "）" } ?: "")
                    failure = msg
                    error(msg)
                }
                // 只拦明确不是图片的（HTML 年龄确认页等）；octet-stream 交给尺寸校验兜底
                val looksImage = contentType.startsWith("image/", ignoreCase = true) ||
                    contentType.isBlank() ||
                    contentType.startsWith("application/octet-stream", ignoreCase = true)
                if (!looksImage) {
                    xmlError = readXmlError(conn)
                    val msg = "返回的不是图片（" + contentType + "）" + (xmlError?.let { "：" + it } ?: "")
                    failure = msg
                    error(msg)
                }
                conn.inputStream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
            } finally {
                conn.disconnect()
            }
            val wh = boundsOf(tmp)
            if (wh == null) {
                // 状态 200、Content-Type 也是图片，但字节其实是 XML —— 同样解析出错误码
                xmlError = xmlError ?: readXmlError(tmp)
                val msg = "下回来的文件解不出图片" + (xmlError?.let { "（" + it + "）" } ?: "")
                failure = msg
                error(msg)
            }
            val verdict = com.kiite.player.core.CoverGuard.judge(wh.first, wh.second)
            if (verdict != com.kiite.player.core.CoverGuard.Verdict.OK) {
                val msg = com.kiite.player.core.CoverGuard.reason(wh.first, wh.second)
                failure = msg
                error(msg)
            }
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                runCatching { tmp.delete() }
            }
            true
        }.onFailure { if (failure == null) failure = it.message ?: it.javaClass.simpleName }
            .getOrDefault(false)
        // 主机连不上（DNS 被劫持 / 被墙）：换地址也没意义，交给上层提前收手
        val outcome = when {
            ok -> CoverFetch.OK
            status == 0 -> CoverFetch.NETWORK
            else -> CoverFetch.FAILED
        }
        if (ok) {
            AppLog.log(
                "[DLsite Cover Debug] code=" + work.code +
                    " url=" + url +
                    " status=" + status +
                    " type=" + contentType.ifBlank { "-" } +
                    " mode=" + (if (withIdentity) "identity" else "bare") +
                    " result=OK",
            )
        } else {
            AppLog.log(
                "[DLsite Cover Debug] code=" + work.code +
                    " original=" + (work.originalCode ?: work.code) +
                    " translation=" + (work.translationCode ?: "-") +
                    " page=" + (work.productUrl ?: "-") +
                    " url=" + url +
                    " final=" + finalUrl +
                    " status=" + status +
                    " type=" + contentType.ifBlank { "-" } +
                    " len=" + length +
                    " xml=" + (xmlError ?: "-") +
                    " mode=" + (if (withIdentity) "identity" else "bare") +
                    " referer=" + runCatching { java.net.URL(work.productUrl ?: com.kiite.player.core.DlsiteParse.productUrl(work.code)).host }.getOrDefault("-") +
                    " result=" + (failure ?: "失败"),
            )
        }
        runCatching { if (tmp.isFile) tmp.delete() }
        return outcome
    }

    /** 从响应体里读出 CDN 的 XML 错误说明（不是 XML 就返回 null）。 */
    private fun readXmlError(conn: java.net.HttpURLConnection): String? = runCatching {
        val stream = conn.errorStream ?: conn.inputStream
        stream.use { readXmlError(it) }
    }.getOrNull()

    private fun readXmlError(file: java.io.File): String? = runCatching {
        file.takeIf { it.isFile && it.length() > 0 }?.inputStream()?.use { readXmlError(it) }
    }.getOrNull()

    private fun readXmlError(input: java.io.InputStream): String? {
        val buf = ByteArray(4096)
        val n = input.read(buf)
        if (n <= 0) return null
        return com.kiite.player.core.DlsiteCdnError.parse(String(buf, 0, n, Charsets.UTF_8))
    }

    private companion object {
        /** 带来源信息时用完整浏览器 UA。裸请求也用它 —— 截断的 UA 更容易被 CDN 判成爬虫。 */
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
    }

    /** 一次封面请求的结论：成功 / 这个地址不行 / 整台机器连不上 CDN。 */
    private enum class CoverFetch { OK, FAILED, NETWORK }

    private fun coversDir(): java.io.File = java.io.File(app.filesDir, "dlsite-covers")

    /** 失败原因的状态分类（日志用），让「作品不存在」和「被风控挡住」能分开看。 */
    private fun stateOf(e: Throwable): String =
        (e as? com.kiite.player.core.DlsiteFetchException)?.state?.name ?: "ERROR"

    /** 同一次运行里已经失败过的封面不再反复重试，避免每轮批量识别都空跑一遍。 */
    private val coverFailures = java.util.Collections.synchronizedSet(HashSet<String>())

    // ---------- 登录态 / 已购 ----------

    private val _loginState = MutableStateFlow(DlsiteLoginState.UNKNOWN)
    val loginState: StateFlow<DlsiteLoginState> = _loginState.asStateFlow()

    private val _purchases = MutableStateFlow<List<DlsitePurchase>>(emptyList())
    val purchases: StateFlow<List<DlsitePurchase>> = _purchases.asStateFlow()

    fun refreshLogin() {
        scope.launch {
            val state = withContext(kotlinx.coroutines.Dispatchers.IO) {
                DlsiteClient.verifyLogin(runCatching { dlsiteCookieHeader() }.getOrNull())
            }
            _loginState.value = state
            // 登录有效且还没有已购列表（首次使用或被清掉）时，静默同步一次，主页就能直接显示
            if (state == DlsiteLoginState.LOGGED_IN && _purchases.value.isEmpty()) {
                syncPurchases(manual = false)
            }
        }
    }

    /** @param manual 手动点「同步」时给提示；启动自动同步静默进行，失败不打扰。 */
    fun syncPurchases(manual: Boolean = true) {
        scope.launch {
            if (manual) setBusy("purchases", "正在同步已购作品…")
            val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                DlsiteClient.fetchPurchases(cookie)
            }
            if (manual) setBusy("purchases", null)
            outcome
                .onSuccess { list ->
                    _purchases.value = list
                    _loginState.value = DlsiteLoginState.LOGGED_IN
                    runCatching { store.savePurchases(list) }
                    if (manual) notify("已同步 " + list.size + " 部已购作品")
                    fetchPurchaseMeta()
                }
                .onFailure {
                    _loginState.value = DlsiteLoginState.LOGGED_OUT
                    if (manual) notify("同步失败：" + it.message)
                }
        }
    }

    fun logout() {
        runCatching { clearDlsiteSession() }
        _loginState.value = DlsiteLoginState.LOGGED_OUT
        _purchases.value = emptyList()
        runCatching { store.clearPurchases() }
        notify("已退出 DLsite")
    }

    /**
     * 给已购条目补抓元数据（标题之外的封面 / 社团 / 简介），让主页「已购未下载」
     * 直接带封面显示。串行小步跑，避免并发把 DLsite 打挂；本地已有识别结果的跳过。
     */
    private var metaFetching = false

    private fun fetchPurchaseMeta() {
        if (metaFetching) return
        metaFetching = true
        scope.launch {
            val targets = _purchases.value
                .filter { it.code.uppercase() !in _works.value.keys }
                .take(60)
            var ok = 0
            var failed = 0
            targets.forEachIndexed { i, item ->
                val code = item.code.uppercase()
                setBusy("purchase_meta", "DLsite 元数据 " + (i + 1) + "/" + targets.size + "：" + code)
                val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
                val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    DlsiteClient.fetch(code, cookie)
                }
                outcome.onSuccess { work ->
                    val enriched = withContext(kotlinx.coroutines.Dispatchers.IO) { downloadCover(work) }
                    put(enriched)
                    ok++
                }.onFailure { failed++ }
            }
            setBusy("purchase_meta", null)
            metaFetching = false
            if (targets.isNotEmpty()) {
                AppLog.log("DLsite 已购元数据补抓完成：成功 " + ok + "，失败 " + failed)
            }
        }
    }

    // ---------- 已下载标记（压缩包下了但没解压时也不能算「未下载」） ----------

    private val downloadedCodesFile = java.io.File(app.filesDir, "downloaded-codes.txt")

    private val _downloadedCodes = MutableStateFlow<Set<String>>(emptySet())
    val downloadedCodes: StateFlow<Set<String>> = _downloadedCodes.asStateFlow()

    fun loadDownloadedCodes() {
        runCatching {
            _downloadedCodes.value = if (downloadedCodesFile.isFile) {
                downloadedCodesFile.readLines().map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }
        }
    }

    private fun markDownloaded(code: String) {
        val merged = _downloadedCodes.value + code.uppercase()
        _downloadedCodes.value = merged
        runCatching { downloadedCodesFile.writeText(merged.joinToString("\n")) }
    }

    /** 已购里、本地还没有的作品。 */
    fun missingPurchases(): List<DlsitePurchase> {
        val local = projects().mapNotNull { it.code?.uppercase() }.toSet()
        val rest = _purchases.value.filter {
            val c = it.code.uppercase()
            c !in local && c !in _downloadedCodes.value
        }
        return if (purchaseAsmrOnly()) rest.filter { it.asmr } else rest
    }

    /** 已购里非音声的作品数量（用于提示被过滤掉多少）。 */
    fun skippedNonAsmr(): Int {
        val local = projects().mapNotNull { it.code?.uppercase() }.toSet()
        return _purchases.value.count { it.code.uppercase() !in local && !it.asmr }
    }

    // ---------- 站内在线搜索 ----------

    /** 在线搜索的状态：主页搜索切到「DLsite 在线」标签时使用。 */
    data class OnlineSearch(
        val loading: Boolean = false,
        val query: String = "",
        val hits: List<com.kiite.player.core.DlsiteSearchHit> = emptyList(),
        val error: String? = null,
    )

    private val _onlineSearch = MutableStateFlow(OnlineSearch())
    val onlineSearch: StateFlow<OnlineSearch> = _onlineSearch.asStateFlow()

    private var searchSeq = 0

    fun searchOnline(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            searchSeq++
            _onlineSearch.value = OnlineSearch()
            return
        }
        val seq = ++searchSeq
        _onlineSearch.value = OnlineSearch(loading = true, query = trimmed)
        scope.launch {
            val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                DlsiteClient.search(trimmed, cookie)
            }
            if (seq != searchSeq) return@launch
            _onlineSearch.value = outcome.fold(
                { hits ->
                    OnlineSearch(
                        query = trimmed,
                        hits = hits,
                        error = if (hits.isEmpty()) "DLsite 没有搜到相关作品" else null,
                    )
                },
                { OnlineSearch(query = trimmed, error = "在线搜索失败：" + it.message) },
            )
        }
    }

    // ---------- 应用内下载 ----------
    //
    // 下载本体在 [DlsiteDownloads]（应用级作用域）：退出下载页 / 切后台 / 锁屏都继续，
    // 通知栏由 [com.kiite.player.download.DownloadService] 统一挂。这里只做转发。

    private val downloads: DlsiteDownloader by lazy { DlsiteDownloads.get(app) }

    /** 当前 DLsite 下载状态（null = 空闲）。 */
    val downloadState: StateFlow<DlsiteDownloader.State?> = downloads.state

    /** 提示流（完成/失败），VM 收集后弹提示。 */
    val downloadEvents = downloads.events

    fun clearStatus() {
        downloads.clear()
    }

    /** 取消当前下载（连接立刻断开，半截文件删掉）。 */
    fun cancelDownload() = downloads.cancel()

    /** 下载的是压缩包，就地解压（复用已有的 zip 解压）。 */
    fun extractDownloaded() {
        val path = downloads.state.value?.savedPath ?: return
        scope.launch {
            // 每个下载的作品解压成曲库根目录下的独立一级文件夹，
            // 否则全落在 DLsite/ 里会被当成同一个总项目
            val root = rootPath()?.let { java.io.File(it) }
            val target = root?.let { java.io.File(it, java.io.File(path).nameWithoutExtension) }
            val outcome = runCatching { repo.extractZip(path, target) }
            notify(
                outcome.fold(
                    { n ->
                        rescan()
                        "已解压 " + n + " 个文件，正在重新扫描"
                    },
                    { "解压失败：" + it.message + "（rar/7z 需要外部解压工具）" },
                ),
            )
        }
    }

    /** 从下载页捕获到的直链：带登录 Cookie 拉取并写入本地（后台跑，可取消）。 */
    fun startDownload(url: String, userAgent: String?, contentDisposition: String?, code: String? = null) {
        // 直链可能指向第三方 CDN，绝不能把 DLsite 会话 Cookie 发过去
        val cookie = runCatching { dlsiteCookieHeader() }.getOrNull()
            ?.takeIf { isDlsiteHost(url) }
        downloads.start(
            url = url,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            code = code,
            rootDir = downloadBase(),
            cookie = cookie,
            onCompleted = { c ->
                // 标记「已下载」，主页的「已购未下载」列表就不会再列它
                c?.let { markDownloaded(it) }
            },
        )
    }

    /**
     * 下载落盘位置：设置里自选的目录优先，其次曲库根目录。
     * 都会在下面再建一层 `DLsite/`（用户已经选到 DLsite 目录时不再套一层）。
     */
    private fun downloadBase(): String? =
        downloadDir()?.trim()?.takeIf { it.isNotEmpty() }
            ?: rootPath()?.trim()?.takeIf { it.isNotEmpty() }

    /** 只对 DLsite 自己（及其子域）附带登录态。 */
    private fun isDlsiteHost(url: String): Boolean = runCatching {
        val host = java.net.URL(url).host.lowercase()
        host == "dlsite.com" || host.endsWith(".dlsite.com")
    }.getOrDefault(false)
}
