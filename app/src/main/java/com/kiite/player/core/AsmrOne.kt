package com.kiite.player.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * asmr.one（免费 ASMR 资源站）的数据模型。
 *
 * 真实接口（2026-11 实测）：
 *   搜索  GET https://api.asmr.one/api/search/{关键词}?order=dl_count&sort=desc&page=1&subtitle=0
 *   文件  GET https://api.asmr.one/api/tracks/{作品id}
 * 无需登录即可搜索；封面字段返回完整 URL；作品编号在 source_id（RJxxxxxxxx）。
 *
 * 注意：vas[].id / circle[].id 在真实响应里可能是 UUID 字符串（不是数字），
 * 这些 id 本应用用不到，模型里直接不带，靠 ignoreUnknownKeys 兜住。
 */
@Serializable
data class AsmrOneCircle(val name: String? = null)

@Serializable
data class AsmrOneVa(val name: String? = null)

@Serializable
data class AsmrOneWork(
    val id: Long,
    val title: String? = null,
    /** 顶层的社团名（与 circle.name 冗余，做兜底用）。 */
    val name: String? = null,
    val circle: AsmrOneCircle? = null,
    val vas: List<AsmrOneVa> = emptyList(),
    /** DLsite 作品编号，如 RJ285384。 */
    @SerialName("source_id") val sourceId: String? = null,
    val price: Int? = null,
    @SerialName("dl_count") val dlCount: Long? = null,
    @SerialName("rate_average_2dp") val rateAverage: Double? = null,
    @SerialName("rate_count") val rateCount: Int? = null,
    /** 时长（秒）。 */
    val duration: Int? = null,
    @SerialName("age_category_string") val ageCategory: String? = null,
    @SerialName("has_subtitle") val hasSubtitle: Boolean? = null,
    @SerialName("mainCoverUrl") val mainCoverUrl: String? = null,
    @SerialName("thumbnailCoverUrl") val thumbnailCoverUrl: String? = null,
) {
    /** 可用于下载归档的作品编号；source_id 缺失或不是 RJ 格式时为 null。 */
    val code: String? get() = sourceId?.uppercase()?.takeIf { Regex("RJ\\d{6,8}").matches(it) }

    val circleName: String? get() = circle?.name ?: name

    val vaNames: List<String> get() = vas.mapNotNull { it.name }.filter { it.isNotBlank() }
}

@Serializable
data class AsmrOnePagination(
    @SerialName("currentPage") val currentPage: Int = 1,
    @SerialName("pageSize") val pageSize: Int = 20,
    @SerialName("totalCount") val totalCount: Int = 0,
)

@Serializable
data class AsmrOneSearchResponse(
    val works: List<AsmrOneWork> = emptyList(),
    val pagination: AsmrOnePagination? = null,
)
