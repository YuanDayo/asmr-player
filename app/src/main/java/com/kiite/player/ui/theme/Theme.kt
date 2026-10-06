package com.kiite.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/** 贴近 HyperOS 的大圆角。 */
private val AsmrShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** 卡片 / 卡片类容器的背景不透明度，可在设置里调。 */
val LocalCardAlpha = compositionLocalOf { 1f }

/** 顶栏与底栏的不透明度（给个下限，保证可读）。 */
val LocalBarAlpha = compositionLocalOf { 1f }

/** 是否处于「明快」皮肤：2px 墨黑描边 + 硬投影 + 黄色填充。 */
val LocalIsBright = compositionLocalOf { false }

/** 深色模式三态。 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun of(id: String?): ThemeMode = values().firstOrNull { it.id == id } ?: LIGHT
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
    KIITE("kiite", "kiite"),
    BRIGHT("bright", "明快"),
    DEEP_SEA("deep_sea", "深海"),
    MINT("mint", "薄荷"),
    SAKURA("sakura", "樱花"),
    PAPER("paper", "纸质");

    companion object {
        fun of(id: String?): SkinStyle = values().firstOrNull { it.id == id } ?: KIITE
    }
}

/** 强调色（主题色）可换。DEFAULT 表示跟随皮肤配色。 */
enum class AccentColor(
    val id: String,
    val label: String,
    val light: Color,
    val dark: Color,
    val onLight: Color,
    val onDark: Color,
) {
    DEFAULT("default", "跟随皮肤", Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified),
    AMBER("amber", "琥珀黄", Color(0xFFF5D90A), Color(0xFFF5D90A), Color(0xFF111111), Color(0xFF111111)),
    OCEAN("ocean", "海蓝", Color(0xFF2F6F8F), Color(0xFF7FC8E8), Color.White, Color(0xFF00323F)),
    MINT("mint", "薄荷绿", Color(0xFF1F7A6B), Color(0xFF7FD8C4), Color.White, Color(0xFF00312A)),
    SAKURA("sakura", "樱粉", Color(0xFFB4576F), Color(0xFFF0A9BC), Color.White, Color(0xFF3E1220)),
    GRAPE("grape", "葡萄紫", Color(0xFF6A4FA3), Color(0xFFC4B0F0), Color.White, Color(0xFF2A1B4A)),
    CORAL("coral", "珊瑚橙", Color(0xFFC25E2A), Color(0xFFFFB07C), Color.White, Color(0xFF3A1A06)),
    LIME("lime", "青柠", Color(0xFF5E8C1F), Color(0xFFCBEB86), Color.White, Color(0xFF1E2C06));

    companion object {
        fun of(id: String?): AccentColor = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}

private fun lightScheme(style: SkinStyle): ColorScheme = when (style) {
    // kiite：看板娘的冷灰 + 耳机青光
    SkinStyle.KIITE -> lightColorScheme(
        primary = Color(0xFF232A30), onPrimary = Color(0xFF4FD8E8),
        secondary = Color(0xFF4FD8E8), onSecondary = Color(0xFF0E1418),
        secondaryContainer = Color(0xFF4FD8E8), onSecondaryContainer = Color(0xFF0E1418),
        background = Color(0xFFF1F4F7), onBackground = Color(0xFF232A30),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF232A30),
        surfaceVariant = Color(0xFFE3E9EE), onSurfaceVariant = Color(0xFF63707B),
        outline = Color(0xFF232A30), outlineVariant = Color(0xFFD3DBE1),
    )
    // 范例风格：纸白 / 墨黑 / 功能黄，2px 描边
    SkinStyle.BRIGHT -> lightColorScheme(
        primary = Color(0xFF111111), onPrimary = Color(0xFFF5D90A),
        secondary = Color(0xFFF5D90A), onSecondary = Color(0xFF111111),
        secondaryContainer = Color(0xFFF5D90A), onSecondaryContainer = Color(0xFF111111),
        background = Color(0xFFF4F3EE), onBackground = Color(0xFF111111),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFE7E5DD), onSurfaceVariant = Color(0xFF6B6963),
        outline = Color(0xFF111111), outlineVariant = Color(0xFFD9D7CE),
    )
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
    SkinStyle.KIITE -> darkColorScheme(
        primary = Color(0xFF4FD8E8), onPrimary = Color(0xFF0E1418),
        secondary = Color(0xFF4FD8E8), onSecondary = Color(0xFF0E1418),
        secondaryContainer = Color(0xFF4FD8E8), onSecondaryContainer = Color(0xFF0E1418),
        background = Color(0xFF10161A), onBackground = Color(0xFFE9EFF3),
        surface = Color(0xFF1A2228), onSurface = Color(0xFFE9EFF3),
        surfaceVariant = Color(0xFF26313A), onSurfaceVariant = Color(0xFFAAB6C0),
        outline = Color(0xFFE9EFF3), outlineVariant = Color(0xFF33404A),
    )
    SkinStyle.BRIGHT -> darkColorScheme(
        primary = Color(0xFFF5D90A), onPrimary = Color(0xFF111111),
        secondary = Color(0xFFF5D90A), onSecondary = Color(0xFF111111),
        secondaryContainer = Color(0xFFF5D90A), onSecondaryContainer = Color(0xFF111111),
        background = Color(0xFF121210), onBackground = Color(0xFFF4F3EE),
        surface = Color(0xFF1E1E19), onSurface = Color(0xFFF4F3EE),
        surfaceVariant = Color(0xFF2A2A23), onSurfaceVariant = Color(0xFFB9B6AC),
        outline = Color(0xFFF4F3EE), outlineVariant = Color(0xFF3A3A31),
    )
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

/** 无衬线标题 + 等宽标签，做出排版层次。 */
private val KiiteTypography = Typography().let { b ->
    b.copy(
        displaySmall = b.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = b.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black),
        titleLarge = b.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, letterSpacing = 0.2.sp),
        titleMedium = b.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
        titleSmall = b.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
        bodyLarge = b.bodyLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.2.sp),
        bodyMedium = b.bodyMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.2.sp),
        bodySmall = b.bodySmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.15.sp),
        labelLarge = b.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        labelMedium = b.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        labelSmall = b.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
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
    accent: AccentColor = AccentColor.DEFAULT,
    backgroundDim: Float = 0.70f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val base = if (dark) darkScheme(style) else lightScheme(style)
    val bright = style == SkinStyle.BRIGHT || style == SkinStyle.KIITE
    // 主题色：不影响皮肤本身，只换强调色。
    // 明快皮肤下主色是墨黑（保证纸面可读），强调色只落到「填充」上（选中态那块黄）。
    val scheme = when {
        accent == AccentColor.DEFAULT -> base
        bright -> base.copy(
            secondaryContainer = if (dark) accent.dark else accent.light,
            onSecondaryContainer = if (dark) accent.onDark else accent.onLight,
        )
        else -> base.copy(
            primary = if (dark) accent.dark else accent.light,
            onPrimary = if (dark) accent.onDark else accent.onLight,
        )
    }

    // 放大一点再模糊，避免模糊后四周出现透明边
    val blurModifier = if (backgroundBlur > 0f) {
        Modifier.graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }.blur(backgroundBlur.dp)
    } else {
        Modifier
    }

    CompositionLocalProvider(
        LocalCardAlpha provides cardAlpha.coerceIn(0.15f, 1f),
        LocalBarAlpha provides maxOf(0.78f, cardAlpha).coerceAtMost(1f),
        LocalIsBright provides bright,
        // 关键：没有 Surface 时 LocalContentColor 默认是黑色，深色模式下纯文本台本会看不见
        LocalContentColor provides scheme.onBackground,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = AsmrShapes, typography = KiiteTypography) {
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
                                .background(scheme.background.copy(alpha = backgroundDim.coerceIn(0f, 0.97f))),
                        )
                    }
                    brush != null -> Box(Modifier.fillMaxSize().background(brush).then(blurModifier))
                }
                content()
            }
        }
    }
}
