package com.kiite.player.core

/**
 * 文件名归一化与相似度计算，用于音频 ↔ 台本的自动匹配。
 */
object TextUtils {

    private val NOISE_TOKENS = listOf(
        "台本", "台本集", "脚本", "文本", "字幕", "翻译", "汉化", "中文", "中字", "字幕组", "原文",
        "script", "scripts", "transcript", "translation", "subtitle", "subs", "text", "lyrics",
        "本篇", "完整版", "完全版", "全篇", "全文", "附台本", "带台本", "版",
        "full", "ver", "version", "final", "edit", "rev", "hires", "lossless",
    )
    private val NOISE_SET = NOISE_TOKENS.toHashSet()

    /** 只对 CJK 噪声词做「词内剥离」，避免 cover 里的 ver、reverb 里的 verb 被误删。 */
    private val CJK_NOISE = NOISE_TOKENS.filter { t -> t.any { it.code > 0x2E80 } }

    private val BRACKET_CONTENT = Regex("""[（(【\[][^）)】\]]*[）)】\]]""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val LEADING_NUM = Regex("""(?:第)?0*(\d{1,3})(?!\d)""")
    private val LRC_TAG = Regex("""\[\s*\d{1,3}\s*:\s*\d{1,2}(?:[.:]\d{1,3})?\s*]""")
    private val EXT_SUFFIX = Regex("""\.[a-z0-9]{1,5}$""")

    /** 全角 → 半角。 */
    fun toHalfWidth(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val c = ch.code
            when {
                c in 0xFF01..0xFF5E -> sb.append((c - 0xFEE0).toChar())
                c == 0x3000 -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** 去掉扩展名，仅当形如 .ext 时。 */
    fun stripExtension(name: String): String = name.replace(EXT_SUFFIX, "")

    /** 归一化：小写、去括号内容、去标点、去噪声词、压缩空白。 */
    fun normalizeForMatch(raw: String): String {
        var s = toHalfWidth(raw).lowercase()
        s = stripExtension(s)
        s = s.replace(BRACKET_CONTENT, " ")
        s = s.replace(NON_WORD, " ")
        val kept = s.split(' ')
            .filter { it.isNotBlank() }
            .map { cleanToken(it) }
            .filter { it.isNotBlank() }
        return kept.joinToString(" ").trim()
    }

    private fun cleanToken(token: String): String {
        if (token in NOISE_SET) return ""
        var t = token
        for (n in CJK_NOISE) {
            if (t.isEmpty()) break
            if (t.contains(n)) t = t.replace(n, "")
        }
        return if (t in NOISE_SET) "" else t
    }

    /** 提取编号；仅在前 16 个字符内寻找，降低年份等误判。 */
    fun leadingNumber(raw: String): Int? {
        val s = toHalfWidth(raw).lowercase()
        val head = s.take(16)
        val m = LEADING_NUM.find(head) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        return if (n in 1..999) n else null
    }

    private fun bigrams(s: String): Set<String> {
        val compact = s.replace(" ", "")
        if (compact.isEmpty()) return emptySet()
        if (compact.length == 1) return setOf(compact)
        val out = HashSet<String>(compact.length)
        for (i in 0 until compact.length - 1) out.add(compact.substring(i, i + 2))
        return out
    }

    /** Dice 系数 0.0 ~ 1.0。 */
    fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val x = bigrams(a)
        val y = bigrams(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val inter = x.count { it in y }
        return (2.0 * inter) / (x.size + y.size)
    }

    private val WORK_CODE = Regex("""(?i)(RJ|VJ|BJ|RE|DL|KUI)\s?(\d{4,})""")

    /** 从目录名里识别作品编号，如 RJ123456。用于「自动识别总项目」。 */
    fun workCode(text: String): String? =
        WORK_CODE.find(text)?.let { m -> (m.groupValues[1] + m.groupValues[2]).uppercase() }

    /** 文本是否含 LRC 时间标签。 */
    fun looksLikeLrc(text: String): Boolean = LRC_TAG.containsMatchIn(text)

    /** 含时间标签且时间标签后确有内容行数。 */
    fun lrcLineCount(text: String): Int = text.lineSequence().count { line ->
        LRC_TAG.containsMatchIn(line) && line.replace(LRC_TAG, "").isNotBlank()
    }

    /** 交给正则逐行取出 LRC 标签与文本。 */
    fun lrcTagsOf(line: String): List<String> = LRC_TAG.findAll(line).map { it.value }.toList()
    fun lrcTextOf(line: String): String = line.replace(LRC_TAG, "").trim()
    /**
     * 行首时间标注：可带 `[] () （） 【】` 括号，秒后可用 `.` 或 `．` 带小数，
     * 也支持 `h:mm:ss`；时间与正文之间可用空格或 `- — : ： 、 , .` 等分隔。
     */
    private val INLINE_TS = Regex(
        """^\s*[\[\(（【]?\s*(\d{1,3}(?:\s*[:：]\s*\d{1,2}){1,2}(?:\s*[.．]\s*\d{1,3})?)\s*[\]\)）】]?\s*[-—–~～:：、,，.>»]?\s*(\S.*)""",
    )

    /** 从一行里取出「时间 + 正文」；该行没有时间标注则返回 null。 */
    fun inlineTimestamp(line: String): Pair<Long, String>? {
        val m = INLINE_TS.find(line.trim()) ?: return null
        val ms = parseFlexibleTime(m.groupValues[1]) ?: return null
        val text = m.groupValues[2].trim()
        if (text.isEmpty()) return null
        return ms to text
    }

    /** 宽松时间解析："12:34" → 754000；"1:02:03.5" → 3723500。 */
    fun parseFlexibleTime(raw: String): Long? {
        val norm = raw.trim()
            .trim('[', ']', '(', ')', '（', '）', '【', '】')
            .replace('：', ':')
            .replace('．', '.')
            .replace(" ", "")
        val parts = norm.split(':')
        if (parts.size !in 2..3) return null
        fun frac(s: String): Long {
            val f = s.substringAfter('.', "")
            return when (f.length) {
                0 -> 0L
                1 -> (f.toLongOrNull() ?: 0L) * 100
                2 -> (f.toLongOrNull() ?: 0L) * 10
                else -> f.take(3).toLongOrNull() ?: 0L
            }
        }
        return if (parts.size == 2) {
            val m = parts[0].toLongOrNull() ?: return null
            val sec = parts[1]
            val whole = sec.substringBefore('.').toLongOrNull() ?: return null
            m * 60_000 + whole * 1000 + frac(sec)
        } else {
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val sec = parts[2]
            val whole = sec.substringBefore('.').toLongOrNull() ?: return null
            h * 3_600_000 + m * 60_000 + whole * 1000 + frac(sec)
        }
    }

    /** LRC 时间标签解析，直接复用宽松解析（同时支持 mm:ss.xx 与 h:mm:ss）。 */
    fun parseLrcTimestamp(tag: String): Long? = parseFlexibleTime(tag)
}
