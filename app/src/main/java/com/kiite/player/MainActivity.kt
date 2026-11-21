package com.kiite.player

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiite.player.ui.AppRoot
import com.kiite.player.ui.MainViewModel
import com.kiite.player.ui.theme.AccentColor
import com.kiite.player.ui.theme.AsmrTheme
import com.kiite.player.ui.theme.BackgroundPreset
import com.kiite.player.ui.theme.SkinStyle
import com.kiite.player.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            AsmrTheme(
                themeMode = ThemeMode.of(settings.themeMode),
                style = SkinStyle.of(settings.skinStyle),
                backgroundImage = settings.backgroundImage,
                backgroundPreset = BackgroundPreset.of(settings.backgroundPreset),
                cardAlpha = settings.cardAlpha,
                backgroundBlur = settings.backgroundBlur,
                accent = AccentColor.of(settings.accentColor),
                backgroundDim = settings.backgroundDim,
                // 模拟器（SwiftShader 软件 GL）跑不动 Haze 的每帧全屏捕获 + 高斯模糊：
                // 开着实时毛玻璃会把 CPU 顶到 100%、连弹 ANR。这里只对模拟器兜底关掉，
                // 真机照常走 settings.liveBlur。
                liveBlur = settings.liveBlur && !isEmulator(),
                simplifiedMotion = settings.simplifiedMotion,
            ) {
                AppRoot(vm)
            }
        }
    }
}

/** 保守判模拟器：AVD 的指纹/硬件名足够稳定，真机不会命中。 */
private fun isEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT ?: ""
    val hardware = Build.HARDWARE ?: ""
    return fingerprint.startsWith("generic") ||
        fingerprint.contains("emulator") ||
        hardware.contains("goldfish") ||
        hardware.contains("ranchu")
}
