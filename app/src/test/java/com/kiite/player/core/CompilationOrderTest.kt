package com.kiite.player.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 总集篇顺序：一部作品里塞了多个子作品，每个子作品的章节文件夹重名（都是「1.音声文件」）。
 *
 * 以前曲目只按「文件夹名」排序，这些重名章节全部并列，退化成按文件名排——
 * 5 个「0.序章」连成一串，再 5 个「1.xxx」……播放列表因此按曲名混排、完全不连贯。
 */
class CompilationOrderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 每个子作品 3 集：0.序章 / 1.本篇 / 2.附赠，章节目录都叫「1.音声文件」。 */
    private fun volume(dir: File, vararg tracks: String) {
        val chapter = File(dir, "1.音声文件")
        chapter.mkdirs()
        for (t in tracks) File(chapter, t).writeBytes(ByteArray(8))
    }

    private fun names(scan: ScanResult) = scan.tracks.map { it.name }

    @Test
    fun `总集篇按子作品顺序排列，不按曲名混排`() {
        val root = tmp.newFolder("lib")
        val work = File(root, "RJ01263522 总集篇")
        // 故意让曲名互相"插队"：第一卷只有 0/2，第二卷只有 0/1，第三卷只有 1/2。
        // 旧逻辑（只比文件夹名）会排成 0,0,1,1,2,2；正确顺序应是整卷连着走。
        volume(File(work, "01 第一卷"), "0.序章.mp3", "2.附赠.mp3", "10.特典.mp3")
        volume(File(work, "02 第二卷"), "0.序章.mp3", "1.本篇.mp3")
        volume(File(work, "03 第三卷"), "1.本篇.mp3", "2.附赠.mp3")

        val scan = LibraryScanner().scan(root)
        assertEquals(
            listOf(
                "0.序章.mp3", "2.附赠.mp3", "10.特典.mp3",   // 第一卷
                "0.序章.mp3", "1.本篇.mp3",                  // 第二卷
                "1.本篇.mp3", "2.附赠.mp3",                  // 第三卷
            ),
            names(scan),
        )
    }

    @Test
    fun `路径逐段自然排序`() {
        val sorted = listOf(
            "卷10/1.音声文件",
            "卷2/1.音声文件",
            "卷2/2.特典",
            "卷1",
        ).sortedWith(Comparator { a, b -> NaturalOrder.comparePath(a, b) })
        assertEquals(listOf("卷1", "卷2/1.音声文件", "卷2/2.特典", "卷10/1.音声文件"), sorted)
    }

    @Test
    fun `章节按子作品顺序分组，重名章节标题带上级目录`() {
        val root = tmp.newFolder("lib")
        val work = File(root, "RJ01263522 总集篇")
        volume(File(work, "01 第一卷"), "0.序章.mp3")
        volume(File(work, "02 第二卷"), "0.序章.mp3")

        val scan = LibraryScanner().scan(root)
        val project = scan.projects.single()
        assertEquals(2, project.chapters.size)
        assertEquals(
            listOf("01 第一卷 / 1.音声文件", "02 第二卷 / 1.音声文件"),
            project.chapters.map { it.name },
        )
        // 章节顺序 = 子作品顺序，第一章就是第一卷
        assertTrue(project.chapters[0].path.contains("01 第一卷"))
    }

    @Test
    fun `章节名唯一时不加前缀`() {
        val root = tmp.newFolder("lib")
        val work = File(root, "RJ001")
        for (ch in listOf("01 第一章", "02 第二章")) {
            File(work, ch).mkdirs()
            File(File(work, ch), "a.mp3").writeBytes(ByteArray(8))
        }
        val project = LibraryScanner().scan(root).projects.single()
        assertEquals(listOf("01 第一章", "02 第二章"), project.chapters.map { it.name })
    }

    @Test
    fun `旧排序版本的缓存不被复用`() {
        val root = tmp.newFolder("lib")
        volume(File(File(root, "RJ001"), "01 第一卷"), "0.序章.mp3", "1.本篇.mp3")
        val fresh = LibraryScanner().scan(root)
        assertEquals(LibraryScanner.ORDER_VERSION, fresh.orderVersion)

        // 模拟上个版本写下的缓存：文件集没变，但排序规则版本是旧的
        val stale = fresh.copy(orderVersion = 0, tracks = fresh.tracks.reversed())
        val rescanned = LibraryScanner().scan(root, previous = stale)
        assertEquals(fresh.tracks.map { it.path }, rescanned.tracks.map { it.path })
    }
}
