package ru.steno.presentation.app

import androidx.compose.runtime.Immutable
import androidx.lifecycle.*
import kotlinx.coroutines.flow.*
import ru.steno.domain.ThemeMode
import ru.steno.StenoApp

@Immutable data class AppState(val themeMode: ThemeMode = ThemeMode.SYSTEM,val isLoaded: Boolean = false)
class AppViewModel(app: StenoApp) : ViewModel() {
    val state = app.settings.flow.map { AppState(it.themeMode,true) }.stateIn(viewModelScope,SharingStarted.Eagerly,AppState())
}
