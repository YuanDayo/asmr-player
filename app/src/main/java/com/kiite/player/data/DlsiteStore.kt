package com.kiite.player.data

import android.content.Context
import com.kiite.player.core.DlsiteWork
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 把抓到的 DLsite 作品信息缓存到本地，避免每次重扫都联网。 */
class DlsiteStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "dlsite-works.json")

    fun load(): Map<String, DlsiteWork> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            json.decodeFromString(ListSerializer(DlsiteWork.serializer()), file.readText())
                .associateBy { it.code }
        }.getOrDefault(emptyMap())
    }

    fun save(works: Map<String, DlsiteWork>) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(DlsiteWork.serializer()), works.values.toList()))
        }
    }
}
