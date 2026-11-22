package com.kiite.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 应用的 Miuix 主题（HyperOS 设计语言）。
 *
 * 全项目**只有这一处**把 Miuix 的配色接上应用主题：
 * - 深浅不读系统夜间模式，跟应用自己的背景亮度走（应用是三态主题，与系统解耦）；
 * - 强调色跟当前皮肤（`MaterialTheme.colorScheme.primary`），换强调色时 Miuix 的
 *   primary 一起变，不会出现「设置页是蓝的、按钮是紫的」。
 *
 * 曲库、设置、首页、播放页都包这一层：卡片、按钮、按压反馈才会是同一套材质。
 * 以前曲库与设置各写了一遍同样的 5 行，首页与播放页干脆没包 ——
 * 于是那两页的 Miuix 组件拿的是默认配色，和别的页面对不上。
 */
@Composable
fun AsmrMiuixTheme(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = MaterialTheme.colorScheme.primary
    val accentOn = MaterialTheme.colorScheme.onPrimary
    val colors = if (dark) {
        darkColorScheme(primary = accent, onPrimary = accentOn)
    } else {
        lightColorScheme(primary = accent, onPrimary = accentOn)
    }
    MiuixTheme(colors = colors, content = content)
}
