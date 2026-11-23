package com.kiite.player.data

import android.content.Context
import com.kiite.player.core.HistoryRecord
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** history.json 的完整内容：记录列表 + 累计收听时长。 */
@Serializable
data class HistoryFile(
    val records: List<HistoryRecord> = emptyList(),
    val totalListenedMs: Long = 0L,
    /** 按天累计的收听时长，key 是 ISO 日期（yyyy-MM-dd）。 */
    val dailyListenedMs: Map<String, Long> = emptyMap(),
    /**
     * 按「一天里的第几小时」累计的收听时长，key 是 `0`..`23`。
     *
     * 只在小时上聚合、不记日期：这张图回答的是「习惯几点听」（睡前 22-24 点的峰），
     * 不是某天的明细。按日期×小时分会变成几千个键，而 history.json 是高频重写的文件。
     * 老版本没有这个字段，反序列化缺省为空 map（`ignoreUnknownKeys` 之外还要给默认值）。
     */
    val hourlyListenedMs: Map<String, Long> = emptyMap(),
)

/**
 * 播放历史的落盘。
 *
 * 和 [ProgressStore] 分开存：那个文件是「每一集看到哪儿」的运行时索引，
 * 结构简单、写入频繁，改动它有丢续播记录的风险；历史是给人看的记录，字段会继续长。
 */
class HistoryStore(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "history.json")

    fun load(): HistoryFile =
        runCatching {
            if (!file.isFile) HistoryFile()
            else json.decodeFromString<HistoryFile>(file.readText())
        }.getOrDefault(HistoryFile())

    fun save(state: HistoryFile) {
        runCatching { file.writeText(json.encodeToString(state)) }
    }
}
