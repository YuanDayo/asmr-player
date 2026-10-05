package com.asmrplayer.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 构造最小可用的音频/文档样本，用于离线验证核心逻辑（不需要真机）。 */
object TestFiles {

    fun u32(v: Long): ByteArray {
        val b = ByteArray(4)
        b[0] = ((v shr 24) and 0xFF).toByte()
        b[1] = ((v shr 16) and 0xFF).toByte()
        b[2] = ((v shr 8) and 0xFF).toByte()
        b[3] = (v and 0xFF).toByte()
        return b
    }

    fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32((8 + payload.size).toLong()))
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        return out.toByteArray()
    }

    fun utf16(s: String): ByteArray {
        val body = s.toByteArray(Charsets.UTF_16LE)
        val out = ByteArray(body.size + 2)
        out[0] = 0xFF.toByte(); out[1] = 0xFE.toByte()
        System.arraycopy(body, 0, out, 2, body.size)
        return out
    }

    /** 一个带 TIT2 / TPE1（可选 APIC 封面）帧的 ID3v2.3 MP3 + 假音频数据。 */
    fun writeMp3(file: File, audio: String = "AUDIO-FRAMES-0123456789", artwork: ByteArray? = null) {
        val frames = ByteArrayOutputStream()
        frames.write(textFrame("TIT2", "测试曲目"))
        frames.write(textFrame("TPE1", "测试作者"))
        if (artwork != null) frames.write(apicFrame("image/jpeg", artwork))
        val frameBytes = frames.toByteArray()

        val body = ByteArrayOutputStream()
        body.write(frameBytes)
        body.write(ByteArray(64))     // padding
        val bodyBytes = body.toByteArray()

        val header = ByteArray(10)
        header[0] = 'I'.code.toByte(); header[1] = 'D'.code.toByte(); header[2] = '3'.code.toByte()
        header[3] = 3; header[4] = 0; header[5] = 0
        Bin.putSyncSafe(header, 6, bodyBytes.size)

        val out = ByteArrayOutputStream()
        out.write(header)
        out.write(bodyBytes)
        out.write(audio.toByteArray(Charsets.US_ASCII))
        file.writeBytes(out.toByteArray())
    }

    /** APIC 帧：encoding + mime(0 结尾) + pictureType + 空描述(0) + 图片数据。 */
    private fun apicFrame(mime: String, image: ByteArray): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(0)
        content.write(mime.toByteArray(Charsets.ISO_8859_1))
        content.write(0)
        content.write(3)
        content.write(0)
        content.write(image)
        val c = content.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("APIC".toByteArray(Charsets.ISO_8859_1))
        out.write(u32(c.size.toLong()))
        out.write(ByteArray(2))
        out.write(c)
        return out.toByteArray()
    }

    private fun textFrame(id: String, text: String): ByteArray {
        val content = ByteArrayOutputStream()
        content.write(1)                       // UTF-16
        content.write(utf16(text))
        val c = content.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(u32(c.size.toLong()))
        out.write(ByteArray(2))                // flags
        out.write(c)
        return out.toByteArray()
    }

    /** FLAC：fLaC + STREAMINFO + (可选) VORBIS_COMMENT + PADDING + 假音频帧。 */
    /** FLAC PICTURE 块（type 6）。 */
    fun pictureBlock(image: ByteArray, mime: String = "image/jpeg"): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32(3))                                   // picture type
        val mb = mime.toByteArray(Charsets.ISO_8859_1)
        out.write(u32(mb.size.toLong())); out.write(mb)
        out.write(u32(0))                                   // 描述长度 0
        out.write(u32(600)); out.write(u32(600))            // width / height
        out.write(u32(24)); out.write(u32(0))               // depth / colors
        out.write(u32(image.size.toLong())); out.write(image)
        return out.toByteArray()
    }

    fun writeFlac(
        file: File,
        withComment: Boolean,
        withPadding: Boolean,
        paddingBytes: Int = 2048,
        artwork: ByteArray? = null,
    ) {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        val blocks = ArrayList<Pair<Int, ByteArray>>()
        blocks.add(0 to ByteArray(34))                        // STREAMINFO（全 0 即可）
        if (withComment) blocks.add(4 to vorbis("AsmrTest", listOf("TITLE=测试")))
        if (artwork != null) blocks.add(6 to pictureBlock(artwork))
        if (withPadding) blocks.add(1 to ByteArray(paddingBytes))
        for ((i, b) in blocks.withIndex()) {
            val last = i == blocks.size - 1
            val head = ByteArray(4)
            head[0] = (if (last) b.first or 0x80 else b.first).toByte()
            Bin.putU24be(head, 1, b.second.size)
            out.write(head)
            out.write(b.second)
        }
        out.write("FLACFRAMES-abcdefghij".toByteArray(Charsets.US_ASCII))
        file.writeBytes(out.toByteArray())
    }

    fun vorbis(vendor: String, entries: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val vb = vendor.toByteArray(Charsets.UTF_8)
        out.write(u32(vb.size.toLong())); out.write(vb)
        out.write(u32(entries.size.toLong()))
        for (e in entries) {
            val eb = e.toByteArray(Charsets.UTF_8)
            out.write(u32(eb.size.toLong())); out.write(eb)
        }
        return out.toByteArray()
    }

    /** MP4：ftyp + moov(mvhd, trak→mdia→minf→stbl→stco) + mdat，stco 指向 mdat 负载。 */
    fun writeMp4(file: File, artwork: ByteArray? = null): Long {
        val ftyp = box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + u32(0) + "isomiso2".toByteArray(Charsets.US_ASCII))
        val mdatPayload = "AUDIODATA-MP4-PAYLOAD".toByteArray(Charsets.US_ASCII) + ByteArray(48)

        fun buildStco(mdatOffset: Long): ByteArray {
            val payload = ByteArray(4 + 4 + 4)
            putU32(payload, 4, 1)               // entry count
            putU32(payload, 8, mdatOffset)      // 第一个 chunk 偏移
            return box("stco", payload)
        }

        fun buildMoov(stco: ByteArray): ByteArray {
            val mvhd = box("mvhd", ByteArray(100))
            val stbl = box("stbl", stco)
            val minf = box("minf", stbl)
            val mdia = box("mdia", minf)
            val trak = box("trak", mdia)
            val ilstChildren = ByteArrayOutputStream()
            ilstChildren.write(box("\u00A9nam", box("data", ByteArray(8) + "旧标题".toByteArray(Charsets.UTF_8))))
            if (artwork != null) ilstChildren.write(box("covr", box("data", ByteArray(8) + artwork)))
            val udta = box("udta", box("meta", ByteArray(4) + box("hdlr", ByteArray(24) + ByteArray(1)) +
                box("ilst", ilstChildren.toByteArray())))
            return box("moov", mvhd + trak + udta)
        }

        // 第一遍：占位 0，量出 mdat 负载的真实位置
        var moov = buildMoov(buildStco(0))
        val mdat = box("mdat", mdatPayload)
        val mdatPayloadStart = (ftyp.size + moov.size + 8).toLong()

        // 第二遍：写入正确偏移（size 不变）
        moov = buildMoov(buildStco(mdatPayloadStart))

        val out = ByteArrayOutputStream()
        out.write(ftyp); out.write(moov); out.write(mdat)
        file.writeBytes(out.toByteArray())
        return mdatPayloadStart
    }

    fun putU32(b: ByteArray, off: Int, v: Long) {
        b[off] = ((v shr 24) and 0xFF).toByte()
        b[off + 1] = ((v shr 16) and 0xFF).toByte()
        b[off + 2] = ((v shr 8) and 0xFF).toByte()
        b[off + 3] = (v and 0xFF).toByte()
    }

    /** 读取 MP4 里第一个 stco 的 chunk 偏移。 */
    fun readFirstStcoOffset(file: File): Long {
        val bytes = file.readBytes()
        val idx = indexOf(bytes, "stco".toByteArray(Charsets.US_ASCII))
        require(idx >= 0) { "找不到 stco" }
        val payloadStart = idx + 4
        return Bin.u32be(bytes, payloadStart + 8)
    }

    fun mdatPayloadStart(file: File): Long {
        val bytes = file.readBytes()
        val idx = indexOf(bytes, "mdat".toByteArray(Charsets.US_ASCII))
        require(idx >= 0) { "找不到 mdat" }
        return (idx + 4).toLong()
    }

    fun mp4HasAtom(file: File, type: String): Boolean =
        indexOf(file.readBytes(), type.toByteArray(Charsets.ISO_8859_1)) >= 0

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** 生成一个最小 docx（word/document.xml）。 */
    fun writeDocx(file: File, paragraphs: List<String>) {
        val xml = StringBuilder()
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        xml.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
        for (p in paragraphs) {
            xml.append("<w:p><w:r><w:t>").append(escapeXml(p)).append("</w:t></w:r></w:p>")
        }
        xml.append("</w:body></w:document>")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(xml.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("<Types/>".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
