package com.fuke.daily.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════
//  扩展颜色 — 通过 LocalExtendedColors 在 Compose 树中传递
// ═══════════════════════════════════════════════════

data class ExtendedColorPalette(
    val bg: Color,
    val card: Color,
    val primary: Color,
    val accent: Color,
    val muted: Color,
    val text: Color,
    val light: Color,
    val border: Color,
    val success: Color,
    val contentBg: Color,
    val inputBg: Color,
    val slotColors: List<Color>,
    val fixedSlotColors: List<Color>,
    val overlay: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColorPalette(
        bg = Color.Unspecified,
        card = Color.Unspecified,
        primary = Color.Unspecified,
        accent = Color.Unspecified,
        muted = Color.Unspecified,
        text = Color.Unspecified,
        light = Color.Unspecified,
        border = Color.Unspecified,
        success = Color.Unspecified,
        contentBg = Color.Unspecified,
        inputBg = Color.Unspecified,
        slotColors = emptyList(),
        fixedSlotColors = emptyList(),
        overlay = Color.Unspecified,
    )
}

// 便捷访问扩展颜色
object FukeTheme {
    val extended: ExtendedColorPalette
        @Composable @ReadOnlyComposable
        get() = LocalExtendedColors.current

    val mainlineSelect: MainlineSelectPalette
        @Composable @ReadOnlyComposable
        get() = LocalMainlineSelectColors.current
}

// ═══════════════════════════════════════════════════
//  主线选择页颜色（固定深色，不受主题切换影响）
// ═══════════════════════════════════════════════════

data class MainlineSelectPalette(
    val bg: Color,
    val card: Color,
    val gold: Color,
    val goldDim: Color,
    val textLight: Color,
    val muted: Color,
)

val LocalMainlineSelectColors = staticCompositionLocalOf {
    MainlineSelectPalette(
        bg = MainlineSelectColors.bg,
        card = MainlineSelectColors.card,
        gold = MainlineSelectColors.gold,
        goldDim = MainlineSelectColors.goldDim,
        textLight = MainlineSelectColors.textLight,
        muted = MainlineSelectColors.muted,
    )
}

// ═══════════════════════════════════════════════════
//  主题模式
// ═══════════════════════════════════════════════════

enum class ThemeMode { WARM, PURPLE }

// ═══════════════════════════════════════════════════
//  Material3 ColorScheme 构建
// ═══════════════════════════════════════════════════

private val WarmColorScheme = lightColorScheme(
    primary = WarmColors.primary,
    onPrimary = Color.White,
    primaryContainer = WarmColors.accent,
    onPrimaryContainer = WarmColors.text,
    secondary = WarmColors.muted,
    onSecondary = Color.White,
    secondaryContainer = WarmColors.light,
    onSecondaryContainer = WarmColors.text,
    tertiary = WarmColors.success,
    background = WarmColors.bg,
    onBackground = WarmColors.text,
    surface = WarmColors.card,
    onSurface = WarmColors.text,
    surfaceVariant = WarmColors.contentBg,
    onSurfaceVariant = WarmColors.muted,
    outline = WarmColors.border,
    outlineVariant = WarmColors.border,
    error = Color(0xFFD4534A),
    onError = Color.White,
)

private val PurpleColorScheme = darkColorScheme(
    primary = PurpleColors.primary,
    onPrimary = Color.White,
    primaryContainer = PurpleColors.accent,
    onPrimaryContainer = PurpleColors.text,
    secondary = PurpleColors.muted,
    onSecondary = Color(0xFF1A1025),
    secondaryContainer = PurpleColors.light,
    onSecondaryContainer = PurpleColors.text,
    tertiary = PurpleColors.success,
    background = PurpleColors.bg,
    onBackground = PurpleColors.text,
    surface = PurpleColors.card,
    onSurface = PurpleColors.text,
    surfaceVariant = PurpleColors.contentBg,
    onSurfaceVariant = PurpleColors.muted,
    outline = PurpleColors.border,
    outlineVariant = PurpleColors.border,
    error = Color(0xFFEF4444),
    onError = Color.White,
)

// ═══════════════════════════════════════════════════
//  扩展颜色映射
// ═══════════════════════════════════════════════════

private val WarmExtended = ExtendedColorPalette(
    bg = WarmColors.bg,
    card = WarmColors.card,
    primary = WarmColors.primary,
    accent = WarmColors.accent,
    muted = WarmColors.muted,
    text = WarmColors.text,
    light = WarmColors.light,
    border = WarmColors.border,
    success = WarmColors.success,
    contentBg = WarmColors.contentBg,
    inputBg = WarmColors.inputBg,
    slotColors = WarmColors.slotColors,
    fixedSlotColors = WarmColors.fixedSlotColors,
    overlay = WarmColors.overlay,
)

private val PurpleExtended = ExtendedColorPalette(
    bg = PurpleColors.bg,
    card = PurpleColors.card,
    primary = PurpleColors.primary,
    accent = PurpleColors.accent,
    muted = PurpleColors.muted,
    text = PurpleColors.text,
    light = PurpleColors.light,
    border = PurpleColors.border,
    success = PurpleColors.success,
    contentBg = PurpleColors.contentBg,
    inputBg = PurpleColors.inputBg,
    slotColors = PurpleColors.slotColors,
    fixedSlotColors = PurpleColors.fixedSlotColors,
    overlay = PurpleColors.overlay,
)

// ═══════════════════════════════════════════════════
//  主题入口
// ═══════════════════════════════════════════════════

@Composable
fun FukeDailyTheme(
    themeMode: ThemeMode = ThemeMode.WARM,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeMode) {
        ThemeMode.WARM -> WarmColorScheme
        ThemeMode.PURPLE -> PurpleColorScheme
    }

    val extendedColors = when (themeMode) {
        ThemeMode.WARM -> WarmExtended
        ThemeMode.PURPLE -> PurpleExtended
    }

    androidx.compose.material3.ProvideTextStyle(
        value = FukeTypography.bodyLarge,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalExtendedColors provides extendedColors,
            LocalMainlineSelectColors provides MainlineSelectPalette(
                bg = MainlineSelectColors.bg,
                card = MainlineSelectColors.card,
                gold = MainlineSelectColors.gold,
                goldDim = MainlineSelectColors.goldDim,
                textLight = MainlineSelectColors.textLight,
                muted = MainlineSelectColors.muted,
            ),
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = FukeTypography,
                shapes = FukeShapes,
                content = content,
            )
        }
    }
}
