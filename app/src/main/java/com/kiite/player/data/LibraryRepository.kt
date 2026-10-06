package com.kiite.player.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.kiite.player.core.LibraryScanner
import com.kiite.player.core.ParsedScript
import com.kiite.player.core.ScanResult
import com.kiite.player.core.ScriptFormat
import com.kiite.player.core.ScriptParser
import com.kiite.player.core.TagIO
import com.kiite.player.core.TagWriteResult
import com.kiite.player.pdf.PdfBoxTextExtractor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 扫描、解析台本、读写标签。所有磁盘操作都在 IO 线程。 */
class LibraryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cacheFile: File get() = File(context.filesDir, "library-cache.json")

    val parser: ScriptParser by lazy { ScriptParser(PdfBoxTextExtractor(context)) }

    suspend fun scan(root: File): ScanResult = withContext(Dispatchers.IO) {
        val result = LibraryScanner().scan(root)
        runCatching { cacheFile.writeText(json.encodeToString(ScanResult.serializer(), result)) }
        result
    }

    suspend fun loadCache(): ScanResult? = withContext(Dispatchers.IO) {
        if (!cacheFile.isFile) return@withContext null
        runCatching { json.decodeFromString(ScanResult.serializer(), cacheFile.readText()) }.getOrNull()
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        runCatching { cacheFile.delete() }
        Unit
    }

    suspend fun parseScript(path: String): ParsedScript = withContext(Dispatchers.IO) {
        val f = File(path)
        if (!f.isFile) ParsedScript(ScriptFormat.UNKNOWN, "", warning = "台本文件不存在")
        else parser.parse(f)
    }

    suspend fun readEmbeddedLyrics(trackPath: String): String? = withContext(Dispatchers.IO) {
        val f = File(trackPath)
        if (!f.isFile) null else TagIO.readLyrics(f)
    }

    /** 把音频内嵌封面解出来缓存成图片文件，交给 Coil 加载；没有内嵌封面返回 null。 */
    suspend fun artworkFile(trackPath: String): File? = withContext(Dispatchers.IO) {
        val src = File(trackPath)
        if (!src.isFile) return@withContext null
        val dir = File(context.cacheDir, "artwork")
        if (!dir.isDirectory) dir.mkdirs()
        val key = (src.absolutePath + src.length() + src.lastModified()).hashCode().toString()
        val out = File(dir, key + ".img")
        if (out.isFile && out.length() > 64) return@withContext out
        val bytes = TagIO.readArtwork(src) ?: return@withContext null
        try {
            out.writeBytes(bytes)
            if (out.length() > 0) out else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun embed(trackPath: String, text: String): TagWriteResult = withContext(Dispatchers.IO) {
        TagIO.writeLyrics(File(trackPath), text)
    }

    suspend fun embedFromScript(trackPath: String, scriptPath: String): TagWriteResult =
        withContext(Dispatchers.IO) {
            val parsed = parser.parse(File(scriptPath))
            if (parsed.isEmpty) {
                TagWriteResult.Failed(parsed.warning ?: "台本内容为空，无法写入")
            } else {
                TagIO.writeLyrics(File(trackPath), parsed.plainText)
            }
        }

    /** 递归列出可进入的子目录，用于内置文件夹选择器。 */
    suspend fun listSubDirectories(dir: File): List<File> = withContext(Dispatchers.IO) {
        val children = dir.listFiles() ?: return@withContext emptyList()
        children.filter { it.isDirectory && !it.name.startsWith('.') }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun countAudioFiles(dir: File): Int = withContext(Dispatchers.IO) {
        var count = 0
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        var visited = 0
        while (stack.isNotEmpty() && visited < 4000) {
            val d = stack.removeLast()
            visited++
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                if (k.isDirectory) {
                    if (!k.name.startsWith('.')) stack.addLast(k)
                } else if (k.name.substringAfterLast('.', "").lowercase() in LibraryScanner.DEFAULT_AUDIO_EXT) {
                    count++
                    if (count > 5000) return@withContext count
                }
            }
        }
        count
    }

    // ---------- 压缩包 ----------

    /**
     * 解压 zip 到同级的「<名字>_解压」文件夹，返回写出的文件数。
     * rar / 7z 没有原生支持，界面上只做「识别 + 提示」。
     */
    fun extractZip(archivePath: String, intoDir: File? = null): Int {
        val src = File(archivePath)
        require(src.isFile) { "压缩包不存在" }
        val target = intoDir ?: File(src.parentFile, src.nameWithoutExtension + "_解压")
        target.mkdirs()
        val rootCanonical = target.canonicalPath
        var count = 0
        java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(src))).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val out = File(target, entry.name)
                // 防目录穿越
                if (!out.canonicalPath.startsWith(rootCanonical)) {
                    zin.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    java.io.FileOutputStream(out).use { fos -> zin.copyTo(fos) }
                    count++
                }
                zin.closeEntry()
            }
        }
        return count
    }

    // ---------- 手动指定台本 ----------

    private val manualFile: File get() = File(context.filesDir, "manual-links.json")

    suspend fun loadManualLinks(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!manualFile.isFile) return@withContext emptyMap()
        runCatching {
            json.decodeFromString(ManualLinkStore.serializer(), manualFile.readText()).links
        }.getOrDefault(emptyMap())
    }

    suspend fun saveManualLink(trackPath: String, scriptPath: String) = withContext(Dispatchers.IO) {
        val links = (loadManualLinksInner()).toMutableMap()
        links[trackPath] = scriptPath
        manualFile.writeText(json.encodeToString(ManualLinkStore.serializer(), ManualLinkStore(links)))
    }

    suspend fun clearManualLink(trackPath: String) = withContext(Dispatchers.IO) {
        val links = (loadManualLinksInner()).toMutableMap()
        links.remove(trackPath)
        manualFile.writeText(json.encodeToString(ManualLinkStore.serializer(), ManualLinkStore(links)))
    }

    private fun loadManualLinksInner(): Map<String, String> {
        if (!manualFile.isFile) return emptyMap()
        return runCatching {
            json.decodeFromString(ManualLinkStore.serializer(), manualFile.readText()).links
        }.getOrDefault(emptyMap())
    }

    // ---------- 从 SAF Uri 复制到应用内部存储 ----------

    /**
     * 把用户挑的图片复制成应用内部文件并校验可解码。
     * 不依赖路径还原，因此相册 / 云盘 / 任意 provider 都能用。
     */
    suspend fun saveBackgroundImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val out = File(context.filesDir, "custom-background.img")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } != null
        }.getOrDefault(false)
        if (!ok || out.length() <= 0) {
            runCatching { out.delete() }
            return@withContext null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(out.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            runCatching { out.delete() }
            return@withContext null
        }
        out.absolutePath
    }

    /** 无法还原成路径的台本（云盘等）复制进应用内部存储后再使用。 */
    suspend fun importScript(uri: Uri): String? = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: ("imported-" + System.currentTimeMillis() + ".txt")
        val dir = File(context.filesDir, "imported-scripts")
        if (!dir.isDirectory) dir.mkdirs()
        val out = File(dir, name.replace(File.separatorChar, '_'))
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } != null
        }.getOrDefault(false)
        if (!ok || out.length() <= 0) {
            runCatching { out.delete() }
            return@withContext null
        }
        out.absolutePath
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    // ---------- 文件选择器用 ----------

    /** 列出目录（用于导航）与台本文件（用于选择）。 */
    suspend fun listForPicker(dir: File): DirListing = withContext(Dispatchers.IO) {
        val children = dir.listFiles() ?: return@withContext DirListing(emptyList(), emptyList())
        val dirs = children
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .sortedBy { it.name.lowercase() }
        val scripts = children
            .filter { it.isFile && it.name.substringAfterLast('.', "").lowercase() in LibraryScanner.DEFAULT_SCRIPT_EXT }
            .sortedBy { it.name.lowercase() }
        DirListing(dirs, scripts)
    }
}

@Serializable
private data class ManualLinkStore(val links: Map<String, String> = emptyMap())

/** 文件选择器的列表结果：目录 + 台本文件。 */
data class DirListing(val dirs: List<File>, val scripts: List<File>)
