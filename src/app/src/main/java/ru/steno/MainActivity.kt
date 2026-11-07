package ru.steno

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.steno.domain.*
import ru.steno.presentation.UiMessage
import ru.steno.presentation.main.*
import ru.steno.presentation.settings.*

internal fun ComponentActivity.message(text: String) = Toast.makeText(this,text,Toast.LENGTH_LONG).show()
internal fun ComponentActivity.message(m: UiMessage) = message(when(m) {
    UiMessage.UnsupportedFormat -> "В файле нет поддерживаемой аудиодорожки"
    UiMessage.FileUnreadable -> "Не удалось прочитать файл"
    UiMessage.NotEnoughStorage -> "Недостаточно места"
    UiMessage.ExportDone -> "Расшифровка сохранена"
    UiMessage.ExportFailed -> "Не удалось сохранить расшифровку"
    UiMessage.TranscriptNotReady -> "Дождитесь завершения обработки"
    UiMessage.MicPermissionDenied -> "Для записи разрешите доступ к микрофону"
    UiMessage.RecordingFailed -> "Не удалось записать звук"
    UiMessage.RecordingTooShort -> "Запись слишком короткая"
    UiMessage.WhisperNotInstalled -> "Сначала скачайте Whisper"
    UiMessage.WhisperInstalled -> "Whisper установлен"
    UiMessage.WhisperDownloadFailed -> "Не удалось скачать Whisper"
    UiMessage.ModelInUse -> "Модель сейчас используется"
})

internal fun statusLabel(s: ProcessingStatus) = when(s) {
    ProcessingStatus.QUEUED -> "В очереди"
    ProcessingStatus.PROCESSING -> "Обработка"
    ProcessingStatus.DONE -> "Готово"
    ProcessingStatus.FAILED -> "Ошибка обработки"
    ProcessingStatus.CANCELLED -> "Обработка отменена"
}

internal fun ComponentActivity.openNote(id: Long) {
    startActivity(Intent(this,NoteActivity::class.java).putExtra(EXTRA_NOTE_ID,id))
}

class MainActivity : ComponentActivity() {
    private val main: MainViewModel by viewModel()
    private val settings: SettingsViewModel by viewModel()
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        main.onIntent(MainIntent.AudioPicked(it?.toString()))
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            when {
                intent.hasExtra(EXTRA_NOTE_ID) -> openNote(intent.getLongExtra(EXTRA_NOTE_ID,-1))
                intent.getBooleanExtra(EXTRA_OPEN_RECORDING,false) -> startActivity(Intent(this,RecordingActivity::class.java))
                Build.VERSION.SDK_INT >= 33 && !getPreferences(0).getBoolean("askedNotifications",false) -> {
                    getPreferences(0).edit().putBoolean("askedNotifications",true).apply()
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        setContent {
            val state by main.state.collectAsState()
            LaunchedEffect(main) { main.effects.collect { effect -> when(effect) {
                is MainEffect.OpenNote -> openNote(effect.id)
                MainEffect.OpenRecording -> startActivity(Intent(this@MainActivity,RecordingActivity::class.java))
                is MainEffect.PickAudio -> picker.launch(effect.mimeTypes.toTypedArray())
                is MainEffect.ShowMessage -> message(effect.message)
            } } }
            LaunchedEffect(settings) { settings.effects.collect { if(it is SettingsEffect.ShowMessage) message(it.message) } }
            HexTheme(settings.state.collectAsState().value.themeMode) {
                MainScreen(state, main::onIntent) { SettingsDrawer(settings,it) }
            }
        }
    }
}
