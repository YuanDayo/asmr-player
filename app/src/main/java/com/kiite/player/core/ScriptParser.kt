package com.kiite.player.core

import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** 带时间轴的一句台本。 */
data class TimedLine(val startMs: Long, val endMs: Long?, val text: String)

/** 解析后的台本。 */
data class ParsedScript(
    val format: ScriptFormat,
    val plainText: String,
    val timedLines: List<TimedLine> = emptyList(),
    val title: String? = null,
    val warning: String? = null,
) {
    val isTimed: Boolean get() = timedLines.isNotEmpty()
    val isEmpty: Boolean get() = plainText.isBlank()
}

/** PDF 文本抽取由 App 层注入（pdfbox-android），核心逻辑保持纯 JVM 可测。 */
fun interface PdfTextExtractor {
    fun extract(file: File): String?
}

/**
 * 台本解析：txt / md / lrc / srt / ass / docx / pdf。
 * 若 txt/md 内容里带 LRC 时间标签，会按 LRC 解析。
 */
class ScriptParser(
    private val pdfExtractor: PdfTextExtractor? = null,
    private val maxChars: Int = 400_000,
) {

    fun parse(file: File): ParsedScript = parse(file.name, file)

    fun parse(name: String, file: File): ParsedScript {
        val format = ScriptFormat.of(name)
        return try {
            when (format) {
                ScriptFormat.DOCX -> parseDocx(name, file)
                ScriptFormat.PDF -> parsePdf(name, file)
                ScriptFormat.LRC -> fromLrc(safeDecode(file.readBytes()))
                ScriptFormat.SRT -> fromSrt(safeDecode(file.readBytes()))
                ScriptFormat.VTT -> fromVtt(safeDecode(file.readBytes()))
                ScriptFormat.ASS -> fromAss(safeDecode(file.readBytes()))
                ScriptFormat.MD -> fromPlainOrLrc(ScriptFormat.MD, stripMarkdown(safeDecode(file.readBytes())))
                ScriptFormat.TXT -> fromPlainOrLrc(ScriptFormat.TXT, safeDecode(file.readBytes()))
                ScriptFormat.UNKNOWN -> fromPlainOrLrc(ScriptFormat.UNKNOWN, safeDecode(file.readBytes()))
            }
        } catch (e: Exception) {
            ParsedScript(format, "", warning = "解析失败: ${e.message}")
        }
    }

    /** 直接解析内存文本，便于单测与缓存。 */
    fun parseText(name: String, text: String): ParsedScript {
        return when (ScriptFormat.of(name)) {
            ScriptFormat.LRC -> fromLrc(text)
            ScriptFormat.SRT -> fromSrt(text)
            ScriptFormat.VTT -> fromVtt(text)
            ScriptFormat.ASS -> fromAss(text)
            ScriptFormat.MD -> fromPlainOrLrc(ScriptFormat.MD, stripMarkdown(text))
            else -> fromPlainOrLrc(ScriptFormat.TXT, text)
        }
    }

    private fun parseDocx(name: String, file: File): ParsedScript {
        val text = DocxText.extract(file) ?: return ParsedScript(ScriptFormat.DOCX, "", warning = "无法读取 docx")
        return if (TextUtils.lrcLineCount(text) >= 3) fromLrc(text) else fromPlainOrLrc(ScriptFormat.DOCX, text)
    }

    private fun parsePdf(name: String, file: File): ParsedScript {
        val extractor = pdfExtractor ?: return ParsedScript(ScriptFormat.PDF, "", warning = "PDF 抽取器未就绪")
        val text = extractor.extract(file) ?: return ParsedScript(ScriptFormat.PDF, "", warning = "无法从 PDF 提取文本（可能是扫描件）")
        return if (TextUtils.lrcLineCount(text) >= 3) fromLrc(text) else fromPlainOrLrc(ScriptFormat.PDF, text)
    }

    private fun fromPlainOrLrc(format: ScriptFormat, text: String): ParsedScript {
        val trimmed = text.trim()
        val lrc = if (TextUtils.lrcLineCount(trimmed) >= 3) fromLrc(trimmed) else null
        if (lrc != null) return lrc.copy(format = format)
        fromInlineTimed(trimmed, format)?.let { return it }
        return ParsedScript(format, clip(trimmed))
    }

    /**
     * 纯文本台本（txt / md / docx / pdf）里用行首时间标注来对齐音频，
     * 例如 `[00:12] 台词`、`00:12：台词`、`1:02:03 - 台词`。
     *
     * 为避免把普通文本误判成时间轴，要求：至少 3 行带时间、带时间的行占比 ≥ 70%、且时间单调不减。
     */
    fun fromInlineTimed(text: String, format: ScriptFormat): ParsedScript? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 3) return null

        val parsed = ArrayList<TimedLine>(lines.size)
        var last = -1L
        var monotonic = true
        for (line in lines) {
            val hit = TextUtils.inlineTimestamp(line) ?: continue
            if (hit.first < last) monotonic = false
            last = hit.first
            parsed.add(TimedLine(hit.first, null, hit.second))
        }
        if (parsed.size < 3) return null
        if (parsed.size * 10 < lines.size * 7) return null
        if (!monotonic) return null

        parsed.sortBy { it.startMs }
        val withEnd = parsed.mapIndexed { i, l -> l.copy(endMs = parsed.getOrNull(i + 1)?.startMs) }
        val plain = clip(withEnd.joinToString("\n") { it.text }.trim())
        return ParsedScript(format, plain, withEnd)
    }

    private val META_TAGS = listOf("[ar:", "[al:", "[by:", "[re:", "[ve:", "[length:", "[au:", "[ti:")

    /** LRC：[mm:ss.xx]文本，一行可有多个时间标签；支持 [offset:±ms]。 */
    fun fromLrc(text: String): ParsedScript {
        val lines = ArrayList<TimedLine>()
        var title: String? = null
        var offsetMs = 0L
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            // 元数据标签必须先处理：它们没有时间标签，否则会被当成空行跳过
            if (line.startsWith("[ti:")) {
                title = line.substringAfter(':').trim().trimEnd(']').trim().ifBlank { null }
                continue
            }
            if (line.startsWith("[offset:")) {
                offsetMs = line.substringAfter(':').trim().trimEnd(']').trim().toLongOrNull() ?: 0L
                continue
            }
            if (META_TAGS.any { line.startsWith(it) }) continue
            val tags = TextUtils.lrcTagsOf(line)
            if (tags.isEmpty()) continue
            val content = TextUtils.lrcTextOf(line)
            if (content.isEmpty()) continue
            for (tag in tags) {
                val ms = TextUtils.parseLrcTimestamp(tag) ?: continue
                lines.add(TimedLine(ms + offsetMs, null, content))
            }
        }
        lines.sortBy { it.startMs }
        val withEnd = lines.mapIndexed { i, l ->
            val end = lines.getOrNull(i + 1)?.startMs
            l.copy(endMs = end)
        }
        val plain = clip(withEnd.joinToString("\n") { it.text }.trim())
        return ParsedScript(ScriptFormat.LRC, plain, withEnd, title)
    }

    /** SRT：序号 / 00:00:01,000 --> 00:00:04,000 / 文本行。 */
    fun fromSrt(text: String): ParsedScript {
        val lines = ArrayList<TimedLine>()
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalized.split(Regex("""\n\s*\n"""))
        val timeRe = Regex("""(\d{1,2}):(\d{1,2}):(\d{1,2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{1,2}):(\d{1,2})[,.](\d{1,3})""")
        for (block in blocks) {
            val bl = block.trim()
            if (bl.isEmpty()) continue
            val m = timeRe.find(bl) ?: continue
            val start = hmsToMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
            val end = hmsToMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
            val body = bl.substring(m.range.last + 1).trim()
            val content = body.replace(Regex("""<[^>]+>"""), "").trim()
            if (content.isNotEmpty()) lines.add(TimedLine(start, end, content))
        }
        lines.sortBy { it.startMs }
        val plain = clip(lines.joinToString("\n") { it.text }.trim())
        return ParsedScript(ScriptFormat.SRT, plain, lines)
    }

    /**
     * WebVTT：
     *   WEBVTT
     *   00:00:01.000 --> 00:00:04.000
     *   台词
     * 支持 mm:ss.mmm 与 hh:mm:ss.mmm、NOTE/STYLE/REGION 块、以及 <v>/<c>/<b> 等行内标签。
     */
    fun fromVtt(text: String): ParsedScript {
        val lines = ArrayList<TimedLine>()
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        for (block in normalized.split(VTT_BLOCK_SEP)) {
            val body = block.trim()
            if (body.isEmpty()) continue
            val head = body.substringBefore('\n').trim()
            if (head.startsWith("WEBVTT")) {
                // 头部块里可能直接跟着第一条 cue
                if (!body.contains("-->")) continue
            }
            if (head.startsWith("NOTE") || head.startsWith("STYLE") || head.startsWith("REGION")) continue

            val rows = body.split('\n')
            val cueRow = rows.indexOfFirst { it.contains("-->") }
            if (cueRow < 0) continue
            val m = VTT_CUE.find(rows[cueRow].trim()) ?: continue
            val start = TextUtils.parseFlexibleTime(m.groupValues[1]) ?: continue
            val end = TextUtils.parseFlexibleTime(m.groupValues[2])
            val content = stripVttMarkup(rows.drop(cueRow + 1).joinToString("\n")).trim()
            if (content.isNotEmpty()) lines.add(TimedLine(start, end, content))
        }
        lines.sortBy { it.startMs }
        val plain = clip(lines.joinToString("\n") { it.text }.trim())
        return ParsedScript(ScriptFormat.VTT, plain, lines)
    }

    /** 去掉 VTT 的行内标签与实体。 */
    fun stripVttMarkup(s: String): String = s
        .replace(Regex("""<[^>]*>"""), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&lrm;", "")
        .replace("&rlm;", "")
        .replace("&amp;", "&")

    private val VTT_BLOCK_SEP = Regex("""\n\s*\n""")
    private val VTT_CUE = Regex(
        """^\s*((?:(?:\d{1,2}):)?\d{1,2}:\d{1,2}[.,]\d{1,3})\s*-->\s*((?:(?:\d{1,2}):)?\d{1,2}:\d{1,2}[.,]\d{1,3})(?:\s+.*)?$""",
    )

    /** ASS/SSA：Dialogue: Layer,Start,End,Style,Name,ML,MR,MV,Effect,Text */
    fun fromAss(text: String): ParsedScript {
        val lines = ArrayList<TimedLine>()
        var title: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("Title:", true)) { title = line.substringAfter(':').trim().ifBlank { null }; continue }
            if (!line.startsWith("Dialogue:", true)) continue
            val payload = line.substringAfter(':').trim()
            val parts = payload.split(',', limit = 10)
            if (parts.size < 10) continue
            val start = assTime(parts[1].trim()) ?: continue
            val end = assTime(parts[2].trim())
            val content = parts[9].replace(Regex("""\{[^}]*}"""), "").replace("\\N", "\n").replace("\\n", "\n").trim()
            if (content.isNotEmpty()) lines.add(TimedLine(start, end, content))
        }
        lines.sortBy { it.startMs }
        val plain = clip(lines.joinToString("\n") { it.text }.trim())
        return ParsedScript(ScriptFormat.ASS, plain, lines, title)
    }

    private fun assTime(s: String): Long? {
        val m = Regex("""(\d+):(\d{1,2}):(\d{1,2})[.:](\d{1,2})""").find(s) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: return null
        val mi = m.groupValues[2].toLongOrNull() ?: return null
        val se = m.groupValues[3].toLongOrNull() ?: return null
        val cs = m.groupValues[4].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        return h * 3_600_000 + mi * 60_000 + se * 1000 + cs * 10
    }

    private fun hmsToMs(h: String, m: String, s: String, ms: String): Long {
        val hh = h.toLongOrNull() ?: 0L
        val mm = m.toLongOrNull() ?: 0L
        val ss = s.toLongOrNull() ?: 0L
        val milli = ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return hh * 3_600_000 + mm * 60_000 + ss * 1000 + milli
    }

    /** 轻量清理 Markdown 标记，保留正文。 */
    fun stripMarkdown(text: String): String {
        val sb = StringBuilder(text.length)
        for (raw in text.lineSequence()) {
            var line = raw
            if (line.trimStart().startsWith("```")) continue
            line = line.replace(Regex("""^\s{0,3}#{1,6}\s*"""), "")
            line = line.replace(Regex("""^\s*[->*+]\s+"""), "· ")
            line = line.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            line = line.replace(Regex("""__(.+?)__"""), "$1")
            line = line.replace(Regex("""\`([^\`]+)\`"""), "$1")
            line = line.replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
            line = line.replace(Regex("""^\s*[-*_]{3,}\s*$"""), "")
            sb.append(line).append('\n')
        }
        return sb.toString().trim()
    }

    /** 智能解码：BOM → UTF-8/UTF-16；UTF-8 出现替换字符时回退 GBK。 */
    fun safeDecode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        val bad = utf8.count { it == '\uFFFD' }
        if (bad == 0) return utf8
        return try {
            val gbk = String(bytes, Charset.forName("GBK"))
            if (gbk.count { it == '\uFFFD' } < bad) gbk else utf8
        } catch (e: Exception) {
            utf8
        }
    }

    private fun clip(s: String): String = if (s.length <= maxChars) s else s.substring(0, maxChars) + "\n…（已截断）"
}

/** 从 docx 的 word/document.xml 提取纯文本。 */
object DocxText {

    fun extract(file: File): String? {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return null
            zip.getInputStream(entry).use { input ->
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                    runCatching { isExpandEntityReferences = false }
                }
                val doc = factory.newDocumentBuilder().parse(input)
                val paras = doc.getElementsByTagNameNS("*", "p")
                val sb = StringBuilder()
                if (paras.length == 0) {
                    val all = doc.getElementsByTagNameNS("*", "t")
                    for (i in 0 until all.length) sb.append(all.item(i).textContent ?: "")
                    return sb.toString().trim().ifEmpty { null }
                }
                for (i in 0 until paras.length) {
                    val node = paras.item(i)
                    val texts = (node as? Element)?.getElementsByTagNameNS("*", "t")
                    val line = StringBuilder()
                    if (texts != null) {
                        for (j in 0 until texts.length) line.append(texts.item(j).textContent ?: "")
                    } else if (node.nodeType == Node.ELEMENT_NODE) {
                        line.append(node.textContent ?: "")
                    }
                    sb.append(line).append('\n')
                }
                return sb.toString().trim().ifEmpty { null }
            }
        }
    }
}
