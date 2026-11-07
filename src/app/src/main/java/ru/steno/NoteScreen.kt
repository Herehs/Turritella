package ru.steno

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ru.steno.domain.*
import ru.steno.presentation.note.*
import ru.steno.presentation.common.format.*

@Composable internal fun NoteScreen(s: NoteState, send: (NoteIntent) -> Unit, back: () -> Unit) {
    var more by remember { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var rename by remember { mutableStateOf<UtteranceItem?>(null) }
    var speakerName by rememberSaveable { mutableStateOf("") }
    val list=rememberLazyListState()
    LaunchedEffect(s.activeUtteranceIndex) {
        val index=s.activeUtteranceIndex
        if(index!=null && s.player.isPlaying && !list.isScrollInProgress) list.animateScrollToItem(index)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start=15.dp,end=5.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            BackButton(back)
            Column(Modifier.weight(1f)) {
                BasicTextField(s.title,{send(NoteIntent.TitleChanged(it))},Modifier.fillMaxWidth(),singleLine=true,
                    textStyle=TextStyle(fontFamily=Golos,fontSize=18.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary))
                Text("Расшифровка · ${s.speakerCount} ${participants(s.speakerCount)}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton({more=true},Modifier.size(32.dp)) {Text("⋮",fontSize=26.sp)}
                DropdownMenu(more,{more=false}) {
                    DropdownMenuItem(text={Text("Обработать повторно")},onClick={more=false;send(NoteIntent.RetryProcessingClicked)},enabled=s.status !in setOf(ProcessingStatus.QUEUED,ProcessingStatus.PROCESSING))
                    DropdownMenuItem(text={Text("Удалить запись")},onClick={more=false;delete=true})
                }
            }
        }
        PlayerPanel(s,send)
        if(s.status!=ProcessingStatus.DONE) ProcessingPanel(s,send)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,contentPadding=PaddingValues(start=24.dp,end=24.dp,top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(30.dp)) {
            itemsIndexed(s.utterances,key={_,row->row.id}) { index,row ->
                Utterance(row,s.activeUtteranceIndex==index,s.durationMs,{send(NoteIntent.UtteranceClicked(index))},{rename=row;speakerName=row.speakerName.orEmpty()})
            }
        }
    }
    if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("Удалить запись?")},text={Text("Аудио и расшифровка будут удалены.")},
        confirmButton={TextButton({delete=false;send(NoteIntent.DeleteNoteClicked)}){Text("Удалить")}},dismissButton={TextButton({delete=false}){Text("Отмена")}})
    rename?.let { row -> AlertDialog(onDismissRequest={rename=null},title={Text("Имя участника ${row.speakerNumber}")},text={
        OutlinedTextField(speakerName,{speakerName=it},singleLine=true,label={Text("Имя")})
    },confirmButton={TextButton({send(NoteIntent.RenameSpeaker(row.speakerNumber,speakerName));rename=null}){Text("Сохранить")}},dismissButton={TextButton({rename=null}){Text("Отмена")}}) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Utterance(row: UtteranceItem, active: Boolean, duration: Long, seek: () -> Unit, rename: () -> Unit) {
    val color=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().combinedClickable(onClick=seek,onLongClick=rename),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text((row.speakerName?.takeIf{it.isNotBlank()}?:"Участник ${row.speakerNumber}")+"   ·   "+formatTimeRange(row.startMs,row.endMs,duration>=3600000),
            fontSize=12.sp,lineHeight=17.sp,color=color,fontWeight=FontWeight.Medium)
        Text(row.text,fontSize=26.sp,lineHeight=33.sp,color=color,fontWeight=FontWeight.Medium)
    }
}
@Composable private fun PlayerPanel(s: NoteState, send: (NoteIntent) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val max=s.durationMs.coerceAtLeast(1).toFloat()
    Surface(Modifier.fillMaxWidth().padding(horizontal=10.dp),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal=24.dp,vertical=10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(formatTimestamp((dragging?.toLong()?:s.player.positionMs),s.durationMs>=3600000),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTimestamp(s.durationMs,s.durationMs>=3600000),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(value=(dragging?:s.player.positionMs.toFloat()).coerceIn(0f,max),onValueChange={dragging=it},
                onValueChangeFinished={dragging?.let{send(NoteIntent.SeekTo(it.toLong()))};dragging=null},valueRange=0f..max,
                enabled=s.player.isReady || s.player.isPlaying,modifier=Modifier.fillMaxWidth().height(24.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton({send(NoteIntent.PlayPauseClicked)},enabled=s.player.isReady || s.player.isPlaying,modifier=Modifier.size(48.dp)) {
                    if(s.player.isPlaying) {
                        PlayerIcon(R.drawable.hex_433_5763_player_pause_filled,"Пауза",40)
                    } else Text("▶",Modifier.semantics {contentDescription="Воспроизвести"},fontSize=30.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton({val speeds=PlaybackSpeed.entries;send(NoteIntent.SpeedSelected(speeds[(speeds.indexOf(s.player.speed)+1)%speeds.size]))},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(60.dp)) {
                    if(s.player.speed==PlaybackSpeed.X1) PlayerIcon(R.drawable.hex_433_5763_multiplier1_x,"Скорость 1x",50)
                    else Text(s.player.speed.label,fontSize=22.sp,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                }
                IconButton({send(NoteIntent.ExportClicked)},enabled=s.status==ProcessingStatus.DONE && !s.isExporting) {
                    if(s.isExporting) CircularProgressIndicator(Modifier.size(24.dp))
                    else PlayerIcon(R.drawable.hex_433_5763_file_download,"Экспорт расшифровки",30)
                }
            }
        }
    }
}
@Composable private fun ProcessingPanel(s: NoteState, send: (NoteIntent) -> Unit) {
    val context=LocalContext.current
    Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(s.error?.let{errorLabel(it)}?:statusLabel(s.status),fontWeight=FontWeight.Medium)
        s.progress?.let { progress ->
            Text(context.resources.getStringArray(R.array.stages)[progress.stage.ordinal]+" · ${(progress.fraction*100).toInt()}%",fontSize=14.sp)
            LinearProgressIndicator(progress={progress.fraction},modifier=Modifier.fillMaxWidth())
        }
        if(s.status==ProcessingStatus.PROCESSING || s.status==ProcessingStatus.QUEUED)
            TextButton({send(NoteIntent.CancelProcessingClicked)}){Text("Отменить обработку")}
        else TextButton({send(NoteIntent.RetryProcessingClicked)}){Text("Повторить")}
    }
}
private fun participants(n: Int) = when { n%100 in 11..14 -> "участников";n%10==1 -> "участник";n%10 in 2..4 -> "участника";else -> "участников" }
private fun errorLabel(e: ProcessingError) = when(e) {
    ProcessingError.NO_SPEECH -> "Речь не обнаружена"
    ProcessingError.MODEL_UNAVAILABLE -> "Модель недоступна"
    ProcessingError.OUT_OF_MEMORY -> "Недостаточно оперативной памяти"
    ProcessingError.NOT_ENOUGH_STORAGE -> "Недостаточно места"
    ProcessingError.UNSUPPORTED_FORMAT -> "Формат аудио не поддерживается"
    ProcessingError.UNKNOWN -> "Не удалось обработать запись"
}

@Composable private fun PlayerIcon(resource: Int, label: String, size: Int) {
    Image(painterResource(resource),label,Modifier.size(size.dp),
        colorFilter=if(HexDark.current) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null)
}
