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
    // 实测：DLsite 作品页里 R18 出现百余次，而「成人向」一次都不出现
    private val MARKERS = listOf("R18", "r18", "18禁", "adult", "成人向")

    /** DLsite 作品页里出现成人向标记就算成人级。 */
    fun looksR18(html: String, tags: List<String> = emptyList()): Boolean =
        DlsiteParse.isAgeGate(html) ||
            MARKERS.any { m -> html.contains(m, ignoreCase = true) } ||
            tags.any { t -> MARKERS.any { m -> t.contains(m, ignoreCase = true) } }
}
