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
    /**
     * 真实接口的文件节点常常不给 source，而是给完整媒体地址
     * （mediaDownloadUrl / mediaStreamUrl，形如 …/api/media/{id}/mp3/01.mp3）。
     * 解析时统一归一化成 source，见 [AsmrOneTree.normalize]。
     */
    @SerialName("mediaDownloadUrl") val mediaDownloadUrl: String? = null,
    @SerialName("mediaStreamUrl") val mediaStreamUrl: String? = null,
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

    /** /api/tracks/{id} 的解析：顶层是数组（作品根目录是第一个元素），也可能直接给对象。 */
    fun parseRoot(body: String, json: kotlinx.serialization.json.Json): AsmrOneTreeNode {
        val elem = json.parseToJsonElement(body)
        val raw = if (elem is kotlinx.serialization.json.JsonArray) {
            val list = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(AsmrOneTreeNode.serializer()),
                body,
            )
            if (list.size == 1) list[0] else AsmrOneTreeNode(children = list)
        } else {
            json.decodeFromString(AsmrOneTreeNode.serializer(), body)
        }
        return normalize(raw)
    }

    /**
     * 把树里每个文件节点的 source 补齐（相对作品根的路径）。
     *
     * 真实接口的文件节点经常既没有 source 也没有可用路径——这一度导致选择页
     * 「点文件没反应、点文件夹共 0 个」（source 全是 null，勾选集合永远为空）。
     * 这里按优先级给 source 兜底：
     *   1) 节点自带 source；
     *   2) mediaDownloadUrl / mediaStreamUrl 里 /api/media/{id}/ 之后的部分；
     *   3) 用父文件夹名逐级拼出相对路径（mp3/01.mp3），这正是 /api/media 要的形式。
     * 根节点自身的名字是作品文件夹（媒体路径相对它，不含它），所以从 children 起拼。
     */
    fun normalize(root: AsmrOneTreeNode): AsmrOneTreeNode {
        fun pathFromUrl(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val marker = "/api/media/"
            val at = url.indexOf(marker)
            if (at < 0) return null
            val rest = url.substring(at + marker.length)
            val slash = rest.indexOf('/')
            if (slash <= 0 || slash >= rest.length - 1) return null
            return rest.substring(slash + 1).substringBefore('?').trim('/').takeIf { it.isNotEmpty() }
        }

        fun walk(node: AsmrOneTreeNode, parentPath: String): AsmrOneTreeNode {
            val name = node.name?.trim()?.trim('/')?.takeIf { it.isNotEmpty() }
            val joined = when {
                parentPath.isEmpty() -> name.orEmpty()
                name == null -> parentPath
                else -> parentPath + "/" + name
            }
            val explicit = node.source?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() }
                ?: pathFromUrl(node.mediaDownloadUrl)
                ?: pathFromUrl(node.mediaStreamUrl)
            val isLeaf = node.children.isEmpty()
            val selfPath = explicit ?: joined.takeIf { it.isNotEmpty() && name != null }
            return node.copy(
                source = if (isLeaf) selfPath else explicit,
                children = node.children.map { walk(it, joined.takeIf { it.isNotEmpty() } ?: parentPath) },
            )
        }

        return root.copy(
            children = root.children.map { walk(it, "") },
            source = root.source,
        )
    }

    fun extOf(node: AsmrOneTreeNode): String? =
        (node.source ?: node.name)?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }

    fun isAudio(node: AsmrOneTreeNode): Boolean {
        if (node.children.isNotEmpty()) return false
        val ext = extOf(node) ?: return false
        val t = node.type?.lowercase()
        return when (t) {
            "audio" -> true
            "folder" -> false
            // 明确非音频的类型
            "image", "text", "pdf", "archive", "zip", "rar", "7z" -> false
            // null / "file" / 扩展名本身（"mp3"/"wav"…）/ "video"（asmr.one 上常见
            // 名为「视频1….mp3」的音轨）都按扩展名判断
            else -> ext in AUDIO_EXT
        }
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
