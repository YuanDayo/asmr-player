package com.asmrplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

/** 卡片 / 卡片类容器的背景不透明度，可在设置里调。 */
val LocalCardAlpha = compositionLocalOf { 1f }

/** 顶栏与底栏的不透明度（给个下限，保证可读）。 */
val LocalBarAlpha = compositionLocalOf { 1f }

/** 深色模式三态。 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun of(id: String?): ThemeMode = values().firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** 内置背景预设（不想挑图片时直接用）。 */
enum class BackgroundPreset(val id: String, val label: String) {
    NONE("none", "默认"),
    DEEP_SEA("deep_sea", "深海渐变"),
    SAKURA("sakura", "夜樱"),
    MINT("mint", "薄荷"),
    PAPER("paper", "纸纹");

    companion object {
        fun of(id: String?): BackgroundPreset = values().firstOrNull { it.id == id } ?: NONE
    }
}

/** 皮肤配色。 */
enum class SkinStyle(val id: String, val label: String) {
    DEEP_SEA("deep_sea", "深海"),
    MINT("mint", "薄荷"),
    SAKURA("sakura", "樱花"),
    PAPER("paper", "纸质");

    companion object {
        fun of(id: String?): SkinStyle = values().firstOrNull { it.id == id } ?: DEEP_SEA
    }
}

private fun lightScheme(style: SkinStyle): ColorScheme = when (style) {
    SkinStyle.DEEP_SEA -> lightColorScheme(
        primary = Color(0xFF2F6F8F), onPrimary = Color.White, secondary = Color(0xFFE8B96A),
        background = Color(0xFFF7F9FB), surface = Color.White, surfaceVariant = Color(0xFFE3EAF0),
    )
    SkinStyle.MINT -> lightColorScheme(
        primary = Color(0xFF1F7A6B), onPrimary = Color.White, secondary = Color(0xFF8FD3B6),
        background = Color(0xFFF3FAF7), surface = Color.White, surfaceVariant = Color(0xFFDCEFE7),
    )
    SkinStyle.SAKURA -> lightColorScheme(
        primary = Color(0xFFB4576F), onPrimary = Color.White, secondary = Color(0xFFF0A9BC),
        background = Color(0xFFFDF6F7), surface = Color.White, surfaceVariant = Color(0xFFF6E1E7),
    )
    SkinStyle.PAPER -> lightColorScheme(
        primary = Color(0xFF6B5B45), onPrimary = Color.White, secondary = Color(0xFFC9A227),
        background = Color(0xFFFAF6EE), surface = Color(0xFFFFFDF8), surfaceVariant = Color(0xFFEDE4D3),
    )
}

private fun darkScheme(style: SkinStyle): ColorScheme = when (style) {
    SkinStyle.DEEP_SEA -> darkColorScheme(
        primary = Color(0xFF7FC8E8), onPrimary = Color(0xFF00323F), secondary = Color(0xFFE8B96A),
        background = Color(0xFF101619), surface = Color(0xFF182025), surfaceVariant = Color(0xFF243038),
    )
    SkinStyle.MINT -> darkColorScheme(
        primary = Color(0xFF7FD8C4), onPrimary = Color(0xFF00312A), secondary = Color(0xFF8FD3B6),
        background = Color(0xFF0C1614), surface = Color(0xFF14201D), surfaceVariant = Color(0xFF1F2E2A),
    )
    SkinStyle.SAKURA -> darkColorScheme(
        primary = Color(0xFFF0A9BC), onPrimary = Color(0xFF3E1220), secondary = Color(0xFFE7C6D2),
        background = Color(0xFF1A1114), surface = Color(0xFF241A1E), surfaceVariant = Color(0xFF33252A),
    )
    SkinStyle.PAPER -> darkColorScheme(
        primary = Color(0xFFD8C39A), onPrimary = Color(0xFF2A2216), secondary = Color(0xFFC9A227),
        background = Color(0xFF16130E), surface = Color(0xFF201C15), surfaceVariant = Color(0xFF2E281E),
    )
}

private fun presetBrush(preset: BackgroundPreset, dark: Boolean): Brush? = when (preset) {
    BackgroundPreset.NONE -> null
    BackgroundPreset.DEEP_SEA -> Brush.verticalGradient(
        listOf(Color(0xFF0B1A22), Color(0xFF12303D)).takeIf { dark }
            ?: listOf(Color(0xFFDCEEF7), Color(0xFFF8FBFD)),
    )
    BackgroundPreset.SAKURA -> Brush.verticalGradient(
        listOf(Color(0xFF241419), Color(0xFF3A222A)).takeIf { dark }
            ?: listOf(Color(0xFFFBE7EC), Color(0xFFFFF8FA)),
    )
    BackgroundPreset.MINT -> Brush.verticalGradient(
        listOf(Color(0xFF0C1D1A), Color(0xFF16332C)).takeIf { dark }
            ?: listOf(Color(0xFFDFF3EC), Color(0xFFF6FCF9)),
    )
    BackgroundPreset.PAPER -> Brush.verticalGradient(
        listOf(Color(0xFF1C1811), Color(0xFF2A2318)).takeIf { dark }
            ?: listOf(Color(0xFFF7EFDD), Color(0xFFFEFCF6)),
    )
}

@Composable
fun AsmrTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    style: SkinStyle = SkinStyle.DEEP_SEA,
    backgroundImage: String? = null,
    backgroundPreset: BackgroundPreset = BackgroundPreset.NONE,
    cardAlpha: Float = 1f,
    backgroundBlur: Float = 0f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = if (dark) darkScheme(style) else lightScheme(style)

    // 放大一点再模糊，避免模糊后四周出现透明边
    val blurModifier = if (backgroundBlur > 0f) {
        Modifier.graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }.blur(backgroundBlur.dp)
    } else {
        Modifier
    }

    CompositionLocalProvider(
        LocalCardAlpha provides cardAlpha.coerceIn(0.15f, 1f),
        LocalBarAlpha provides maxOf(0.78f, cardAlpha).coerceAtMost(1f),
    ) {
        MaterialTheme(colorScheme = scheme) {
            Box(Modifier.fillMaxSize().background(scheme.background)) {
                val imageFile = backgroundImage?.let(::File)?.takeIf { it.isFile }
                val brush = presetBrush(backgroundPreset, dark)
                when {
                    imageFile != null -> {
                        AsyncImage(
                            model = imageFile,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().then(blurModifier),
                        )
                        // 压一层底色保证文字可读（背景越透，这层越淡）
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(scheme.background.copy(alpha = if (dark) 0.72f else 0.68f)),
                        )
                    }
                    brush != null -> Box(Modifier.fillMaxSize().background(brush).then(blurModifier))
                }
                content()
            }
        }
    }
}
