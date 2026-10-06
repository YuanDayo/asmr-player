package com.asmrplayer.core

import java.io.File

/**
 * 把扫描结果里的曲目统合成「总项目」。
 *
 * 规则：总项目 = 曲库根目录下的第一级子目录（即整部 ASMR 作品）；
 * 作品内部再按子文件夹切成「章节」。曲库根目录本身直接放音频时，作为单个项目处理。
 *
 * 例：
 *   /sdcard/ASMR/
 *     RJ123456 作品A/           ← 总项目
 *       01 第一章/a.mp3
 *       02 第二章/b.mp3         ← 章节
 *     RJ654321 作品B/           ← 总项目
 */
object ProjectGrouper {

    fun group(
        rootPath: String,
        tracks: List<TrackEntry>,
        archives: List<ArchiveEntry> = emptyList(),
    ): List<ProjectEntry> {
        if (tracks.isEmpty() && archives.isEmpty()) return emptyList()
        val root = rootPath.trimEnd(File.separatorChar)
        val byProject = LinkedHashMap<String, MutableList<TrackEntry>>()
        for (t in tracks) {
            val p = projectRootOf(root, t.folderPath)
            byProject.getOrPut(p) { ArrayList() }.add(t)
        }
        // 只有压缩包、还没解压出音频的项目也要出现
        for (a in archives) {
            val p = projectRootOf(root, a.folderPath)
            byProject.getOrPut(p) { ArrayList() }
        }
        val coverCache = HashMap<String, String?>()
        return byProject.map { (path, list) ->
            val chapters = list
                .groupBy { it.folderPath }
                .map { (cp, cl) ->
                    ChapterEntry(
                        path = cp,
                        name = if (cp == path) "本目录" else File(cp).name,
                        trackCount = cl.size,
                    )
                }
                .sortedWith(Comparator { a, b -> NaturalOrder.compare(a.name, b.name) })
            val projectName = if (path == root) rootName(root) else File(path).name
            ProjectEntry(
                path = path,
                name = projectName,
                code = TextUtils.workCode(projectName),
                trackCount = list.size,
                scriptCount = list.mapNotNull { it.primaryScript?.scriptPath }.distinct().size,
                coverPath = coverCache.getOrPut(path) { CoverArtFinder.find(File(path), 2) },
                chapters = chapters,
                archiveCount = archives.count { projectRootOf(root, it.folderPath) == path },
            )
        }.sortedWith(Comparator { a, b -> NaturalOrder.compare(a.name, b.name) })
    }

    /** 某个文件夹所属的总项目根路径。 */
    fun projectRootOf(root: String, folder: String): String {
        if (folder == root) return root
        val prefix = root + File.separator
        if (!folder.startsWith(prefix)) return root
        val rel = folder.substring(prefix.length)
        val first = rel.substringBefore(File.separator)
        return root + File.separator + first
    }

    private fun rootName(root: String): String = File(root).name.ifBlank { "曲库" }
}
