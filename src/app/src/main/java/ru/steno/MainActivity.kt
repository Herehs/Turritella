package ru.steno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.koin.androidx.compose.koinViewModel
import ru.steno.presentation.app.AppViewModel
import ru.steno.presentation.main.MainScreenRoute
import ru.steno.presentation.navigation.MainRoute
import ru.steno.presentation.navigation.NoteRoute
import ru.steno.presentation.navigation.RecordingRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    MaterialTheme {
        val appViewModel: AppViewModel = koinViewModel()
        NavHost()
    }
}

@Composable
private fun NavHost() {
    val backStack = rememberNavBackStack(MainRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { count -> repeat(count) { backStack.removeLastOrNull() } },
        entryProvider = entryProvider<NavKey> {
            entry<MainRoute> {
                MainScreenRoute(
                    onOpenRecording = { backStack.add(RecordingRoute) },
                    onOpenNote = { id -> },
                )
            }

            entry<RecordingRoute> {
                // TODO: заменить на RecordingScreen(
                //     onBack = { backStack.removeLastOrNull() },
                //     onOpenNote = { id -> backStack[backStack.lastIndex] = NoteRoute(id) },
                // )
                PlaceholderScreen(
                    title = "Запись диалога",
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<NoteRoute> { route ->
                // TODO: заменить на NoteScreen(noteId = route.noteId, onBack = { ... })
                PlaceholderScreen(
                    title = "Заметка ${route.noteId}",
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}

@Composable
private fun PlaceholderScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("Назад") }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        actions()
    }
}
