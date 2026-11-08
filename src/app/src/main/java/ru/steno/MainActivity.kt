package ru.steno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import ru.steno.presentation.StenoTheme
import ru.steno.presentation.TerritullaTheme
import ru.steno.presentation.note.NoteScreenRoute
import ru.steno.presentation.recording.RecordingScreenRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    val appViewModel: AppViewModel = koinViewModel()
    val state by appViewModel.state.collectAsStateWithLifecycle()
    TerritullaTheme(state.themeMode) {
        Surface(
            color = StenoTheme.colors.bg
        ) {
            NavHost()
        }
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
                    onOpenNote = { id -> backStack.add(NoteRoute(id))},
                )
            }

            entry<RecordingRoute> {
                RecordingScreenRoute(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenNote = { id -> backStack[backStack.lastIndex] = NoteRoute(id) },
                )
            }

            entry<NoteRoute> { route ->
                NoteScreenRoute(
                    noteId = route.noteId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        },
    )
}