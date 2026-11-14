package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按行书签：行号判重、开关、指纹失效判断。
 *
 * 这些是「台本改了之后书签会不会乱指」的全部判断依据，所以单独一组测。
 */
class LineBookmarkTest {

    private val t0 = 1_760_000_000_000L

    private fun lineBm(
        id: String,
        line: Int,
        path: String = "/a/01.mp3",
        pos: Long = 10_000L,
        fingerprint: String = "10:abc",
        text: String = "第 ${line + 1} 句",
        at: Long = t0,
    ) = Bookmark(
        id = id,
        trackPath = path,
        trackName = path.substringAfterLast('/'),
        positionMs = pos,
        createdAtMs = at,
        lineIndex = line,
        lineText = text,
        scriptFingerprint = fingerprint,
    )

    private fun timeBm(id: String, pos: Long, path: String = "/a/01.mp3") = Bookmark(
        id = id,
        trackPath = path,
        positionMs = pos,
        createdAtMs = t0,
    )

    @Test
    fun `lineIndex 非负即为按行书签`() {
        assertTrue(lineBm("1", line = 0).isLine)
        assertFalse(timeBm("2", pos = 1_000L).isLine)
    }

    @Test
    fun `同一行重复添加会合并，位置跟着新值走`() {
        val first = BookmarkMath.add(emptyList(), lineBm("1", line = 3, pos = 10_000L))
        val second = BookmarkMath.add(first.bookmarks, lineBm("2", line = 3, pos = 12_000L))
        assertTrue(second.merged)
        assertEquals(1, second.bookmarks.size)
        assertEquals(12_000L, second.bookmarks.first().positionMs)
        // 合并保留原来的 id，界面上的动画/状态不会因为换个 id 而闪一下
        assertEquals("1", second.bookmarks.first().id)
    }

    @Test
    fun `相邻的两句即使时间几乎相同也是两条`() {
        val list = BookmarkMath.add(
            emptyList(),
            lineBm("1", line = 3, pos = 10_000L),
        ).bookmarks
        // 时间只差 200ms，但行号不同：这是用户刻意标了两句，不能按「位置接近」合并
        val out = BookmarkMath.add(list, lineBm("2", line = 4, pos = 10_200L))
        assertFalse(out.merged)
        assertEquals(2, out.bookmarks.size)
    }

    @Test
    fun `按行书签不会吃掉附近的时间点书签`() {
        val list = listOf(timeBm("t1", pos = 10_100L))
        val out = BookmarkMath.add(list, lineBm("l1", line = 3, pos = 10_000L))
        assertFalse(out.merged)
        assertEquals(2, out.bookmarks.size)
    }

    @Test
    fun `时间点书签不会吃掉附近的按行书签`() {
        val list = listOf(lineBm("l1", line = 3, pos = 10_000L))
        val out = BookmarkMath.add(list, timeBm("t1", pos = 10_100L))
        assertFalse(out.merged)
        assertEquals(2, out.bookmarks.size)
    }

    @Test
    fun `开关第一次点亮第二次熄灭`() {
        val on = BookmarkMath.toggleLine(emptyList(), lineBm("1", line = 2))
        assertTrue(on.added)
        assertEquals(1, on.bookmarks.size)

        val off = BookmarkMath.toggleLine(on.bookmarks, lineBm("2", line = 2))
        assertFalse(off.added)
        assertTrue(off.bookmarks.isEmpty())
    }

    @Test
    fun `开关只影响命中那一行`() {
        val list = BookmarkMath.add(emptyList(), lineBm("1", line = 2)).bookmarks
        val off = BookmarkMath.toggleLine(list, lineBm("2", line = 2))
        assertTrue(off.bookmarks.isEmpty())
        // 换一行去关，什么都不会发生（列表原样返回）
        val untouched = BookmarkMath.toggleLine(list, lineBm("3", line = 5))
        assertTrue(untouched.added)
        assertEquals(2, untouched.bookmarks.size)
    }

    @Test
    fun `ofLine 只认同一集的同一行`() {
        val list = listOf(lineBm("1", line = 2, path = "/a/01.mp3"))
        assertEquals("1", BookmarkMath.ofLine(list, "/a/01.mp3", 2)?.id)
        assertNull(BookmarkMath.ofLine(list, "/a/02.mp3", 2))
        assertNull(BookmarkMath.ofLine(list, "/a/01.mp3", 3))
    }

    @Test
    fun `lineIndexesOf 只返回按行书签且按曲目过滤`() {
        val list = listOf(
            lineBm("1", line = 0),
            lineBm("2", line = 5),
            timeBm("3", pos = 8_000L),
            lineBm("4", line = 7, path = "/a/02.mp3"),
        )
        assertEquals(setOf(0, 5), BookmarkMath.lineIndexesOf(list, "/a/01.mp3"))
    }

    @Test
    fun `指纹对内容与行数都敏感`() {
        val a = listOf("第一句", "第二句", "第三句")
        val same = listOf("第一句", "第二句", "第三句")
        val changed = listOf("第一句", "第二句改了", "第三句")
        val shorter = listOf("第一句", "第二句")

        assertEquals(BookmarkMath.fingerprint(a), BookmarkMath.fingerprint(same))
        assertTrue(BookmarkMath.fingerprint(a) != BookmarkMath.fingerprint(changed))
        assertTrue(BookmarkMath.fingerprint(a) != BookmarkMath.fingerprint(shorter))
    }

    @Test
    fun `空台本的指纹为空串`() {
        assertEquals("", BookmarkMath.fingerprint(emptyList()))
    }

    @Test
    fun `行号在指纹变化后判定为失效`() {
        val bm = lineBm("1", line = 3, fingerprint = "10:abc")
        assertTrue(BookmarkMath.lineStale(bm, "11:def"))
        assertFalse(BookmarkMath.lineStale(bm, "10:abc"))
        // 没有记录指纹的老书签、或当前台本还没解析出来，都不算失效（不能乱报警）
        assertFalse(BookmarkMath.lineStale(bm.copy(scriptFingerprint = ""), "11:def"))
        assertFalse(BookmarkMath.lineStale(bm, ""))
        // 时间点书签跟台本行号无关，永不失效
        assertFalse(BookmarkMath.lineStale(timeBm("2", pos = 1L), "11:def"))
    }
}
