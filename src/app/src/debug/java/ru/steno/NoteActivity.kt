package ru.steno

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import ru.steno.domain.*
import ru.steno.presentation.common.format.*
import ru.steno.presentation.note.*

class NoteActivity : ComponentActivity() {
    private val vm: NoteViewModel by viewModel { parametersOf(intent.getLongExtra(EXTRA_NOTE_ID,-1)) }
    private val save = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onIntent(NoteIntent.ExportDestinationPicked(it.data?.data?.takeIf { _ -> it.resultCode==RESULT_OK }?.toString()))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by vm.state.collectAsState()
            LaunchedEffect(vm) { vm.effects.collect { when(it) {
                NoteEffect.NavigateBack -> finish()
                is NoteEffect.ShowMessage -> message(it.message)
                is NoteEffect.PickExportDestination -> save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType(it.mimeType).putExtra(Intent.EXTRA_TITLE,it.suggestedName))
            } } }
            TestPage("Расшифровка") {
                LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    item {
                        TextButton({finish()}) { Text("‹  Мои записи") }
                        Text(s.title,style=MaterialTheme.typography.titleLarge)
                        Text(statusLabel(s.status)+" · "+formatClock(s.durationMs))
                        s.progress?.let { progress ->
                            Text(resources.getStringArray(R.array.stages)[progress.stage.ordinal]+" · ${(progress.fraction*100).toInt()}%")
                            LinearProgressIndicator(progress={progress.fraction},modifier=Modifier.fillMaxWidth())
                        }
                        s.error?.let { error -> Text(when(error) {
                            ProcessingError.NO_SPEECH -> "Речь не обнаружена"
                            ProcessingError.MODEL_UNAVAILABLE -> "Модель недоступна"
                            ProcessingError.OUT_OF_MEMORY -> "Недостаточно оперативной памяти"
                            ProcessingError.NOT_ENOUGH_STORAGE -> "Недостаточно места"
                            ProcessingError.UNSUPPORTED_FORMAT -> "Формат аудио не поддерживается"
                            ProcessingError.UNKNOWN -> "Не удалось обработать запись"
                        }) }
                        if(s.status in setOf(ProcessingStatus.QUEUED,ProcessingStatus.PROCESSING)) {
                            TextButton({vm.onIntent(NoteIntent.CancelProcessingClicked)}) {Text("Отменить обработку")}
                        } else {
                            TextButton({vm.onIntent(NoteIntent.RetryProcessingClicked)}) {Text("Обработать повторно")}
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({vm.onIntent(NoteIntent.PlayPauseClicked)},enabled=s.player.isReady || s.player.isPlaying) {
                                Text(if(s.player.isPlaying) "Пауза" else "Слушать")
                            }
                            OutlinedButton({
                                val speeds=PlaybackSpeed.entries
                                vm.onIntent(NoteIntent.SpeedSelected(speeds[(speeds.indexOf(s.player.speed)+1)%speeds.size]))
                            }) {Text(s.player.speed.label)}
                            Text(formatClock(s.player.positionMs),Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                        }
                        Button({vm.onIntent(NoteIntent.ExportClicked)},enabled=s.status==ProcessingStatus.DONE && !s.isExporting) {
                            Text("Сохранить "+s.exportFormat.extension.uppercase())
                        }
                        if(s.utterances.isNotEmpty()) Text("Участников: ${s.speakerCount} · Реплик: ${s.utterances.size}")
                    }
                    itemsIndexed(s.utterances,key={_,row -> row.id}) { index,row ->
                        Card(Modifier.fillMaxWidth().clickable {vm.onIntent(NoteIntent.UtteranceClicked(index))},
                            colors=CardDefaults.cardColors(containerColor=if(s.activeUtteranceIndex==index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(14.dp)) {
                                Text((row.speakerName ?: "Участник ${row.speakerNumber}")+" · "+formatTimeRange(row.startMs,row.endMs,s.durationMs>=3600000),
                                    style=MaterialTheme.typography.labelLarge)
                                Text(row.text)
                            }
                        }
                    }
                }
            }
        }
    }
}
