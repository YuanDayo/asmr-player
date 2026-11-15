package com.kiite.player.domain

import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * DLsite 下载的**完整链路**（本地开一个 socket 当 CDN）：流式写 `.kiite-part` → 完成后改名 → 状态收尾。
 *
 * 这块以前没有测试，「中断留下半截 zip」「失败后状态停在正在下载」这类问题只能靠真机撞；
 * 用真实 socket 跑一遍比 mock 更有说服力。注意用 `ServerSocket` 而不是
 * `com.sun.net.httpserver`——后者不在 Android 单测的编译类路径里。
 */
class DlsiteDownloadFlowTest {

    private var server: TinyServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        runCatching { server?.close() }
        scope.cancel()
    }

    private fun awaitTerminal(dl: DlsiteDownloader, timeoutMs: Long = 15_000L): DlsiteDownloader.State {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            dl.state.value?.takeIf { !it.running }?.let { return it }
            Thread.sleep(20)
        }
        fail("下载 15 秒内没有结束")
        error("unreachable")
    }


    @Test
    fun `完整下载会落盘、给出总大小且不留下临时文件`() {
        val payload = ByteArray(300_000) { (it % 251).toByte() }
        val srv = TinyServer(
            status = 200,
            body = payload,
            headers = listOf(
                "Content-Disposition" to "attachment; filename*=UTF-8''RJ000001.zip",
            ),
        )
        server = srv
        val dir = Files.createTempDirectory("dlsite-dl").toFile()
        val dl = DlsiteDownloader(scope)

        dl.start(
            srv.url("/dl"),
            null,
            "attachment; filename*=UTF-8''RJ000001.zip",
            "RJ000001",
            dir.absolutePath,
        )
        val st = awaitTerminal(dl)

        assertTrue("应该完成：" + st.status, st.completed)
        assertFalse(st.failed)
        assertEquals("status=" + st.status + " total=" + st.totalBytes, payload.size.toLong(), st.totalBytes)
        assertEquals(payload.size.toLong(), st.doneBytes)
        assertEquals(100, st.percent)
        assertEquals("RJ000001.zip", st.fileName)

        val saved = st.savedPath?.let { java.io.File(it) }
        assertNotNull(saved)
        assertTrue(saved!!.isFile)
        assertEquals(payload.size.toLong(), saved.length())
        // 目录里只应该有成品，没有 .kiite-part 残留
        assertEquals(listOf("RJ000001.zip"), java.io.File(dir, "DLsite").listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `服务端报错时标记失败且不留半截文件`() {
        val srv = TinyServer(status = 404, body = ByteArray(0))
        server = srv
        val dir = Files.createTempDirectory("dlsite-dl-404").toFile()
        val dl = DlsiteDownloader(scope)

        dl.start(srv.url("/dl"), null, null, "RJ000002", dir.absolutePath)
        val st = awaitTerminal(dl)

        assertTrue("应该失败：" + st.status, st.failed)
        assertFalse(st.completed)
        assertTrue(st.status.contains("HTTP 404"))
        // 状态是「立刻」变的（cancel 同步置位），半成品清理在下载协程里稍后完成——等它一下
        val outDir = java.io.File(dir, "DLsite")
        val deadline = System.currentTimeMillis() + 3_000
        while (outDir.listFiles().orEmpty().isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(emptyList<String>(), outDir.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `没有总大小时进度不冒充百分比`() {
        val srv = TinyServer(status = 200, body = ByteArray(50_000) { b -> b.toByte() }, chunked = true)
        server = srv
        val dir = Files.createTempDirectory("dlsite-dl-chunked").toFile()
        val dl = DlsiteDownloader(scope)

        dl.start(srv.url("/chunked"), null, null, null, dir.absolutePath)
        val st = awaitTerminal(dl)

        assertTrue(st.completed)
        assertFalse(st.hasTotal)
        assertEquals(-1L, st.totalBytes)
        assertEquals(50_000L, st.doneBytes)
    }


    @Test
    fun `取消会停在已暂停且不留半截文件`() {
        val srv = TinyServer(200, ByteArray(2_000_000), chunkDelayMs = 20L)
        server = srv
        val dir = Files.createTempDirectory("dlsite-dl-cancel").toFile()
        val dl = DlsiteDownloader(scope)

        dl.start(srv.url("/dl"), null, null, "RJ000003", dir.absolutePath)
        // 服务端每 8KB 停 20ms，整包要 5 秒左右；这里在 300ms 时取消，稳定落在「下载中」
        Thread.sleep(300)
        dl.cancel()
        val st = awaitTerminal(dl)

        assertTrue("应该已暂停：" + st.status, st.cancelled)
        assertFalse(st.completed)
        assertFalse(st.failed)
        // 状态是「立刻」变的（cancel 同步置位），半成品清理在下载协程里稍后完成——等它一下
        val outDir = java.io.File(dir, "DLsite")
        val deadline = System.currentTimeMillis() + 3_000
        while (outDir.listFiles().orEmpty().isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(emptyList<String>(), outDir.listFiles().orEmpty().map { it.name })
    }

    /**
     * 极简 HTTP/1.1 服务：只回固定响应，够测下载用（顺序处理连接，重试的第 2 次也能接）。
     */
    private class TinyServer(
        private val status: Int,
        private val body: ByteArray,
        private val headers: List<Pair<String, String>> = emptyList(),
        private val chunked: Boolean = false,
        /** 每写一块停一下，用来制造「下载中」的时间窗口（测取消）。 */
        private val chunkDelayMs: Long = 0L,
    ) {
        private val socket = ServerSocket(0)
        @Volatile
        private var closed = false

        init {
            Thread {
                while (!closed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    runCatching { respond(client) }
                    runCatching { client.close() }
                }
            }.apply { isDaemon = true }.start()
        }

        fun url(path: String): String = "http://127.0.0.1:" + socket.localPort + path

        fun close() {
            closed = true
            runCatching { socket.close() }
        }

        private fun respond(client: java.net.Socket) {
            // 读掉请求头（到空行为止），我们不需要请求内容
            val input = client.getInputStream().buffered()
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
            }
            val out = BufferedOutputStream(client.getOutputStream())
            val reason = when (status) {
                200 -> "OK"
                404 -> "Not Found"
                else -> "Error"
            }
            val sb = StringBuilder()
            sb.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
            if (status == 200) {
                if (chunked) {
                    sb.append("Transfer-Encoding: chunked\r\n")
                } else {
                    sb.append("Content-Length: ").append(body.size).append("\r\n")
                }
            }
            sb.append("Connection: close\r\n\r\n")
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            if (status == 200) {
                if (chunked) {
                    out.write((Integer.toHexString(body.size) + "\r\n").toByteArray())
                    out.write(body)
                    out.write("\r\n0\r\n\r\n".toByteArray())
                } else if (chunkDelayMs > 0L) {
                    var off = 0
                    while (off < body.size) {
                        val n = minOf(8192, body.size - off)
                        out.write(body, off, n)
                        out.flush()
                        off += n
                        Thread.sleep(chunkDelayMs)
                    }
                } else {
                    out.write(body)
                }
            }
            out.flush()
        }

        private fun readLine(input: java.io.InputStream): String? {
            val buf = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0) return if (buf.isEmpty()) null else buf.toString()
                if (c == '\n'.code) return buf.toString().trimEnd('\r')
                buf.append(c.toChar())
            }
        }
    }
}
