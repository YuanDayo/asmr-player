package com.kiite.player.data

import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 用一个极小的本地 HTTP 服务验证「联网抓取 + 解析」整条链路。
 * （本机网络对 dlsite.com 做了 DNS 劫持，无法直连，所以用本地服务代替。）
 */
class DlsiteClientTest {

    private lateinit var server: ServerSocket
    private var lastCookie: String? = null
    private var lastUa: String? = null
    private var body = ""

    private val page = """
        <html><head>
        <meta property="og:title" content="【耳かき】テスト作品 [RJ123456] | DLsite">
        <meta property="og:image" content="https://img.dlsite.jp/x.jpg">
        </head><body>
        <a href="https://www.dlsite.com/maniax/circle/profile/=/maker_id/RG1.html">テストサークル</a>
        <a href="https://www.dlsite.com/maniax/works/=/genre/1.html">ASMR</a>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        server = ServerSocket(0)
        Thread {
            try {
                while (!server.isClosed) {
                    val sock = server.accept()
                    sock.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) {
                            val l = line
                            if (l.startsWith("Cookie:", true)) lastCookie = l.substringAfter(":").trim()
                            if (l.startsWith("User-Agent:", true)) lastUa = l.substringAfter(":").trim()
                            line = reader.readLine()
                        }
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        val out = s.getOutputStream()
                        out.write(
                            ("HTTP/1.0 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: " + bytes.size + "\r\nConnection: close\r\n\r\n").toByteArray(),
                        )
                        out.write(bytes)
                        out.flush()
                    }
                }
            } catch (_: Exception) {
                // server closed
            }
        }.apply { isDaemon = true }.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun url() = "http://127.0.0.1:" + server.localPort + "/work"

    @Test
    fun fetchesAndParsesAndSendsCookie() {
        body = page
        val work = DlsiteClient.fetchFrom(url(), "rj123456", "sessionid=abc").getOrThrow()
        assertEquals("RJ123456", work.code)
        assertEquals("【耳かき】テスト作品 [RJ123456]", work.title)
        assertEquals("テストサークル", work.circle)
        assertEquals("https://img.dlsite.jp/x.jpg", work.coverUrl)
        assertEquals(listOf("ASMR"), work.tags)
        assertEquals("登录态要透传给请求", "sessionid=abc", lastCookie)
        assertTrue("要带浏览器 UA: " + lastUa, lastUa!!.contains("Mozilla"))
    }

    @Test
    fun reportsAgeGateInsteadOfGenericFailure() {
        body = "<html><body>年齢確認が必要です</body></html>"
        val err = DlsiteClient.fetchFrom(url(), "RJ999999", null).exceptionOrNull()!!
        assertTrue("应提示登录: " + err.message, err.message!!.contains("年龄确认"))
    }

    @Test
    fun reportsGenericFailureForUnparsablePage() {
        body = "<html><body>404 not found</body></html>"
        val err = DlsiteClient.fetchFrom(url(), "RJ000000", null).exceptionOrNull()!!
        assertTrue(err.message!!.contains("没找到作品信息"))
    }
}
