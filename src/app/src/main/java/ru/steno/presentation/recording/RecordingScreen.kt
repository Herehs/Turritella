package ru.steno.presentation.recording

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import ru.steno.data.audio.RecorderStatus
import ru.steno.domain.ThemeMode
import ru.steno.presentation.StenoSnackbarHost
import ru.steno.presentation.StenoTheme
import ru.steno.presentation.TerritullaTheme
import ru.steno.presentation.stringRes

@Composable
fun RecordingScreenRoute(
    onOpenNote: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources by rememberUpdatedState(LocalResources.current)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val currentOnOpenNote by rememberUpdatedState(onOpenNote)
    val currentOnBack by rememberUpdatedState(onBack)

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onIntent(RecordingIntent.MicPermissionResult(granted))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RecordingEffect.RequestMicPermission -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                is RecordingEffect.OpenNote -> currentOnOpenNote(effect.id)
                RecordingEffect.NavigateBack -> currentOnBack()
                is RecordingEffect.ShowMessage -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(effect.message.stringRes))
                }
            }
        }
    }

    BackHandler { viewModel.onIntent(RecordingIntent.BackClicked) }

    RecordingScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
fun RecordingScreen(
    state: RecordingState,
    snackbarHostState: SnackbarHostState,
    onIntent: (RecordingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { StenoSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            RecordingTopBar(
                title = state.title,
                date = state.date,
                onBackClick = { onIntent(RecordingIntent.BackClicked) },
                onTitleChange = { onIntent(RecordingIntent.TitleChanged(it)) },
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = formatTimer(state.elapsedMs),
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Medium,
                    color = StenoTheme.colors.text,
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
                softWrap = false,
            )

            Spacer(Modifier.height(64.dp))

            Box(Modifier.fillMaxWidth().height(WAVE_HEIGHT), contentAlignment = Alignment.Center) {
                when {
                    state.status == RecorderStatus.IDLE && state.micPermissionDenied -> Text(
                        text = "Нет доступа к микрофону.\nРазрешите его в настройках приложения.",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.status != RecorderStatus.IDLE -> Waveform(
                        amplitudes = state.amplitudes,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            RecordingControls(
                status = state.status,
                onStart = {
                    focusManager.clearFocus()
                    onIntent(RecordingIntent.StartClicked)
                },
                onPause = { onIntent(RecordingIntent.PauseClicked) },
                onResume = { onIntent(RecordingIntent.ResumeClicked) },
                onStop = { onIntent(RecordingIntent.StopClicked) },
            )

            Spacer(Modifier.height(48.dp))
        }
    }

    if (state.showExitDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(RecordingIntent.ExitDialogDismissed) },
            title = { Text("Завершить запись?") },
            text = { Text("Сохранить запись как заметку или удалить её?") },
            dismissButton = {
                TextButton(onClick = { onIntent(RecordingIntent.ExitDialogDismissed) }) {
                    Text("Продолжить")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { onIntent(RecordingIntent.ExitDiscardClicked) }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { onIntent(RecordingIntent.ExitSaveClicked) }) {
                        Text("Сохранить")
                    }
                }
            },
        )
    }
}

@Composable
private fun RecordingTopBar(
    title: String,
    date: Long,
    onBackClick: () -> Unit,
    onTitleChange: (String) -> Unit,
) {
    val subtitle = remember(date) { "Разговор · ${formatDay(date)}" }
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

        Column(Modifier.weight(1f).padding(start = 10.dp)) {
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

private val MainButtonSize = 70.dp
private val PauseButtonSize = 50.dp

@Composable
private fun RecordingControls(
    status: RecorderStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val active = status == RecorderStatus.RECORDING || status == RecorderStatus.PAUSED
    val paused = status == RecorderStatus.PAUSED

    val enabled = active || status == RecorderStatus.IDLE

    val mainColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.error else StenoTheme.colors.accent,
        animationSpec = tween(250),
        label = "mainButtonColor",
    )
    val mainContent by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onError else StenoTheme.colors.onAccent,
        animationSpec = tween(250),
        label = "mainButtonContent",
    )

    Box(
        Modifier.fillMaxWidth().height(MainButtonSize),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = active,
            modifier = Modifier.offset(x = (-70).dp),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            CircleButton(
                size = PauseButtonSize,
                color = StenoTheme.colors.textSecondary,
                contentDescription = if (paused) "Продолжить запись" else "Пауза",
                onClick = if (paused) onResume else onPause,
            ) {
                if (paused) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = StenoTheme.colors.surface,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    PauseGlyph(StenoTheme.colors.surface)
                }
            }
        }

        CircleButton(
            size = MainButtonSize,
            color = mainColor,
            enabled = enabled,
            contentDescription = if (active) "Остановить и сохранить" else "Начать запись",
            onClick = if (active) onStop else onStart,
        ) {
            Crossfade(targetState = active, animationSpec = tween(200), label = "mainGlyph") { isActive ->
                if (isActive) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(mainContent),
                    )
                } else {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(mainContent),
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleButton(
    size: Dp,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PauseGlyph(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

private const val WAVE_BARS = 40
private val WAVE_HEIGHT = 80.dp

@Composable
private fun Waveform(amplitudes: List<Float>, modifier: Modifier = Modifier) {
    val color = StenoTheme.colors.accent

    Canvas(modifier) {
        val barWidth = 4.dp.toPx()
        val minHeight = 4.dp.toPx()
        val margin = 35.dp.toPx()
        val step = (size.width - 2 * margin - barWidth) / (WAVE_BARS - 1)

        val tail = amplitudes.takeLast(WAVE_BARS)
        val shift = WAVE_BARS - tail.size

        for (i in 0 until WAVE_BARS) {
            val a = tail.getOrNull(i - shift)?.coerceIn(0f, 1f) ?: 0f
            val h = max(minHeight, a * size.height)
            drawRoundRect(
                color = color,
                topLeft = Offset(margin + i * step, (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

private fun formatTimer(ms: Long): String {
    val totalSec = ms / 1000
    return "%02d:%02d:%02d".format(totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)
}

private fun formatDay(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))

private val PreviewAmplitudes = List(40) { i -> (0.1f + 0.9f * kotlin.math.abs(kotlin.math.sin(i * 0.55f))) }

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun RecordingIdlePreview() = TerritullaTheme(ThemeMode.LIGHT) {
    RecordingScreen(
        state = RecordingState(title = "Новая запись", date = 1_762_400_000_000),
        snackbarHostState = remember { SnackbarHostState() },
        onIntent = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun RecordingActivePreview() = TerritullaTheme(ThemeMode.LIGHT) {
    RecordingScreen(
        state = RecordingState(
            title = "Новая запись",
            date = 1_762_400_000_000,
            status = RecorderStatus.RECORDING,
            elapsedMs = 3_725_000,
            amplitudes = PreviewAmplitudes,
        ),
        snackbarHostState = remember { SnackbarHostState() },
        onIntent = {},
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun RecordingPausedDarkPreview() = TerritullaTheme(ThemeMode.DARK) {
    RecordingScreen(
        state = RecordingState(
            title = "Новая запись",
            date = 1_762_400_000_000,
            status = RecorderStatus.PAUSED,
            elapsedMs = 65_000,
            amplitudes = PreviewAmplitudes,
        ),
        snackbarHostState = remember { SnackbarHostState() },
        onIntent = {},
    )
}
