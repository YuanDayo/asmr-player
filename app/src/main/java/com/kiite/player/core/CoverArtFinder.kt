package com.kiite.player.core

import java.io.File

/** 在项目/曲目文件夹里寻找专辑封面图片。 */
object CoverArtFinder {

    val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    private val preferredNames = listOf(
        "cover", "folder", "front", "album", "albumart", "jacket", "package", "thumb",
        "封面", "表紙", "ジャケット", "パッケージ", "预览", "海报",
    )

    /**
     * 在 dir 及其最多 [depth] 层子目录里找封面：
     * 优先「名字像封面」的图片，其次体积最大的图片。找不到返回 null。
     */
    fun find(dir: File, depth: Int = 2): String? {
        if (!dir.isDirectory) return null
        val images = ArrayList<File>()
        collect(dir, depth, images)
        if (images.isEmpty()) return null
        val preferred = images.firstOrNull { f ->
            val n = f.name.substringBeforeLast('.', f.name).lowercase()
            preferredNames.any { n.contains(it) }
        }
        return (preferred ?: images.maxByOrNull { it.length() })?.absolutePath
    }

    private fun collect(dir: File, depth: Int, out: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory) {
                if (depth > 0 && !f.name.startsWith('.')) collect(f, depth - 1, out)
            } else if (f.name.substringAfterLast('.', "").lowercase() in IMAGE_EXT) {
                out.add(f)
            }
        }
    }
}
