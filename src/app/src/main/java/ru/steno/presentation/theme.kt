package ru.steno.presentation

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ru.steno.domain.ThemeMode

/* ───────────── Примитивы (из палитры) ───────────── */

private object Palette {
    // Blue
    val Blue50 = Color(0xFFEFF5FF)
    val Blue100 = Color(0xFFDCE8FF)
    val Blue200 = Color(0xFFB9D0FF)
    val Blue300 = Color(0xFF8AB0FF)
    val Blue400 = Color(0xFF5B8CFF)
    val Blue500 = Color(0xFF2F6BFF)
    val Blue600 = Color(0xFF2355DB)
    val Blue700 = Color(0xFF1B43B0)
    val Blue800 = Color(0xFF153486)
    val Blue900 = Color(0xFF0F2460)

    // Gray
    val Gray50 = Color(0xFFF6F8FB)
    val Gray100 = Color(0xFFEEF1F6)
    val Gray200 = Color(0xFFDDE3EC)
    val Gray300 = Color(0xFFC3CCD9)
    val Gray500 = Color(0xFF7A8699)
    val Gray700 = Color(0xFF3B4554)
    val Gray900 = Color(0xFF131A26)
}

/* ───────────── Семантические цвета ───────────── */

@Immutable
data class StenoColors(
    val isDark: Boolean,
    // Background
    val bg: Color,
    val surface: Color,
    // Text
    val text: Color,
    val textSecondary: Color,
    // Border
    val border: Color,
    // Accent
    val accent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val onAccent: Color,
    // Status
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

val LightStenoColors = StenoColors(
    isDark = false,
    bg = Color(0xFFE9ECEF),
    surface = Color(0xFFFFFFFF),
    text = Palette.Gray900,
    textSecondary = Palette.Gray500,
    border = Palette.Gray200,
    accent = Palette.Blue500,
    accentHover = Palette.Blue600,
    accentPressed = Palette.Blue700,
    accentSoft = Palette.Blue100,
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF16A34A),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFDC2626),
    info = Palette.Blue500,
)

val DarkStenoColors = StenoColors(
    isDark = true,
    bg = Color(0xFF0B1020),
    surface = Color(0xFF121A2E),
    text = Color(0xFFE8EDF7),
    textSecondary = Color(0xFF9AA7C0),
    border = Color(0xFF26314D),
    accent = Palette.Blue400,
    accentHover = Color(0xFF7AA0FF),
    accentPressed = Color(0xFF4A78E6),
    accentSoft = Color(0xFF1B2B55),
    onAccent = Color(0xFF0B1020),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    error = Color(0xFFF87171),
    info = Palette.Blue400,
)

/** Не static: цвета меняются каждый кадр анимации, перекомпонируются только те, кто их читает. */
val LocalStenoColors = compositionLocalOf { LightStenoColors }

/** Линейная интерполяция всех токенов. [isDark] берётся от целевой темы. */
private fun lerpColors(from: StenoColors, to: StenoColors, f: Float) = StenoColors(
    isDark = if (f < 0.5f) from.isDark else to.isDark,
    bg = lerp(from.bg, to.bg, f),
    surface = lerp(from.surface, to.surface, f),
    text = lerp(from.text, to.text, f),
    textSecondary = lerp(from.textSecondary, to.textSecondary, f),
    border = lerp(from.border, to.border, f),
    accent = lerp(from.accent, to.accent, f),
    accentHover = lerp(from.accentHover, to.accentHover, f),
    accentPressed = lerp(from.accentPressed, to.accentPressed, f),
    accentSoft = lerp(from.accentSoft, to.accentSoft, f),
    onAccent = lerp(from.onAccent, to.onAccent, f),
    success = lerp(from.success, to.success, f),
    warning = lerp(from.warning, to.warning, f),
    error = lerp(from.error, to.error, f),
    info = lerp(from.info, to.info, f),
)

/** Доступ из любого Composable: `StenoTheme.colors.accentSoft`. */
object StenoTheme {
    val colors: StenoColors
        @Composable @ReadOnlyComposable get() = LocalStenoColors.current
}

/* ───────────── Маппинг на Material 3 ───────────── */

private fun StenoColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentSoft,
        onSecondaryContainer = text,
        tertiary = info,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        surfaceVariant = bg,
        onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        outline = border,
        outlineVariant = border,
        error = error,
        onError = if (isDark) bg else Color.White,
        errorContainer = error.copy(alpha = 0.16f),
        onErrorContainer = error,
    )
}

/* ───────────── Тема ───────────── */

@Composable
fun StenoTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Один прогресс 0..1 на всю палитру. При первой композиции анимации нет:
    // animateFloatAsState стартует сразу с целевого значения.
    val progress by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "themeProgress",
    )
    val colors = remember(progress) { lerpColors(LightStenoColors, DarkStenoColors, progress) }

    // Иконки статус-бара и навигации: тёмные на светлой теме и наоборот.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalStenoColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            content = content,
        )
    }
}