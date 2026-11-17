package com.kiite.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 台本阅读面板的配色。
 *
 * 它不属于 `MaterialTheme.colorScheme`：跟随的是「纸页 / 夜间」这个**阅读主题**，
 * 与应用皮肤（EDITORIAL / CLASSIC / …）互不影响 —— 深色皮肤下也可能想看纸页色。
 * 所以单独放一组常量，而不是塞进配色方案。
 */
object ScriptPalette {
    val paperBackground = Color(0xFFF6F1E7)
    val paperText = Color(0xFF4A3F31)
    val paperAccent = Color(0xFF8D6E3F)

    val nightBackground = Color(0xFF14161A)
    val nightText = Color(0xFFC9CDC4)
    val nightAccent = Color(0xFFE8C87A)
}

/**
 * 「iOS 玻璃」皮肤的极淡描边色。
 *
 * 做成函数而不是常量：同一个皮肤在浅色/深色底上要用两种透明度，
 * 而判断底色亮暗要用当前 `colorScheme.surface`，只能在组合期算。
 */
object HairlineStroke {
    private val light = Color(0x143C3C43)
    private val dark = Color.White.copy(alpha = 0.12f)

    fun of(lightSurface: Boolean): Color = if (lightSurface) light else dark
}
