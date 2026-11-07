package ru.steno

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ru.steno.domain.ThemeMode

internal val HexDark = staticCompositionLocalOf { false }
internal val Golos = FontFamily(Font(R.font.golos_text))
private val Light = lightColorScheme(primary=Color(0xff2f6bff), background=Color(0xffe9ecef),
    surface=Color.White, onSurface=Color(0xff131a26), onBackground=Color(0xff131a26),
    onSurfaceVariant=Color(0xff7a8699), secondary=Color(0xff7a8699))
private val Dark = darkColorScheme(primary=Color(0xff5b8cff), background=Color(0xff0b1020),
    surface=Color(0xff121a2e), onSurface=Color(0xffe8edf7), onBackground=Color(0xffe8edf7),
    onSurfaceVariant=Color(0xff9aa7c0), secondary=Color(0xff9aa7c0))

@Composable internal fun HexTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val colors = if(dark) Dark else Light
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = colors.background.toArgb()
        window.navigationBarColor = colors.background.toArgb()
        WindowCompat.getInsetsController(window,view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    val base = Typography()
    val typography = Typography(
        bodyLarge=base.bodyLarge.copy(fontFamily=Golos), bodyMedium=base.bodyMedium.copy(fontFamily=Golos),
        bodySmall=base.bodySmall.copy(fontFamily=Golos), titleLarge=base.titleLarge.copy(fontFamily=Golos),
        titleMedium=base.titleMedium.copy(fontFamily=Golos), titleSmall=base.titleSmall.copy(fontFamily=Golos),
        labelLarge=base.labelLarge.copy(fontFamily=Golos), labelMedium=base.labelMedium.copy(fontFamily=Golos),
        labelSmall=base.labelSmall.copy(fontFamily=Golos), headlineSmall=base.headlineSmall.copy(fontFamily=Golos))
    CompositionLocalProvider(HexDark provides dark) {
        MaterialTheme(colorScheme=colors, typography=typography) {
            Surface(Modifier.fillMaxSize(), color=colors.background) {
                Box(Modifier.safeDrawingPadding()) { content() }
            }
        }
    }
}

/** Оригинальные Figma SVG хранятся в design/hexteam, PNG — их точный рендер 4×. */
@Composable internal fun HexImage(light: Int, dark: Int = light, description: String? = null,
    width: Int = 24, height: Int = width) {
    Image(painterResource(if(HexDark.current) dark else light),description,Modifier.size(width.dp,height.dp))
}

@Composable internal fun BackButton(onClick: () -> Unit) {
    Surface(onClick=onClick, shape=CircleShape, color=MaterialTheme.colorScheme.surface, modifier=Modifier.size(50.dp)) {
        Box(contentAlignment=androidx.compose.ui.Alignment.Center) {
            HexImage(R.drawable.hex_431_4900_arrow_back_up,R.drawable.hex_421_4137_arrow_back_up,"Назад",30)
        }
    }
}
