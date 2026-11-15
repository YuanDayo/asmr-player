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
    /** 旧版/自建镜像可能给 name；kikoeru 官方用 title。 */
    val name: String? = null,
    val title: String? = null,
    /** folder / audio / image / text / other */
    val type: String? = null,
    /**
     * kikoeru 的媒体标识：`作品id/序号`，如 `12345/0`。
     * 下载地址就是 /api/media/download/{hash}（见 [AsmrOneClient.urlFor]）。
     * 真实响应里**没有 source 字段**，路径只能靠 hash 或 mediaDownloadUrl 拿到。
     */
    val hash: String? = null,
    /**
     * 归一化后写入的「下载地址说明」（见 [AsmrOneTree.normalize]）：
     * 绝对 URL / 以 / 开头的 API 路径 / `@hash:12345/0` / 旧式相对路径。
     */
    val source: String? = null,
    @SerialName("mediaDownloadUrl") val mediaDownloadUrl: String? = null,
    @SerialName("mediaStreamUrl") val mediaStreamUrl: String? = null,
    @SerialName("workTitle") val workTitle: String? = null,
    val children: List<AsmrOneTreeNode> = emptyList(),
    /** 归一化后写入：落盘用的相对路径（如 mp3/01.mp3）。 */
    val relPath: String? = null,
) {
    /** 界面显示/落盘用的名字（真实响应只有 title）。 */
    val displayName: String
        get() = (title ?: name)?.takeIf { it.isNotBlank() } ?: (hash ?: "")
}

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

    /** 标记「下载地址 = /api/media/download/{hash}」，见 [AsmrOneClient.urlFor]。 */
    const val HASH_TAG = "@hash:"

    /** /api/tracks/{id} 的解析：顶层是数组（作品下的顶级条目），也可能直接给对象。 */
    fun parseRoot(body: String, json: kotlinx.serialization.json.Json): AsmrOneTreeNode {
        val elem = json.parseToJsonElement(body)
        val raw = if (elem is kotlinx.serialization.json.JsonArray) {
            // kikoeru 的 toTree() 直接返回数组：作品目录里的顶级文件/文件夹，没有作品根节点。
            // 统一包一层合成根，后面拼路径时不会把作品目录名混进去。
            AsmrOneTreeNode(
                children = json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(AsmrOneTreeNode.serializer()),
                    body,
                ),
            )
        } else {
            json.decodeFromString(AsmrOneTreeNode.serializer(), body)
        }
        return normalize(raw)
    }

    /**
     * 归一化整棵树：给每个文件节点补出**下载地址说明**（source）和**落盘相对路径**（relPath）。
     *
     * 真实接口（kikoeru-express filesystem/utils.js 的 toTree）：
     *   文件夹 {"type":"folder","title":"mp3","children":[...]}
     *   文件   {"type":"audio","title":"01.mp3","hash":"12345/0",
     *           "mediaDownloadUrl":"/api/media/download/12345/0", ...}
     * 也就是说**没有 source、没有 name**——之前只认这两个字段，导致叶子节点路径全为 null，
     * 表现就是「文件点不动、文件夹共 0 个」。这里按优先级兜底：
     *   1) mediaDownloadUrl / mediaStreamUrl（绝对 URL 或 /api/... 路径）；
     *   2) hash → `@hash:12345/0`；
     *   3) 节点自带 source；
     *   4) 父子文件夹名拼出的相对路径。
     */
    fun normalize(root: AsmrOneTreeNode): AsmrOneTreeNode {
        fun walk(node: AsmrOneTreeNode, parentRel: String): AsmrOneTreeNode {
            val name = node.displayName.trim().trim('/').takeIf { it.isNotEmpty() }
            val rel = when {
                parentRel.isEmpty() -> name.orEmpty()
                name == null -> parentRel
                else -> parentRel + "/" + name
            }
            val url = node.mediaDownloadUrl?.trim()?.takeIf { it.isNotEmpty() }
                ?: node.mediaStreamUrl?.trim()?.takeIf { it.isNotEmpty() }
            val explicit = node.source?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() }
            val hash = node.hash?.trim()?.takeIf { it.isNotEmpty() }
            val spec: String? = when {
                url != null && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/")) -> url
                explicit != null -> explicit
                hash != null -> HASH_TAG + hash
                rel.isNotEmpty() -> rel
                else -> null
            }
            val isLeaf = node.children.isEmpty()
            return node.copy(
                source = if (isLeaf) spec else null,
                relPath = if (isLeaf) rel.takeIf { it.isNotEmpty() } else null,
                children = node.children.map { walk(it, rel.ifEmpty { parentRel }) },
            )
        }
        return root.copy(
            children = root.children.map { walk(it, "") },
            source = root.source,
            relPath = null,
        )
    }

    fun extOf(node: AsmrOneTreeNode): String? =
        (node.title ?: node.name ?: node.relPath)?.substringAfterLast('.', "")
            ?.lowercase()?.takeIf { it.isNotBlank() }

    fun isAudio(node: AsmrOneTreeNode): Boolean {
        if (node.children.isNotEmpty()) return false
        val ext = extOf(node) ?: return false
        val t = node.type?.lowercase()
        return when (t) {
            "audio" -> true
            "folder", "image", "text", "other", "pdf", "archive" -> false
            // null / "file" / 扩展名本身（"mp3"/"wav"…）/ "video"（asmr.one 上常见
            // 名为「视频1….mp3」的音轨）都按扩展名判断
            else -> ext in AUDIO_EXT
        }
    }

    /** 递归收集全部叶子节点。 */
    fun leaves(root: AsmrOneTreeNode): List<AsmrOneTreeNode> {
        if (root.children.isEmpty()) {
            return if (root.source != null || root.displayName.isNotEmpty()) listOf(root) else emptyList()
        }
        return root.children.flatMap { leaves(it) }
    }

    fun audioFiles(root: AsmrOneTreeNode): List<AsmrOneTreeNode> {
        val files = leaves(root).filter { it.source != null && isAudio(it) }
        if (files.isEmpty()) return files
        // 目录路径 = 落盘相对路径去掉最后一段文件名（source 可能是 hash，不能用来分组）
        return files.groupBy { (it.relPath ?: it.displayName).substringBeforeLast('/', "") }
            .flatMap { (_, group) ->
                val byExt = group.groupBy { extOf(it) }
                val chosen = PREFER.firstOrNull { byExt.containsKey(it) }
                if (chosen == null) group else byExt[chosen].orEmpty()
            }
    }
}
