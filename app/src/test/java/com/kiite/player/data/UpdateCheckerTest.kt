package com.kiite.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    // —— 纯版本比较 ——
    @Test fun newerPatch() = assertTrue(UpdateChecker.isNewer("1.5.3", "1.5.2"))
    @Test fun olderIsNotNewer() = assertFalse(UpdateChecker.isNewer("1.5.1", "1.5.2"))
    @Test fun sameIsNotNewer() = assertFalse(UpdateChecker.isNewer("1.5.2", "1.5.2"))
    @Test fun minorBeatsPatch() = assertTrue(UpdateChecker.isNewer("1.6", "1.5.9"))
    @Test fun majorBeatsMinor() = assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
    @Test fun shorterVersionPadsWithZero() = assertFalse(UpdateChecker.isNewer("1.5", "1.5.0"))
    @Test fun stableBeatsOwnPrerelease() = assertTrue(UpdateChecker.isNewer("2.5.2", "2.5.2-beta1"))

    // —— 更新通道策略：正式版用户只收正式版 ——
    private fun should(latest: String, current: String, latestCode: Int = 0, currentCode: Int = 0, pre: Boolean = false) =
        UpdateChecker.shouldUpdate(latest, latestCode, pre, current, currentCode)

    @Test fun stableUserGetsNextStable() = assertTrue(should("2.5.2", "2.5.1"))
    @Test fun stableUserIgnoresBeta() = assertFalse(should("2.5.2-beta1", "2.5.1"))
    @Test fun stableUserIgnoresBetaEvenWithHigherCode() =
        assertFalse(should("2.6.0-beta1", "2.5.1", latestCode = 99, currentCode = 53))

    @Test fun betaUserGetsStable() = assertTrue(should("2.5.2", "2.5.2-beta1"))
    @Test fun betaUserGetsNextBeta() = assertTrue(should("2.5.2-beta2", "2.5.2-beta1"))
    @Test fun betaUserDoesNotDowngradeBeta() = assertFalse(should("2.5.2-beta1", "2.5.2-beta2"))
    @Test fun stableUserDoesNotDowngradeToBeta() = assertFalse(should("2.5.2-beta1", "2.5.2"))
    @Test fun noDowngradeToOlderStable() = assertFalse(should("2.5.1", "2.5.2"))
    @Test fun sameVersionIsNotUpdate() = assertFalse(should("2.5.2", "2.5.2"))

    // —— versionCode 优先 ——
    @Test fun versionCodeDrivesDecision() {
        assertTrue(should("2.5.2", "2.5.1", latestCode = 55, currentCode = 53))
        assertFalse(should("2.5.2", "2.5.1", latestCode = 53, currentCode = 53))
    }

    // —— 预发布标记来自 API 时同样拦 ——
    @Test fun apiPrereleaseFlagBlocksStableUser() =
        assertFalse(should("2.6.0", "2.5.1", pre = true))
}
