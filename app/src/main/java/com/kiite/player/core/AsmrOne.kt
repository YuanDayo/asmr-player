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

// ---------- 文件树 / 登录（2026-11 实测） ----------
//
//   登录  POST https://api.asmr.one/api/auth/me  body {"name":"guest","password":"guest"}
//         （访客账号 guest/guest 即可，返回 {token}。kikoeru 的登录路由就叫 me——
//          /api/auth/login 不存在，撞进 JWT 中间件会 401）
//   文件树 GET https://api.asmr.one/api/tracks/{数字id}   ← 需要 Bearer（/api/tree 不存在会 404）；
//         audio 节点的 source 是相对作品根的完整路径，媒体下载就是 GET /api/media/{id}/{source}

@Serializable
data class AsmrOneAuthResponse(
    val token: String? = null,
    val refresh: String? = null,
    val user: AsmrOneUser? = null,
)

@Serializable
data class AsmrOneUser(val id: Int? = null, val name: String? = null)

@Serializable
data class AsmrOneTreeNode(
    val name: String? = null,
    val title: String? = null,
    /** folder / audio / image / text / video / file … */
    val type: String? = null,
    /** 相对作品根的完整路径，媒体下载 URL 直接用（/api/media/{workId}/{source}）。 */
    val source: String? = null,
    val children: List<AsmrOneTreeNode> = emptyList(),
)

/**
 * 文件树 → 要下载的音频文件列表。纯函数，方便单测。
 *
 * asmr.one 上的作品经常同一批音轨同时放 mp3 / wav / flac 三份（体积差几倍），
 * 全下太浪费：按「目录」分组，同一目录里按 mp3 > wav > flac 的优先级只挑一种扩展名。
 * 目录里没有这三样时（ogg/m4a 等）就全保留。
 */
object AsmrOneTree {

    private val AUDIO_EXT = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus")

    /** 优先级从高到低；命中的扩展名会被保留。 */
    private val PREFER = listOf("mp3", "wav", "flac")

    fun extOf(node: AsmrOneTreeNode): String? =
        (node.source ?: node.name)?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }

    fun isAudio(node: AsmrOneTreeNode): Boolean {
        val ext = extOf(node) ?: return false
        return node.type == "audio" || (node.type == null && ext in AUDIO_EXT)
    }

    /** 递归收集全部叶子节点。 */
    fun leaves(root: AsmrOneTreeNode): List<AsmrOneTreeNode> {
        if (root.children.isEmpty()) return if (root.source != null || root.name != null) listOf(root) else emptyList()
        return root.children.flatMap { leaves(it) }
    }

    fun audioFiles(root: AsmrOneTreeNode): List<AsmrOneTreeNode> {
        val files = leaves(root).filter { it.source != null && isAudio(it) }
        if (files.isEmpty()) return files
        // 目录路径 = source 去掉最后一段文件名
        return files.groupBy { it.source!!.substringBeforeLast('/', "") }
            .flatMap { (_, group) ->
                val byExt = group.groupBy { extOf(it) }
                val chosen = PREFER.firstOrNull { byExt.containsKey(it) }
                if (chosen == null) group else byExt[chosen].orEmpty()
            }
    }
}
