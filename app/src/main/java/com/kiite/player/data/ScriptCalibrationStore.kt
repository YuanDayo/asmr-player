package com.kiite.player.data

import android.content.Context
import com.kiite.player.core.ScriptCalibration
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * script_calibration.json：台本校准结果，键是台本文件路径（内嵌台本用音频路径）。
 *
 * 单独存一个文件，**从不回写用户的 .lrc/.srt**：
 * 用户拿这些文件在别处也看，被 App 悄悄改掉时间轴是很讨厌的事；
 * 而且曲库目录可能在 NAS 上只读挂载。
 */
class ScriptCalibrationStore(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "script_calibration.json")

    fun load(): Map<String, ScriptCalibration> =
        runCatching {
            if (!file.isFile) emptyMap()
            else json.decodeFromString<Map<String, ScriptCalibration>>(file.readText())
        }.getOrDefault(emptyMap())

    fun save(map: Map<String, ScriptCalibration>) {
        // 全空的校准没有保留价值，顺手剔掉，免得文件里堆一堆空对象
        val kept = map.filterValues { !it.isEmpty }
        runCatching {
            if (kept.isEmpty()) file.delete() else file.writeText(json.encodeToString(kept))
        }
    }
}
