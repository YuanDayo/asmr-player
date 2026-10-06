package com.kiite.player.core

/** 作品分级。自动来自 DLsite，也可由用户手动改。 */
enum class WorkRating(val id: String, val label: String) {
    ALL("all", "全年龄"),
    R18("r18", "成人"),
    UNKNOWN("unknown", "未分类");

    companion object {
        fun of(id: String?): WorkRating = values().firstOrNull { it.id == id } ?: UNKNOWN
    }
}

object RatingParse {
    private val MARKERS = listOf("成人向け", "成人向", "R18", "r18", "18禁", "adult")

    /** DLsite 作品页里出现成人向标记就算成人级。 */
    fun looksR18(html: String, tags: List<String> = emptyList()): Boolean =
        DlsiteParse.isAgeGate(html) ||
            MARKERS.any { m -> html.contains(m, ignoreCase = true) } ||
            tags.any { t -> MARKERS.any { m -> t.contains(m, ignoreCase = true) } }
}
