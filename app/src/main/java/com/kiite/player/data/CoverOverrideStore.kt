package com.kiite.player.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 用户自选的作品头图（封面）。
 *
 * 为什么单独存一份而不是改曲库里的图片：曲库是用户的文件，应用只在**自己**的目录里
 * 放一张副本（`files/custom-covers/`），原图不动、删了也不影响曲库。
 *
 * 键：优先用作品编号（RJ/VJ…，挪目录也不失效），没有编号时退回项目绝对路径。
 * 因此移动目录后「无编号作品」的自选头图会失效——这是可接受的代价，重新选一次即可。
 */
class CoverOverrideStore(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val dir = File(context.filesDir, "custom-covers")
    private val file = File(context.filesDir, "custom-covers.json")

    fun load(): Map<String, String> = runCatching {
        if (!file.isFile) emptyMap()
        else json.decodeFromString<Map<String, String>>(file.readText())
            // 文件被清掉（清数据/手动删）时不要留下指向空气的映射
            .filterValues { File(it).isFile }
    }.getOrDefault(emptyMap())

    fun save(map: Map<String, String>) {
        runCatching { file.writeText(json.encodeToString(map)) }
    }

    /** 把选中的图片复制进应用目录，返回新文件路径；失败返回 null。 */
    fun importBytes(key: String, bytes: ByteArray, ext: String = "jpg"): String? = runCatching {
        dir.mkdirs()
        val out = File(dir, fileNameOf(key, ext))
        out.writeBytes(bytes)
        if (out.length() > 0) out.absolutePath else null
    }.getOrNull()

    /** 删除某条覆盖对应的副本图片（换图 / 恢复默认时调用）。 */
    fun deleteFile(path: String?) {
        val p = path?.takeIf { it.isNotBlank() } ?: return
        // 只删自己目录里的文件，避免误删用户的原图
        if (File(p).parentFile?.absolutePath == dir.absolutePath) runCatching { File(p).delete() }
    }

    companion object {

        /** 编号优先；没有编号时用路径。两者都没有时返回 null（调用方跳过）。 */
        fun keyOf(code: String?, projectPath: String): String? {
            val c = code?.trim()?.uppercase()
            if (!c.isNullOrEmpty()) return "code:" + c
            val p = projectPath.trim()
            return if (p.isEmpty()) null else "path:" + p
        }

        /** 文件名按 key 做 MD5，避免路径里的奇怪字符直接进文件名。 */
        fun fileNameOf(key: String, ext: String): String {
            val md = MessageDigest.getInstance("MD5")
            val hash = md.digest(key.toByteArray(Charsets.UTF_8))
                .joinToString("") { b -> "%02x".format(b) }
            val e = ext.trim().trimStart('.').lowercase().let { if (it in setOf("jpg", "jpeg", "png", "webp")) it else "jpg" }
            return hash + "." + e
        }
    }
}
