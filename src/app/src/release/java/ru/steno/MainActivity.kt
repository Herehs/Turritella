package ru.steno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Единственная Activity приложения.
 *
 * Здесь размещается навигационный хост для [ru.steno.presentation.navigation.MainRoute],
 * [ru.steno.presentation.navigation.RecordingRoute] и [ru.steno.presentation.navigation.NoteRoute].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            // Навигационный хост UI.
        }
    }
}
