package com.kiite.player.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 把应用内的图片保存到系统相册（`Pictures/Kiite`）。
 *
 * 为什么不统一走一条路：Android 10 起有分区存储，直接往公共目录写文件会被拦，
 * 正确做法是往 MediaStore 插一条记录再写流（**不需要任何权限**）；
 * Android 8/9 没有这层，只能写公共目录再让媒体库扫一遍，需要存储权限。
 */
object GallerySave {

    private const val ALBUM = "Kiite"

    /**
     * @param displayName 建议文件名（不含扩展名，会自动补 .jpg/.png）。
     * @return 成功时返回「相册里显示的文件名」，失败返回异常信息。
     */
    fun save(context: Context, source: File, displayName: String): Result<String> = runCatching {
        if (!source.isFile || source.length() == 0L) error("图片不存在或为空")
        val mime = mimeOf(source.name)
        val name = fileName(displayName, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, name, mime)
        } else {
            saveToPublicDir(context, source, name)
        }
    }

    private fun saveViaMediaStore(context: Context, source: File, name: String, mime: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("系统相册拒绝了写入请求")
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: error("打不开相册写入通道")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return name
    }

    private fun saveToPublicDir(context: Context, source: File, name: String): String {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) error("系统版本较低，需要存储权限才能保存到相册")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!dir.exists() && !dir.mkdirs()) error("无法创建相册目录")
        var target = File(dir, name)
        if (target.exists()) target = File(dir, uniqueName(name))
        source.copyTo(target, overwrite = false)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return target.name
    }

    private fun mimeOf(fileName: String): String = when {
        fileName.endsWith(".png", true) -> "image/png"
        fileName.endsWith(".webp", true) -> "image/webp"
        else -> "image/jpeg"
    }

    private fun fileName(displayName: String, mime: String): String {
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val base = displayName.trim()
            .replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_")
            .take(80)
            .ifBlank { "kiite-cover" }
        return base + "." + ext
    }

    private fun uniqueName(name: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        return base + "-" + System.currentTimeMillis() + ext
    }
}
