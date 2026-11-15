package com.kiite.player.core

import java.util.Locale

/**
 * 字节数 → 人话：「1.24 GB」「356 MB」「845 KB」。
 *
 * 下载前容量估算、单个文件体积、下载完成汇总都走这一个口径，
 * 免得同一个数字在界面不同位置显示成三种样子（以前只有 Controller 里一个私有 mb()）。
 */
object SizeFormat {

    /** 未知/无效（0 或负数）统一显示成破折号，避免出现「0 B」误导。 */
    fun text(bytes: Long): String = when {
        bytes <= 0L -> "—"
        bytes >= GB -> String.format(Locale.US, "%.2f GB", bytes / GB.toDouble())
        bytes >= MB -> String.format(Locale.US, "%.1f MB", bytes / MB.toDouble())
        bytes >= KB -> String.format(Locale.US, "%.0f KB", bytes / KB.toDouble())
        else -> bytes.toString() + " B"
    }

    /** 估算口径用「约 1.2 GB」：不到 100 MB 时保留一位小数，越大越粗略。 */
    fun approx(bytes: Long): String = when {
        bytes <= 0L -> "—"
        bytes >= GB -> String.format(Locale.US, "%.2f GB", bytes / GB.toDouble())
        bytes >= 100 * MB -> String.format(Locale.US, "%.0f MB", bytes / MB.toDouble())
        else -> String.format(Locale.US, "%.1f MB", bytes / MB.toDouble())
    }

    const val KB = 1024L
    const val MB = 1024L * 1024L
    const val GB = 1024L * 1024L * 1024L
}
