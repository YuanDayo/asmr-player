package com.kiite.player.core

/**
 * 「已下载」台账：作品编号 → 落盘文件。
 *
 * 为什么不再只是「一堆编号」：老版本只记编号，于是**用户把下载的文件删了，应用还认为
 * 本机有**——「已购未下载」里永远不再出现它（2.5.5-beta3 用户反馈）。台账要能落回到
 * 具体文件，判定时以磁盘为准：文件都不在了 = 没下载。
 *
 * 文件格式（一行一个作品，`\t` 后是 `|` 分隔的绝对路径，分卷作品会有多条）：
 * ```
 * RJ01234567	/storage/emulated/0/ASMR/DLsite/RJ01234567.zip
 * ```
 * 老版本留下的「只有编号」的行照旧能读，只是没有路径可验证（[present] 返回 false）——
 * 调用方可以按编号去下载目录里回捞一次再决定。
 */
object DownloadedLedger {

    private const val CODE_SEP = '\t'
    private const val PATH_SEP = "|"

    /**
     * 解析台账。空行、只有编号的行、重复编号都容忍：
     * 同一个编号出现多次时路径合并去重（历史上可能重复写过）。
     */
    fun parse(lines: List<String>): Map<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val code = line.substringBefore(CODE_SEP).trim().uppercase()
            if (code.isEmpty()) return@forEach
            val paths = line.substringAfter(CODE_SEP, "")
                .split(PATH_SEP)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            out[code] = (out[code].orEmpty() + paths).distinct()
        }
        return out
    }

    /** 写回台账：一行一个编号，路径为空时只写编号（等价于老格式）。 */
    fun render(ledger: Map<String, List<String>>): String = ledger.entries
        .filter { it.key.isNotBlank() }
        .joinToString("\n") { (code, paths) ->
            code.trim().uppercase() + CODE_SEP +
                paths.filter { it.isNotBlank() }.joinToString(PATH_SEP)
        }

    /**
     * 台账里这一条还有没有文件在。
     *
     * 空路径（老台账）一律 false：**没法确认就当没有**，宁可让用户重新下一次，
     * 也不要让「已下载」变成一条永远不消失的假记录。
     */
    fun present(
        paths: List<String>,
        exists: (String) -> Boolean = { java.io.File(it).exists() },
    ): Boolean = paths.any { it.isNotBlank() && exists(it) }
}
