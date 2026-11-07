package ru.steno

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import ru.steno.domain.*
import ru.steno.presentation.main.*
import ru.steno.presentation.settings.SettingsViewModel
import ru.steno.presentation.common.format.formatTimestamp
import java.text.SimpleDateFormat
import java.util.*

@Composable internal fun MainScreen(s: MainState, send: (MainIntent) -> Unit, drawerContent: @Composable (Modifier) -> Unit) {
    var drawer by rememberSaveable { mutableStateOf(false) }
    var add by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<NoteItem?>(null) }
    val slide by animateFloatAsState(if(drawer) 1f else 0f,label="drawer")
    val density = LocalDensity.current
    BackHandler(drawer) { drawer=false }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff131a26))) {
        val drawerWidth = minOf(260.dp,maxWidth * .72f)
        if(slide>0f) drawerContent(Modifier.width(drawerWidth).fillMaxHeight())
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX=with(density){drawerWidth.toPx()}*slide
            scaleX=1f-.1f*slide; scaleY=1f-.1f*slide
            transformOrigin=TransformOrigin(0f,.5f)
            shape=RoundedCornerShape(25.dp); clip=slide>0f
        }.background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().padding(horizontal=15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.height(0.dp))
                Row(Modifier.fillMaxWidth().height(50.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    IconButton({drawer=true},Modifier.size(40.dp)) {
                        HexImage(R.drawable.hex_431_4813_menu2,R.drawable.hex_421_4050_menu2,"Открыть меню",30,25)
                    }
                    Row(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(start=20.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        BasicTextField(s.query,{send(MainIntent.QueryChanged(it))},Modifier.weight(1f),singleLine=true,
                            textStyle=TextStyle(fontFamily=Golos,fontSize=18.sp,color=MaterialTheme.colorScheme.onSurface),
                            cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),decorationBox={ inner ->
                                if(s.query.isEmpty()) Text("Поиск..",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=18.sp)
                                inner()
                            })
                        IconButton({send(MainIntent.LayoutSelected(if(s.layout==NotesLayout.GRID) NotesLayout.LIST else NotesLayout.GRID))},Modifier.size(40.dp)) {
                            if(s.layout==NotesLayout.LIST) HexImage(R.drawable.hex_431_4813_layout_grid,R.drawable.hex_421_4050_layout_grid,"Вид сеткой")
                            else HexImage(R.drawable.hex_431_4838_layout_list,description="Вид списком")
                        }
                        Box {
                            IconButton({sort=true},Modifier.size(40.dp)) { HexImage(R.drawable.hex_431_4813_arrows_sort,R.drawable.hex_421_4050_arrows_sort,"Сортировка",24,26) }
                            DropdownMenu(sort,{sort=false}) { NoteSort.entries.forEach { order ->
                                DropdownMenuItem(text={Text((if(s.sort==order) "✓  " else "")+sortLabel(order))},onClick={send(MainIntent.SortSelected(order));sort=false})
                            } }
                        }
                    }
                }
                when(val m=s.modelsPreparation) {
                    is ModelsPreparation.Preparing -> { Text("Подготовка моделей · ${(m.fraction*100).toInt()}%",fontSize=12.sp); LinearProgressIndicator(progress={m.fraction},modifier=Modifier.fillMaxWidth()) }
                    is ModelsPreparation.Failed -> Text("Не удалось подготовить модели. Перезапустите приложение.",color=MaterialTheme.colorScheme.error)
                    ModelsPreparation.Ready -> Unit
                }
                if(s.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(s.isLoading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
                else if(s.notes.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(if(s.query.isBlank()) "Здесь будут ваши записи" else "Ничего не найдено",fontSize=20.sp,fontWeight=FontWeight.Medium)
                        Text(if(s.query.isBlank()) "Нажмите +, чтобы записать разговор или выбрать аудиофайл" else "Попробуйте другое название или фрагмент текста",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else LazyVerticalGrid(columns=GridCells.Fixed(if(s.layout==NotesLayout.GRID) 2 else 1),
                    contentPadding=PaddingValues(bottom=90.dp),verticalArrangement=Arrangement.spacedBy(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(s.notes,key={it.id}) { note -> NoteCard(note,s.layout==NotesLayout.GRID,
                        {send(MainIntent.NoteClicked(note.id))},{send(MainIntent.FavoriteClicked(note.id))},{deleting=note}) }
                }
            }
            FloatingActionButton({add=true},Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=20.dp).size(60.dp),shape=CircleShape,
                containerColor=MaterialTheme.colorScheme.primary,contentColor=Color.White) {
                HexImage(R.drawable.hex_431_4813_plus,R.drawable.hex_421_4050_plus,"Добавить запись",30)
            }
            if(drawer) Box(Modifier.matchParentSize().clickable {drawer=false})
        }
    }
    if(add) AlertDialog(onDismissRequest={add=false},title={Text("Новая запись")},text={
        Column { TextButton({add=false;send(MainIntent.RecordClicked)}) {Text(if(s.hasActiveRecording) "Вернуться к записи" else "Записать с микрофона")}
            TextButton({add=false;send(MainIntent.ImportAudioClicked)},enabled=!s.isImporting) {Text("Выбрать аудиофайл")} }
    },confirmButton={TextButton({add=false}){Text("Отмена")}})
    deleting?.let { note -> AlertDialog(onDismissRequest={deleting=null},title={Text("Удалить запись?")},text={Text(note.title)},
        confirmButton={TextButton({send(MainIntent.DeleteNoteClicked(note.id));deleting=null}){Text("Удалить")}},dismissButton={TextButton({deleting=null}){Text("Отмена")}}) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun NoteCard(note: NoteItem, grid: Boolean, open: () -> Unit, favorite: () -> Unit, delete: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(if(grid) 142.dp else 150.dp).clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surface).combinedClickable(onClick=open,onLongClick=delete).padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(note.title,Modifier.weight(1f),fontSize=16.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Box(Modifier.size(24.dp).clickable(onClickLabel=if(note.isFavorite) "Убрать из избранного" else "В избранное",onClick=favorite),contentAlignment=Alignment.Center) {
                if(note.isFavorite) HexImage(R.drawable.hex_431_4813_star,R.drawable.hex_421_4050_star,"В избранном",18)
                else HexImage(R.drawable.hex_431_4813_star1,R.drawable.hex_421_4050_star1,"Добавить в избранное",18)
            }
        }
        Text(note.preview.ifBlank {if(note.status==ProcessingStatus.DONE) "Нет текста" else statusLabel(note.status)},
            Modifier.weight(1f),fontSize=12.sp,lineHeight=13.sp,maxLines=if(grid) 4 else 6,overflow=TextOverflow.Ellipsis)
        note.progress?.let { LinearProgressIndicator(progress={it.fraction},modifier=Modifier.fillMaxWidth().height(2.dp)) }
        if(grid) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { NoteMeta(note,false) }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { DateMeta(note.createdAt) }
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) { NoteMeta(note,true) }
    }
}
@Composable private fun NoteMeta(note: NoteItem, date: Boolean) {
    HexImage(R.drawable.hex_431_4813_users,R.drawable.hex_421_4050_users,width=16)
    Text(note.speakerCount?.toString()?:"—",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(6.dp)); HexImage(R.drawable.hex_431_4813_clock,R.drawable.hex_421_4050_clock,width=16)
    Text(formatTimestamp(note.durationMs,note.durationMs>=3600000),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(date) {Spacer(Modifier.width(6.dp));DateMeta(note.createdAt)}
}
@Composable private fun DateMeta(date: Long) {
    HexImage(R.drawable.hex_431_4813_calendar,R.drawable.hex_421_4050_calendar,width=16)
    Text(SimpleDateFormat("dd.MM.yy",Locale.ROOT).format(Date(date)),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
private fun sortLabel(sort: NoteSort) = when(sort) {
    NoteSort.DATE_DESC -> "Сначала новые"; NoteSort.DATE_ASC -> "Сначала старые"
    NoteSort.TITLE_ASC -> "Название: А–Я"; NoteSort.TITLE_DESC -> "Название: Я–А"
    NoteSort.DURATION_DESC -> "Сначала длинные"; NoteSort.DURATION_ASC -> "Сначала короткие"
}
