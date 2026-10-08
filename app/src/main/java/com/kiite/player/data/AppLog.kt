package com.kiite.player.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用内调试日志：内存里保留最近若干行（界面实时看），同时追加写盘（重启后还在）。
 * 出问题时把日志复制给作者即可定位，不用连电脑抓 logcat。
 */
object AppLog {
    private const val MAX_LINES = 500
    private const val MAX_FILE_BYTES = 512L * 1024L

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var file: File? = null

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    /** 在 ViewModel 启动时调一次。 */
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "app-log.txt")
        file = f
        runCatching {
            if (f.isFile) _lines.value = f.readLines().takeLast(MAX_LINES)
        }
    }

    @Synchronized
    fun log(message: String) {
        val line = timeFormat.format(Date()) + "  " + message
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
        val f = file ?: return
        runCatching {
            f.appendText(line + "\n")
            if (f.length() > MAX_FILE_BYTES) {
                f.writeText(_lines.value.joinToString("\n") + "\n")
            }
        }
    }

    fun clear() {
        _lines.value = emptyList()
        runCatching { file?.writeText("") }
    }

    fun snapshot(): String = _lines.value.joinToString("\n")
}
