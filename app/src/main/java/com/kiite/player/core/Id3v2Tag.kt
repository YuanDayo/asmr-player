package com.kiite.player.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * MP3 的 ID3v2 歌词标签（USLT，非同步歌词）。
 * 保留原有帧，只替换 USLT；其余音频字节原样保留。
 */
object Id3v2Tag {

    private const val TAG_HEADER = 10

    private data class Tag(val major: Int, val size: Int, val frames: MutableList<ByteArray>)

    fun writeLyrics(file: File, lyrics: String): Int {
        val tag = readTag(file)
        val newFrames = ArrayList<ByteArray>()
        if (tag != null) {
            for (f in tag.frames) {
                val id = frameId(f)
                if (id == "USLT" || id == "SYLT") continue   // 旧的歌词帧丢掉
                newFrames.add(f)
            }
        }
        val major = tag?.major ?: 3
        newFrames.add(buildUsltFrame(lyrics, major))

        val body = ByteArrayOutputStream()
        for (f in newFrames) body.write(f)
        // 留一点 padding，方便下次原地改写
        val padding = 256
        body.write(ByteArray(padding))

        val bodyBytes = body.toByteArray()
        val header = ByteArray(TAG_HEADER)
        header[0] = 'I'.code.toByte(); header[1] = 'D'.code.toByte(); header[2] = '3'.code.toByte()
        header[3] = major.toByte()
        header[4] = 0
        header[5] = 0                     // flags：不使用 unsync / 扩展头
        Bin.putSyncSafe(header, 6, bodyBytes.size)

        val newTag = ByteArray(TAG_HEADER + bodyBytes.size)
        System.arraycopy(header, 0, newTag, 0, TAG_HEADER)
        System.arraycopy(bodyBytes, 0, newTag, TAG_HEADER, bodyBytes.size)

        val oldTotal = tag?.let { TAG_HEADER + it.size } ?: 0
        val temp = IoUtil.tempFor(file)
        try {
            IoUtil.splice(file, temp, 0L, oldTotal.toLong(), newTag)
            IoUtil.replace(file, temp)
        } finally {
            if (temp.exists()) temp.delete()
        }
        return newTag.size
    }

    fun readLyrics(file: File): String? {
        val tag = readTag(file) ?: return null
        for (f in tag.frames) {
            if (frameId(f) != "USLT") continue
            val content = frameContent(f, tag.major)
            if (content.isEmpty()) continue
            val encoding = content[0].toInt() and 0xFF
            if (content.size < 4) continue
            var i = 4                                   // 跳过 encoding + 3 字节语言
            // 跳过内容描述（以当前编码的空字符结束）
            if (encoding == 1 || encoding == 2) {
                while (i + 1 < content.size) {
                    if (content[i] == 0.toByte() && content[i + 1] == 0.toByte()) { i += 2; break }
                    i += 2
                }
            } else {
                while (i < content.size && content[i] != 0.toByte()) i++
                i += 1
            }
            // 容错：描述符没写终止符时，退回「编码 + 语言」之后直接解析
            if (i >= content.size) i = 4
            val text = Bin.decodeText(content, i, content.size - i, encoding)
                .trimStart('\uFEFF', '\u0000')
                .trim()
            if (text.isNotBlank()) return text
        }
        return null
    }

    /** 读取内嵌封面（APIC 帧）。 */
    fun readArtwork(file: File): ByteArray? {
        val tag = readTag(file) ?: return null
        for (f in tag.frames) {
            if (frameId(f) != "APIC") continue
            val c = frameContent(f, tag.major)
            if (c.size < 4) continue
            val enc = c[0].toInt() and 0xFF
            var i = 1
            // MIME（ISO-8859-1，以 0 结尾）
            while (i < c.size && c[i] != 0.toByte()) i++
            i += 1
            if (i >= c.size) continue
            i += 1                                   // picture type
            // 描述（按 enc 编码，以空字符结尾）
            if (enc == 1 || enc == 2) {
                while (i + 1 < c.size) {
                    if (c[i] == 0.toByte() && c[i + 1] == 0.toByte()) { i += 2; break }
                    i += 2
                }
            } else {
                while (i < c.size && c[i] != 0.toByte()) i++
                i += 1
            }
            if (i >= c.size) continue
            val img = c.copyOfRange(i, c.size)
            if (img.size > 64) return img
        }
        return null
    }

    /** 读取 ID3v2 标签（若有），并还原 unsync。 */
    private fun readTag(file: File): Tag? {
        if (!TagIO.hasPrefix(file, Bin.ascii("ID3"))) return null
        return RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(TAG_HEADER)
            raf.readFully(head)
            val major = head[3].toInt() and 0xFF
            if (major !in 2..4) return null
            val size = Bin.syncSafe(head, 6)
            if (size <= 0) return Tag(major, 0, ArrayList())
            val flags = head[5].toInt() and 0xFF
            val available = (raf.length() - TAG_HEADER).coerceAtLeast(0)
            val body = ByteArray(minOf(size.toLong(), available).toInt())
            if (body.isNotEmpty()) raf.readFully(body)
            var data = if (flags and 0x80 != 0) deUnsync(body) else body

            // 跳过扩展头（自己重建标签时会丢弃它）
            if (flags and 0x40 != 0 && major >= 3) {
                if (major == 3) {
                    val extSize = Bin.u32be(data, 0).toInt() + 4
                    if (extSize in 0..data.size) data = data.copyOfRange(extSize, data.size)
                } else {
                    val extSize = Bin.syncSafe(data, 0)
                    if (extSize in 0..data.size) data = data.copyOfRange(extSize, data.size)
                }
            }
            Tag(major, size, parseFrames(data, major))
        }
    }

    private fun parseFrames(data: ByteArray, major: Int): MutableList<ByteArray> {
        val frames = ArrayList<ByteArray>()
        var i = 0
        while (i + TAG_HEADER <= data.size) {
            val idBytes = data.copyOfRange(i, i + 4)
            if (idBytes[0] == 0.toByte()) break
            val id = String(idBytes, Charsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) break
            val frameSize = if (major >= 4) Bin.syncSafe(data, i + 4) else Bin.u32be(data, i + 4).toInt()
            if (frameSize <= 0 || i + TAG_HEADER + frameSize > data.size) break
            frames.add(data.copyOfRange(i, i + TAG_HEADER + frameSize))
            i += TAG_HEADER + frameSize
        }
        return frames
    }

    private fun frameId(frame: ByteArray): String =
        if (frame.size < 4) "" else String(frame, 0, 4, Charsets.ISO_8859_1)

    private fun frameContent(frame: ByteArray, major: Int): ByteArray {
        if (frame.size <= TAG_HEADER) return ByteArray(0)
        // v2.4 的帧大小是同步安全整数，v2.3 是普通大端整数
        var size = if (major >= 4) Bin.syncSafe(frame, 4) else Bin.u32be(frame, 4).toInt()
        if (size <= 0 || TAG_HEADER + size > frame.size) size = frame.size - TAG_HEADER
        return frame.copyOfRange(TAG_HEADER, minOf(frame.size, TAG_HEADER + size))
    }

    /** USLT：encoding(1) + language(3) + 描述(终止符) + 歌词。 */
    private fun buildUsltFrame(lyrics: String, major: Int): ByteArray {
        val encoding = 1                                     // UTF-16 with BOM
        val lang = IoUtil.detectLangTag(lyrics)
        val body = ByteArrayOutputStream()
        body.write(encoding)
        body.write(Bin.ascii(lang))
        body.write(Bin.utf16WithBom(""))                     // 空描述：BOM
        body.write(0)                                        // 描述符终止符 00 00
        body.write(0)
        body.write(Bin.utf16WithBom(trimForTag(lyrics)))
        val content = body.toByteArray()

        val frame = ByteArray(TAG_HEADER + content.size)
        System.arraycopy(Bin.ascii("USLT"), 0, frame, 0, 4)
        if (major >= 4) Bin.putSyncSafe(frame, 4, content.size) else Bin.putU32be(frame, 4, content.size.toLong())
        frame[8] = 0; frame[9] = 0
        System.arraycopy(content, 0, frame, TAG_HEADER, content.size)
        return frame
    }

    private fun trimForTag(s: String): String =
        if (s.length <= 200_000) s else s.substring(0, 200_000) + "\n…（已截断）"

    private fun deUnsync(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            val cur = b[i]
            out.write(cur.toInt())
            if (cur == 0xFF.toByte() && i + 1 < b.size && b[i + 1] == 0x00.toByte()) i++
            i++
        }
        return out.toByteArray()
    }
}
