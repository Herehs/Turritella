package ru.steno

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.steno.presentation.recording.*

class RecordingActivity : ComponentActivity() {
    private val appTheme: ru.steno.presentation.app.AppViewModel by viewModel()
    private val vm: RecordingViewModel by viewModel()
    private val mic = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onIntent(RecordingIntent.MicPermissionResult(it))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by vm.state.collectAsState()
            BackHandler { vm.onIntent(RecordingIntent.BackClicked) }
            LaunchedEffect(vm) { vm.effects.collect { when(it) {
                RecordingEffect.RequestMicPermission -> mic.launch(Manifest.permission.RECORD_AUDIO)
                RecordingEffect.NavigateBack -> finish()
                is RecordingEffect.OpenNote -> { openNote(it.id); finish() }
                is RecordingEffect.ShowMessage -> message(it.message)
            } } }
            HexTheme(appTheme.state.collectAsState().value.themeMode) {
                RecordingScreen(s, vm::onIntent)
            }
        }
    }
}
