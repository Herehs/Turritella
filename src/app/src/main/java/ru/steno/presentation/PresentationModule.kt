package ru.steno.presentation

import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.steno.StenoApp
import ru.steno.presentation.app.AppViewModel
import ru.steno.presentation.main.MainViewModel
import ru.steno.presentation.note.NoteViewModel
import ru.steno.presentation.recording.RecordingViewModel
import ru.steno.presentation.settings.SettingsViewModel

import org.koin.androidx.workmanager.dsl.worker
import ru.steno.data.work.TranscriptionWorker
import ru.steno.data.model.download.WhisperWorker

val presentationModule = module {
    worker { p -> TranscriptionWorker(androidApplication(),p.get()) }
    worker { p -> WhisperWorker(androidApplication(),p.get()) }
    single { androidApplication() as StenoApp }
    viewModel { AppViewModel(get()) }
    viewModel { MainViewModel(get()) }
    viewModel { RecordingViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { params -> NoteViewModel(get(),params.get()) }
}
