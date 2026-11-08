package ru.steno

import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.steno.presentation.navigation.*

class NavigationTest {
    @Test fun notificationChoosesValidDestination() {
        assertEquals(NoteRoute(7), notificationRoute(7, false))
        assertEquals(RecordingRoute, notificationRoute(-1, true))
        assertNull(notificationRoute(-1, false))
        assertNull(notificationRoute(0, false))
    }

    @Test fun repeatedBackKeepsRoot() {
        val stack = mutableListOf<NavKey>(MainRoute, NoteRoute(7))
        repeat(3) { stack.popRoute() }
        assertEquals(listOf(MainRoute), stack)
    }

    @Test fun openingExistingRoutePopsInsteadOfDuplicatingPlayer() {
        val stack = mutableListOf<NavKey>(MainRoute, NoteRoute(7), NoteRoute(8))
        stack.openRoute(NoteRoute(7))
        assertEquals(listOf(MainRoute, NoteRoute(7)), stack)
        stack.openRoute(NoteRoute(7))
        assertEquals(2, stack.size)
        stack.openRoute(RecordingRoute)
        assertEquals(RecordingRoute, stack.last())
    }
}
