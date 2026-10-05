package com.asmrplayer.core

import java.io.File

/** 手动指定的「曲目 → 台本」对应关系。置顶显示，优先于自动匹配。 */
object ManualLinks {

    /** 把手动指定的台本置顶，并去掉指向同一路径的自动匹配项，避免重复。 */
    fun apply(tracks: List<TrackEntry>, links: Map<String, String>): List<TrackEntry> {
        if (links.isEmpty()) return tracks
        return tracks.map { track ->
            val manual = links[track.path]
            if (manual.isNullOrBlank()) return@map track
            val scriptFile = File(manual)
            val att = ScriptAttachment(
                scriptPath = manual,
                scriptName = scriptFile.name,
                format = ScriptFormat.of(scriptFile.name),
                score = 1_000_000,
                reason = "手动指定",
                manual = true,
            )
            track.copy(scripts = listOf(att) + track.scripts.filter { it.scriptPath != manual })
        }
    }
}
