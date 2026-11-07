package ru.steno.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object MainRoute : NavKey
@Serializable data object RecordingRoute : NavKey
@Serializable data class NoteRoute(val noteId: Long) : NavKey
