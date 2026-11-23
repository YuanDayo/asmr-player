package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首启引导的触发判断。
 *
 * 真出过的事故：老版本装过的用户（看过 5 页功能说明、但那时还没有条款页）升级后，
 * `seen == true && version == 0` 被判成「只补条款」，于是「首次下载直接落到免责声明页」。
 * 下面把每种组合钉死，防止再改回去。
 */
class OnboardingRulesTest {

    private val cur = 1

    @Test
    fun `盘还没读完时什么都不弹`() {
        assertEquals(OnboardingTrigger.NONE, onboardingTrigger(null, 0, cur))
    }

    @Test
    fun `新安装走完整流程`() {
        assertEquals(OnboardingTrigger.FULL, onboardingTrigger(false, 0, cur))
    }

    @Test
    fun `旧版本只走过功能页的也走完整流程`() {
        // 关键回归：seen=true 但 version=0 —— 条款从没读过，不能只补条款
        assertEquals(OnboardingTrigger.FULL, onboardingTrigger(true, 0, cur))
    }

    @Test
    fun `已经同意过当前版本的不再打扰`() {
        assertEquals(OnboardingTrigger.NONE, onboardingTrigger(true, cur, cur))
    }

    @Test
    fun `内容升版只补条款页`() {
        assertEquals(OnboardingTrigger.LEGAL_ONLY, onboardingTrigger(true, 1, 2))
    }

    @Test
    fun `版本比当前还新也不打扰`() {
        // 降级安装 / 备份来自更新的版本：不能把用户按回引导
        assertEquals(OnboardingTrigger.NONE, onboardingTrigger(true, 3, 2))
    }
}
