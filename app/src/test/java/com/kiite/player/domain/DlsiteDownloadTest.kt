package com.kiite.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DLsite 下载的纯逻辑：进度换算与文件名解析。
 *
 * 下载本体（网络、落盘、取消）没法在单测里跑，但这两块出错最影响体验：
 * 百分比算错会显示 0% 或提前满格；文件名解析错会写不进去（或写到别的目录）。
 */
class DlsiteDownloadTest {

    // ---------- 进度换算 ----------

    @Test
    fun `有总长时进度与百分比按字节算`() {
        val s = DlsiteDownloader.State(doneBytes = 512_000, totalBytes = 2_048_000)
        assertEquals(0.25f, s.progress, 0.001f)
        assertEquals(25, s.percent)
        assertEquals("500 KB / 2.0 MB", s.sizeText)
        assertTrue(s.hasTotal)
    }

    @Test
    fun `没有总长时不假装有进度`() {
        val s = DlsiteDownloader.State(doneBytes = 3_000_000, totalBytes = -1)
        assertEquals(0f, s.progress, 0.001f)
        assertEquals(0, s.percent)
        assertEquals("2.9 MB", s.sizeText)
        assertFalse(s.hasTotal)
        assertNull(s.etaSeconds)
    }

    @Test
    fun `完成后进度满格且不再算速度`() {
        val s = DlsiteDownloader.State(
            doneBytes = 100, totalBytes = 100, running = false, completed = true,
        )
        assertEquals(1f, s.progress, 0.001f)
        assertEquals(100, s.percent)
        assertEquals("", s.speedText)
        assertEquals("", s.etaText)
    }

    @Test
    fun `速度与剩余时间按已下字节折算`() {
        val s = DlsiteDownloader.State(
            doneBytes = 10L * 1024 * 1024,
            totalBytes = 30L * 1024 * 1024,
            speedBps = 1024L * 1024,
        )
        assertEquals("1.0 MB/s", s.speedText)
        // 还剩 20 MB / 1 MBps = 20 秒
        assertEquals(20L, s.etaSeconds)
        assertEquals("约 20 秒", s.etaText)
        assertTrue(s.progressText.startsWith("33% · 10.0 MB / 30.0 MB · 1.0 MB/s"))
    }

    @Test
    fun `超过一分钟的剩余时间用分钟秒表示`() {
        val s = DlsiteDownloader.State(
            doneBytes = 0,
            totalBytes = 100L * 1024 * 1024,
            speedBps = 1024L * 1024,
        )
        assertEquals("约 1 分 40 秒", s.etaText)
    }

    @Test
    fun `重试次数会显示在进度文案里`() {
        val s = DlsiteDownloader.State(
            doneBytes = 1024, totalBytes = 2048, speedBps = 1024, attempt = 2,
        )
        assertTrue(s.progressText.contains("第 2 次尝试"))
    }

    // ---------- 文件名解析 ----------

    @Test
    fun `普通 filename 头部直接用它`() {
        assertEquals(
            "RJ123456.zip",
            DlsiteDownloader.guessName("attachment; filename=\"RJ123456.zip\"", "https://x/y"),
        )
    }

    @Test
    fun `RFC5987 的 filename星号 会剥掉编码声明`() {
        assertEquals(
            "RJ01123456.zip",
            DlsiteDownloader.guessName("attachment; filename*=UTF-8''RJ01123456.zip", "https://x/y"),
        )
        assertEquals(
            "音声作品.zip",
            DlsiteDownloader.guessName(
                "attachment; filename*=UTF-8''%E9%9F%B3%E5%A3%B0%E4%BD%9C%E5%93%81.zip",
                "https://x/y",
            ),
        )
    }

    @Test
    fun `头部没有文件名时退到 URL 最后一段`() {
        assertEquals(
            "RJ654321.zip",
            DlsiteDownloader.guessName(null, "https://cdn.example.com/a/b/RJ654321.zip?token=1"),
        )
        assertEquals(
            "dlsite-download.zip",
            DlsiteDownloader.guessName(null, "https://cdn.example.com/download?token=1"),
        )
    }

    @Test
    fun `落盘名用编号加标题 不再全都是一个名字`() {
        assertEquals(
            "RJ01275416 夜の耳かき.zip",
            DlsiteDownloader.displayName("rj01275416", "夜の耳かき"),
        )
        // 标题里已经带了编号时不要重复拼一遍
        assertEquals(
            "RJ01275416 夜の耳かき.zip",
            DlsiteDownloader.displayName("RJ01275416", "RJ01275416 夜の耳かき"),
        )
        // 分卷各自独立，但都带同一作品的标题
        assertEquals(
            "RJ01275416 夜の耳かき 第2部分.zip",
            DlsiteDownloader.displayName("RJ01275416", "夜の耳かき", index = 2, count = 3),
        )
        // 缓存里没标题时退回站方文件名，仍带编号
        assertEquals(
            "RJ01275416 download.zip",
            DlsiteDownloader.displayName("RJ01275416", null, fallback = "download.zip"),
        )
    }

    @Test
    fun `路径分隔符被替换掉不会写到别的目录`() {
        assertEquals(
            ".._.._etc_passwd.zip",
            DlsiteDownloader.guessName("attachment; filename=\"../../etc/passwd.zip\"", "https://x/y"),
        )
        assertFalse(DlsiteDownloader.safeName("a\\b.zip").contains("\\"))
    }

    @Test
    fun `下载目录跟着用户设置的路径走`() {
        // 断言用 path / name（绝对路径在 Windows 上会带盘符，跑 CI 的 JVM 与真机不同）
        val a = DlsiteDownloader.targetDir("/storage/emulated/0/Music")
        assertEquals("DLsite", a.name)
        assertEquals(java.io.File("/storage/emulated/0/Music").path, a.parentFile!!.path)
        // 结尾多一个斜杠也照样是 Music/DLsite
        val b = DlsiteDownloader.targetDir("/storage/emulated/0/Music/")
        assertEquals("DLsite", b.name)
        assertEquals(java.io.File("/storage/emulated/0/Music").path, b.parentFile!!.path)
        // 用户已经选到 DLsite 这一层，不再套一层
        val c = DlsiteDownloader.targetDir("/storage/emulated/0/Music/DLsite")
        assertEquals("DLsite", c.name)
        assertEquals(java.io.File("/storage/emulated/0/Music").path, c.parentFile!!.path)
        val d = DlsiteDownloader.targetDir("/storage/emulated/0/Music/DLsite/")
        assertEquals("DLsite", d.name)
        assertEquals(java.io.File("/storage/emulated/0/Music").path, d.parentFile!!.path)
    }
}
