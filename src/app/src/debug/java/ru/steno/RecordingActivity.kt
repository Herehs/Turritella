package ru.steno

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.steno.data.audio.RecorderStatus
import ru.steno.presentation.common.format.formatClock
import ru.steno.presentation.recording.*

class RecordingActivity : ComponentActivity() {
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
            TestPage("Запись диалога") {
                OutlinedTextField(s.title,{vm.onIntent(RecordingIntent.TitleChanged(it))},Modifier.fillMaxWidth(),
                    label={Text("Название")},singleLine=true)
                Text(formatClock(s.elapsedMs),style=MaterialTheme.typography.displayMedium)
                Text(when(s.status) {
                    RecorderStatus.IDLE -> "Микрофон готов"
                    RecorderStatus.RECORDING -> "Идёт запись"
                    RecorderStatus.PAUSED -> "На паузе"
                    RecorderStatus.SAVING -> "Сохранение…"
                })
                LinearProgressIndicator(progress={ s.amplitudes.lastOrNull() ?: 0f },modifier=Modifier.fillMaxWidth())
                if(s.micPermissionDenied) Text("Разрешите доступ к микрофону в настройках приложения.")
                when(s.status) {
                    RecorderStatus.IDLE -> Button({vm.onIntent(RecordingIntent.StartClicked)}) {Text("Начать запись")}
                    RecorderStatus.RECORDING,RecorderStatus.PAUSED -> {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({vm.onIntent(if(s.status==RecorderStatus.PAUSED) RecordingIntent.ResumeClicked else RecordingIntent.PauseClicked)}) {
                                Text(if(s.status==RecorderStatus.PAUSED) "Продолжить" else "Пауза")
                            }
                            Button({vm.onIntent(RecordingIntent.StopClicked)}) { Text("Завершить") }
                        }
                    }
                    RecorderStatus.SAVING -> Unit
                }
                TextButton({vm.onIntent(RecordingIntent.BackClicked)},enabled=s.status!=RecorderStatus.SAVING) {Text("Назад")}
                if(s.showExitDialog) AlertDialog(
                    onDismissRequest={vm.onIntent(RecordingIntent.ExitDialogDismissed)},title={Text("Сохранить запись?")},
                    confirmButton={TextButton({vm.onIntent(RecordingIntent.ExitSaveClicked)}) {Text("Сохранить")}},
                    dismissButton={TextButton({vm.onIntent(RecordingIntent.ExitDiscardClicked)}) {Text("Удалить запись")}})
            }
        }
    }
}
