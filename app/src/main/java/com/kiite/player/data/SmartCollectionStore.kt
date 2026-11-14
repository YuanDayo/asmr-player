package com.kiite.player.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 智能收藏（智能集合）：把「关键词 + 标签 + 分级」等筛选条件存成命名好的集合，
 * 点一下就整体应用回曲库筛选。
 *
 * 与自定义标签的区别：标签标在作品上，智能集合存的是**一组筛选条件**——
 * 例如「睡前 · 温柔」= 搜索词「温柔」+ 标签「睡前」。作品换了标签集合的成员会跟着变。
 */
@Serializable
data class SmartCollection(
    val id: String,
    val name: String,
    /** 搜索关键词（匹配作品名 / 编号）。空串表示不限。 */
    val query: String = "",
    /** 要求作品带上的自定义标签；null 表示不限。 */
    val tag: String? = null,
    /** 只看收藏。 */
    val onlyFavorite: Boolean = false,
    /** 分级筛选：1 成人 / 2 全年龄 / 0 或 null 表示不限。 */
    val rating: Int? = null,
    /** 创建时间（排序用）。 */
    val createdAtMs: Long = 0L,
)

/** 智能集合存储：filesDir/smart-collections.json。 */
class SmartCollectionStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "smart-collections.json")

    fun load(): List<SmartCollection> =
        runCatching {
            if (!file.isFile) emptyList()
            else json.decodeFromString<List<SmartCollection>>(file.readText())
        }.getOrDefault(emptyList())

    fun save(list: List<SmartCollection>) {
        runCatching { file.writeText(json.encodeToString(list)) }
    }
}
