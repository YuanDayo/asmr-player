package com.kiite.player.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 「查看图片」的收集规则。
 *
 * 曾经的实现是 `File.listFiles()` 单层枚举，导致「根目录 → イラスト → 差分」这种
 * 两层嵌套的图在播放页根本看不到。这里把「往下挖几层、最多几张、什么不收」钉死。
 */
class ProjectImagesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun image(dir: File, name: String): File {
        dir.mkdirs()
        return File(dir, name).apply { writeBytes(ByteArray(4)) }
    }

    @Test
    fun `子文件夹里的图片也会被收集`() {
        val root = tmp.newFolder("RJ001")
        image(root, "cover.jpg")
        image(File(root, "イラスト"), "01.png")
        image(File(root, "イラスト/差分"), "02.webp")

        val found = collectProjectImages(listOf(root.absolutePath)).map { File(it).name }

        assertEquals(listOf("cover.jpg", "01.png", "02.webp"), found)
    }

    @Test
    fun `超过深度上限的目录不再往下挖`() {
        val root = tmp.newFolder("RJ002")
        image(root, "a.jpg")
        var deep = root
        for (i in 1..PROJECT_IMAGE_DEPTH) {
            deep = File(deep, "d$i")
            image(deep, "ok$i.jpg")
        }
        // 再多一层：这一层的图不该出现
        val tooDeep = File(deep, "d${PROJECT_IMAGE_DEPTH + 1}")
        image(tooDeep, "toolate.jpg")

        val found = collectProjectImages(listOf(root.absolutePath)).map { File(it).name }

        assertTrue("上限内的都在：$found", found.contains("ok$PROJECT_IMAGE_DEPTH.jpg"))
        assertFalse("超出一层的不该在：$found", found.contains("toolate.jpg"))
    }

    @Test
    fun `隐藏目录整棵跳过`() {
        val root = tmp.newFolder("RJ003")
        image(root, "a.jpg")
        image(File(root, ".thumbnails"), "cache.jpg")

        val found = collectProjectImages(listOf(root.absolutePath)).map { File(it).name }

        assertEquals(listOf("a.jpg"), found)
    }

    @Test
    fun `数量到上限就停`() {
        val root = tmp.newFolder("RJ004")
        repeat(12) { image(root, "p$it.jpg") }

        val found = collectProjectImages(listOf(root.absolutePath), maxCount = 5)

        assertEquals(5, found.size)
    }

    @Test
    fun `只收图片扩展名`() {
        val root = tmp.newFolder("RJ005")
        image(root, "a.jpg")
        File(root, "readme.txt").writeText("x")
        File(root, "01.wav").writeBytes(ByteArray(4))

        val found = collectProjectImages(listOf(root.absolutePath)).map { File(it).name }

        assertEquals(listOf("a.jpg"), found)
    }

    @Test
    fun `多个目录之间去重`() {
        val root = tmp.newFolder("RJ006")
        image(root, "a.jpg")

        val found = collectProjectImages(listOf(root.absolutePath, root.absolutePath))

        assertEquals(1, found.size)
    }

    @Test
    fun `不存在的目录被忽略`() {
        val found = collectProjectImages(listOf(File(tmp.root, "nope").absolutePath, ""))
        assertTrue(found.isEmpty())
    }
}
