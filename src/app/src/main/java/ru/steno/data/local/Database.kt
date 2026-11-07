package ru.steno.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.steno.domain.*

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val path: String, val title: String,
    val titleNormalized: String = normalize(title), val durationMs: Long, val createdAt: Long = System.currentTimeMillis(),
    val status: ProcessingStatus = ProcessingStatus.QUEUED, val stage: ProcessingStage = ProcessingStage.PREPARING_MODELS,
    val fraction: Float = 0f, val error: ProcessingError? = null, val attempts: Int = 0,
    val totalMs: Long = 0, val speakerCount: Int = 0, val preview: String = ""
)

@Entity(tableName = "turns", primaryKeys = ["noteId","ordinal"], foreignKeys = [
    ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)
])
data class Turn(val noteId: Long, val ordinal: Int, val speaker: Int, val startMs: Long, val endMs: Long,
                val text: String, val normalized: String = normalize(text), val speakerName: String? = null) {
    fun domain() = Utterance(speaker, startMs, endMs, text)
}

@Dao
abstract class Notes {
    @Insert abstract suspend fun insert(note: Note): Long
    @Query("SELECT * FROM notes WHERE instr(titleNormalized,:query)>0 OR EXISTS (SELECT 1 FROM turns WHERE noteId=notes.id AND instr(normalized,:query)>0) ORDER BY createdAt DESC")
    abstract fun search(query: String): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE id=:id") abstract fun observe(id: Long): Flow<Note?>
    @Query("SELECT * FROM notes WHERE id=:id") abstract suspend fun get(id: Long): Note?
    @Query("SELECT * FROM turns WHERE noteId=:id ORDER BY ordinal") abstract fun turns(id: Long): Flow<List<Turn>>
    @Query("UPDATE notes SET title=:title,titleNormalized=:normalized WHERE id=:id") abstract suspend fun rename(id: Long,title: String,normalized: String)
    @Query("UPDATE turns SET speakerName=:name WHERE noteId=:id AND speaker=:speaker") abstract suspend fun renameSpeaker(id: Long,speaker: Int,name: String?)
    @Query("UPDATE notes SET status='PROCESSING',attempts=attempts+1,error=NULL WHERE id=:id AND status='QUEUED'") abstract suspend fun claim(id: Long): Int
    @Query("UPDATE notes SET stage=:stage,fraction=:fraction WHERE id=:id AND status='PROCESSING'") abstract suspend fun progress(id: Long,stage: ProcessingStage,fraction: Float): Int
    @Query("UPDATE notes SET status='CANCELLED' WHERE id=:id AND status IN ('QUEUED','PROCESSING')") abstract suspend fun cancel(id: Long)
    @Query("UPDATE notes SET status='QUEUED',error=NULL,fraction=0,attempts=0 WHERE id=:id AND status IN ('FAILED','DONE','CANCELLED')") abstract suspend fun retry(id: Long): Int
    @Query("UPDATE notes SET status='FAILED', error=:reason WHERE id=:id AND status='PROCESSING'") abstract suspend fun fail(id: Long,reason: ProcessingError)
    @Query("UPDATE notes SET status=CASE WHEN attempts<3 THEN 'QUEUED' ELSE 'FAILED' END, error=CASE WHEN attempts<3 THEN NULL ELSE 'UNKNOWN' END WHERE status='PROCESSING'") abstract suspend fun recover()
    @Query("UPDATE notes SET status=CASE WHEN attempts<3 THEN 'QUEUED' ELSE 'FAILED' END WHERE id=:id AND status='PROCESSING'") abstract suspend fun requeueInterrupted(id: Long)
    @Query("SELECT id FROM notes WHERE status='QUEUED' ORDER BY id") abstract suspend fun pending(): List<Long>
    @Query("DELETE FROM notes WHERE id=:id") abstract suspend fun delete(id: Long)
    @Query("DELETE FROM turns WHERE noteId=:id") abstract suspend fun clearTurns(id: Long)
    @Insert abstract suspend fun insertTurns(turns: List<Turn>)
    @Query("UPDATE notes SET status='DONE',fraction=1,error=NULL,totalMs=:ms,speakerCount=:speakers,preview=:preview,durationMs=:duration WHERE id=:id AND status='PROCESSING'")
    abstract suspend fun finish(id: Long,ms: Long,speakers: Int,preview: String,duration: Long): Int
    @Transaction open suspend fun save(id: Long, result: List<Utterance>, ms: Long, duration: Long) {
        if (finish(id, ms, result.distinctBy { it.speaker }.size, result.first().text.take(160), duration) == 0) return
        clearTurns(id)
        insertTurns(result.mapIndexed { i, t -> Turn(id,i,t.speaker,t.startMs,t.endMs,t.text) })
    }
}

@androidx.room.Database(entities = [Note::class,Turn::class], version = 1, exportSchema = false)
abstract class Database : RoomDatabase() { abstract fun notes(): Notes }
