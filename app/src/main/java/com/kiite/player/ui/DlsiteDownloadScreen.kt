package com.kiite.player.ui

import android.Manifest
import android.app.DownloadManager
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiite.player.core.SizeFormat

/**
 * 应用内下载：打开 DLsite 的下载页，用户点它的下载按钮，
 * 我们通过 DownloadListener 拿到直链，再用带登录态的方式保存到本地目录。
 * （不走扒 WebView 离线缓存那条路——那是 blob 化的私有数据，没有索引、改版就废。）
 *
 * 下载本身跑在应用级作用域（见 `DlsiteDownloads`）：**页面不会被进度面板顶掉**，
 * 可以继续浏览；退出本页、切后台、锁屏都继续下，通知栏有进度条和「取消」。
 *
 * @param owned 已购作品 → 直链下载页；未购 → 作品页（下载页对未购直接 403）。
 * @param startUrl 非空时直接打开该地址（asmr.one 等第三方站复用本屏）。
 * @param heading / hint 非空时替换顶栏标题与提示文案。
 * 打开期间隐藏悬浮迷你播放器与底部导航胶囊（否则 WebView 底部内容被遮）。
 */
@Composable
fun DlsiteDownloadScreen(
    vm: MainViewModel,
    code: String,
    owned: Boolean = true,
    startUrl: String? = null,
    heading: String? = null,
    hint: String? = null,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ctx = { context }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val download by vm.dlsiteDownload.collectAsStateWithLifecycle()
    // 本页只展示属于这个作品的下载（从别的作品页切过来时不会串台）
    val st = download?.takeIf { it.code == null || it.code == code }
    val url = remember(code, owned, startUrl) {
        startUrl ?: if (owned) com.kiite.player.core.DlsiteAuth.downloadUrl(code)
        else com.kiite.player.core.DlsiteAuth.workUrl(code)
    }

    // 通知栏进度需要 POST_NOTIFICATIONS（Android 13+）。拒绝也能下载，只是看不到通知。
    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 授权与否都继续，下载不依赖通知 */ }

    // 本页是全屏二级页：藏起迷你播放条与导航胶囊，返回时恢复；并给系统栏让位
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.setChromeHidden(true)
        onDispose { vm.setChromeHidden(false) }
    }

    // 系统返回键：WebView 有历史就先在页面内后退，没有才退出本页（不直接杀掉应用）
    val webViewRef = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<WebView?>(null) }
    androidx.activity.compose.BackHandler {
        val wv = webViewRef.value
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    val done = st?.completed == true && st?.savedPath != null

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    heading ?: if (owned) "下载 " + code else "DLsite · " + code,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    hint ?: if (owned) {
                        "在页面里点 DLsite 的下载按钮，会自动保存到 曲库根目录/DLsite"
                    } else {
                        "未购买作品从作品页进入；购买后在此点下载按钮会自动保存到 曲库根目录/DLsite"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onBack) { Text("完成") }
        }
        if (loading && !done) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(
                it,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // ---------- 下载进度条（不遮挡 WebView，可以边下边继续浏览） ----------
        st?.takeIf { it.running }?.let { s ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                if (s.hasTotal) {
                    DownloadProgressBar(
                        progress = s.progress,
                        modifier = Modifier.padding(vertical = 4.dp),
                        height = 5.dp,
                    )
                } else {
                    // 服务端没给 Content-Length：画无限进度，别让用户以为卡住了
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.progressText.ifBlank { s.status },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "可以退出本页或切后台，下载会继续；通知栏可看进度、也能取消",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.cancelDlsiteDownload() }) {
                        Text("取消", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        // 失败 / 已暂停：给一句能照着做的提示
        st?.takeIf { !it.running && !it.completed }?.let { s ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    s.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    if (s.cancelled) "已停在 " + SizeFormat.text(s.doneBytes) + "（半截文件已删除），要重新下就在页面里再点一次下载"
                    else "回到页面上再点一次下载即可重试",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (done) {
            val saved = st.savedPath ?: ""
            Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
                Text("下载完成", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(st.status, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(saved, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (saved.endsWith(".zip", ignoreCase = true)) {
                    Text(
                        "下载的是压缩包，点下面直接解压即可被曲库识别；rar/7z 请用外部解压工具。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.extractDownloaded() }) { Text("立即解压") }
                } else {
                    Text(
                        "不是压缩包，已放到 曲库根目录/DLsite 下，回曲库刷新即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = {
                        runCatching {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.setDataAndType(
                                androidx.core.content.FileProvider.getUriForFile(
                                    ctx(),
                                    ctx().packageName + ".fileprovider",
                                    java.io.File(saved),
                                ),
                                "application/zip",
                            )
                            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            ctx().startActivity(intent)
                        }.onFailure { vm.say("没有可用的解压应用，请手动打开：" + saved) }
                    }) { Text("用其他应用打开") }
                    TextButton(onClick = { vm.clearDownloadStatus(); onBack() }) { Text("返回曲库") }
                }
            }
        } else AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef.value = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
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
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean = false

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
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    ctx, Manifest.permission.POST_NOTIFICATIONS,
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                runCatching { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            }
                            vm.startDlsiteDownload(
                                downloadUrl,
                                userAgent,
                                contentDisposition ?: ("attachment; filename=\"" + name + "\""),
                                code,
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
