package ru.steno

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ru.steno.data.audio.RecorderStatus
import ru.steno.presentation.recording.*
import ru.steno.presentation.common.format.formatClock
import java.text.SimpleDateFormat
import java.util.*

@Composable internal fun RecordingScreen(s: RecordingState, send: (RecordingIntent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=15.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            BackButton {send(RecordingIntent.BackClicked)}
            Column(Modifier.weight(1f)) {
                BasicTextField(s.title,{send(RecordingIntent.TitleChanged(it))},singleLine=true,
                    textStyle=TextStyle(fontFamily=Golos,fontWeight=FontWeight.Medium,fontSize=18.sp,color=MaterialTheme.colorScheme.onSurface),
                    cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),decorationBox={inner ->
                        if(s.title.isBlank()) Text("Новая запись",fontSize=18.sp,fontWeight=FontWeight.Medium)
                        inner()
                    })
                Text("Разговор · "+SimpleDateFormat("d MMMM",Locale.forLanguageTag("ru")).format(Date(s.date.takeIf{it>0}?:System.currentTimeMillis())),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val availableWidth=maxWidth
            val availableHeight=maxHeight
            Column(Modifier.fillMaxWidth().align(Alignment.Center).offset(y=maxHeight*.12f).padding(horizontal=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                val timerSize = minOf(64f,(availableWidth.value-40)/5.2f).sp
                Text(formatClock(s.elapsedMs),fontFamily=Golos,fontSize=timerSize,fontWeight=FontWeight.Medium,maxLines=1)
                Spacer(Modifier.height((availableHeight*.17f).coerceAtMost(110.dp)))
                val color = MaterialTheme.colorScheme.primary
                // Сигнал поступает от AudioRecord; макетная волна не подменяет реальные данные.
                Canvas(Modifier.fillMaxWidth().height(85.dp)) {
                    val samples=s.amplitudes.takeLast(41)
                    repeat(41) { i ->
                        val amplitude=samples.getOrNull(i-(41-samples.size))?.coerceIn(0f,1f)?:0f
                        val height=maxOf(6.dp.toPx(),amplitude*size.height)
                        val x=(i+.5f)*size.width/41
                        drawLine(color,Offset(x,(size.height-height)/2),Offset(x,(size.height+height)/2),4.dp.toPx(),StrokeCap.Round)
                    }
                }
            }
        }
        if(s.micPermissionDenied) Text("Разрешите доступ к микрофону в настройках приложения.",color=MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().heightIn(min=145.dp).padding(vertical=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            when(s.status) {
                RecorderStatus.IDLE -> RoundControl("Начать запись",70,Color(0xffdc2626),{send(RecordingIntent.StartClicked)}) {
                    HexImage(R.drawable.hex_431_4877_player_record_filled,description="Начать запись",width=36)
                }
                RecorderStatus.RECORDING,RecorderStatus.PAUSED -> {
                    RoundControl(if(s.status==RecorderStatus.PAUSED) "Продолжить" else "Пауза",50,Color(0xff7a8699),{
                        send(if(s.status==RecorderStatus.PAUSED) RecordingIntent.ResumeClicked else RecordingIntent.PauseClicked)
                    }) {
                        if(s.status==RecorderStatus.PAUSED) Text("▶",color=Color.White,fontSize=24.sp)
                        else HexImage(R.drawable.hex_431_4900_player_pause_filled,description="Пауза")
                    }
                    Spacer(Modifier.width(10.dp))
                    RoundControl("Завершить запись",70,Color(0xffdc2626),{send(RecordingIntent.StopClicked)}) {
                        HexImage(R.drawable.hex_431_4900_player_stop_filled,description="Завершить запись",width=36)
                    }
                    Spacer(Modifier.width(60.dp))
                }
                RecorderStatus.SAVING -> CircularProgressIndicator()
            }
        }
        if(s.status==RecorderStatus.PAUSED) Text("На паузе",Modifier.align(Alignment.CenterHorizontally).padding(bottom=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(s.showExitDialog) AlertDialog(onDismissRequest={send(RecordingIntent.ExitDialogDismissed)},title={Text("Сохранить запись?")},
        confirmButton={TextButton({send(RecordingIntent.ExitSaveClicked)}){Text("Сохранить")}},
        dismissButton={TextButton({send(RecordingIntent.ExitDiscardClicked)}){Text("Удалить запись")}})
}
@Composable private fun RoundControl(label: String, size: Int, color: Color, action: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick=action,shape=CircleShape,color=color,shadowElevation=2.dp,modifier=Modifier.size(size.dp)) {
        Box(contentAlignment=Alignment.Center) {content()}
    }
}
