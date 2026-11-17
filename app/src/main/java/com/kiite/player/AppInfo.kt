package com.kiite.player

/** 作者与开源信息，设置页「关于」分区展示。 */
object AppInfo {
    const val AUTHOR = "@Lipal_Desu"
    const val REPO_URL = "https://github.com/YuanDayo/asmr-player"
    const val EMAIL = "yuan_space123@163.com"
    const val BILIBILI_UID = "636898526"
    const val BILIBILI_URL = "https://space.bilibili.com/636898526"

    /**
     * 版本号的唯一读取口，值来自 `app/build.gradle.kts` 顶部的 `appVersionName`。
     *
     * 这里**不要**再写死字符串：此前硬编码为 "2.5.0-beta2"，导致设置页/更新日志标题
     * 显示的版本与实际安装包对不上（见 docs/RELEASE-SPEC.md 第 1 节）。
     */
    val VERSION_NAME: String = BuildConfig.VERSION_NAME

    /** 版本序号，同样来自 Gradle；比较新旧时优先用它，再退回 SemVer。 */
    val VERSION_CODE: Int = BuildConfig.VERSION_CODE

    /** 构建类型（debug / release），「关于」页附带展示，便于分辨自测包。 */
    val BUILD_TYPE: String = BuildConfig.BUILD_TYPE
}
