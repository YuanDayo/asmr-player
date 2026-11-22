package com.kiite.player.data

import android.content.Context
import coil.Coil
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 缓存目录的统一口径：算大小、清空。
 *
 * **只碰 `cacheDir`**。曲库索引（filesDir）、播放历史、书签、分级标签、已购台账
 * （DownloadedLedger 落在 filesDir）、下载下来的音频（外部存储）都不在这里 ——
 * 「清理缓存」永远不该让用户丢东西，也不该让「本机有没有」的台账变空。
 *
 * 缓存一共两层，都要清：
 * - Coil 的磁盘缓存 `cacheDir/coil`（必须走它自己的 API，见 [clear] 里的注释）；
 * - 从音频标签里解出来的封面临时文件 `cacheDir/artwork`（LibraryRepository 自己管上限）。
 */
object CacheManager {

    /** `cacheDir` 下所有文件的总字节数。目录不存在 / 读不了按 0 算。 */
    suspend fun sizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        runCatching {
            val root: File = context.cacheDir
            if (!root.isDirectory) 0L else root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    /**
     * 清空缓存，返回实际清掉的字节数。
     *
     * 顺序有讲究：先让 Coil 自己清（`DiskCache.clear()` 会拿 journal 的锁、写完空日志），
     * 再按目录删别的。反过来直接 `deleteRecursively()` 掉 `cacheDir/coil`，Coil 手里
     * 那个 DiskCache 实例的目录就没了，之后每次写缓存都抛 IOException。
     * 所以 `coil` 这个目录本身要留着，只清里面的内容。
     */
    suspend fun clear(context: Context): Long = withContext(Dispatchers.IO) {
        val before = sizeBytes(context)
        val loader = runCatching { Coil.imageLoader(context) }.getOrNull()
        runCatching { loader?.diskCache?.clear() }
        runCatching { loader?.memoryCache?.clear() }
        context.cacheDir?.listFiles()?.forEach { child ->
            // coil 交给上面那把 API；这里再删一次目录会让它的 journal 悬空
            if (child.name == "coil") return@forEach
            runCatching { child.deleteRecursively() }
        }
        val after = sizeBytes(context)
        (before - after).coerceAtLeast(0L)
    }

    /** 给设置页那行副标题用：`12.3 MB` / `820 KB` / `0 B`。 */
    fun format(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L)
        return when {
            b >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", b / (1024.0 * 1024 * 1024))
            b >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024))
            b >= 1024L -> String.format(Locale.US, "%.0f KB", b / 1024.0)
            else -> b.toString() + " B"
        }
    }
}
