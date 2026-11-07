package ru.steno.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import ru.steno.domain.*

private val Context.preferences by preferencesDataStore("settings")
class Settings(private val context: Context) {
    private inline fun <reified E : Enum<E>> Preferences.read(key: String, default: E): E =
        enumValues<E>().find { it.name == this[stringPreferencesKey(key)] } ?: default
    val flow = context.preferences.data.map {
        AppSettings(it.read("theme",ThemeMode.SYSTEM), it.read("export",ExportFormat.JSON),
            it.read("asr",AsrModelType.GIGA_AM), it.read("layout",NotesLayout.GRID), it.read("sort",NoteSort.DATE_DESC),
            it[booleanPreferencesKey("punctuation")] ?: true, it[intPreferencesKey("speakers")],
            it[stringSetPreferencesKey("favorites")].orEmpty().mapNotNull(String::toLongOrNull).toSet())
    }
    suspend fun set(key: String, value: Enum<*>) { context.preferences.edit { it[stringPreferencesKey(key)] = value.name } }
    suspend fun toggleFavorite(id: Long) {
        context.preferences.edit { prefs ->
            val key = stringSetPreferencesKey("favorites")
            val ids = prefs[key].orEmpty()
            prefs[key] = if (id.toString() in ids) ids - id.toString() else ids + id.toString()
        }
    }
    suspend fun punctuation(enabled: Boolean) { context.preferences.edit { it[booleanPreferencesKey("punctuation")] = enabled } }
    suspend fun speakers(count: Int?) {
        require(count == null || count > 0)
        context.preferences.edit { if (count == null) it.remove(intPreferencesKey("speakers")) else it[intPreferencesKey("speakers")] = count }
    }
}
