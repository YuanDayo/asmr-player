package com.asmrplayer.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/**
 * 把系统文件选择器（SAF）返回的 Uri **尽力**还原成本地真实路径。
 *  - 内置存储 / SD 卡（ExternalStorageProvider）：由 docId 直接拼路径
 *  - 相册 / 媒体（MediaStore）：查 MediaStore 的 DATA 列
 *  - 云盘等其它 provider：无法还原，返回 null（调用方应改用「复制到应用内」兜底）
 */
object SafPath {

    fun fromTreeUri(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        return fromDocId(runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull())
    }

    fun fromDocumentUri(context: Context, uri: Uri): String? = when (uri.authority) {
        "com.android.externalstorage.documents" ->
            fromDocId(runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull())
        "com.android.providers.media.documents" ->
            mediaStorePath(context, runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull())
        else -> null
    }

    /** docId 形如 primary:ASMR/RJ123456 。 */
    private fun fromDocId(docId: String?): String? {
        if (docId.isNullOrBlank()) return null
        val parts = docId.split(':', limit = 2)
        if (parts.size != 2) return null
        val volume = parts[0]
        val rel = parts[1]
        val base = if (volume.equals("primary", true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/" + volume
        }
        return if (rel.isBlank()) base else base + File.separator + rel
    }

    /** docId 形如 image:1234 / video:1234 / audio:1234 / file:1234 。 */
    private fun mediaStorePath(context: Context, docId: String?): String? {
        if (docId.isNullOrBlank()) return null
        val kind = docId.substringBefore(':', "")
        val id = docId.substringAfter(':', docId)
        if (id.isBlank()) return null
        val collection = when (kind) {
            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns.DATA),
                "_id=?",
                arrayOf(id),
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }
}
