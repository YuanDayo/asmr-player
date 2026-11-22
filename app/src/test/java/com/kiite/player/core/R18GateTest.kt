package com.kiite.player.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「隐藏 R18」的唯一判定。
 *
 * 这条判定被曲库列表、首页建议、随机播放、社团/声优聚合共用，
 * 所以它的边界（开关、主动筛成人、未分类）必须钉死 —— 任何一处跑偏都会变成
 * 「首页还能看到成人作品」这类回调。
 */
class R18GateTest {

    @Test
    fun `开关关闭时什么都不隐藏`() {
        assertFalse(hidesR18(hideR18 = false, rating = WorkRating.R18))
        assertFalse(hidesR18(hideR18 = false, rating = WorkRating.ALL))
        assertFalse(hidesR18(hideR18 = false, rating = WorkRating.UNKNOWN))
    }

    @Test
    fun `开关打开只隐藏成人`() {
        assertTrue(hidesR18(hideR18 = true, rating = WorkRating.R18))
        assertFalse(hidesR18(hideR18 = true, rating = WorkRating.ALL))
        // 没识别、也没手动标过：不隐藏（否则未分类作品会整批消失）
        assertFalse(hidesR18(hideR18 = true, rating = WorkRating.UNKNOWN))
    }

    @Test
    fun `主动筛成人时不受开关限制`() {
        assertFalse(hidesR18(hideR18 = true, rating = WorkRating.R18, forceShowR18 = true))
    }
}
