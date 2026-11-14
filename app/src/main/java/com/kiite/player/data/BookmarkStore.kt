package com.kiite.player.data

import android.content.Context
import com.kiite.player.core.Bookmark
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** bookmarks.json：全部书签平铺存一个列表，读取时再按曲目分组。 */
class BookmarkStore(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "bookmarks.json")

    fun load(): List<Bookmark> =
        runCatching {
            if (!file.isFile) emptyList()
            else json.decodeFromString<List<Bookmark>>(file.readText())
        }.getOrDefault(emptyList())

    fun save(list: List<Bookmark>) {
        runCatching { file.writeText(json.encodeToString(list)) }
    }
}
