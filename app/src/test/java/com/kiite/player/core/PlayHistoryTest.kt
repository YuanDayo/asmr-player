package com.kiite.player.core

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放历史纯逻辑测试。
 *
 * 重点在「什么时候算听完」「续播该从哪儿开始」「记录会不会自己重排」这三件事，
 * 它们出错时的表现都很隐蔽：列表跳、点进去停在片尾、听完的还挂着「继续」。
 */
class PlayHistoryTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val t0 = 1_760_000_000_000L

    private fun rec(
        path: String,
        pos: Long = 0L,
        dur: Long = 600_000L,
        at: Long = t0,
        finished: Long = 0L,
    ) = HistoryRecord(
        trackPath = path,
        trackName = path.substringAfterLast('/'),
        positionMs = pos,
        durationMs = dur,
        lastPlayedAtMs = at,
        finishedAtMs = finished,
    )

    // ---------- 开始播放 ----------

    @Test
    fun `首次播放插到最前面`() {
        val list = HistoryMath.onStart(emptyList(), "/a/01.mp3", "01", "/a", "A", 0L, 600_000L, t0)
        assertEquals(1, list.size)
        assertEquals("/a/01.mp3", list[0].trackPath)
        assertEquals(1, list[0].playedCount)
        assertEquals(t0, list[0].lastPlayedAtMs)
    }

    @Test
    fun `重复播放同一集只占一条且次数加一`() {
        val first = HistoryMath.onStart(emptyList(), "/a/01.mp3", "01", "/a", "A", 0L, 600_000L, t0)
        val second = HistoryMath.onStart(first, "/a/01.mp3", "01", "/a", "A", 120_000L, 600_000L, t0 + 1_000L)
        assertEquals(1, second.size)
        assertEquals(2, second[0].playedCount)
        assertEquals(120_000L, second[0].positionMs)
    }

    @Test
    fun `重新开播会清掉已听完标记`() {
        val finished = listOf(rec("/a/01.mp3", pos = 600_000L, finished = t0))
        val again = HistoryMath.onStart(finished, "/a/01.mp3", "01", "/a", "A", 0L, 600_000L, t0 + 5_000L)
        assertFalse(again[0].finished)
        assertEquals(0L, again[0].finishedAtMs)
    }

    @Test
    fun `开播时置顶但不丢其它记录`() {
        val list = listOf(rec("/a/01.mp3", at = t0), rec("/a/02.mp3", at = t0 - 1_000L))
        val next = HistoryMath.onStart(list, "/a/02.mp3", "02", "/a", "A", 0L, 600_000L, t0 + 1_000L)
        assertEquals(2, next.size)
        assertEquals("/a/02.mp3", next[0].trackPath)
        assertEquals("/a/01.mp3", next[1].trackPath)
    }

    // ---------- 播放中的位置同步 ----------

    @Test
    fun `位置同步不改变列表顺序`() {
        val list = listOf(
            rec("/a/01.mp3", at = t0),
            rec("/a/02.mp3", at = t0 - 1_000L),
        )
        // 更新的是第二条，它不应该被顶到最前
        val next = HistoryMath.onProgress(list, "/a/02.mp3", 300_000L, 600_000L, t0 + 2_000L)
        assertEquals("/a/01.mp3", next[0].trackPath)
        assertEquals("/a/02.mp3", next[1].trackPath)
        assertEquals(300_000L, next[1].positionMs)
    }

    @Test
    fun `位置同步对不存在的记录不产生新条目`() {
        val list = listOf(rec("/a/01.mp3"))
        val next = HistoryMath.onProgress(list, "/a/99.mp3", 10_000L, 600_000L, t0)
        assertEquals(1, next.size)
        assertEquals("/a/01.mp3", next[0].trackPath)
    }

    @Test
    fun `时长未知时保留上一次已知时长`() {
        val list = listOf(rec("/a/01.mp3", pos = 10_000L, dur = 600_000L))
        val next = HistoryMath.onProgress(list, "/a/01.mp3", 20_000L, 0L, t0)
        assertEquals(600_000L, next[0].durationMs)
    }

    // ---------- 听完 ----------

    @Test
    fun `播完把位置顶到结尾并标记完成`() {
        val list = listOf(rec("/a/01.mp3", pos = 598_000L, dur = 600_000L))
        val next = HistoryMath.onFinished(list, "/a/01.mp3", t0 + 3_000L)
        assertEquals(600_000L, next[0].positionMs)
        assertTrue(next[0].finished)
        assertEquals(t0 + 3_000L, next[0].finishedAtMs)
    }

    @Test
    fun `距结尾三秒内算听完，差一毫秒不算`() {
        // 600000 - 3000 = 597000：恰好等于阈值算听完
        assertTrue(HistoryMath.isFinished(597_000L, 600_000L))
        assertFalse(HistoryMath.isFinished(596_999L, 600_000L))
        assertFalse(HistoryMath.isFinished(600_000L, 0L))
    }

    @Test
    fun `进度比例不会越界`() {
        assertEquals(1f, rec("/a/01.mp3", pos = 900_000L, dur = 600_000L).progress, 0.0001f)
        assertEquals(0f, rec("/a/01.mp3", pos = 100L, dur = 0L).progress, 0.0001f)
    }

    // ---------- 续播位置 ----------

    @Test
    fun `续播位置：听完过就从零开始`() {
        assertEquals(0L, HistoryMath.resumePositionOf(rec("/a/01.mp3", pos = 600_000L, finished = t0)))
    }

    @Test
    fun `续播位置：只听到开头几秒也从零开始`() {
        assertEquals(0L, HistoryMath.resumePositionOf(rec("/a/01.mp3", pos = 3_999L)))
        assertEquals(4_000L, HistoryMath.resumePositionOf(rec("/a/01.mp3", pos = 4_000L)))
    }

    @Test
    fun `续播位置：没有记录返回零`() {
        assertEquals(0L, HistoryMath.resumePositionOf(null))
    }

    // ---------- 淘汰与删除 ----------

    @Test
    fun `超出上限时淘汰最旧的`() {
        val many = (1..205).map { i -> rec("/a/$i.mp3", at = t0 + i * 1_000L) }
        val pruned = HistoryMath.prune(many)
        assertEquals(HistoryMath.MAX_RECORDS, pruned.size)
        // 最旧的 1..5 被淘汰
        assertTrue(pruned.none { it.trackPath == "/a/1.mp3" })
        assertTrue(pruned.any { it.trackPath == "/a/205.mp3" })
        // 按时间倒序
        assertEquals("/a/205.mp3", pruned[0].trackPath)
    }

    @Test
    fun `删除只影响指定路径`() {
        val list = listOf(rec("/a/01.mp3"), rec("/a/02.mp3"))
        val next = HistoryMath.remove(list, "/a/01.mp3")
        assertEquals(1, next.size)
        assertEquals("/a/02.mp3", next[0].trackPath)
    }

    // ---------- 累计收听时长 ----------

    @Test
    fun `收听时长同时累加到今日和总计`() {
        val (daily, total) = HistoryMath.addListened(emptyMap(), 0L, 30_000L, t0, zone)
        assertEquals(30_000L, total)
        assertEquals(30_000L, HistoryMath.todayMs(daily, t0, zone))
    }

    @Test
    fun `跨天分开统计但总计继续累加`() {
        val (d1, t1) = HistoryMath.addListened(emptyMap(), 0L, 60_000L, t0, zone)
        val (d2, t2) = HistoryMath.addListened(d1, t1, 60_000L, t0 + 24L * 3_600_000L, zone)
        assertEquals(120_000L, t2)
        assertEquals(60_000L, HistoryMath.todayMs(d2, t0, zone))
        assertEquals(60_000L, HistoryMath.todayMs(d2, t0 + 24L * 3_600_000L, zone))
    }

    @Test
    fun `零或负的时长不写入`() {
        val (daily, total) = HistoryMath.addListened(mapOf("2026-01-01" to 5L), 5L, 0L, t0, zone)
        assertEquals(5L, total)
        assertEquals(1, daily.size)
    }

    // ---------- 时段分布 / 区间汇总（统计页用） ----------

    @Test
    fun `按小时累加且跨小时分开`() {
        val h1 = HistoryMath.addToHour(emptyMap(), 60_000L, t0, zone)
        val h2 = HistoryMath.addToHour(h1, 30_000L, t0, zone)
        val h3 = HistoryMath.addToHour(h2, 10_000L, t0 + 3_600_000L, zone)
        val hour = java.time.Instant.ofEpochMilli(t0).atZone(zone).hour
        assertEquals(90_000L, HistoryMath.hourMs(h3, hour))
        assertEquals(10_000L, HistoryMath.hourMs(h3, (hour + 1) % 24))
        assertEquals(0L, HistoryMath.hourMs(h3, (hour + 5) % 24))
    }

    @Test
    fun `零时长不进时段表`() {
        val h = HistoryMath.addToHour(mapOf("3" to 5L), 0L, t0, zone)
        assertEquals(1, h.size)
    }

    @Test
    fun `区间合计包含两端`() {
        val daily = mapOf(
            "2026-03-01" to 10L,
            "2026-03-02" to 20L,
            "2026-03-03" to 30L,
        )
        val d1 = java.time.LocalDate.of(2026, 3, 1)
        val d3 = java.time.LocalDate.of(2026, 3, 3)
        assertEquals(60L, HistoryMath.sumRange(daily, d1, d3))
        assertEquals(30L, HistoryMath.sumRange(daily, d1, java.time.LocalDate.of(2026, 3, 2)))
        // 反了就返回 0，不抛异常
        assertEquals(0L, HistoryMath.sumRange(daily, d3, d1))
    }

    @Test
    fun `有记录的天数不算空天`() {
        val days = HistoryMath.listenedDays(
            mapOf("2026-03-02" to 20L, "2026-03-01" to 10L, "2026-03-03" to 0L),
        )
        assertEquals(listOf(java.time.LocalDate.of(2026, 3, 1), java.time.LocalDate.of(2026, 3, 2)), days)
    }

    @Test
    fun `一周从周一开始`() {
        // 2026-03-04 是周三
        val wed = java.time.LocalDate.of(2026, 3, 4)
        assertEquals(java.time.LocalDate.of(2026, 3, 2), HistoryMath.weekStart(wed))
        // 周日归到同一周，不跳到下一周
        val sun = java.time.LocalDate.of(2026, 3, 8)
        assertEquals(java.time.LocalDate.of(2026, 3, 2), HistoryMath.weekStart(sun))
    }

    // ---------- 显示 ----------

    @Test
    fun `相对时间从刚刚到具体日期`() {
        assertEquals("刚刚", HistoryMath.relativeTime(t0, t0, zone))
        assertEquals("5 分钟前", HistoryMath.relativeTime(t0, t0 - 5 * 60_000L, zone))
        assertEquals("3 小时前", HistoryMath.relativeTime(t0, t0 - 3 * 3_600_000L, zone))
        assertEquals("昨天", HistoryMath.relativeTime(t0, t0 - 30 * 3_600_000L, zone))
        assertEquals("3 天前", HistoryMath.relativeTime(t0, t0 - 3 * 24 * 3_600_000L, zone))
        assertEquals("—", HistoryMath.relativeTime(t0, 0L, zone))
    }

    @Test
    fun `时长文案`() {
        assertEquals("0 分钟", HistoryMath.formatDuration(0L))
        assertEquals("不到 1 分钟", HistoryMath.formatDuration(30_000L))
        assertEquals("12 分钟", HistoryMath.formatDuration(12 * 60_000L))
        assertEquals("1 小时", HistoryMath.formatDuration(60 * 60_000L))
        assertEquals("3 小时 20 分", HistoryMath.formatDuration((3 * 60 + 20) * 60_000L))
    }

    @Test
    fun `同一天判断`() {
        assertTrue(HistoryMath.isSameDay(t0, t0 + 1_000L, zone))
        assertFalse(HistoryMath.isSameDay(t0, t0 + 24L * 3_600_000L, zone))
    }
}

/**
 * 书签纯逻辑测试。
 *
 * 最容易出问题的是「重复点加书签」和「合并时备注被空值覆盖」，
 * 以及分组顺序：用户新加的书签应该出现在最上面。
 */
class BookmarkTest {

    private val t0 = 1_760_000_000_000L

    private fun bm(
        id: String,
        path: String = "/a/01.mp3",
        pos: Long,
        note: String = "",
        at: Long = t0,
    ) = Bookmark(
        id = id,
        trackPath = path,
        trackName = path.substringAfterLast('/'),
        positionMs = pos,
        note = note,
        createdAtMs = at,
    )

    @Test
    fun `第一条书签直接加入`() {
        val out = BookmarkMath.add(emptyList(), bm("1", pos = 30_000L))
        assertFalse(out.merged)
        assertEquals(1, out.bookmarks.size)
    }

    @Test
    fun `一点五秒内重复添加会合并而不是堆两条`() {
        val list = listOf(bm("1", pos = 30_000L))
        val out = BookmarkMath.add(list, bm("2", pos = 31_000L))
        assertTrue(out.merged)
        assertEquals(1, out.bookmarks.size)
        assertEquals("1", out.bookmarks[0].id)
        assertEquals(31_000L, out.bookmarks[0].positionMs)
    }

    @Test
    fun `合并时保留原有备注`() {
        val list = listOf(bm("1", pos = 30_000L, note = "这里开始下雨"))
        val out = BookmarkMath.add(list, bm("2", pos = 30_500L, note = ""))
        assertEquals("这里开始下雨", out.bookmarks[0].note)
    }

    @Test
    fun `合并时新备注非空则覆盖`() {
        val list = listOf(bm("1", pos = 30_000L, note = "旧的"))
        val out = BookmarkMath.add(list, bm("2", pos = 30_500L, note = "新的"))
        assertEquals("新的", out.bookmarks[0].note)
    }

    @Test
    fun `超过容差就是两条`() {
        val list = listOf(bm("1", pos = 30_000L))
        val out = BookmarkMath.add(list, bm("2", pos = 32_000L))
        assertFalse(out.merged)
        assertEquals(2, out.bookmarks.size)
    }

    @Test
    fun `不同曲目的相同位置不合并`() {
        val list = listOf(bm("1", path = "/a/01.mp3", pos = 30_000L))
        val out = BookmarkMath.add(list, bm("2", path = "/a/02.mp3", pos = 30_000L))
        assertFalse(out.merged)
        assertEquals(2, out.bookmarks.size)
    }

    @Test
    fun `超出总数上限时淘汰最早创建的`() {
        val list = (1..3).map { bm("$it", pos = it * 10_000L, at = t0 + it) }
        val out = BookmarkMath.add(list, bm("4", pos = 90_000L, at = t0 + 100), max = 3)
        assertEquals(3, out.bookmarks.size)
        assertTrue(out.bookmarks.none { it.id == "1" })
        assertTrue(out.bookmarks.any { it.id == "4" })
    }

    @Test
    fun `编辑备注会去掉首尾空格`() {
        val next = BookmarkMath.updateNote(listOf(bm("1", pos = 0L)), "1", "   台词很好   ")
        assertEquals("台词很好", next[0].note)
    }

    @Test
    fun `删除指定 id`() {
        val list = listOf(bm("1", pos = 0L), bm("2", pos = 60_000L))
        val next = BookmarkMath.remove(list, "1")
        assertEquals(1, next.size)
        assertEquals("2", next[0].id)
    }

    @Test
    fun `某一集的书签按时间点升序`() {
        val list = listOf(
            bm("1", path = "/a/01.mp3", pos = 90_000L),
            bm("2", path = "/a/02.mp3", pos = 10_000L),
            bm("3", path = "/a/01.mp3", pos = 30_000L),
        )
        val mine = BookmarkMath.ofTrack(list, "/a/01.mp3")
        assertEquals(2, mine.size)
        assertEquals(listOf(30_000L, 90_000L), mine.map { it.positionMs })
    }

    @Test
    fun `最近书签在容差外返回空`() {
        val list = listOf(bm("1", pos = 30_000L))
        assertNotNull(BookmarkMath.nearestOfTrack(list, "/a/01.mp3", 30_800L))
        assertNull(BookmarkMath.nearestOfTrack(list, "/a/01.mp3", 40_000L))
    }

    @Test
    fun `多个候选时取最近的`() {
        val list = listOf(
            bm("1", pos = 30_000L),
            bm("2", pos = 31_000L),
        )
        assertEquals("2", BookmarkMath.nearestOfTrack(list, "/a/01.mp3", 30_900L)?.id)
    }

    @Test
    fun `分组按最近创建倒序，组内按位置升序`() {
        val list = listOf(
            bm("1", path = "/a/01.mp3", pos = 90_000L, at = t0),
            bm("2", path = "/a/01.mp3", pos = 10_000L, at = t0 + 10),
            bm("3", path = "/a/02.mp3", pos = 50_000L, at = t0 - 10),
        )
        val groups = BookmarkMath.grouped(list)
        assertEquals("/a/01.mp3", groups[0].first)
        assertEquals(listOf(10_000L, 90_000L), groups[0].second.map { it.positionMs })
        assertEquals("/a/02.mp3", groups[1].first)
    }
}
