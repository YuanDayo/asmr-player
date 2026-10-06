package com.kiite.player.core

import java.io.File

/**
 * 递归扫描解压后的 ASMR 目录，找出音频与台本文件。
 * 只做发现与自动匹配；「总项目」分组交给 [ProjectGrouper]。
 */
/** 同格式优先级：越靠前越优先作为主文件（无损优先）。 */
val FORMAT_RANK = listOf("flac", "wav", "ape", "aiff", "alac", "m4a", "aac", "ogg", "opus", "mp3", "wma")

/** 能识别的压缩包扩展名。目前只有 zip 能原生解压。 */
val ARCHIVE_EXT: Set<String> = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "xz", "bz2")

class LibraryScanner(
    private val audioExtensions: Set<String> = DEFAULT_AUDIO_EXT,
    private val scriptExtensions: Set<String> = DEFAULT_SCRIPT_EXT,
    private val maxDepth: Int = 14,
    private val maxScriptBytes: Long = 32L * 1024 * 1024,
) {

    fun scan(root: File): ScanResult {
        val tracks = ArrayList<DiscoveredTrack>()
        val scripts = ArrayList<ScriptRef>()
        val archives = ArrayList<ArchiveEntry>()
        walk(root, 0, tracks, scripts, archives)
        val matcher = ScriptMatcher()
        val (matchedTracks, orphans) = matcher.associate(tracks, scripts, root.absolutePath)
        val sorted = mergeVariants(matchedTracks.sortedWith(trackOrder))
        val folders = sorted
            .groupBy { it.folderPath }
            .map { (folderPath, list) ->
                FolderEntry(
                    path = folderPath,
                    name = list.first().folderName,
                    trackCount = list.size,
                    scriptCount = scripts.count { it.folderPath == folderPath },
                )
            }
            .sortedWith(Comparator { a, b -> NaturalOrder.compare(a.name, b.name) })
        return ScanResult(
            rootPath = root.absolutePath,
            scannedAtMs = System.currentTimeMillis(),
            folders = folders,
            tracks = sorted,
            projects = ProjectGrouper.group(root.absolutePath, sorted, archives),
            orphanScripts = orphans,
            archives = archives.sortedBy { it.path },
        )
    }

    /** 同目录同名的多格式音频合并成一条，其余格式记在 altPaths。 */
    private fun mergeVariants(list: List<TrackEntry>): List<TrackEntry> {
        val index = HashMap<String, Int>()
        val out = ArrayList<TrackEntry>()
        for (t in list) {
            val key = t.folderPath + "\u0000" + t.baseName.lowercase()
            val at = index[key]
            if (at == null) {
                index[key] = out.size
                out.add(t)
            } else {
                val cur = out[at]
                if (formatRank(t.name) < formatRank(cur.name)) {
                    out[at] = t.copy(altPaths = listOf(cur.path) + cur.altPaths)
                } else {
                    out[at] = cur.copy(altPaths = cur.altPaths + t.path)
                }
            }
        }
        return out
    }

    private fun formatRank(name: String): Int {
        val e = name.substringAfterLast('.', "").lowercase()
        val i = FORMAT_RANK.indexOf(e)
        return if (i < 0) FORMAT_RANK.size else i
    }

    private fun walk(
        dir: File,
        depth: Int,
        tracks: MutableList<DiscoveredTrack>,
        scripts: MutableList<ScriptRef>,
        archives: MutableList<ArchiveEntry>,
    ) {
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            val name = f.name
            if (name.startsWith('.')) continue
            if (name.equals("System Volume Information", true)) continue
            if (name.equals("\u0024RECYCLE.BIN", true)) continue
            if (f.isDirectory) {
                if (isNoiseDir(name)) continue
                walk(f, depth + 1, tracks, scripts, archives)
            } else {
                val ext = name.substringAfterLast('.', "").lowercase()
                // 未解压的压缩包：识别出来提示用户
                if (ext in ARCHIVE_EXT) {
                    archives.add(
                        ArchiveEntry(
                            path = f.absolutePath,
                            name = name,
                            folderPath = dir.absolutePath,
                            sizeBytes = f.length(),
                            extractable = ext == "zip",
                        ),
                    )
                    continue
                }
                when {
                    ext in audioExtensions -> tracks.add(
                        DiscoveredTrack(
                            path = f.absolutePath,
                            name = name,
                            folderPath = f.parentFile?.absolutePath ?: "",
                            folderName = f.parentFile?.name ?: "",
                            sizeBytes = f.length(),
                            modifiedMs = f.lastModified(),
                        )
                    )
                    ext in scriptExtensions -> {
                        val len = f.length()
                        if (len in 1..maxScriptBytes) {
                            scripts.add(
                                ScriptRef(
                                    path = f.absolutePath,
                                    name = name,
                                    folderPath = f.parentFile?.absolutePath ?: "",
                                    format = ScriptFormat.of(name),
                                    sizeBytes = len,
                                    modifiedMs = f.lastModified(),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /** 封面/预览/图标一类目录里的文件不算台本。 */
    private fun isNoiseDir(name: String): Boolean {
        val n = name.lowercase()
        return n == "node_modules" || n == "\u0024recycle.bin"
    }

    /** 先按文件夹名，再按文件名自然排序（01 < 02 < 10）。 */
    private val trackOrder = Comparator<TrackEntry> { a, b ->
        val byFolder = NaturalOrder.compare(a.folderName, b.folderName)
        if (byFolder != 0) byFolder else NaturalOrder.compare(a.name, b.name)
    }

    companion object {
        val DEFAULT_AUDIO_EXT = setOf(
            "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "ape", "alac", "m4b", "aiff", "aif",
        )
        val DEFAULT_SCRIPT_EXT = setOf(
            "txt", "text", "md", "markdown", "lrc", "srt", "vtt", "ass", "ssa", "docx", "pdf",
        )
    }
}

/** 扫描阶段发现的音频，还没有台本关联信息。 */
data class DiscoveredTrack(
    val path: String,
    val name: String,
    val folderPath: String,
    val folderName: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
)
