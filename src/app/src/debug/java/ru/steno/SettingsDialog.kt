package ru.steno

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.steno.domain.*
import ru.steno.presentation.settings.*

@Composable internal fun SettingsDialog(vm: SettingsViewModel, close: () -> Unit) {
    val s by vm.state.collectAsState()
    AlertDialog(onDismissRequest=close,title={ Text("Настройки") },confirmButton={ TextButton(close) { Text("Готово") } },text={
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Формат экспорта")
            Row { ExportFormat.entries.forEach { format ->
                FilterChip(s.exportFormat==format,{vm.onIntent(SettingsIntent.ExportFormatSelected(format))},{Text(format.extension.uppercase())})
            } }
            Text("Пунктуация через локальную LLM")
            Switch(s.punctuationEnabled,{ vm.onIntent(SettingsIntent.PunctuationToggled(it)) })
            Text("Участники")
            Row { listOf(null,1,2,3).forEach { count ->
                FilterChip(s.speakerCount==count,{vm.onIntent(SettingsIntent.SpeakerCountSelected(count))},{Text(count?.toString()?:"Авто")})
            } }
            Text("Распознавание")
            Row { AsrModelType.entries.forEach { type ->
                FilterChip(s.asrModel==type,{vm.onIntent(SettingsIntent.AsrModelSelected(type))},{Text(if(type==AsrModelType.GIGA_AM) "GigaAM" else "Whisper")})
            } }
            when(val w=s.whisper) {
                is WhisperModelState.Installed -> TextButton({ vm.onIntent(SettingsIntent.DeleteWhisperClicked) }) { Text("Удалить Whisper") }
                is WhisperModelState.NotInstalled, is WhisperModelState.Failed -> {
                    if(w is WhisperModelState.Failed) Text("Ошибка загрузки: ${w.error}")
                    TextButton({ vm.onIntent(SettingsIntent.DownloadWhisperClicked) }) { Text("Скачать Whisper · 375 МБ") }
                }
                else -> {
                    Text(when(w) {
                        is WhisperModelState.Downloading -> "Загрузка · ${w.downloadedBytes*100/w.totalBytes.coerceAtLeast(1)}%"
                        WhisperModelState.Verifying -> "Проверка файлов"
                        else -> "Ожидание сети"
                    })
                    TextButton({ vm.onIntent(SettingsIntent.CancelWhisperDownloadClicked) }) { Text("Остановить загрузку") }
                }
            }
        }
    })
}
