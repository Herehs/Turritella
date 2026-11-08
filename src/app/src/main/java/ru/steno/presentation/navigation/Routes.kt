package ru.steno.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object MainRoute : NavKey
@Serializable data object RecordingRoute : NavKey
@Serializable data class NoteRoute(val noteId: Long) : NavKey

/** Открывает существующий экран без второго экземпляра плеера/рекордера. */
internal fun MutableList<NavKey>.openRoute(route: NavKey) {
    val existing = indexOf(route)
    if (existing >= 0) {
        while (lastIndex > existing) removeAt(lastIndex)
    } else add(route)
}

internal fun notificationRoute(noteId: Long, openRecording: Boolean): NavKey? = when {
    noteId > 0 -> NoteRoute(noteId)
    openRecording -> RecordingRoute
    else -> null
}

/** Не удаляем корневой экран при повторном нажатии «Назад». */
internal fun MutableList<NavKey>.popRoute() {
    if (size > 1) removeAt(lastIndex)
}
