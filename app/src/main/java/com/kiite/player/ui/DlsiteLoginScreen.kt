package com.kiite.player.ui

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** WebView 上常带 "wv" 标记，很多站点会因此不给完整页面，覆盖成普通 Chrome UA。 */
private const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * 应用内登录：整页显示，不再用 Dialog 包 WebView。
 * （Dialog 里的 WebView 在部分机型上会渲染成黑屏。）
 */
@Composable
fun DlsiteLoginScreen(onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf("https://www.dlsite.com/maniax/") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text("登录 DLsite", style = MaterialTheme.typography.titleMedium)
                Text(
                    url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onBack) { Text("完成") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(
                it,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.userAgentString = CHROME_UA
                        settings.builtInZoomControls = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.setSupportMultipleWindows(true)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onCreateWindow(
                            view: android.webkit.WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?,
                        ): Boolean {
                            // DLsite 会用 target=_blank 打开作品/下载页，不接管就会跳到系统浏览器
                            val transport = resultMsg?.obj as? android.webkit.WebView.WebViewTransport
                                ?: return false
                            transport.webView = view
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                                loading = true
                                error = null
                            }

                            override fun onPageFinished(view: WebView?, u: String?) {
                                loading = false
                                url = u ?: url
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = false

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                err: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    loading = false
                                    error = "页面加载失败：" + (err?.description ?: "未知错误") +
                                        "（请检查网络；本机若无法直连 dlsite.com 就会失败）"
                                }
                            }
                        }
                        webView = this
                        loadUrl("https://www.dlsite.com/maniax/")
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            TextButton(onClick = { webView?.loadUrl("https://www.dlsite.com/maniax/mypage/") }) {
                Text("去我的页面")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { webView?.reload() }) { Text("重新加载") }
        }
        Text(
            "登录成功后点右上角「完成」。登录态保存在系统 Cookie 中，应用不会记录你的密码。",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

private val COOKIE_URLS = listOf(
    "https://www.dlsite.com",
    "https://www.dlsite.com/maniax/",
    "https://www.dlsite.com/maniax/mypage/",
    "https://dlsite.com",
    "https://login.dlsite.com",
    "https://ssl.dlsite.com",
)

/**
 * 收集 DLsite 的 Cookie 头。
 * WebView 的 Cookie 存在系统的 CookieManager 里，HttpURLConnection 不会自动带上，必须手动转发；
 * 而且登录 Cookie 可能落在其它子域 / 路径上，只查 www 根路径经常取不到。
 */
fun dlsiteCookieHeader(): String? {
    val cm = CookieManager.getInstance()
    runCatching { cm.flush() }
    val pairs = LinkedHashMap<String, String>()
    for (u in COOKIE_URLS) {
        val raw = runCatching { cm.getCookie(u) }.getOrNull() ?: continue
        for (part in raw.split(";")) {
            val kv = part.trim()
            if (kv.isEmpty() || !kv.contains('=')) continue
            pairs[kv.substringBefore('=')] = kv
        }
    }
    return if (pairs.isEmpty()) null else pairs.values.joinToString("; ")
}

/** 诊断用：当前收集到多少个 Cookie。 */
fun dlsiteCookieCount(): Int =
    dlsiteCookieHeader()?.split(";")?.count { it.isNotBlank() } ?: 0

/** 退出登录：清掉所有 Cookie。 */
fun clearDlsiteSession() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
}
