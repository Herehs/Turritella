package ru.steno.presentation.main

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import ru.steno.R
import ru.steno.domain.*
import ru.steno.presentation.settings.SettingsViewModel
import ru.steno.presentation.StenoTheme
import ru.steno.presentation.settings.Settings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(10.dp)

@Composable
fun MainScreenRoute(
    onOpenNote: (Long) -> Unit,
    onOpenRecording: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val currentOnOpenNote by rememberUpdatedState(onOpenNote)
    val currentOnOpenRecording by rememberUpdatedState(onOpenRecording)

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        viewModel.onIntent(MainIntent.AudioPicked(uri?.toString()))
    }

    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MainEffect.OpenNote -> currentOnOpenNote(effect.id)
                MainEffect.OpenRecording -> currentOnOpenRecording()
                is MainEffect.PickAudio -> audioPicker.launch(effect.mimeTypes.toTypedArray())
                is MainEffect.ShowMessage -> scope.launch {
                    snackbarHostState.showSnackbar(effect.message.toString())
                }
            }
        }
    }

    ZoomDrawer(
        isMenuOpen = settingsOpen,
        onCloseMenu = { settingsOpen = false },
        menuContent = {
            Settings(
                state = settingsState,
                onIntent = settingsViewModel::onIntent
            )
        },
        mainContent = {
            MainScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onIntent = viewModel::onIntent,
                modifier = modifier,
                onMenuClick = {settingsOpen = !settingsOpen}
            )
        },
        gesturesEnabled = true
    )
}

@Composable
fun MainScreen(
    state: MainState,
    snackbarHostState: SnackbarHostState,
    onIntent: (MainIntent) -> Unit,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
) {
    var noteToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            MainFabs(
                expanded = fabExpanded,
                hasActiveRecording = state.hasActiveRecording,
                isImporting = state.isImporting,
                onToggle = { fabExpanded = !fabExpanded },
                onRecordClick = { fabExpanded = false; onIntent(MainIntent.RecordClicked) },
                onImportClick = { fabExpanded = false; onIntent(MainIntent.ImportAudioClicked) },
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

            Box(Modifier.weight(1f).fillMaxWidth()) {
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
            Icon(painterResource(R.drawable.ic_menu_2), contentDescription = "Меню", tint = StenoTheme.colors.text)
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
    val textStyle = TextStyle(color = StenoTheme.colors.text, fontSize = 18.sp)
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(StenoTheme.colors.surface)
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(StenoTheme.colors.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Поиск..", style = textStyle.copy(color = StenoTheme.colors.textSecondary))
                    }
                    inner()
                }
            },
        )

        val isGrid = layout == NotesLayout.GRID
        IconButton(onClick = {
            val all = NotesLayout.entries
            onLayoutSelected(all[(layout.ordinal + 1) % all.size])
        }) {
            Icon(
                painter = if (isGrid) painterResource(R.drawable.ic_layout_grid) else painterResource(R.drawable.ic_layout_list),
                contentDescription = "Сменить вид списка",
                tint = StenoTheme.colors.textSecondary,
            )
        }

        Box {
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(painter = painterResource(R.drawable.ic_arrows_sort), "Сортировка", tint = StenoTheme.colors.textSecondary)
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                NoteSort.entries.forEach { option ->
                    val label = when(option){
                        NoteSort.DATE_DESC -> "Дата по убыванию"
                        NoteSort.DATE_ASC -> "Дата по возрастанию"
                        NoteSort.TITLE_ASC -> "Название А–Я"
                        NoteSort.TITLE_DESC -> "Название Я–А"
                        NoteSort.DURATION_DESC -> "Длительность по убыванию"
                        NoteSort.DURATION_ASC -> "Длительность по возрастанию"
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
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

private fun NoteSort.label(): String = when (this) {
    NoteSort.DATE_DESC -> "Сначала новые"
    else -> name
}

@Composable
private fun ImportingIndicator(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Копирование аудио…", fontSize = 13.sp, color = StenoTheme.colors.textSecondary)
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = StenoTheme.colors.accent)
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
                    .background(StenoTheme.colors.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Подготовка моделей… ${(fraction * 100).roundToInt()}%",
                    fontSize = 14.sp,
                    color = StenoTheme.colors.text,
                )
                LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth(), color = StenoTheme.colors.accent)
            }
        }
        is ModelsPreparation.Failed -> {
            Text(
                "Не удалось подготовить модели. Расшифровка недоступна.",
                modifier = modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(StenoTheme.colors.surface)
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
        color = StenoTheme.colors.textSecondary,
    )
}

private val MiniFabOrigin = TransformOrigin(1f, 1f)

private fun miniEnter(delay: Int) =
    fadeIn(tween(150, delayMillis = delay)) +
            scaleIn(tween(220, delayMillis = delay), initialScale = 0.6f, transformOrigin = MiniFabOrigin) +
            slideInVertically(tween(220, delayMillis = delay)) { it / 2 }

private fun miniExit() =
    fadeOut(tween(100)) +
            scaleOut(tween(120), targetScale = 0.6f, transformOrigin = MiniFabOrigin) +
            slideOutVertically(tween(120)) { it / 2 }

@Composable
private fun MainFabs(
    expanded: Boolean,
    hasActiveRecording: Boolean,
    isImporting: Boolean,
    onToggle: () -> Unit,
    onRecordClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(250),
        label = "fabRotation",
    )

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(visible = expanded, enter = miniEnter(delay = 50), exit = miniExit()) {
            MiniFab(
                label = "Импорт аудио",
                icon = painterResource(R.drawable.ic_file_import),
                enabled = !isImporting,
                onClick = onImportClick,
            )
        }
        AnimatedVisibility(visible = expanded, enter = miniEnter(delay = 0), exit = miniExit()) {
            MiniFab(
                label = if (hasActiveRecording) "Вернуться к записи" else "Новая заметка",
                icon = painterResource(R.drawable.ic_file_plus),
                enabled = true,
                onClick = onRecordClick,
            )
        }
        FloatingActionButton(
            onClick = onToggle,
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            containerColor = StenoTheme.colors.accent,
            contentColor = StenoTheme.colors.onAccent,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = if (expanded) "Закрыть меню" else "Открыть меню",
                modifier = Modifier.size(28.dp).rotate(rotation),
            )
        }
    }
}

@Composable
private fun MiniFab(
    label: String,
    icon: Painter,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(end = 10.dp).alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            color = StenoTheme.colors.surface,
            shadowElevation = 2.dp,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 14.sp,
                color = StenoTheme.colors.text,
            )
        }
        SmallFloatingActionButton(
            onClick = { if (enabled) onClick() },
            containerColor = StenoTheme.colors.surface,
            contentColor = StenoTheme.colors.accent,
            shape = CircleShape,
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun NotesGrid(
    notes: List<NoteItem>,
    layout: NotesLayout,
    onNoteClick: (Long) -> Unit,
    onCancelClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
) {
    val columns = if (layout == NotesLayout.GRID) 2 else 1

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 15.dp, end = 15.dp, bottom = 150.dp),
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

    val isProcessing = note.progress != null

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(StenoTheme.colors.surface)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 15.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = StenoTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = note.preview.ifBlank { if (isProcessing) "Идёт обработка…" else "" },
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = StenoTheme.colors.text,
                minLines = 6,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )

            if (isProcessing) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = StenoTheme.colors.accent)
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
                note.speakerCount?.let { MetaItem(painterResource(R.drawable.ic_users), it.toString()) }
                MetaItem(painterResource(R.drawable.ic_clock), formatDuration(note.durationMs))
                MetaItem(painterResource(R.drawable.ic_calendar), remember(note.createdAt) { formatDate(note.createdAt) })
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
                text = { Text("Удалить", color = StenoTheme.colors.error) },
                onClick = {
                    menuOpen = false
                    onDeleteClick()
                },
            )
        }
    }
}

@Composable
private fun MetaItem(icon: Painter, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = StenoTheme.colors.textSecondary)
        Text(text, fontSize = 14.sp, color = StenoTheme.colors.textSecondary, maxLines = 1)
    }
}

private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateFormatter)

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun MainScreenGridPreview() {
    val notes = List(8) {
        NoteItem(
            id = it.toLong(),
            title = "Note name ${it + 1}",
            createdAt = 1_762_300_000_000,
            durationMs = 125_000,
            status = ProcessingStatus.entries.first(),
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
