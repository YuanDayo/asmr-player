package com.kiite.player.data

import android.content.Context
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 用户自定义的作品标签（键 → 标签列表）。
 * 键与手动分级一致：有 RJ/VJ 编号用编号，没有就用项目路径。
 */
class TagStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "tags.json")

    fun load(): Map<String, List<String>> =
        runCatching {
            if (!file.isFile) emptyMap()
            else json.decodeFromString<Map<String, List<String>>>(file.readText())
        }.getOrDefault(emptyMap())

    fun save(map: Map<String, List<String>>) {
        runCatching { file.writeText(json.encodeToString(map)) }
    }
}
