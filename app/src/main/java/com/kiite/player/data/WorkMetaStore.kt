package com.kiite.player.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 作品详情页里「可自由编辑」的部分（影视剧式详情页用）。
 *
 * 键与手动分级 / 自定义标签一致：有 RJ/VJ 编号用编号，没有就用项目路径。
 */
@Serializable
data class WorkMeta(
    /** 简介（DLsite 抓到的会填进来，也可以手写覆盖）。 */
    val synopsis: String = "",
    /** 曲目路径 → 自定义集名。 */
    val epNames: Map<String, String> = emptyMap(),
    /** 被隐藏的曲目路径（详情页不显示、播放全部也不带）。 */
    val hidden: List<String> = emptyList(),
    /** 自定义集顺序（曲目路径）；空列表表示按曲目原有顺序。 */
    val order: List<String> = emptyList(),
    /** 是否隐藏「未匹配到台本」这类提示，留作扩展。 */
    val hideMeta: Boolean = false,
)

/** 作品元数据存储：与标签一样落在 filesDir 下的 json。 */
class WorkMetaStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "work-meta.json")

    fun load(): Map<String, WorkMeta> =
        runCatching {
            if (!file.isFile) emptyMap()
            else json.decodeFromString<Map<String, WorkMeta>>(file.readText())
        }.getOrDefault(emptyMap())

    fun save(map: Map<String, WorkMeta>) {
        runCatching { file.writeText(json.encodeToString(map)) }
    }
}
