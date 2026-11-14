package com.notecraft.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notecraft.app.data.EditorFont
import com.notecraft.app.data.Settings
import com.notecraft.app.data.ThemeMode

@Immutable
data class Accent(val name: String, val light: Color, val dark: Color)

val Accents = listOf(
    Accent("Ink", Color(0xFF2F6BEA), Color(0xFF8AB0FF)),
    Accent("Amber", Color(0xFFC77A12), Color(0xFFF2B562)),
    Accent("Sage", Color(0xFF3F7F5E), Color(0xFF8CCBA7)),
    Accent("Rose", Color(0xFFC94C6F), Color(0xFFF59AB4)),
    Accent("Violet", Color(0xFF6B4EE6), Color(0xFFB4A2FF)),
    Accent("Graphite", Color(0xFF3B4048), Color(0xFFC9CED6)),
)

@Immutable
data class NoteColor(val name: String, val light: Color, val dark: Color, val swatch: Color)

val NoteColors = listOf(
    NoteColor("Default", Color.Unspecified, Color.Unspecified, Color.Unspecified),
    NoteColor("Coral", Color(0xFFFBE4DE), Color(0xFF3F2622), Color(0xFFF08C74)),
    NoteColor("Peach", Color(0xFFFCEBD7), Color(0xFF3E2E1F), Color(0xFFF2A65A)),
    NoteColor("Sand", Color(0xFFF9F0CC), Color(0xFF39351C), Color(0xFFE6C445)),
    NoteColor("Sage", Color(0xFFE4EFDB), Color(0xFF263524), Color(0xFF86BE73)),
    NoteColor("Mint", Color(0xFFDAF0EB), Color(0xFF1E3532), Color(0xFF55BFAC)),
    NoteColor("Sky", Color(0xFFDEE9FA), Color(0xFF1F2D40), Color(0xFF6A9FEE)),
    NoteColor("Lavender", Color(0xFFEAE3FA), Color(0xFF2F2840), Color(0xFFA48AEE)),
    NoteColor("Rose", Color(0xFFF9DFEA), Color(0xFF3F2430), Color(0xFFEC7FA9)),
    NoteColor("Stone", Color(0xFFECE9E3), Color(0xFF2E2C29), Color(0xFFA59F96)),
)

/** App-specific tokens Material's scheme doesn't cover. */
@Immutable
data class Extra(
    val isDark: Boolean,
    val paper: Color,
    val card: Color,
    val cardBorder: Color,
    val muted: Color,
    val faint: Color,
    val highlight: Color,
    val codeBg: Color,
    val success: Color,
    val danger: Color,
    val editorFont: FontFamily,
    val editorFontSize: Int,
    val haptics: Boolean,
)

val LocalExtra = staticCompositionLocalOf {
    Extra(false, Color.White, Color.White, Color.LightGray, Color.Gray, Color.LightGray, Color.Yellow, Color.LightGray,
        Color.Green, Color.Red, FontFamily.Default, 17, true)
}

object NC {
    val extra: Extra @Composable get() = LocalExtra.current

    @Composable
    fun noteBg(index: Int): Color {
        val c = NoteColors.getOrElse(index) { NoteColors[0] }
        if (index == 0) return LocalExtra.current.card
        return if (LocalExtra.current.isDark) c.dark else c.light
    }
}

private val Display = TextStyle(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.merge(Display).copy(fontSize = 32.sp, lineHeight = 38.sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.1).sp),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = t.labelMedium.copy(letterSpacing = 0.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isAppDark(settings: Settings): Boolean = when (settings.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun NoteCraftTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = isAppDark(settings)
    val amoled = dark && settings.amoled
    val accent = Accents.getOrElse(settings.accent) { Accents[0] }
    val ctx = LocalContext.current

    val paper = when { amoled -> Color.Black; dark -> Color(0xFF131312); else -> Color(0xFFF6F4EF) }
    val card = when { amoled -> Color(0xFF0E0E0E); dark -> Color(0xFF1D1C1A); else -> Color(0xFFFFFFFF) }
    val border = when { amoled -> Color(0xFF1E1E1E); dark -> Color(0xFF2A2926); else -> Color(0xFFE9E5DD) }
    val ink = if (dark) Color(0xFFEDEBE6) else Color(0xFF1C1B19)
    val muted = if (dark) Color(0xFFB3AFA8) else Color(0xFF6F6A62)
    val faint = if (dark) Color(0xFF85817A) else Color(0xFFA9A49B)

    val base: ColorScheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        val primary = if (dark) accent.dark else accent.light
        val onPrimary = if (dark) Color(0xFF111111) else Color.White
        if (dark) darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.22f).compositeOver(card), onPrimaryContainer = ink,
            secondary = muted, onSecondary = Color.Black,
            secondaryContainer = Color(0xFF2B2A27), onSecondaryContainer = ink,
            tertiary = primary,
        ) else lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.12f).compositeOver(Color.White), onPrimaryContainer = Color(0xFF14171C),
            secondary = muted, onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDE9E1), onSecondaryContainer = ink,
            tertiary = primary,
        )
    }

    val scheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) base.copy(
        background = if (amoled) Color.Black else base.background,
        surface = if (amoled) Color.Black else base.surface,
    ) else base.copy(
        background = paper, onBackground = ink,
        surface = paper, onSurface = ink,
        surfaceVariant = if (dark) Color(0xFF262522) else Color(0xFFEFEBE4), onSurfaceVariant = muted,
        surfaceContainerLowest = card,
        surfaceContainerLow = if (amoled) Color(0xFF0A0A0A) else if (dark) Color(0xFF191817) else Color(0xFFFBFAF7),
        surfaceContainer = if (amoled) Color(0xFF121212) else if (dark) Color(0xFF1F1E1C) else Color(0xFFF8F6F2),
        surfaceContainerHigh = if (amoled) Color(0xFF161616) else if (dark) Color(0xFF252422) else Color(0xFFFFFFFF),
        surfaceContainerHighest = if (amoled) Color(0xFF1C1C1C) else if (dark) Color(0xFF2C2B28) else Color(0xFFEFEBE4),
        outline = border, outlineVariant = border,
        error = if (dark) Color(0xFFFF8A80) else Color(0xFFD03B2F),
        inverseSurface = if (dark) Color(0xFFEDEBE6) else Color(0xFF262522),
        inverseOnSurface = if (dark) Color(0xFF1C1B19) else Color(0xFFF3F1EC),
        inversePrimary = if (dark) accent.light else accent.dark,
        scrim = Color.Black,
    )

    val extra = Extra(
        isDark = dark,
        paper = scheme.background,
        card = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) scheme.surfaceContainerLow else card,
        cardBorder = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) scheme.outlineVariant else border,
        muted = scheme.onSurfaceVariant,
        faint = faint,
        highlight = if (dark) Color(0x66C9A227) else Color(0x80FFE27A),
        codeBg = if (dark) Color(0xFF2A2926) else Color(0xFFF0EDE6),
        success = if (dark) Color(0xFF7FD19B) else Color(0xFF2E8B57),
        danger = scheme.error,
        editorFont = when (settings.editorFont) { EditorFont.SERIF -> FontFamily.Serif; EditorFont.MONO -> FontFamily.Monospace; else -> FontFamily.Default },
        editorFontSize = settings.editorFontSize,
        haptics = settings.haptics,
    )

    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
            // Screens draw their own paper backgrounds rather than using Surface, so provide the
            // content color explicitly — otherwise Text/Icon fall back to black in dark mode.
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(
        red = red * a + bg.red * (1 - a),
        green = green * a + bg.green * (1 - a),
        blue = blue * a + bg.blue * (1 - a),
        alpha = 1f,
    )
}
