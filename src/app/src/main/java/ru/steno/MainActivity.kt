package ru.steno

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.koin.androidx.compose.koinViewModel
import ru.steno.presentation.StenoTheme
import ru.steno.presentation.TerritullaTheme
import ru.steno.presentation.app.AppViewModel
import ru.steno.presentation.main.MainScreenRoute
import ru.steno.presentation.navigation.MainRoute
import ru.steno.presentation.navigation.NoteRoute
import ru.steno.presentation.navigation.RecordingRoute
import ru.steno.presentation.navigation.notificationRoute
import ru.steno.presentation.navigation.openRoute
import ru.steno.presentation.navigation.popRoute
import ru.steno.presentation.note.NoteScreenRoute
import ru.steno.presentation.recording.RecordingScreenRoute

class MainActivity : ComponentActivity() {
    private val requestedRoute = mutableStateOf<NavKey?>(null)
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute.value = routeFrom(intent)
    }

    private fun routeFrom(intent: Intent): NavKey? = notificationRoute(
        intent.getLongExtra(EXTRA_NOTE_ID, -1),
        intent.getBooleanExtra(EXTRA_OPEN_RECORDING, false),
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedRoute.value = routeFrom(intent)
        if (Build.VERSION.SDK_INT >= 33 && !getPreferences(0).getBoolean("askedNotifications", false)) {
            getPreferences(0).edit().putBoolean("askedNotifications", true).apply()
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { App(requestedRoute.value) { requestedRoute.value = null } }
    }
}

@Composable
private fun App(requestedRoute: NavKey?, routeHandled: () -> Unit) {
    val appViewModel: AppViewModel = koinViewModel()
    val state by appViewModel.state.collectAsStateWithLifecycle()
    TerritullaTheme(state.themeMode) {
        Surface(
            color = StenoTheme.colors.bg
        ) {
            NavHost(requestedRoute, routeHandled)
        }
    }
}

@Composable
private fun NavHost(requestedRoute: NavKey?, routeHandled: () -> Unit) {
    val backStack = rememberNavBackStack(MainRoute)

    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { backStack.openRoute(it); routeHandled() }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.popRoute() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider<NavKey> {
            entry<MainRoute> {
                MainScreenRoute(
                    onOpenRecording = { backStack.openRoute(RecordingRoute) },
                    onOpenNote = { id -> backStack.openRoute(NoteRoute(id))},
                )
            }

            entry<RecordingRoute> {
                RecordingScreenRoute(
                    onBack = { backStack.popRoute() },
                    onOpenNote = { id -> backStack[backStack.lastIndex] = NoteRoute(id) },
                )
            }

            entry<NoteRoute> { route ->
                NoteScreenRoute(
                    noteId = route.noteId,
                    onBack = { backStack.popRoute() }
                )
            }
        },
    )
}
