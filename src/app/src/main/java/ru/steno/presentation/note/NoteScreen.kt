package ru.steno.presentation.note

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.steno.R
import ru.steno.domain.*
import ru.steno.presentation.StenoSnackbarHost
import ru.steno.presentation.StenoTheme
import ru.steno.presentation.TerritullaTheme
import ru.steno.presentation.stringRes

@Composable
fun NoteScreenRoute(
    noteId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteViewModel = koinViewModel(key = "note-$noteId", parameters = { parametersOf(noteId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources by rememberUpdatedState(LocalResources.current)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }
        viewModel.onIntent(NoteIntent.ExportDestinationPicked(uri?.toString()))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NoteEffect.PickExportDestination -> exportPicker.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(effect.mimeType).putExtra(Intent.EXTRA_TITLE, effect.suggestedName)
                )
                NoteEffect.NavigateBack -> currentOnBack()
                is NoteEffect.ShowMessage -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(effect.message.stringRes))
                }
            }
        }
    }

    NoteScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun NoteScreen(
    state: NoteState,
    snackbarHostState: SnackbarHostState,
    onIntent: (NoteIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var renamingSpeaker by rememberSaveable { mutableStateOf<Int?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { StenoSnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                NoteTopBar(
                    title = state.title,
                    subtitle = noteSubtitle(state),
                    onBackClick = onBack,
                    onTitleChange = { onIntent(NoteIntent.TitleChanged(it)) },
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.utterances.isNotEmpty() && state.status == ProcessingStatus.DONE -> TranscriptList(
                            utterances = state.utterances,
                            activeIndex = state.activeUtteranceIndex,
                            onUtteranceClick = { onIntent(NoteIntent.UtteranceClicked(it)) },
                            onSpeakerClick = { renamingSpeaker = it },
                        )
                        else -> StatusBlock(
                            status = state.status,
                            error = state.error,
                            progress = state.progress,
                            onCancel = { onIntent(NoteIntent.CancelProcessingClicked) },
                            onRetry = { onIntent(NoteIntent.RetryProcessingClicked) },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !playerExpanded,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                Surface(
                    onClick = { playerExpanded = true },
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = StenoTheme.colors.surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_narrow_up),
                            contentDescription = "Открыть плеер",
                            tint = StenoTheme.colors.text,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = playerExpanded,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                enter = fadeIn() + slideInVertically(tween(250)) { it / 2 },
                exit = fadeOut() + slideOutVertically(tween(200)) { it / 2 },
            ) {
                PlayerPanel(
                    player = state.player,
                    durationMs = state.durationMs,
                    isExporting = state.isExporting,
                    onPlayPause = { onIntent(NoteIntent.PlayPauseClicked) },
                    onSeek = { onIntent(NoteIntent.SeekTo(it)) },
                    onSpeedClick = {
                        val all = PlaybackSpeed.entries
                        onIntent(NoteIntent.SpeedSelected(all[(state.player.speed.ordinal + 1) % all.size]))
                    },
                    onExport = { onIntent(NoteIntent.ExportClicked) },
                    onCollapse = { playerExpanded = false },
                )
            }
        }
    }

    renamingSpeaker?.let { number ->
        val current = state.utterances.firstOrNull { it.speakerNumber == number }?.speakerName.orEmpty()
        RenameSpeakerDialog(
            current = current,
            defaultLabel = defaultSpeakerLabel(number),
            onConfirm = {
                onIntent(NoteIntent.RenameSpeaker(number, it))
                renamingSpeaker = null
            },
            onDismiss = { renamingSpeaker = null },
        )
    }
}

@Composable
private fun NoteTopBar(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    onTitleChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBackClick,
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            color = StenoTheme.colors.surface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = StenoTheme.colors.text,
                )
            }
        }

        Column(Modifier.weight(1f).padding(start = 15.dp)) {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = StenoTheme.colors.text,
                ),
                cursorBrush = SolidColor(StenoTheme.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = StenoTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TranscriptList(
    utterances: List<UtteranceItem>,
    activeIndex: Int?,
    onUtteranceClick: (Int) -> Unit,
    onSpeakerClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex != null && !listState.isScrollInProgress) {
            val offset = -(listState.layoutInfo.viewportSize.height / 4)
            listState.animateScrollToItem(activeIndex, offset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        itemsIndexed(utterances, key = { _, item -> item.id }) { index, item ->
            UtteranceRow(
                item = item,
                active = index == activeIndex,
                onClick = { onUtteranceClick(index) },
                onSpeakerClick = { onSpeakerClick(item.speakerNumber) },
            )
        }
    }
}

@Composable
private fun UtteranceRow(
    item: UtteranceItem,
    active: Boolean,
    onClick: () -> Unit,
    onSpeakerClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (active) StenoTheme.colors.accent else StenoTheme.colors.textSecondary,
        animationSpec = tween(200),
        label = "utteranceColor",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Text(
            text = "${item.speakerName ?: defaultSpeakerLabel(item.speakerNumber)} · " +
                    "${formatStamp(item.startMs)}–${formatStamp(item.endMs)}",
            modifier = Modifier.clickable(onClick = onSpeakerClick),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.text,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun StatusBlock(
    status: ProcessingStatus,
    error: ProcessingError?,
    progress: TranscriptionProgress?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            error != null || status == ProcessingStatus.FAILED -> {
                Text(
                    when (error) {
                        ProcessingError.NO_SPEECH -> "Речь в записи не найдена"
                        ProcessingError.MODEL_UNAVAILABLE -> "Модель недоступна"
                        ProcessingError.OUT_OF_MEMORY -> "Недостаточно оперативной памяти"
                        ProcessingError.NOT_ENOUGH_STORAGE -> "Недостаточно места на устройстве"
                        ProcessingError.UNSUPPORTED_FORMAT -> "Формат аудио не поддерживается"
                        else -> "Не удалось расшифровать запись"
                    },
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text("Повторить", color = StenoTheme.colors.accent) }
            }
            status == ProcessingStatus.PROCESSING || status == ProcessingStatus.QUEUED -> {
                Text(
                    if (status == ProcessingStatus.PROCESSING) "Идёт расшифровка…" else "Запись в очереди",
                    fontSize = 16.sp,
                    color = StenoTheme.colors.textSecondary,
                )

                if (progress == null) {
                    LinearProgressIndicator(Modifier.width(200.dp), color = StenoTheme.colors.accent)
                } else {
                    val stages = androidx.compose.ui.res.stringArrayResource(R.array.stages)
                    Text("${stages[progress.stage.ordinal]} · ${(progress.fraction * 100).toInt()}%", color = StenoTheme.colors.textSecondary)
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.width(200.dp), color = StenoTheme.colors.accent)
                }
                TextButton(onClick = onCancel) { Text("Отменить", color = StenoTheme.colors.accent) }
            }
            status == ProcessingStatus.CANCELLED -> {
                Text("Обработка отменена", color = StenoTheme.colors.textSecondary)
                TextButton(onClick = onRetry) { Text("Повторить", color = StenoTheme.colors.accent) }
            }
            else -> Text(
                "Речь в записи не найдена",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = StenoTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PlayerPanel(
    player: PlayerUiState,
    durationMs: Long,
    isExporting: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedClick: () -> Unit,
    onExport: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragMs by remember { mutableStateOf<Long?>(null) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = StenoTheme.colors.surface,
    ) {
        Column(Modifier.padding(horizontal = 30.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlayerTime(dragMs ?: player.positionMs), fontSize = 12.sp, color = StenoTheme.colors.textSecondary)
                Text(formatPlayerTime(durationMs), fontSize = 12.sp, color = StenoTheme.colors.textSecondary)
            }

            PlayerSlider(
                positionMs = player.positionMs,
                durationMs = durationMs,
                enabled = player.isReady,
                onDrag = { dragMs = it },
                onSeek = onSeek,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPause, enabled = player.isReady) {
                    if (player.isPlaying) {
                        Icon(
                            painter = painterResource(R.drawable.ic_player_pause_filled),
                            contentDescription = "Пауза",
                            tint = StenoTheme.colors.text,
                            modifier = Modifier.size(32.dp),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_player_play_filled),
                            contentDescription = "Воспроизвести",
                            tint = StenoTheme.colors.text,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Text(
                    text = speedLabel(player.speed.rate.toFloat()),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSpeedClick)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = StenoTheme.colors.text,
                )

                IconButton(onClick = onExport, enabled = !isExporting) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = StenoTheme.colors.accent,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_file_download),
                            contentDescription = "Экспорт",
                            tint = StenoTheme.colors.text,
                        )
                    }
                }

                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Свернуть плеер",
                        tint = StenoTheme.colors.text,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSlider(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onDrag: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val max = durationMs.coerceAtLeast(1L).toFloat()
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val value = (dragValue ?: positionMs.toFloat()).coerceIn(0f, max)

    Slider(
        value = value,
        onValueChange = {
            dragValue = it
            onDrag(it.toLong())
        },
        onValueChangeFinished = {
            dragValue?.let { v -> onSeek(v.toLong()) }
            dragValue = null
            onDrag(null)
        },
        valueRange = 0f..max,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        thumb = {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(StenoTheme.colors.accent),
            )
        },
        track = { sliderState ->
            val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
            val fraction = ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(StenoTheme.colors.border),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(StenoTheme.colors.accent),
                )
            }
        },
    )
}

@Composable
private fun RenameSpeakerDialog(
    current: String,
    defaultLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Имя участника") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(defaultLabel) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun defaultSpeakerLabel(number: Int) = "Участник $number"

private fun noteSubtitle(state: NoteState): String = when {
    state.error != null -> "Ошибка обработки"
    state.status == ProcessingStatus.PROCESSING -> "Обработка…"
    state.status == ProcessingStatus.QUEUED -> "В очереди"
    state.speakerCount > 0 -> "Расшифровка · ${speakersLabel(state.speakerCount)}"
    else -> "Расшифровка"
}

private fun speakersLabel(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "участников"
        n % 10 == 1 -> "участник"
        n % 10 in 2..4 -> "участника"
        else -> "участников"
    }
    return "$n $word"
}

private fun formatStamp(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)
}

private fun formatPlayerTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

private fun speedLabel(rate: Float): String =
    (if (rate % 1f == 0f) rate.toInt().toString() else rate.toString()) + "×"

private val PreviewUtterances = listOf(
    UtteranceItem(0, 1, null, 10_000, 18_000, "Давайте начнём с обзора задач на эту неделю и распределим ответственных."),
    UtteranceItem(1, 2, null, 18_000, 27_000, "Я возьму интеграцию с календарём, а дизайн оставим на следующий спринт."),
    UtteranceItem(2, 1, null, 27_000, 34_000, "Хорошо. Тогда зафиксируем срок на пятницу и проверим всё вместе."),
    UtteranceItem(3, 2, "Анна", 34_000, 37_000, "Договорились."),
    UtteranceItem(4, 1, null, 37_000, 44_000, "Если останутся вопросы, пишите в общий чат до конца дня."),
)

private fun previewState(expanded: Boolean = false) = NoteState(
    title = "Обсуждение проекта",
    durationMs = 148_000,
    speakerCount = 2,
    status = ProcessingStatus.DONE,
    utterances = PreviewUtterances,
    activeUtteranceIndex = 1,
    player = PlayerUiState(isReady = true, isPlaying = expanded, positionMs = 66_000),
)

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun NoteCollapsedPreview() = TerritullaTheme(ThemeMode.LIGHT) {
    NoteScreen(previewState(), remember { SnackbarHostState() }, onIntent = {}, onBack = {})
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun NoteDarkPreview() = TerritullaTheme(ThemeMode.DARK) {
    NoteScreen(previewState(expanded = true), remember { SnackbarHostState() }, onIntent = {}, onBack = {})
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun NoteProcessingPreview() = TerritullaTheme(ThemeMode.LIGHT) {
    NoteScreen(
        NoteState(title = "Новая запись", status = ProcessingStatus.PROCESSING),
        remember { SnackbarHostState() }, onIntent = {}, onBack = {},
    )
}
