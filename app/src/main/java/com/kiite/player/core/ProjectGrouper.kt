package com.kiite.player.core

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
            // 章节按**完整相对路径**排序，不能只比文件夹名：
            // 总集篇里每个子作品都可能有一模一样的「1.音声文件」，只比名字会全部并列，
            // 章节顺序变成按扫描顺序乱放，播放列表也跟着不连贯。
            val chapterPaths = list.map { it.folderPath }.distinct()
                .sortedWith(Comparator { a, b -> NaturalOrder.comparePath(a, b) })
            val chapterTitles = chapterTitles(path, chapterPaths)
            val countByChapter = list.groupingBy { it.folderPath }.eachCount()
            val chapters = chapterPaths.mapIndexed { i, cp ->
                ChapterEntry(
                    path = cp,
                    name = chapterTitles[i],
                    trackCount = countByChapter[cp] ?: 0,
                )
            }
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

    /**
     * 章节标题：同名的（总集篇里多卷共用「1.音声文件」这种）补上父目录名做区分，
     * 补到能分辨为止——「第一卷 / 1.音声文件」比出现五条一模一样的标题强。
     */
    private fun chapterTitles(projectPath: String, paths: List<String>): List<String> {
        val base = paths.map { if (it == projectPath) "本目录" else File(it).name }
        if (base.toSet().size == base.size) return base
        val parents = paths.map { p ->
            val rel = p.removePrefix(projectPath)
                .split('/', '\\', File.separatorChar)
                .filter { it.isNotBlank() }
            if (rel.isEmpty()) emptyList() else rel.dropLast(1)
        }
        val maxDepth = parents.maxOfOrNull { it.size } ?: 0
        for (k in 1..maxDepth) {
            val titles = paths.indices.map { i -> (parents[i].takeLast(k) + base[i]).joinToString(" / ") }
            if (titles.toSet().size == titles.size) return titles
        }
        // 父级也分不开（理论上不会，路径本身唯一）：退回完整相对路径
        return paths.mapIndexed { i, p ->
            p.removePrefix(projectPath).trim('/', '\\').ifBlank { base[i] }
        }
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
