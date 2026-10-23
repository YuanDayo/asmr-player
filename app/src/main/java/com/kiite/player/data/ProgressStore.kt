package com.kiite.player.data

import android.content.Context
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 每个音频/视频的「上次看到哪儿」（曲目绝对路径 → 毫秒）。
 * 用来做「记住每集进度」：下次点同一集从上次的位置继续。
 */
class ProgressStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "progress.json")

    fun load(): Map<String, Long> =
        runCatching {
            if (!file.isFile) emptyMap()
            else json.decodeFromString<Map<String, Long>>(file.readText())
        }.getOrDefault(emptyMap())

    fun save(map: Map<String, Long>) {
        runCatching { file.writeText(json.encodeToString(map)) }
    }
}
