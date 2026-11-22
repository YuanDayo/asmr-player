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

/**
 * 「隐藏 R18」的唯一判定（纯函数，便于单测）。
 *
 * 曲库列表、首页建议、随机播放、社团/声优聚合全部走这里 —— 以前这条判断只写在
 * 曲库列表的过滤里，首页那几处直接吃原始数据，于是「隐藏 R18」在首页等于没开。
 *
 * - [forceShowR18]：用户主动筛「成人」，这是明确要看，不受开关限制；
 * - [WorkRating.UNKNOWN]：没识别也没手动标过的作品不隐藏（保持原有语义）。
 */
fun hidesR18(hideR18: Boolean, rating: WorkRating, forceShowR18: Boolean = false): Boolean =
    hideR18 && !forceShowR18 && rating == WorkRating.R18

object RatingParse {
    // 实测：DLsite 作品页里 R18 出现百余次，而「成人向」一次都不出现
    private val MARKERS = listOf("R18", "r18", "18禁", "adult", "成人向")

    /** DLsite 作品页里出现成人向标记就算成人级。 */
    fun looksR18(html: String, tags: List<String> = emptyList()): Boolean =
        DlsiteParse.isAgeGate(html) ||
            MARKERS.any { m -> html.contains(m, ignoreCase = true) } ||
            tags.any { t -> MARKERS.any { m -> t.contains(m, ignoreCase = true) } }
}
