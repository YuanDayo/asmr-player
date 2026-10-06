package com.kiite.player.core

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 歌词标签的统一入口：按容器格式分派。 */
object TagIO {

    /** 该文件是否支持写入歌词标签。 */
    fun canWrite(file: File): Boolean = when (container(file)) {
        Container.MP3, Container.FLAC, Container.MP4 -> true
        Container.OTHER -> false
    }

    fun containerName(file: File): String = when (container(file)) {
        Container.MP3 -> "ID3v2 (MP3)"
        Container.FLAC -> "Vorbis Comment (FLAC)"
        Container.MP4 -> "iTunes ©lyr (M4A)"
        Container.OTHER -> "不支持"
    }

    /** 把台本写入音频标签。 */
    fun writeLyrics(file: File, lyrics: String): TagWriteResult {
        if (!file.isFile) return TagWriteResult.Failed("文件不存在")
        if (lyrics.isBlank()) return TagWriteResult.Failed("台本内容为空")
        return try {
            when (container(file)) {
                Container.MP3 -> TagWriteResult.Written("ID3v2 USLT", Id3v2Tag.writeLyrics(file, lyrics))
                Container.FLAC -> TagWriteResult.Written("FLAC LYRICS", FlacTag.writeLyrics(file, lyrics))
                Container.MP4 -> TagWriteResult.Written("MP4 ©lyr", Mp4Tag.writeLyrics(file, lyrics))
                Container.OTHER -> TagWriteResult.Unsupported
            }
        } catch (e: Exception) {
            TagWriteResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** 读取音频里已嵌入的歌词/台本，没有则返回 null。 */
    fun readLyrics(file: File): String? = try {
        when (container(file)) {
            Container.MP3 -> Id3v2Tag.readLyrics(file)
            Container.FLAC -> FlacTag.readLyrics(file)
            Container.MP4 -> Mp4Tag.readLyrics(file)
            Container.OTHER -> null
        }
    } catch (e: Exception) {
        null
    }

    /** 读取音频内嵌封面，没有则返回 null。 */
    fun readArtwork(file: File): ByteArray? = try {
        when (container(file)) {
            Container.MP3 -> Id3v2Tag.readArtwork(file)
            Container.FLAC -> FlacTag.readArtwork(file)
            Container.MP4 -> Mp4Tag.readArtwork(file)
            Container.OTHER -> null
        }
    } catch (e: Exception) {
        null
    }

    internal enum class Container { MP3, FLAC, MP4, OTHER }

    internal fun container(file: File): Container {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        // 先看扩展名，再看魔数，避免扩展名缺失或写错
        if (ext in setOf("mp3", "mp2", "mpga")) return Container.MP3
        if (ext == "flac") return Container.FLAC
        if (ext in setOf("m4a", "m4b", "mp4", "alac", "aac")) {
            return if (isMp4(file)) Container.MP4 else Container.OTHER
        }
        if (ext == "ogg" || ext == "oga" || ext == "opus") return Container.OTHER
        return when {
            hasPrefix(file, "ID3".toByteArray(Charsets.US_ASCII)) -> Container.MP3
            hasPrefix(file, "fLaC".toByteArray(Charsets.US_ASCII)) -> Container.FLAC
            isMp4(file) -> Container.MP4
            else -> Container.OTHER
        }
    }

    internal fun isMp4(file: File): Boolean {
        if (!file.isFile || file.length() < 12) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(12)
                if (raf.read(head) < 12) return false
                val boxType = String(head, 4, 4, Charsets.US_ASCII)
                boxType in setOf("ftyp", "moov", "mdat", "free", "skip", "wide", "pnot")
            }
        } catch (e: Exception) {
            false
        }
    }

    internal fun hasPrefix(file: File, prefix: ByteArray): Boolean {
        if (!file.isFile || file.length() < prefix.size) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(prefix.size)
                if (raf.read(head) < prefix.size) return false
                head.contentEquals(prefix)
            }
        } catch (e: Exception) {
            false
        }
    }
}

/** 二进制小工具：读写大端/小端、同步安全整数、UTF-16 文本。 */
internal object Bin {

    fun u32be(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    fun u32le(b: ByteArray, off: Int): Long =
        ((b[off + 3].toLong() and 0xFF) shl 24) or ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or (b[off].toLong() and 0xFF)

    fun putU32be(out: ByteArray, off: Int, v: Long) {
        out[off] = ((v shr 24) and 0xFF).toByte()
        out[off + 1] = ((v shr 16) and 0xFF).toByte()
        out[off + 2] = ((v shr 8) and 0xFF).toByte()
        out[off + 3] = (v and 0xFF).toByte()
    }

    fun putU32le(out: ByteArray, off: Int, v: Long) {
        out[off] = (v and 0xFF).toByte()
        out[off + 1] = ((v shr 8) and 0xFF).toByte()
        out[off + 2] = ((v shr 16) and 0xFF).toByte()
        out[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    /** ID3v2 同步安全整数（每字节 7 位）。 */
    fun syncSafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or (b[off + 3].toInt() and 0x7F)

    fun putSyncSafe(out: ByteArray, off: Int, v: Int) {
        out[off] = ((v shr 21) and 0x7F).toByte()
        out[off + 1] = ((v shr 14) and 0x7F).toByte()
        out[off + 2] = ((v shr 7) and 0x7F).toByte()
        out[off + 3] = (v and 0x7F).toByte()
    }

    /** UTF-16LE + BOM，ID3 中最通用的歌词编码。 */
    fun utf16WithBom(s: String): ByteArray {
        val body = s.toByteArray(Charsets.UTF_16LE)
        val out = ByteArray(body.size + 2)
        out[0] = 0xFF.toByte(); out[1] = 0xFE.toByte()
        System.arraycopy(body, 0, out, 2, body.size)
        return out
    }

    /** 解码带 BOM 的 UTF-16 或纯 ASCII/UTF-8。 */
    fun decodeText(b: ByteArray, off: Int, len: Int, encoding: Int): String {
        if (len <= 0 || off >= b.size) return ""
        val end = minOf(b.size, off + len)
        return when (encoding) {
            0 -> String(b, off, end - off, Charsets.ISO_8859_1)
            1 -> {
                if (end - off >= 2 && b[off] == 0xFF.toByte() && b[off + 1] == 0xFE.toByte())
                    String(b, off + 2, end - off - 2, Charsets.UTF_16LE)
                else if (end - off >= 2 && b[off] == 0xFE.toByte() && b[off + 1] == 0xFF.toByte())
                    String(b, off + 2, end - off - 2, Charsets.UTF_16BE)
                else String(b, off, end - off, Charsets.UTF_16LE)
            }
            2 -> String(b, off, end - off, Charsets.UTF_16BE)
            else -> String(b, off, end - off, Charsets.UTF_8)
        }
    }

    /** 3 字节大端长度（FLAC 用）。 */
    fun u24be(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 16) or ((b[off + 1].toInt() and 0xFF) shl 8) or (b[off + 2].toInt() and 0xFF)

    fun putU24be(out: ByteArray, off: Int, v: Int) {
        out[off] = ((v shr 16) and 0xFF).toByte()
        out[off + 1] = ((v shr 8) and 0xFF).toByte()
        out[off + 2] = (v and 0xFF).toByte()
    }

    fun ascii(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)
}

/** 文件拼接与替换：只在必要位置改动，其余字节原样流式拷贝。 */
internal object IoUtil {

    fun copyRange(raf: RandomAccessFile, from: Long, to: Long, out: OutputStream) {
        raf.seek(from)
        val buf = ByteArray(1 shl 16)
        var remaining = to - from
        while (remaining > 0) {
            val n = raf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n <= 0) break
            out.write(buf, 0, n)
            remaining -= n
        }
    }

    /** 用 replacement 替换 [cutStart, cutEnd) 区间，产出到 temp。 */
    fun splice(source: File, temp: File, cutStart: Long, cutEnd: Long, replacement: ByteArray) {
        RandomAccessFile(source, "r").use { raf ->
            temp.outputStream().buffered(1 shl 16).use { out ->
                copyRange(raf, 0, cutStart, out)
                out.write(replacement)
                copyRange(raf, cutEnd, raf.length(), out)
            }
        }
    }

    fun replace(original: File, temp: File) {
        Files.move(temp.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun tempFor(original: File): File = File(original.parentFile, original.name + ".asmrtmp")

    fun readFully(file: File, offset: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(out)
        }
        return out
    }

    fun detectLangTag(text: String): String {
        val cjk = text.take(4000).count { it.code in 0x4E00..0x9FFF || it.code in 0x3040..0x30FF }
        return if (cjk > 20) "chi" else "eng"
    }
}
