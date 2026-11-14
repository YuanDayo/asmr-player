package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 增量扫描：文件集快照没变时复用上一轮关联结果；任何文件的变化（新增/删除/修改）触发全量重算。
 */
class IncrementalScanTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeWork(dir: File, scripts: Int = 1) {
        dir.mkdirs()
        File(dir, "01 track.wav").writeBytes(ByteArray(16))
        File(dir, "02 track.wav").writeBytes(ByteArray(16))
        for (i in 1..scripts) File(dir, "story$i.txt").writeText("第${i}话\r\n这是第${i}话的台词内容。")
    }

    private fun scan(root: File, previous: ScanResult? = null): ScanResult =
        LibraryScanner().scan(root, previous = previous)

    @Test
    fun `首次扫描填充文件快照`() {
        val root = tmp.newFolder("lib")
        makeWork(File(root, "RJ001"))
        val r = scan(root)
        assertTrue(r.snapshot.isNotEmpty())
        assertTrue(r.snapshot.all { it.sizeBytes >= 0 })
        // 音频 + 台本都在快照里
        assertEquals(2, r.snapshot.count { it.kind == "audio" })
        assertEquals(1, r.snapshot.count { it.kind == "script" })
    }

    @Test
    fun `文件集没变时复用上一轮结果`() {
        val root = tmp.newFolder("lib")
        makeWork(File(root, "RJ001"))
        val first = scan(root)
        Thread.sleep(5)
        val second = scan(root, previous = first)
        // 关联结果原样复用：tracks 是同一批对象（不重新跑 ScriptMatcher）
        assertEquals(first.tracks, second.tracks)
        assertEquals(first.projects, second.projects)
        // 但扫描时间戳刷新，界面上的「上次扫描」仍是新值
        assertTrue(second.scannedAtMs >= first.scannedAtMs)
    }

    @Test
    fun `修改音频文件触发全量重算`() {
        val root = tmp.newFolder("lib")
        val work = File(root, "RJ001")
        makeWork(work)
        val first = scan(root)
        // 覆盖写入：同路径、新 mtime
        Thread.sleep(10)
        File(work, "01 track.wav").writeBytes(ByteArray(64))
        val second = scan(root, previous = first)
        assertEquals(first.tracks.size, second.tracks.size)
        val old = first.tracks.first { it.name.startsWith("01") }
        val new = second.tracks.first { it.name.startsWith("01") }
        assertNotEquals(old.sizeBytes, new.sizeBytes)
        assertNotEquals(old.modifiedMs, new.modifiedMs)
    }

    @Test
    fun `新增台本触发重算且台本重新关联`() {
        val root = tmp.newFolder("lib")
        val work = File(root, "RJ001")
        makeWork(work)
        val first = scan(root)
        assertEquals(1, first.tracks.first().scripts.size)
        File(work, "story2.txt").writeText("第2话\r\n新增的台本。")
        val second = scan(root, previous = first)
        assertTrue(second.snapshot.count { it.kind == "script" } == 2)
    }

    @Test
    fun `删除音频触发重算`() {
        val root = tmp.newFolder("lib")
        val work = File(root, "RJ001")
        makeWork(work)
        val first = scan(root)
        File(work, "02 track.wav").delete()
        val second = scan(root, previous = first)
        assertEquals(first.trackCount - 1, second.trackCount)
    }

    @Test
    fun `换根目录不复用快照`() {
        val rootA = tmp.newFolder("libA")
        val rootB = tmp.newFolder("libB")
        makeWork(File(rootA, "RJ001"))
        makeWork(File(rootB, "RJ002"))
        val first = scan(rootA)
        val second = scan(rootB, previous = first)
        assertEquals(rootB.absolutePath, second.rootPath)
        assertEquals(2, second.trackCount)
    }
}
