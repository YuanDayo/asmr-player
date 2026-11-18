package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「已下载」台账的纯逻辑。
 *
 * 这块出错的代价是用户视角的**假记录**：文件早删了，作品却永远不出现在「已购未下载」里
 * （2.5.5-beta3 反馈）。所以三个点各写一条：
 *  1. 老格式（只有编号）还能读；
 *  2. 新格式（编号 + 路径）能读能写，分卷多条路径不丢；
 *  3. 判定只认文件 —— 文件没了就是没下载，空路径（没法确认）也算没下载。
 */
class DownloadedLedgerTest {

    @Test
    fun `老格式只有编号时路径为空`() {
        val map = DownloadedLedger.parse(listOf("RJ01234567", " vj01000001 "))
        assertEquals(setOf("RJ01234567", "VJ01000001"), map.keys)
        assertTrue(map.values.all { it.isEmpty() })
    }

    @Test
    fun `编号与路径往返不变形`() {
        val ledger = mapOf(
            "RJ01234567" to listOf("/a/RJ01234567.zip"),
            "RJ07654321" to listOf("/a/part1.zip", "/a/part2.zip"),
        )
        val text = DownloadedLedger.render(ledger)
        assertEquals(ledger, DownloadedLedger.parse(text.lines()))
    }

    @Test
    fun `同一编号重复出现时路径合并去重`() {
        val text = "RJ01234567\t/a/x.zip\nRJ01234567\t/a/x.zip|/a/y.zip\n"
        val map = DownloadedLedger.parse(text.lines())
        assertEquals(listOf("/a/x.zip", "/a/y.zip"), map["RJ01234567"])
    }

    @Test
    fun `路径里出现空段被丢掉`() {
        val map = DownloadedLedger.parse(listOf("RJ01234567\t/a/x.zip||"))
        assertEquals(listOf("/a/x.zip"), map["RJ01234567"])
    }

    @Test
    fun `文件还在才算已下载`() {
        val existing = setOf("/a/x.zip")
        assertTrue(DownloadedLedger.present(listOf("/a/x.zip")) { it in existing })
        // 分卷作品：还剩一个文件就算在
        assertTrue(DownloadedLedger.present(listOf("/a/y.zip", "/a/x.zip")) { it in existing })
        // 全删了 = 没下载（这就是用户反馈的那个 bug）
        assertFalse(DownloadedLedger.present(listOf("/a/y.zip", "/a/z.zip")) { it in existing })
    }

    @Test
    fun `没有路径的老条目按未下载处理`() {
        assertFalse(DownloadedLedger.present(emptyList()) { true })
    }
}
