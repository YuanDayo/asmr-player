package com.kiite.player.ui

import android.app.DownloadManager
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 应用内下载：打开 DLsite 的下载页，用户点它的下载按钮，
 * 我们通过 DownloadListener 拿到直链，再用带登录态的方式保存到本地目录。
 * （不走扒 WebView 离线缓存那条路——那是 blob 化的私有数据，没有索引、改版就废。）
 */
@Composable
fun DlsiteDownloadScreen(vm: MainViewModel, code: String, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val status by vm.downloadStatus.collectAsStateWithLifecycle()
    val url = remember(code) { com.kiite.player.core.DlsiteAuth.downloadUrl(code) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text("下载 " + code, style = MaterialTheme.typography.titleMedium)
                Text(
                    "在页面里点 DLsite 的下载按钮，会自动保存到 曲库根目录/DLsite",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onBack) { Text("完成") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(
                it,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        status?.let {
            Text(
                it,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView?, u: String?) {
                            loading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            err: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                loading = false
                                error = "页面加载失败：" + (err?.description ?: "未知错误")
                            }
                        }
                    }
                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                        // 有些页面用 blob: 交给浏览器自己处理，其余交给应用下载
                        if (downloadUrl.startsWith("blob:")) {
                            vm.say("这个下载是 blob 流，请用「在线播放」或浏览器下载")
                        } else {
                            val name = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                            vm.startDlsiteDownload(
                                downloadUrl,
                                userAgent,
                                contentDisposition ?: ("attachment; filename=\"" + name + "\""),
                            )
                        }
                    }
                    loadUrl(url)
                }
            },
        )
        Spacer(Modifier.height(6.dp))
    }
}
