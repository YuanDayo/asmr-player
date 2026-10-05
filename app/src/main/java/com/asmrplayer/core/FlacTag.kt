package com.asmrplayer.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * FLAC 的 Vorbis Comment 歌词标签（LYRICS）。
 * 尽量用 PADDING 块吸收长度变化，长度不变时直接原地写回，避免重写整个音频。
 */
object FlacTag {

    private const val BLOCK_STREAMINFO = 0
    private const val BLOCK_PADDING = 1
    private const val BLOCK_VORBIS_COMMENT = 4
    private const val BLOCK_PICTURE = 6

    private data class Block(val type: Int, val data: ByteArray)

    fun writeLyrics(file: File, lyrics: String): Int {
        val (blocks, metadataEnd) = readBlocks(file)
        val commentIdx = blocks.indexOfFirst { it.type == BLOCK_VORBIS_COMMENT }
        val entries: MutableList<String> =
            if (commentIdx >= 0) parseEntries(blocks[commentIdx].data) else ArrayList()
        val vendor = if (commentIdx >= 0) parseVendor(blocks[commentIdx].data) else "AsmrPlayer"

        val filtered = entries.filterNot { isLyricsEntry(it) }.toMutableList()
        filtered.add("LYRICS=" + sanitize(lyrics))
        val newComment = buildVorbis(vendor, filtered)

        val newBlocks = ArrayList<Block>()
        for ((i, b) in blocks.withIndex()) {
            if (b.type == BLOCK_PADDING) continue
            if (i == commentIdx) newBlocks.add(Block(BLOCK_VORBIS_COMMENT, newComment)) else newBlocks.add(b)
        }
        if (commentIdx < 0) {
            val insertAt = if (newBlocks.isNotEmpty() && newBlocks[0].type == BLOCK_STREAMINFO) 1 else 0
            newBlocks.add(insertAt, Block(BLOCK_VORBIS_COMMENT, newComment))
        }

        val fixedTotal = 4 + newBlocks.sumOf { 4 + it.data.size }
        val paddingLen = metadataEnd - fixedTotal - 4
        if (paddingLen >= 0) newBlocks.add(Block(BLOCK_PADDING, ByteArray(paddingLen.toInt())))

        val newBytes = serialize(newBlocks)
        if (newBytes.size.toLong() == metadataEnd) {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(newBytes)
            }
        } else {
            val temp = IoUtil.tempFor(file)
            try {
                IoUtil.splice(file, temp, 0L, metadataEnd, newBytes)
                IoUtil.replace(file, temp)
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
        return newBytes.size
    }

    fun readLyrics(file: File): String? {
        val (blocks, _) = readBlocks(file)
        val comment = blocks.firstOrNull { it.type == BLOCK_VORBIS_COMMENT } ?: return null
        for (e in parseEntries(comment.data)) {
            val key = e.substringBefore('=', "").uppercase()
            if (key == "LYRICS" || key == "UNSYNCEDLYRICS") {
                val v = e.substringAfter('=', "")
                if (v.isNotBlank()) return v
            }
        }
        return null
    }

    fun hasPadding(file: File): Boolean = readBlocks(file).first.any { it.type == BLOCK_PADDING }

    /** 读取内嵌封面（PICTURE 块）。 */
    fun readArtwork(file: File): ByteArray? {
        val pic = readBlocks(file).first.firstOrNull { it.type == BLOCK_PICTURE } ?: return null
        val d = pic.data
        var p = 4
        if (p + 4 > d.size) return null
        val mimeLen = Bin.u32be(d, p).toInt(); p += 4 + mimeLen
        if (p + 4 > d.size) return null
        val descLen = Bin.u32be(d, p).toInt(); p += 4 + descLen
        p += 16                                          // width / height / depth / colors
        if (p + 4 > d.size) return null
        val dataLen = Bin.u32be(d, p).toInt(); p += 4
        if (dataLen <= 0 || p + dataLen > d.size) return null
        return d.copyOfRange(p, p + dataLen)
    }

    private fun readBlocks(file: File): Pair<MutableList<Block>, Long> {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            if (raf.read(magic) < 4 || String(magic, Charsets.US_ASCII) != "fLaC") {
                throw IllegalArgumentException("不是 FLAC 文件")
            }
            val blocks = ArrayList<Block>()
            var pos = 4L
            while (true) {
                val head = ByteArray(4)
                if (raf.read(head) < 4) break
                val last = (head[0].toInt() and 0x80) != 0
                val type = head[0].toInt() and 0x7F
                val len = Bin.u24be(head, 1)
                val data = ByteArray(len)
                if (len > 0) {
                    if (raf.read(data) < len) break
                }
                blocks.add(Block(type, data))
                pos += 4 + len
                if (last) break
                if (pos > raf.length()) break
            }
            return blocks to pos
        }
    }

    private fun serialize(blocks: List<Block>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(Bin.ascii("fLaC"))
        for ((i, b) in blocks.withIndex()) {
            val last = i == blocks.size - 1
            out.write(if (last) b.type or 0x80 else b.type)
            val len = ByteArray(3)
            Bin.putU24be(len, 0, b.data.size)
            out.write(len)
            out.write(b.data)
        }
        return out.toByteArray()
    }

    private fun parseVendor(data: ByteArray): String {
        if (data.size < 4) return "AsmrPlayer"
        val len = Bin.u32le(data, 0).toInt()
        if (len < 0 || 4 + len > data.size) return "AsmrPlayer"
        return String(data, 4, len, Charsets.UTF_8)
    }

    private fun parseEntries(data: ByteArray): MutableList<String> {
        val out = ArrayList<String>()
        if (data.size < 8) return out
        var p = 0
        val vendorLen = Bin.u32le(data, p).toInt()
        p += 4 + vendorLen
        if (p + 4 > data.size) return out
        val count = Bin.u32le(data, p).toInt()
        p += 4
        for (i in 0 until count) {
            if (p + 4 > data.size) break
            val len = Bin.u32le(data, p).toInt()
            p += 4
            if (len < 0 || p + len > data.size) break
            out.add(String(data, p, len, Charsets.UTF_8))
            p += len
        }
        return out
    }

    private fun buildVorbis(vendor: String, entries: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(4)
        val vb = vendor.toByteArray(Charsets.UTF_8)
        Bin.putU32le(tmp, 0, vb.size.toLong())
        out.write(tmp)
        out.write(vb)
        Bin.putU32le(tmp, 0, entries.size.toLong())
        out.write(tmp)
        for (e in entries) {
            val eb = e.toByteArray(Charsets.UTF_8)
            Bin.putU32le(tmp, 0, eb.size.toLong())
            out.write(tmp)
            out.write(eb)
        }
        return out.toByteArray()
    }

    private fun isLyricsEntry(entry: String): Boolean {
        val key = entry.substringBefore('=', "").uppercase()
        return key == "LYRICS" || key == "UNSYNCEDLYRICS"
    }

    private fun sanitize(s: String): String {
        val clipped = if (s.length <= 200_000) s else s.substring(0, 200_000) + "\n…（已截断）"
        return clipped.replace('\u0000', ' ')
    }
}
