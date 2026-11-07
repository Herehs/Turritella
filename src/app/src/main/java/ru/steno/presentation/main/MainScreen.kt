package ru.steno.presentation.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import ru.steno.StenoApp
import ru.steno.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Цвета из макета. Когда будет готова тема — перенесите в неё.
private val ScreenBackground = Color(0xFFE9ECEF)
private val TextPrimary = Color(0xFF131A26)
private val TextSecondary = Color(0xFF7A8699)
private val Accent = Color(0xFF2F6BFF)

private val CardShape = RoundedCornerShape(10.dp)

// ───────────────────────────── Route ─────────────────────────────

/**
 * Связывает [MainViewModel] с UI: подписка на состояние, обработка эффектов,
 * системный выбор файла и snackbar. Навигация приходит колбэками снаружи (NavDisplay).
 */
@Composable
fun MainScreenRoute(
    onOpenNote: (Long) -> Unit,
    onOpenRecording: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val currentOnOpenNote by rememberUpdatedState(onOpenNote)
    val currentOnOpenRecording by rememberUpdatedState(onOpenRecording)

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // null = пользователь отменил выбор, VM тоже должен об этом узнать
        viewModel.onIntent(MainIntent.AudioPicked(uri?.toString()))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MainEffect.OpenNote -> currentOnOpenNote(effect.id)
                MainEffect.OpenRecording -> currentOnOpenRecording()
                is MainEffect.PickAudio -> audioPicker.launch(effect.mimeTypes.toTypedArray())
                is MainEffect.ShowMessage -> scope.launch {
                    // TODO: подставьте ваш маппинг UiMessage -> строка
                    snackbarHostState.showSnackbar(effect.message.toString())
                }
            }
        }
    }

    ZoomDrawer(
        menuContent = {
            DrawerContent()
        },
        mainContent = {
            MainScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onIntent = viewModel::onIntent,
                modifier = modifier,
            )
        },
        isMenuOpen = true,
        onCloseMenu = {},
        gesturesEnabled = true
    )

}

// ───────────────────────────── Screen ─────────────────────────────

@Composable
fun MainScreen(
    state: MainState,
    snackbarHostState: SnackbarHostState,
    onIntent: (MainIntent) -> Unit,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}, // в макете есть «гамбургер», но интента под него нет
) {
    var noteToDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            MainFabs(
                hasActiveRecording = state.hasActiveRecording,
                isImporting = state.isImporting,
                onRecordClick = { onIntent(MainIntent.RecordClicked) },
                onImportClick = { onIntent(MainIntent.ImportAudioClicked) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TopBar(
                query = state.query,
                layout = state.layout,
                sort = state.sort,
                onMenuClick = onMenuClick,
                onQueryChange = { onIntent(MainIntent.QueryChanged(it)) },
                onLayoutSelected = { onIntent(MainIntent.LayoutSelected(it)) },
                onSortSelected = { onIntent(MainIntent.SortSelected(it)) },
            )

            if (state.isImporting) {
                ImportingIndicator(Modifier.padding(start = 15.dp, end = 15.dp, bottom = 10.dp))
            }
            ModelsBanner(
                preparation = state.modelsPreparation,
                modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 10.dp),
            )

            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.notes.isEmpty() -> EmptyState(
                        isSearch = state.query.isNotBlank(),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> NotesGrid(
                        notes = state.notes,
                        layout = state.layout,
                        onNoteClick = { onIntent(MainIntent.NoteClicked(it)) },
                        onCancelClick = { onIntent(MainIntent.CancelProcessingClicked(it)) },
                        onDeleteClick = { noteToDelete = it },
                    )
                }
            }
        }
    }

    noteToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Удалить заметку?") },
            text = { Text("Заметка и её аудиозапись будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(MainIntent.DeleteNoteClicked(id))
                    noteToDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Отмена") }
            },
        )
    }
}

// ───────────────────────────── Top bar ─────────────────────────────

@Composable
private fun TopBar(
    query: String,
    layout: NotesLayout,
    sort: NoteSort,
    onMenuClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onLayoutSelected: (NotesLayout) -> Unit,
    onSortSelected: (NoteSort) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Menu, contentDescription = "Меню", tint = TextPrimary)
        }
        Spacer(Modifier.size(10.dp))
        SearchPill(
            query = query,
            layout = layout,
            sort = sort,
            onQueryChange = onQueryChange,
            onLayoutSelected = onLayoutSelected,
            onSortSelected = onSortSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchPill(
    query: String,
    layout: NotesLayout,
    sort: NoteSort,
    onQueryChange: (String) -> Unit,
    onLayoutSelected: (NotesLayout) -> Unit,
    onSortSelected: (NoteSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = TextStyle(color = TextPrimary, fontSize = 18.sp)
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Поиск..", style = textStyle.copy(color = TextSecondary))
                    }
                    inner()
                }
            },
        )

        // Иконка показывает вид, НА который переключаемся (как в макетах).
        val isGrid = layout == NotesLayout.GRID
        IconButton(onClick = {
            val all = NotesLayout.entries
            onLayoutSelected(all[(layout.ordinal + 1) % all.size])
        }) {
            Icon(
                imageVector = if (isGrid) Icons.Outlined.Place else Icons.Outlined.Place,
                contentDescription = "Сменить вид списка",
                tint = TextSecondary,
            )
        }

        Box {
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(Icons.Outlined.Place, "Сортировка", tint = TextSecondary)
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                NoteSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label()) },
                        trailingIcon = {
                            if (option == sort) Icon(Icons.Default.Check, contentDescription = null)
                        },
                        onClick = {
                            sortMenuOpen = false
                            onSortSelected(option)
                        },
                    )
                }
            }
        }
    }
}

/** Подписи порядка сортировки. Добавьте свои значения [NoteSort]. */
private fun NoteSort.label(): String = when (this) {
    NoteSort.DATE_DESC -> "Сначала новые"
    else -> name
}

// ───────────────────────────── Banners / states ─────────────────────────────

@Composable
private fun ImportingIndicator(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Копирование аудио…", fontSize = 13.sp, color = TextSecondary)
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
    }
}

@Composable
private fun ModelsBanner(preparation: ModelsPreparation, modifier: Modifier = Modifier) {
    when (preparation) {
        ModelsPreparation.Ready -> Unit
        is ModelsPreparation.Preparing -> {
            val fraction = preparation.fraction.coerceIn(0f, 1f)
            Column(
                modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Color.White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Подготовка моделей… ${(fraction * 100).roundToInt()}%",
                    fontSize = 14.sp,
                    color = TextPrimary,
                )
                LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth(), color = Accent)
            }
        }
        is ModelsPreparation.Failed -> {
            Text(
                "Не удалось подготовить модели. Расшифровка недоступна.",
                modifier = modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Color.White)
                    .padding(12.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EmptyState(isSearch: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (isSearch) "Ничего не найдено" else "Заметок пока нет\nНажмите «+», чтобы начать запись",
        modifier = modifier.padding(32.dp),
        textAlign = TextAlign.Center,
        fontSize = 16.sp,
        color = TextSecondary,
    )
}

// ───────────────────────────── FAB ─────────────────────────────

@Composable
private fun MainFabs(
    hasActiveRecording: Boolean,
    isImporting: Boolean,
    onRecordClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Не из макета: иначе в экране нет входа для ImportAudioClicked. Уберите, если вход будет в меню.
        SmallFloatingActionButton(
            onClick = { if (!isImporting) onImportClick() },
            containerColor = Color.White,
            contentColor = Accent,
            shape = CircleShape,
        ) {
            Icon(Icons.Outlined.Place, contentDescription = "Импорт аудио")
        }
        FloatingActionButton(
            onClick = onRecordClick,
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            containerColor = Accent,
            contentColor = Color.White,
        ) {
            Icon(
                imageVector = if (hasActiveRecording) Icons.Outlined.Place else Icons.Default.Add,
                contentDescription = if (hasActiveRecording) "Вернуться к записи" else "Новая запись",
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

// ───────────────────────────── Notes grid ─────────────────────────────

@Composable
private fun NotesGrid(
    notes: List<NoteItem>,
    layout: NotesLayout,
    onNoteClick: (Long) -> Unit,
    onCancelClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
) {
    // Один и тот же грид: 1 колонка = список, 2 = сетка.
    val columns = if (layout == NotesLayout.GRID) 2 else 1

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 15.dp, end = 15.dp, bottom = 150.dp), // запас под FAB
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(notes, key = { it.id }) { note ->
            NoteCard(
                note = note,
                compact = columns > 1,
                onClick = { onNoteClick(note.id) },
                onCancelClick = { onCancelClick(note.id) },
                onDeleteClick = { onDeleteClick(note.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteItem,
    compact: Boolean,
    onClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Нет данных о полях TranscriptionProgress, поэтому «идёт обработка» = progress != null.
    val isProcessing = note.progress != null

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(Color.White)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 15.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // TODO: нужны isFavorite в NoteItem и интент переключения — пока декоративная звезда
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = TextSecondary,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = note.preview.ifBlank { if (isProcessing) "Идёт обработка…" else "" },
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = TextPrimary,
                minLines = 6, // одинаковая высота карточек, как в макете
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )

            if (isProcessing) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (compact) {
                    Arrangement.SpaceBetween
                } else {
                    Arrangement.spacedBy(12.dp, Alignment.End)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                note.speakerCount?.let { MetaItem(Icons.Outlined.Place, it.toString()) }
                MetaItem(Icons.Outlined.Place, formatDuration(note.durationMs))
                MetaItem(Icons.Outlined.Place, remember(note.createdAt) { formatDate(note.createdAt) })
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (isProcessing) {
                DropdownMenuItem(
                    text = { Text("Остановить обработку") },
                    onClick = {
                        menuOpen = false
                        onCancelClick()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onDeleteClick()
                },
            )
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextSecondary)
        Text(text, fontSize = 14.sp, color = TextSecondary, maxLines = 1)
    }
}

// ───────────────────────────── Formatting ─────────────────────────────

private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateFormatter)

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ───────────────────────────── Preview ─────────────────────────────

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun MainScreenGridPreview() {
    val notes = List(8) {
        NoteItem(
            id = it.toLong(),
            title = "Note name ${it + 1}",
            createdAt = 1_762_300_000_000,
            durationMs = 125_000,
            status = ProcessingStatus.entries.first(), // если это не enum — подставьте своё значение
            progress = null,
            speakerCount = 1,
            preview = "Первая строка расшифровки, достаточно длинная, чтобы не поместиться. " +
                    "Вторая строка расшифровки, тоже довольно длинная, и ещё немного текста в конце.",
        )
    }
    MainScreen(
        state = MainState(notes = notes, isLoading = false, layout = NotesLayout.GRID),
        snackbarHostState = remember { SnackbarHostState() },
        onIntent = {},
    )
}

