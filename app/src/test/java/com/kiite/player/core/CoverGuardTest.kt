package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 封面尺寸守卫。
 *
 * 背景：DLsite 在没有会话 / 没有 Referer 时会把封面请求重定向成一张站内横幅，
 * 地址完全正确、内容却是宣传图。只靠 URL 判断永远发现不了，只能看真实尺寸。
 */
class CoverGuardTest {

    @Test
    fun normalCoverPasses() {
        // DLsite 常见封面：约 4:3、3:4、1:1
        assertEquals(CoverGuard.Verdict.OK, CoverGuard.judge(560, 420))
        assertEquals(CoverGuard.Verdict.OK, CoverGuard.judge(600, 800))
        assertEquals(CoverGuard.Verdict.OK, CoverGuard.judge(512, 512))
        assertEquals(CoverGuard.Verdict.OK, CoverGuard.judge(1600, 1200))
    }

    @Test
    fun siteBannerIsRejected() {
        // 「二次元総合ダウンロードショップ DLsite」那张横幅的典型尺寸
        assertEquals(CoverGuard.Verdict.BANNER, CoverGuard.judge(1600, 400))
        assertEquals(CoverGuard.Verdict.BANNER, CoverGuard.judge(1200, 300))
        assertEquals(CoverGuard.Verdict.BANNER, CoverGuard.judge(400, 1600))
    }

    @Test
    fun aspectThresholdBoundary() {
        // 2.2 是阈值：2.2 以内算正常，超过才算横幅
        assertEquals(CoverGuard.Verdict.OK, CoverGuard.judge(220, 100))
        assertEquals(CoverGuard.Verdict.BANNER, CoverGuard.judge(230, 100))
    }

    @Test
    fun tinyImageIsRejected() {
        assertEquals(CoverGuard.Verdict.TOO_SMALL, CoverGuard.judge(64, 64))
        assertEquals(CoverGuard.Verdict.TOO_SMALL, CoverGuard.judge(95, 400))
    }

    @Test
    fun unreadableWhenNoDimensions() {
        // 解不出尺寸 = 下回来的不是图片（常见于被重定向到 HTML 年龄确认页）
        assertEquals(CoverGuard.Verdict.UNREADABLE, CoverGuard.judge(0, 0))
        assertEquals(CoverGuard.Verdict.UNREADABLE, CoverGuard.judge(-1, 500))
    }

    @Test
    fun reasonMentionsRealSize() {
        assertEquals(true, CoverGuard.reason(1600, 400).contains("1600×400"))
        assertEquals(true, CoverGuard.reason(0, 0).contains("不是图片"))
    }
}
