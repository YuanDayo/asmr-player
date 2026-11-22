package com.kiite.player.ui

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.kiite.player.ui.theme.Spacing

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

    // 工具条改成**浮在 WebView 之上**的一层。
    //
    // 原来它是 Column 的第一行：WebView 这个 AndroidView 会盖到它上面去 ——
    // 真机上看到的正是「标题、完成两个字都在，但点在状态栏那条线上完全没反应」。
    // 现在 Column 里先留出等高的一格，工具条用 align 画在最后（层级最高），
    // 同时整页避开系统栏，按钮才真正落在能点的区域。
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(LoginHeaderHeight))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.itemGap)) {
            TextButton(onClick = { webView?.loadUrl("https://www.dlsite.com/maniax/mypage/") }) {
                Text("去我的页面")
            }
            Spacer(Modifier.width(Spacing.sm))
            TextButton(onClick = { webView?.reload() }) { Text("重新加载") }
        }
        Text(
            "登录成功后点右上角「完成」。登录态保存在系统 Cookie 中，应用不会记录你的密码。",
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        }
        // 工具条：最后组合 = 画在最上层，WebView 再大也压不住它
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(LoginHeaderHeight)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
    }
}

/** 登录页工具条的高度：Column 先留出这一格，工具条再浮在同高的位置上。 */
private val LoginHeaderHeight = 56.dp



