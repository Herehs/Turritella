package ru.steno.data.export

import android.content.Context
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import ru.steno.R
import ru.steno.data.local.*
import ru.steno.domain.*
import ru.steno.presentation.common.format.*
import java.util.Locale

@Serializable private data class Export(val transcript: List<Row>)
@Serializable private data class Row(@SerialName("speaker_id") val speaker: String,
    @SerialName("start_ms") val start: Long,@SerialName("end_ms") val end: Long,val text: String)
fun jsonTranscript(turns: List<Utterance>) = Json.encodeToString(Export(turns.map { Row("speaker_${it.speaker}",it.startMs,it.endMs,it.text) }))
fun exportName(title: String, format: ExportFormat) = title.replace(Regex("""[/\\:*?"<>|\p{Cntrl}]"""),"_").trim().ifBlank { "transcript" } + "." + format.extension
fun markdown(context: Context, note: Note, turns: List<Turn>) = buildString {
    appendLine("# " + note.title)
    appendLine()
    appendLine(listOf(formatDate(note.createdAt,Locale.getDefault()),formatClock(note.durationMs),context.getString(R.string.participants,note.speakerCount)).joinToString(" · "))
    for (t in turns) {
        appendLine()
        val name = t.speakerName ?: context.getString(R.string.speaker,t.speaker)
        appendLine("**$name** · " + formatTimeRange(t.startMs,t.endMs,note.durationMs >= 3600000))
        appendLine(t.text)
    }
}
