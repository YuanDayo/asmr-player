package com.asmrplayer.ui

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

/**
 * 在应用内打开 DLsite 页面登录。
 * 登录态由系统 CookieManager 保存，应用拿不到也不会存你的密码；
 * 有了会话 Cookie 之后，成人向作品的「年龄确认」页面也能正常读取元数据。
 */
@Composable
fun DlsiteLoginDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().height(600.dp).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("登录 DLsite", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "登录后可读取成人向作品页面",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                Spacer(Modifier.height(6.dp))
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            loadUrl("https://www.dlsite.com/maniax/")
                        }
                    },
                )
            }
        }
    }
}

/** 是否已有 DLsite 会话 Cookie。 */
fun hasDlsiteSession(): Boolean =
    !CookieManager.getInstance().getCookie("https://www.dlsite.com").isNullOrBlank()

/** 退出登录：清掉所有 Cookie。 */
fun clearDlsiteSession() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
}

/** 取出 Cookie 头，给 HttpURLConnection 用。 */
fun dlsiteCookieHeader(): String? =
    CookieManager.getInstance().getCookie("https://www.dlsite.com")?.takeIf { it.isNotBlank() }
