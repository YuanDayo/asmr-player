package com.asmrplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asmrplayer.ui.AppRoot
import com.asmrplayer.ui.MainViewModel
import com.asmrplayer.ui.theme.AsmrTheme
import com.asmrplayer.ui.theme.BackgroundPreset
import com.asmrplayer.ui.theme.SkinStyle
import com.asmrplayer.ui.theme.ThemeMode

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
            ) {
                AppRoot(vm)
            }
        }
    }
}
