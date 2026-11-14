package com.kiite.player.core

/**
 * 曲库体检：作品完整性检查 + 跨作品重复检测。
 *
 * 纯逻辑（不碰磁盘、不依赖 Android）：输入扫描结果，输出问题清单。
 * 「缺台本」的逐首明细已有台本匹配报告负责，这里只做它没覆盖的部分：
 *
 * ① 空作品：归并成作品但一首音频都没有（多半是残留文件夹 / 只剩封面图）
 * ② 可疑小文件：字节数小于 512 的「音频」，大概率是下载截断或占位文件
 * ③ 跨作品同名：不同作品里有同名的音频文件（拷贝副本最典型的特征）
 * ④ 疑似整部重复：同名 + 同大小的音频出现在另一部作品里
 */
object LibraryAudit {

    /** 可疑小文件的阈值（字节）。 */
    const val SUSPICIOUS_MIN_BYTES = 512L

    enum class IssueType(val label: String) {
        EMPTY_PROJECT("空作品"),
        SUSPICIOUS_FILE("可疑小文件"),
        CROSS_PROJECT_DUPLICATE("跨作品同名"),
        LIKELY_DUPLICATE_WORK("疑似重复作品"),
    }

    data class Issue(
        val type: IssueType,
        val projectPath: String,
        val projectName: String,
        val detail: String,
    )

    data class FileRef(
        val projectPath: String,
        val projectName: String,
        val trackPath: String,
        val trackName: String,
        val sizeBytes: Long,
    )

    /** 一组跨作品重复：key 是「规范化文件名 + 扩展名」。 */
    data class DuplicateGroup(
        val key: String,
        val displayName: String,
        val files: List<FileRef>,
    ) {
        /** 同名且同大小（>1MB）视为强重复，几乎可以断定是同一份内容。 */
        val strong: Boolean
            get() = files.size > 1 &&
                files.map { it.sizeBytes }.distinct().size == 1 &&
                files.first().sizeBytes > 1_000_000L
    }

    data class Report(
        val issues: List<Issue>,
        val duplicates: List<DuplicateGroup>,
    ) {
        val totalIssues: Int get() = issues.size + duplicates.size
        val empty: Boolean get() = totalIssues == 0
    }

    /** 规范化文件名：去扩展名、去 RJ 编号后缀差异、统一小写、压空白。 */
    fun normalizeName(fileName: String): String =
        fileName.substringBeforeLast('.')
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

    /**
     * 体检主入口。
     * @param projects 扫描出的作品列表
     * @param tracksOf 按作品路径取曲目（保持纯函数，由调用方接扫描结果）
     */
    fun audit(
        projects: List<ProjectEntry>,
        tracksOf: (ProjectEntry) -> List<TrackEntry>,
    ): Report {
        val issues = mutableListOf<Issue>()
        val byName = HashMap<String, MutableList<FileRef>>()

        projects.forEach { project ->
            val tracks = tracksOf(project)
            if (tracks.isEmpty()) {
                issues += Issue(
                    IssueType.EMPTY_PROJECT, project.path, project.name,
                    "没有扫到任何音频文件（可能只剩封面或已被清空）",
                )
                return@forEach
            }
            tracks.forEach { track ->
                if (track.sizeBytes in 1..SUSPICIOUS_MIN_BYTES) {
                    issues += Issue(
                        IssueType.SUSPICIOUS_FILE, project.path, project.name,
                        track.name + "（仅 " + track.sizeBytes + " 字节，可能没下载完整）",
                    )
                }
                val key = normalizeName(track.name) + "." + track.name.substringAfterLast('.', "").lowercase()
                byName.getOrPut(key) { mutableListOf() } += FileRef(
                    project.path, project.name, track.path, track.name, track.sizeBytes,
                )
            }
        }

        val duplicates = byName.values
            .filter { refs ->
                refs.size > 1 && refs.map { it.projectPath }.distinct().size > 1
            }
            .map { refs ->
                DuplicateGroup(
                    key = normalizeName(refs.first().trackName) +
                        "." + refs.first().trackName.substringAfterLast('.', "").lowercase(),
                    displayName = refs.first().trackName,
                    files = refs.sortedBy { it.projectName },
                )
            }
            .sortedWith(compareByDescending<DuplicateGroup> { it.strong }.thenByDescending { it.files.size })

        duplicates.forEach { group ->
            issues += Issue(
                if (group.strong) IssueType.LIKELY_DUPLICATE_WORK else IssueType.CROSS_PROJECT_DUPLICATE,
                group.files.first().projectPath,
                group.files.joinToString(" / ") { it.projectName }.take(80),
                "「" + group.displayName + "」出现在 " + group.files.size + " 个作品里：" +
                    group.files.joinToString(" / ") { it.projectName },
            )
        }

        return Report(
            issues = issues.sortedWith(
                compareBy<Issue> { it.type.ordinal }.thenBy { it.projectName }.thenBy { it.detail },
            ),
            duplicates = duplicates,
        )
    }
}
