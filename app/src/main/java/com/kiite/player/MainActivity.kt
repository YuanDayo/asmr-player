package com.kiite.player

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
            ) {
                AppRoot(vm)
            }
        }
    }
}
