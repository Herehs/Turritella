package ru.steno

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.steno.domain.*
import ru.steno.data.audio.RecorderStatus
import ru.steno.presentation.main.*
import ru.steno.presentation.settings.*
import ru.steno.presentation.note.*
import ru.steno.presentation.recording.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w412dp-h917dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HexUiTest {
    @get:Rule val ui=createComposeRule()
    private val notes=(1L..6L).map { NoteItem(it,"Обсуждение проекта $it",1793952000000,125000,ProcessingStatus.DONE,null,2,
        "Обсудили задачи команды и план следующего этапа. Договорились проверить приложение и подготовить демонстрацию.",it==1L) }
    private fun capture(name: String) {
        val bitmap=ui.onRoot().captureToImage().asAndroidBitmap()
        File("build/hexteam-screenshots").mkdirs()
        File("build/hexteam-screenshots/$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun listSearchLayoutAndFavorite() {
        var state by mutableStateOf(MainState(notes=notes,isLoading=false,layout=NotesLayout.LIST))
        val actions=mutableListOf<MainIntent>()
        ui.setContent {HexTheme(ThemeMode.LIGHT){MainScreen(state,{ actions+=it; when(it) {
            is MainIntent.QueryChanged -> state=state.copy(query=it.text)
            is MainIntent.LayoutSelected -> state=state.copy(layout=it.layout)
            else -> Unit
        } }) {}}}
        capture("list-light")
        ui.onNodeWithContentDescription("Вид сеткой").performClick()
        assertTrue(actions.last() is MainIntent.LayoutSelected)
        capture("grid-light")
        ui.onAllNodesWithContentDescription("Добавить в избранное")[0].performClick()
        assertTrue(actions.last() is MainIntent.FavoriteClicked)
        ui.onNodeWithText("Поиск..").performTextInput("проект")
        assertEquals("проект",state.query)
        ui.onNodeWithContentDescription("Добавить запись").performClick()
        ui.onNodeWithText("Выбрать аудиофайл").performClick()
        assertEquals(MainIntent.ImportAudioClicked,actions.last())
    }
    @Test fun drawerThemeChoice() {
        val actions=mutableListOf<SettingsIntent>()
        ui.setContent {HexTheme(ThemeMode.LIGHT){MainScreen(MainState(notes=notes,isLoading=false,layout=NotesLayout.LIST),{}) {
            SettingsDrawerContent(SettingsState(themeMode=ThemeMode.LIGHT),actions::add,it)
        }}}
        ui.onNodeWithContentDescription("Открыть меню").performClick()
        capture("drawer-light")
        ui.onNodeWithText("Тёмная").performClick()
        assertEquals(SettingsIntent.ThemeSelected(ThemeMode.DARK),actions.last())
    }
    @Test fun darkList() {
        ui.setContent {HexTheme(ThemeMode.DARK){MainScreen(MainState(notes=notes,isLoading=false,layout=NotesLayout.LIST),{}){}}}
        capture("list-dark")
    }
    @Test fun recordingPauseAndStop() {
        val actions=mutableListOf<RecordingIntent>()
        ui.setContent {HexTheme(ThemeMode.LIGHT){RecordingScreen(RecordingState(title="Новая запись",date=1793952000000,
            status=RecorderStatus.RECORDING,elapsedMs=36983000,amplitudes=List(41){((it*17)%37)/40f}),actions::add)}}
        capture("recording-light")
        ui.onNodeWithContentDescription("Пауза").performClick()
        assertEquals(RecordingIntent.PauseClicked,actions.last())
        ui.onNodeWithContentDescription("Завершить запись").performClick()
        assertEquals(RecordingIntent.StopClicked,actions.last())
    }
    @Test fun transcriptSeekAndExport() {
        val actions=mutableListOf<NoteIntent>()
        val state=NoteState(title="Обсуждение проекта",durationMs=148000,speakerCount=2,status=ProcessingStatus.DONE,
            player=PlayerUiState(isReady=true,isPlaying=true),activeUtteranceIndex=1,
            utterances=listOf(UtteranceItem(1,1,null,0,18000,"Предлагаю начать с задач на ближайшую неделю."),
                UtteranceItem(2,2,null,18000,27000,"Давайте проверим приложение и подготовим демонстрацию для команды."),
                UtteranceItem(3,1,null,27000,34000,"Хорошо. Я возьму на себя проверку записи и экспорта.")))
        ui.setContent {HexTheme(ThemeMode.LIGHT){NoteScreen(state,actions::add,{})}}
        capture("transcript-light")
        ui.onNodeWithText("Давайте проверим приложение и подготовим демонстрацию для команды.").performClick()
        assertEquals(NoteIntent.UtteranceClicked(1),actions.last())
        ui.onNodeWithContentDescription("Экспорт расшифровки").performClick()
        assertEquals(NoteIntent.ExportClicked,actions.last())
    }
}
