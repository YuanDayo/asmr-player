package com.asmrplayer.core

import kotlinx.serialization.Serializable

/**
 * 台本格式。支持用户实际使用的 txt / md / lrc（带时间轴）/ srt / ass / docx / pdf。
 */
@Serializable
enum class ScriptFormat(val ext: String, val label: String) {
    TXT("txt", "文本"),
    MD("md", "Markdown"),
    LRC("lrc", "歌词(时间轴)"),
    SRT("srt", "字幕(时间轴)"),
    VTT("vtt", "WebVTT(时间轴)"),
    ASS("ass", "ASS 字幕(时间轴)"),
    DOCX("docx", "Word"),
    PDF("pdf", "PDF"),
    UNKNOWN("?", "未知");

    /** 是否自带时间轴，可直接逐句同步。 */
    val isTimed: Boolean get() = this == LRC || this == SRT || this == VTT || this == ASS

    companion object {
        fun of(fileName: String): ScriptFormat =
            when (fileName.substringAfterLast('.', "").lowercase()) {
                "txt", "text" -> TXT
                "md", "markdown", "mkd" -> MD
                "lrc" -> LRC
                "srt" -> SRT
                "vtt", "webvtt" -> VTT
                "ass", "ssa" -> ASS
                "docx" -> DOCX
                "pdf" -> PDF
                else -> UNKNOWN
            }
    }
}

/** 扫描时发现的一个台本文件。 */
@Serializable
data class ScriptRef(
    val path: String,
    val name: String,
    val folderPath: String,
    val format: ScriptFormat,
    val sizeBytes: Long,
    val modifiedMs: Long,
) {
    val baseName: String get() = name.substringBeforeLast('.', name)
}

/** 台本被关联到某条音频上。 */
@Serializable
data class ScriptAttachment(
    val scriptPath: String,
    val scriptName: String,
    val format: ScriptFormat,
    val score: Int,
    val reason: String,
    val manual: Boolean = false,
)

/** 曲库中的一条音频。 */
@Serializable
data class TrackEntry(
    val path: String,
    val name: String,
    val folderPath: String,
    val folderName: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val durationMs: Long? = null,
    val embeddedLyrics: String? = null,
    val scripts: List<ScriptAttachment> = emptyList(),
) {
    val baseName: String get() = name.substringBeforeLast('.', name)
    val primaryScript: ScriptAttachment? get() = scripts.firstOrNull()
    val hasScript: Boolean get() = scripts.isNotEmpty()
    val hasTimedScript: Boolean get() = scripts.any { it.format.isTimed }
}

/** 曲库分组（一个文件夹）。 */
@Serializable
data class FolderEntry(
    val path: String,
    val name: String,
    val trackCount: Int,
    val scriptCount: Int,
    val coverPath: String? = null,
)

/** 总项目内的一个章节（子文件夹）。 */
@Serializable
data class ChapterEntry(
    val path: String,
    val name: String,
    val trackCount: Int,
)

/** 压缩包：尚未解压的资源，扫描时识别出来提醒用户。 */
@Serializable
data class ArchiveEntry(
    val path: String,
    val name: String,
    val folderPath: String,
    val sizeBytes: Long = 0L,
    val extractable: Boolean = false,
)

/** 一个「总项目」（通常是一部 ASMR 作品），下面统合多个章节的音频。 */
@Serializable
data class ProjectEntry(
    val path: String,
    val name: String,
    val code: String? = null,
    val trackCount: Int,
    val scriptCount: Int,
    val coverPath: String? = null,
    val chapters: List<ChapterEntry> = emptyList(),
    /** 项目内未解压的压缩包数量。 */
    val archiveCount: Int = 0,
)

/** 一次扫描的完整结果，可直接序列化缓存。 */
@Serializable
data class ScanResult(
    val rootPath: String,
    val scannedAtMs: Long,
    val folders: List<FolderEntry> = emptyList(),
    val tracks: List<TrackEntry> = emptyList(),
    val projects: List<ProjectEntry> = emptyList(),
    val orphanScripts: List<ScriptRef> = emptyList(),
    val archives: List<ArchiveEntry> = emptyList(),
) {
    val trackCount: Int get() = tracks.size
    val archiveCount: Int get() = archives.size
    val withScriptCount: Int get() = tracks.count { it.hasScript }
    val projectCount: Int get() = projects.size
}

/** 写标签的结果。 */
sealed interface TagWriteResult {
    data class Written(val format: String, val bytes: Int) : TagWriteResult
    data object Unsupported : TagWriteResult
    data class Failed(val message: String) : TagWriteResult
}
