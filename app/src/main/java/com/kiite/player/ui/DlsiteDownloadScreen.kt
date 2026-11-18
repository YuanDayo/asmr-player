package com.kiite.player.ui

import android.Manifest
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiite.player.core.SizeFormat
import com.kiite.player.ui.theme.Spacing
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DlsiteDownloadScreen 独有的控件尺寸。
 *
 * 这些不进全局令牌：它们是本页特有的控件大小（图标按钮、进度条、封面档位），
 * 同值不同义，硬塞进 Spacing / IconSize 会让令牌表变成一张数字表。
 * 集中在这里的目的是可搜、可一处改；真正的归一化（图标按钮统一到 48dp
 * 触控目标）留给单独一批，因为会改变布局。
 */
private object DownloadDim {
    val height5 = 5.dp
}

/**
 * DLsite 下载页。
 *
 * **已购作品（owned = true，且不是第三方站）不再打开网页**：进来就直接向 DLsite 请求
 * 「该下哪些文件 + 每个文件的直链与临时令牌」，然后交给应用级下载器（通知栏有进度，
 * 切页面/后台都不中断）。
 *
 * 为什么改掉「打开下载页让用户点」：
 *  - 点完下载按钮后页面会被 302 带走到 CDN，WebView 停在一块空白页上 —— 深色主题下
 *    就是用户反馈的「黑屏，直到下载结束」；
 *  - 拦截下来的直链要用 302 响应里下发的临时令牌（JWT）才能拉，用登录会话 Cookie 去
 *    重放会被 CDN 拒（用户反馈的「已购作品下载 403」）。
 *
 * 网页仍然保留，但只作兜底：解析失败时用户可以点「在网页里打开」自己操作，未购作品、
 * asmr.one 这类第三方站也照旧走网页。
 *
 * @param owned 已购作品 → 应用内下载；未购 → 作品页（下载页对未购直接 403）。
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
    val pageUrl = remember(code, owned, startUrl) {
        startUrl ?: if (owned) com.kiite.player.core.DlsiteAuth.downloadUrl(code)
        else com.kiite.player.core.DlsiteAuth.workUrl(code)
    }
    // 只有「第三方站 / 未购作品」默认进网页；已购作品走应用内下载
    var web by remember(code, startUrl, owned) { mutableStateOf(startUrl != null || !owned) }
    // 进页面就把地址要下来：直链有时效，每次进入都重新取一次
    LaunchedEffect(code, owned, startUrl) {
        if (owned && startUrl == null) vm.downloadDlsiteOwned(code)
    }

    // 通知栏进度需要 POST_NOTIFICATIONS（Android 13+）。拒绝也能下载，只是看不到通知。
    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 授权与否都继续，下载不依赖通知 */ }

    // 本页是全屏二级页：藏起迷你播放条与导航胶囊，返回时恢复；并给系统栏让位
    DisposableEffect(Unit) {
        vm.setChromeHidden(true)
        onDispose { vm.setChromeHidden(false) }
    }

    // 系统返回键：网页有历史就先在页面内后退，没有才退出本页（不直接杀掉应用）
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    androidx.activity.compose.BackHandler {
        val wv = webViewRef.value
        if (web && wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    val done = st?.completed == true && st?.savedPath != null
    val pageBackground = MaterialTheme.colorScheme.background.toArgb()
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    heading ?: if (owned) "下载 " + code else "DLsite · " + code,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    hint ?: when {
                        !owned -> "未购买作品从作品页进入；购买后点「下载」会直接存到 曲库根目录/DLsite"
                        web -> "在页面里点 DLsite 的下载按钮，会自动保存到 曲库根目录/DLsite"
                        else -> "直接存到 曲库根目录/DLsite，不用在网页里点；退出本页也会继续下"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!web && owned) {
                TextButton(onClick = { web = true }) { Text("网页") }
            } else if (web && owned && startUrl == null) {
                TextButton(onClick = { web = false; vm.downloadDlsiteOwned(code) }) { Text("应用内") }
            }
            TextButton(onClick = onBack) { Text("完成") }
        }

        if (web) {
            if (loading && !done) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.itemGap),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // ---------- 下载进度（不遮挡 WebView，可以边下边继续浏览） ----------
        st?.takeIf { it.running }?.let { s ->
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.itemGap)) {
                if (s.hasTotal) {
                    DownloadProgressBar(
                        progress = s.progress,
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        height = DownloadDim.height5,
                    )
                } else {
                    // 服务端没给 Content-Length（或还在解析地址）：画无限进度，别让用户以为卡住了
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        // 还没拿到 Content-Length 时（正在要地址 / 刚连上）没有百分比可展示，
                        // 这时显示状态本身（「正在向 DLsite 请求下载地址…」），别给一行 0 B
                        Text(
                            if (s.hasTotal) s.progressText else s.status,
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
        // 失败 / 已暂停：给一句能照着做的提示，并且给两条出路（重试 / 网页）
        st?.takeIf { !it.running && !it.completed }?.let { s ->
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.itemGap)) {
                Text(
                    s.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (s.cancelled) {
                    Text(
                        "已停在 " + SizeFormat.text(s.doneBytes) + "（半截文件已删除）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(onClick = { vm.downloadDlsiteOwned(code); web = false }) { Text("重试") }
                    if (!web) {
                        TextButton(onClick = { web = true }) { Text("在网页里打开") }
                    }
                }
            }
        }

        if (done) {
            val savedList = st.savedPaths.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(st.savedPath)
            val saved = savedList.firstOrNull().orEmpty()
            Column(Modifier.weight(1f).fillMaxWidth().padding(Spacing.section)) {
                Text(
                    if (savedList.size > 1) "下载完成（" + savedList.size + " 个文件）" else "下载完成",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(st.status, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.itemGap))
                savedList.take(8).forEach {
                    Text(
                        File(it).name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (savedList.size > 8) {
                    Text(
                        "（共 " + savedList.size + " 个，只列出前 8 个）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.itemGap))
                Text(
                    saved,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.lg))
                if (saved.endsWith(".zip", ignoreCase = true)) {
                    Text(
                        "下载的是压缩包，点下面直接解压即可被曲库识别；rar/7z 请用外部解压工具。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    TextButton(onClick = { vm.extractDownloaded() }) { Text("立即解压") }
                } else {
                    Text(
                        "不是压缩包，已放到 曲库根目录/DLsite 下，回曲库刷新即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Row {
                    TextButton(onClick = {
                        runCatching {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.setDataAndType(
                                androidx.core.content.FileProvider.getUriForFile(
                                    ctx(),
                                    ctx().packageName + ".fileprovider",
                                    File(saved),
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
        } else if (web) AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef.value = this
                    // WebView 没绘制完成时是黑的；跟着主题走，至少不会突然一块黑
                    setBackgroundColor(pageBackground)
                    // 用软件图层：Chromium 的默认（硬件/穿透）渲染会把自己单开成一层
                    // **盖住整个窗口**，应用自己的顶栏与进度条都被吞掉 —— 用户截图里
                    // 「点下载后整屏变黑、只剩状态栏」就是这个。下载页是低频兜底路径，
                    // 平滑滚动的代价换「一定看得见界面」很划算。
                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
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
                            view: WebView?,
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
                            error = null
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
                            // 关键：用**这条直链自己的 Cookie**。DLsite 的直链认的是 302 时下发的
                            // 临时令牌（存在 CookieManager 里），带上登录会话反而 403。
                            val linkCookie = runCatching {
                                CookieManager.getInstance().getCookie(downloadUrl)
                            }.getOrNull()
                            vm.startDlsiteDownload(
                                downloadUrl,
                                userAgent,
                                contentDisposition ?: ("attachment; filename=\"" + name + "\""),
                                code,
                                cookie = linkCookie,
                                referer = webViewRef.value?.url ?: pageUrl,
                            )
                            // 下载会被 302 带走、页面停在空白页（深色主题下像黑屏）：
                            // 下载已经跑在应用级作用域里，把页面拉回下载页，用户还能继续点下一个文件
                            scope.launch {
                                delay(800)
                                webViewRef.value?.let { wv ->
                                    if (wv.url != pageUrl) runCatching { wv.loadUrl(pageUrl) }
                                }
                            }
                        }
                    }
                    loadUrl(pageUrl)
                }
            },
        ) else Column(
            Modifier.weight(1f).fillMaxWidth().padding(Spacing.section),
            verticalArrangement = Arrangement.Top,
        ) {
            // 原生路径的「还没开始」状态：解析地址期间这里什么都不用做，
            // 进度块在上面；解析失败会在上面给出原因与两条出路。
            if (st == null) {
                Text("准备下载 " + code, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.itemGap))
                Text(
                    "应用会直接向 DLsite 请求下载地址并保存到 曲库根目录/DLsite，" +
                        "不需要在网页里点按钮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.lg))
                TextButton(onClick = { vm.downloadDlsiteOwned(code) }) { Text("开始下载") }
                TextButton(onClick = { web = true }) { Text("在网页里打开") }
            }
        }
        Spacer(Modifier.height(Spacing.itemGap))
    }
}
