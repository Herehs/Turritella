package ru.steno.presentation.main

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun ZoomDrawer(
    isMenuOpen: Boolean,
    onCloseMenu: () -> Unit,
    gesturesEnabled: Boolean = true,
    menuContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density

    val screenWidth = configuration.screenWidthDp.dp
    val offsetX by animateFloatAsState(
        targetValue = if (isMenuOpen) (screenWidth.value * 0.6f) * density else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "offset"
    )

    // Масштабирование
    val scale by animateFloatAsState(
        targetValue = if (isMenuOpen) 0.9f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "scale"
    )

    // Анимация скругления углов: 25.dp при открытии, 0.dp при закрытии
    val cornerRadius by animateDpAsState(
        targetValue = if (isMenuOpen) 25.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "cornerRadius"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)) // Цвет фона под меню
    ) {
        // Контент меню (задний план)
        Box(modifier = Modifier.fillMaxSize()) {
            menuContent()
        }

        // Основной контент (экран Home)
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = 0f
                }
                .clip(RoundedCornerShape(cornerRadius))
                .pointerInput(gesturesEnabled) {
                    if (gesturesEnabled) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                if (isMenuOpen && dragAmount < -10) {
                                    onCloseMenu()
                                }
                            }
                        )
                    }
                }
                .clickable(enabled = isMenuOpen) {
                    onCloseMenu()
                }
                .fillMaxSize()
                .background(Color.White)
        ) {
            mainContent()
        }
    }
}