package com.kiite.player.data

import com.kiite.player.core.DlsitePurchase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 备份 / 恢复的本地文件格式。
 *
 * 一份备份 = 设置 + DLsite 登录态（Cookie）+ DLsite 已购清单 + 「之前有哪些作品」。
 * 换手机或清数据后导入这份文件，就能把设置和登录状态原样恢复，并按作品清单从
 * asmr.one / DLsite 批量补下。
 *
 * 注意：文件里含登录 Cookie（等价于「带着已登录状态的小文件」），务必当密码一样保管。
 */
@Serializable
data class BackupFile(
    /** 格式版本：以后字段变了靠它做迁移。 */
    val format: Int = 1,
    val exportedAt: Long = 0L,
    val appVersion: String = "",
    val settings: AppSettings = AppSettings(),
    /** URL → 该站点下的 Cookie 头（仅 DLsite 及其子域）。 */
    val dlsiteCookies: Map<String, String> = emptyMap(),
    /** DLsite 已购作品清单。 */
    val dlsitePurchases: List<DlsitePurchase> = emptyList(),
    /** 之前有的作品（用于换机后批量补下）。 */
    val works: List<BackupWork> = emptyList(),
)

/** 一条「之前有的作品」：source 只取 "dlsite" / "asmr.one"。 */
@Serializable
data class BackupWork(
    val source: String,
    val code: String,
    val title: String = "",
    /** asmr.one 的作品 id（记录过才有）；DLsite 作品为 null。 */
    val workId: Long? = null,
)

object BackupStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile =
        json.decodeFromString(BackupFile.serializer(), text)
}
