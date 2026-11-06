package com.kiite.player.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局滚动状态：**滚动期间暂停实时毛玻璃**。
 *
 * 实时模糊每帧都要把身后内容重新捕获成纹理再做高斯模糊（Haze 官方基准约 +45% 帧耗时），
 * 静态页面看不出问题，一旦列表滚动就是「每帧一次全屏捕获 + 模糊」，低中端机必然掉帧。
 * 滚动开始时把磨砂栏退回半透明纯色（零 GPU 成本），停下约 140ms 后再恢复毛玻璃，
 * 既保证滚动顺滑，静止时观感不变。
 */
@Stable
class ScrollActivity {
    var scrolling by mutableStateOf(false)
        private set

    fun set(value: Boolean) {
        if (scrolling != value) scrolling = value
    }
}

val LocalScrollActivityHolder = compositionLocalOf { ScrollActivity() }
