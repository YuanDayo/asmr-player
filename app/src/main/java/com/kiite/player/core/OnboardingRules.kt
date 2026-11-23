package com.kiite.player.core

/** 首启引导这次该弹哪一档。 */
enum class OnboardingTrigger {
    /** 什么都不弹：已经走完当前内容版本 / 盘还没读到。 */
    NONE,

    /** 功能页 + 条款页都走。 */
    FULL,

    /** 只补条款页（老用户已经看过功能页，只是条款内容升了一版）。 */
    LEGAL_ONLY,
}

/**
 * 「要不要弹引导、弹哪一段」的唯一判断。
 *
 * 三个输入缺一不可，因为三种状态长得太像：
 *  - `seen == null`：DataStore 还没读完，此时**绝不能**弹（否则老用户每次冷启动被盖一帧）；
 *  - `seen == false`：新安装 / 上次引导中途被杀，走完整流程；
 *  - `seen == true && version == 0`：**旧版本装过**的用户 —— 只看过功能页，条款从没读过。
 *    这里必须走完整流程。以前这里判成 `LEGAL_ONLY`，表现就是「首次下载直接落到条款页」，
 *    用户根本没看到 5 页功能说明，却已经要为免责声明勾选。
 *  - `seen == true && 0 < version < 当前版本`：真的只是引导内容升级（比如又加了一步），
 *    只补新增的那一段，别把人按回第 1 页重看 5 屏。
 *
 * 抽成纯函数是为了能被单测覆盖：判断写在 Compose 的 `LaunchedEffect` 里时，
 * 这几种组合只能靠手装三种数据去试。
 */
fun onboardingTrigger(seen: Boolean?, version: Int, currentVersion: Int): OnboardingTrigger = when {
    seen == null -> OnboardingTrigger.NONE
    !seen -> OnboardingTrigger.FULL
    version <= 0 -> OnboardingTrigger.FULL
    version < currentVersion -> OnboardingTrigger.LEGAL_ONLY
    else -> OnboardingTrigger.NONE
}
