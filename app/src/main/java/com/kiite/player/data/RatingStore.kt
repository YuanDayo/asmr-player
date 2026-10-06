package com.kiite.player.data

import android.content.Context
import java.io.File

/** 用户手动指定的作品分级（编号 → 分级 id）。 */
class RatingStore(context: Context) {
    private val file = File(context.filesDir, "ratings.txt")

    fun load(): Map<String, String> =
        runCatching {
            file.readLines()
                .mapNotNull { line -> line.split('=').takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
                .toMap()
        }.getOrDefault(emptyMap())

    fun save(map: Map<String, String>) {
        runCatching { file.writeText(map.entries.joinToString("\n") { it.key + "=" + it.value }) }
    }
}
