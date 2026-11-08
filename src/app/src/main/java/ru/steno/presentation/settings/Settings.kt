package ru.steno.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.steno.R
import ru.steno.domain.AsrModelType
import ru.steno.domain.ExportFormat
import ru.steno.domain.ThemeMode
import ru.steno.domain.WhisperModelState
import ru.steno.presentation.StenoTheme

private val OptionShape = RoundedCornerShape(10.dp)

@Composable
fun Settings(
    state: SettingsState = SettingsState(),
    onIntent: (SettingsIntent) -> Unit
){
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StenoTheme.colors.text)
    ){
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 50.dp)
                .fillMaxWidth(0.5f),
        ) {
            DrawerHeader()

            Spacer(Modifier.height(30.dp))

            DrawerOptionGroup(
                title = "Выберите модель:",
                icon = painterResource(R.drawable.ic_brain),
                options = listOf(AsrModelType.WHISPER, AsrModelType.GIGA_AM),
                selected = state.asrModel,
                label = { it.label() },
                onSelect = { onIntent(SettingsIntent.AsrModelSelected(it)) },
                optionTrailing = { type ->
                    if (type == AsrModelType.WHISPER && state.whisper is WhisperModelState.NotInstalled) {
                        Text(
                            text = "Скачать",
                            fontSize = 13.sp,
                            color = StenoTheme.colors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onIntent(SettingsIntent.DownloadWhisperClicked) }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                },
            )

            Spacer(Modifier.height(30.dp))

            DrawerOptionGroup(
                title = "Тема:",
                icon = painterResource(R.drawable.ic_palette),
                options = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
                selected = state.themeMode,
                label = { it.label() },
                onSelect = { onIntent(SettingsIntent.ThemeSelected(it)) },
            )

            Spacer(Modifier.height(30.dp))

            DrawerOptionGroup(
                title = "Формат экспорта:",
                icon = painterResource(R.drawable.ic_file_export),
                options = listOf(ExportFormat.JSON, ExportFormat.MARKDOWN),
                selected = state.exportFormat,
                label = { it.name },
                onSelect = { onIntent(SettingsIntent.ExportFormatSelected(it)) },
            )
        }
    }
}

private fun AsrModelType.label(): String = when (this) {
    AsrModelType.WHISPER -> "Whisper"
    AsrModelType.GIGA_AM -> "GigaAM"
    else -> name
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
    ThemeMode.SYSTEM -> "Системная"
}

@Composable
fun DrawerHeader(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(width = 47.dp, height = 60.dp),
            tint = StenoTheme.colors.bg,
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = buildAnnotatedString {
                append("Turri")
                withStyle(SpanStyle(color = StenoTheme.colors.accent)) { append("Tell") }
                append("a")
            },
            modifier = Modifier.padding(top = 3.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = StenoTheme.colors.bg,
            maxLines = 1,
        )
    }
}

@Composable
fun <T> DrawerOptionGroup(
    title: String,
    icon: Painter,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionTrailing: (@Composable (T) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().selectableGroup()) {
        Row(
            Modifier.height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = StenoTheme.colors.bg)
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = StenoTheme.colors.bg, maxLines = 1)
        }
        Spacer(Modifier.height(7.dp))
        options.forEach { option ->
            DrawerOption(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                trailing = optionTrailing?.let { slot -> { slot(option) } },
            )
        }
    }
}

@Composable
fun DrawerOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(OptionShape)
            .background(if (selected) StenoTheme.colors.bg else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            color = if (selected) StenoTheme.colors.text else StenoTheme.colors.bg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}
