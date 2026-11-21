package com.kiite.player.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** search_history.json 的内容：按「最近一次使用」倒序排列的搜索词。 */
@Serializable
data class SearchHistoryFile(val queries: List<String> = emptyList())

/**
 * 曲库搜索历史。
 *
 * 存 JSON 而不是 SharedPreferences：这里要存的是**有序列表**（最近优先），
 * SharedPreferences 只能摊成一堆 key/value，清空和截断都要额外维护序号。
 * 另外和 `TagStore` 一样，读写全部 `runCatching` 包住 —— 历史丢了不该让整个曲库崩。
 */
class SearchHistoryStore(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "search_history.json")

    fun load(): List<String> =
        runCatching {
            if (!file.isFile) emptyList()
            else json.decodeFromString<SearchHistoryFile>(file.readText()).queries
        }.getOrDefault(emptyList())

    fun save(queries: List<String>) {
        runCatching { file.writeText(json.encodeToString(SearchHistoryFile(queries))) }
    }
}
