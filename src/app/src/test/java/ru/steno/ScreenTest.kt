package ru.steno

import androidx.media3.exoplayer.ExoPlayer
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import ru.steno.data.audio.Capture
import ru.steno.data.local.*
import ru.steno.domain.*
import ru.steno.presentation.UiMessage
import ru.steno.presentation.app.*
import ru.steno.presentation.main.*
import ru.steno.presentation.note.*
import ru.steno.presentation.recording.*
import ru.steno.presentation.settings.*

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenTest {
    private val dispatcher = StandardTestDispatcher()
    private val app = mockk<StenoApp>(relaxed=true)
    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { app.settings.flow } returns flowOf(AppSettings())
        every { app.recorder.state } returns MutableStateFlow(Capture())
    }
    @AfterEach fun teardown() { Dispatchers.resetMain() }

    @Test fun themeLoads() = runTest(dispatcher) {
        val vm = AppViewModel(app)
        runCurrent()
        assertEquals(AppState(ThemeMode.SYSTEM,true),vm.state.value)
    }

    @Test fun searchAndPicker() = runTest(dispatcher) {
        every { app.notes.search(any()) } returns flowOf(emptyList())
        every { app.models } returns MutableStateFlow(ModelsPreparation.Ready)
        val vm = MainViewModel(app)
        vm.onIntent(MainIntent.QueryChanged(" МОЁ "))
        advanceTimeBy(301)
        runCurrent()
        verify { app.notes.search("мое") }
        vm.onIntent(MainIntent.ImportAudioClicked)
        assertEquals(MainEffect.PickAudio(listOf("audio/*","application/ogg")), vm.effects.first())
        vm.onIntent(MainIntent.AudioPicked(null))
        assertFalse(vm.state.value.isImporting)
    }

    @Test fun missingWhisper() = runTest(dispatcher) {
        every { app.whisper.state } returns flowOf(WhisperModelState.NotInstalled())
        every { app.whisper.installed } returns false
        val vm = SettingsViewModel(app)
        vm.onIntent(SettingsIntent.AsrModelSelected(AsrModelType.WHISPER))
        assertEquals(SettingsEffect.ShowMessage(UiMessage.WhisperNotInstalled),vm.effects.first())
        val settings = app.settings
        coVerify(exactly=0) { settings.set("asr",AsrModelType.WHISPER) }
    }

    @Test fun micPermissionAndBack() = runTest(dispatcher) {
        every { app.getString(any(),*anyVararg()) } returns "Запись"
        every { app.checkSelfPermission(any()) } returns -1
        val vm = RecordingViewModel(app)
        vm.onIntent(RecordingIntent.StartClicked)
        assertEquals(RecordingEffect.RequestMicPermission,vm.effects.first())
        vm.onIntent(RecordingIntent.MicPermissionResult(false))
        assertEquals(RecordingEffect.ShowMessage(UiMessage.MicPermissionDenied),vm.effects.first())
        assertTrue(vm.state.value.micPermissionDenied)
        vm.onIntent(RecordingIntent.BackClicked)
        assertEquals(RecordingEffect.NavigateBack,vm.effects.first())
    }

    @Test fun noteSeekAndExportGuard() = runTest(dispatcher) {
        val player = mockk<ExoPlayer>(relaxed=true)
        every { app.notes.observe(1L) } returns flowOf(null)
        every { app.notes.turns(1L) } returns flowOf(listOf(Turn(1,0,1,1200,3000,"Добрый день")))
        val vm = NoteViewModel(app,1,player)
        runCurrent()
        vm.onIntent(NoteIntent.UtteranceClicked(0))
        verify { player.seekTo(1200L) }
        vm.onIntent(NoteIntent.ExportClicked)
        assertEquals(NoteEffect.ShowMessage(UiMessage.TranscriptNotReady),vm.effects.first())
    }
}
