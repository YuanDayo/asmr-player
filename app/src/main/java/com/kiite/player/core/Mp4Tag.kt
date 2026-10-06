package com.kiite.player.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * M4A/MP4 的 iTunes 歌词标签（©lyr）。
 * moov 变长时会重算并修正 stco/co64 的 chunk 偏移，保证音频仍能正确定位。
 */
object Mp4Tag {

    private const val ATOM_LYR = "\u00A9lyr"
    private const val MAX_MOOV = 192L * 1024 * 1024

    private val CONTAINERS = setOf(
        "moov", "trak", "mdia", "minf", "stbl", "udta", "ilst", "edts", "dinf",
        "mvex", "moof", "traf", "mfra", "tref", "ipro", "sinf", "schi", "wave",
    )
    private val FULLBOX_CONTAINERS = setOf("meta")

    private class Node(val type: String) {
        var prefix: ByteArray = EMPTY
        var raw: ByteArray? = null
        var children: MutableList<Node>? = null

        fun serialize(): ByteArray {
            val payload = children?.let { serializeChildren(it) } ?: (raw ?: EMPTY)
            val total = 8 + prefix.size + payload.size
            val out = ByteArray(total)
            Bin.putU32be(out, 0, total.toLong())
            System.arraycopy(type.toByteArray(Charsets.ISO_8859_1), 0, out, 4, 4)
            System.arraycopy(prefix, 0, out, 8, prefix.size)
            System.arraycopy(payload, 0, out, 8 + prefix.size, payload.size)
            return out
        }

        companion object {
            val EMPTY = ByteArray(0)
            fun serializeChildren(list: List<Node>): ByteArray {
                val out = ByteArrayOutputStream()
                for (n in list) out.write(n.serialize())
                return out.toByteArray()
            }
        }
    }

    private class Atom(val type: String, val start: Long, val size: Long, val header: Long)

    fun writeLyrics(file: File, lyrics: String): Int {
        val tops = readTopLevel(file)
        val moov = tops.firstOrNull { it.type == "moov" } ?: throw IllegalArgumentException("找不到 moov")
        val mdat = tops.firstOrNull { it.type == "mdat" }
        if (moov.size > MAX_MOOV) throw IllegalArgumentException("moov 过大")

        val moovBytes = IoUtil.readFully(file, moov.start, moov.size.toInt())
        val (children, complete) = parseBoxesStrict(moovBytes, moov.header.toInt(), moovBytes.size)
        if (!complete) throw IllegalArgumentException("moov 结构无法解析")

        var udta = children.firstOrNull { it.type == "udta" }
        if (udta == null) {
            udta = Node("udta").apply { this.children = ArrayList() }
            children.add(udta)
        }
        if (udta.children == null) udta.children = ArrayList()
        var meta = udta.children!!.firstOrNull { it.type == "meta" }
        if (meta == null) {
            meta = Node("meta").apply {
                prefix = ByteArray(4)
                this.children = arrayListOf(buildHdlr())
            }
            udta.children!!.add(meta)
        }
        if (meta.children == null) meta.children = ArrayList()
        var ilst = meta.children!!.firstOrNull { it.type == "ilst" }
        if (ilst == null) {
            ilst = Node("ilst").apply { this.children = ArrayList() }
            meta.children!!.add(ilst)
        }
        if (ilst.children == null) ilst.children = ArrayList()
        ilst.children!!.removeAll { it.type == ATOM_LYR }
        ilst.children!!.add(buildLyricsNode(lyrics))

        val newMoov = Node("moov").apply { this.children = children }.serialize()
        val delta = newMoov.size.toLong() - moovBytes.size

        if (delta != 0L && mdat != null && moov.start < mdat.start) {
            patchChunkOffsets(newMoov, moov.start + moovBytes.size, delta)
        }

        val temp = IoUtil.tempFor(file)
        try {
            IoUtil.splice(file, temp, moov.start, moov.start + moovBytes.size, newMoov)
            IoUtil.replace(file, temp)
        } finally {
            if (temp.exists()) temp.delete()
        }
        return newMoov.size
    }

    fun readLyrics(file: File): String? {
        val tops = readTopLevel(file)
        val moov = tops.firstOrNull { it.type == "moov" } ?: return null
        if (moov.size > MAX_MOOV) return null
        val moovBytes = IoUtil.readFully(file, moov.start, moov.size.toInt())
        val (children, _) = parseBoxesStrict(moovBytes, moov.header.toInt(), moovBytes.size)
        val udta = children.firstOrNull { it.type == "udta" } ?: return null
        val meta = udta.children?.firstOrNull { it.type == "meta" } ?: return null
        val ilst = meta.children?.firstOrNull { it.type == "ilst" } ?: return null
        val lyr = ilst.children?.firstOrNull { it.type == ATOM_LYR } ?: return null
        for (d in lyr.children ?: emptyList()) {
            if (d.type != "data") continue
            val raw = d.raw ?: continue
            if (raw.size <= 8) continue
            val text = String(raw, 8, raw.size - 8, Charsets.UTF_8).trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    /** 读取内嵌封面（covr）。 */
    fun readArtwork(file: File): ByteArray? {
        val tops = readTopLevel(file)
        val moov = tops.firstOrNull { it.type == "moov" } ?: return null
        if (moov.size > MAX_MOOV) return null
        val moovBytes = IoUtil.readFully(file, moov.start, moov.size.toInt())
        val (children, _) = parseBoxesStrict(moovBytes, moov.header.toInt(), moovBytes.size)
        val udta = children.firstOrNull { it.type == "udta" } ?: return null
        val meta = udta.children?.firstOrNull { it.type == "meta" } ?: return null
        val ilst = meta.children?.firstOrNull { it.type == "ilst" } ?: return null
        val covr = ilst.children?.firstOrNull { it.type == "covr" } ?: return null
        for (d in covr.children ?: emptyList()) {
            if (d.type != "data") continue
            val raw = d.raw ?: continue
            if (raw.size <= 8) continue
            return raw.copyOfRange(8, raw.size)
        }
        return null
    }

    private fun buildLyricsNode(lyrics: String): Node {
        val data = Node("data").apply {
            prefix = ByteArray(8)
            prefix[3] = 0x01                       // version/flags：UTF-8 文本
            raw = sanitize(lyrics).toByteArray(Charsets.UTF_8)
        }
        return Node(ATOM_LYR).apply { children = arrayListOf(data) }
    }

    private fun buildHdlr(): Node {
        val name = "AsmrPlayer".toByteArray(Charsets.UTF_8)
        val payload = ByteArray(24 + name.size + 1)
        // version/flags(4) + pre_defined(4) + handler_type(4) + reserved(12) + name
        System.arraycopy("mdir".toByteArray(Charsets.ISO_8859_1), 0, payload, 8, 4)
        System.arraycopy(name, 0, payload, 24, name.size)
        return Node("hdlr").apply { raw = payload }
    }

    private fun sanitize(s: String): String =
        if (s.length <= 200_000) s else s.substring(0, 200_000) + "\n…（已截断）"

    private fun readTopLevel(file: File): List<Atom> {
        val out = ArrayList<Atom>()
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            var pos = 0L
            val head = ByteArray(8)
            while (pos + 8 <= len) {
                raf.seek(pos)
                if (raf.read(head) < 8) break
                var size = Bin.u32be(head, 0)
                val type = String(head, 4, 4, Charsets.ISO_8859_1)
                var header = 8L
                if (size == 1L) {
                    val ext = ByteArray(8)
                    if (raf.read(ext) < 8) break
                    size = readU64be(ext, 0)
                    header = 16L
                } else if (size == 0L) {
                    size = len - pos
                }
                if (size < header) break
                out.add(Atom(type, pos, size, header))
                pos += size
            }
        }
        return out
    }

    private fun parseBoxesStrict(
        data: ByteArray,
        from: Int,
        to: Int,
        parentType: String? = null,
    ): Pair<MutableList<Node>, Boolean> {
        val out = ArrayList<Node>()
        var p = from
        while (p + 8 <= to) {
            var size = Bin.u32be(data, p)
            val type = String(data, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                if (p + 16 > to) return out to false
                size = readU64be(data, p + 8)
                header = 16
            } else if (size == 0L) {
                size = (to - p).toLong()
            }
            if (size < header || p + size > to) return out to false
            val cs = p + header
            val ce = (p + size).toInt()
            out.add(buildNode(data, type, cs, ce, parentType))
            p = ce
        }
        return out to (p == to)
    }

    private fun buildNode(data: ByteArray, type: String, cs: Int, ce: Int, parentType: String?): Node {
        val node = Node(type)
        if (type in FULLBOX_CONTAINERS) {
            val prefixLen = metaPrefixLen(data, cs, ce)
            val (kids, complete) = parseBoxesStrict(data, cs + prefixLen, ce, type)
            if (complete && kids.isNotEmpty()) {
                node.prefix = data.copyOfRange(cs, cs + prefixLen)
                node.children = kids
                return node
            }
            node.raw = data.copyOfRange(cs, ce)
            return node
        }
        // ilst 的每个子原子（©lyr / ©nam / covr / ---- 等）都只是 data 的壳，名字不固定，
        // 因此凡是 ilst 的孩子一律按容器解析，解析不完整时再退回原始字节。
        if (type in CONTAINERS || parentType == "ilst") {
            val (kids, complete) = parseBoxesStrict(data, cs, ce, type)
            if (complete && kids.isNotEmpty()) {
                node.children = kids
                return node
            }
            node.raw = data.copyOfRange(cs, ce)
            return node
        }
        node.raw = data.copyOfRange(cs, ce)
        return node
    }

    /** meta 是 FullBox：前面 4 字节 version/flags；少数文件不是，这里做兼容判断。 */
    private fun metaPrefixLen(data: ByteArray, start: Int, end: Int): Int {
        if (start + 4 <= end) {
            val first = Bin.u32be(data, start)
            val looksNumeric = first == 0L && start + 12 <= end && looksLikeBoxHeader(data, start + 4, end)
            if (!looksNumeric && looksLikeBoxHeader(data, start + 4, end)) return 4
        }
        if (looksLikeBoxHeader(data, start, end)) return 0
        return 4
    }

    private fun looksLikeBoxHeader(data: ByteArray, at: Int, end: Int): Boolean {
        if (at + 8 > end) return false
        val size = Bin.u32be(data, at)
        if (size < 8 || at + size > end) return false
        val type = String(data, at + 4, 4, Charsets.ISO_8859_1)
        return type.all { it.isLetterOrDigit() || it == ' ' || it == '\u00A9' }
    }

    /** moov 变长后，修正所有 stco/co64 中指向其后数据的 chunk 偏移。 */
    private fun patchChunkOffsets(data: ByteArray, shiftFrom: Long, delta: Long) {
        walkBoxes(data, 0, data.size) { type, cs, ce ->
            when (type) {
                "stco" -> {
                    if (cs + 8 > ce) return@walkBoxes
                    val count = Bin.u32be(data, cs + 4).toInt()
                    var p = cs + 8
                    for (i in 0 until count) {
                        if (p + 4 > ce) break
                        val off = Bin.u32be(data, p)
                        if (off >= shiftFrom) Bin.putU32be(data, p, off + delta)
                        p += 4
                    }
                }
                "co64" -> {
                    if (cs + 8 > ce) return@walkBoxes
                    val count = Bin.u32be(data, cs + 4).toInt()
                    var p = cs + 8
                    for (i in 0 until count) {
                        if (p + 8 > ce) break
                        val off = readU64be(data, p)
                        if (off >= shiftFrom) putU64be(data, p, off + delta)
                        p += 8
                    }
                }
            }
        }
    }

    private fun walkBoxes(data: ByteArray, from: Int, to: Int, visit: (String, Int, Int) -> Unit) {
        var p = from
        while (p + 8 <= to) {
            var size = Bin.u32be(data, p)
            val type = String(data, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                if (p + 16 > to) return
                size = readU64be(data, p + 8)
                header = 16
            } else if (size == 0L) {
                size = (to - p).toLong()
            }
            if (size < header || p + size > to) return
            val cs = p + header
            val ce = (p + size).toInt()
            visit(type, cs, ce)
            when {
                type in CONTAINERS -> walkBoxes(data, cs, ce, visit)
                type in FULLBOX_CONTAINERS -> walkBoxes(data, cs + metaPrefixLen(data, cs, ce), ce, visit)
            }
            p = ce
        }
    }

    private fun readU64be(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun putU64be(b: ByteArray, off: Int, v: Long) {
        for (i in 0 until 8) b[off + i] = ((v shr (56 - 8 * i)) and 0xFF).toByte()
    }
}
