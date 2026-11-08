package ru.steno.presentation.note

import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.steno.StenoApp
import ru.steno.data.export.*
import ru.steno.data.local.Turn
import ru.steno.domain.*
import ru.steno.presentation.*
import java.io.File

class NoteViewModel(private val app: StenoApp, private val id: Long,
                    private val engine: ExoPlayer = ExoPlayer.Builder(app).build()) : ScreenModel<NoteState,NoteIntent,NoteEffect>(NoteState()) {
    
    private var rows = emptyList<Utterance>()
    private var titleSave: Job? = null
    private var requestedFormat: ExportFormat? = null
    private var ticker: Job? = null
    init {
        engine.setAudioAttributes(AudioAttributes.DEFAULT,true)
        engine.setHandleAudioBecomingNoisy(true)
        engine.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updatePlayer()
                if (engine.isPlaying && ticker?.isActive != true) ticker = viewModelScope.launch {
                    while (engine.isPlaying) { updatePlayer(); delay(100) }
                } else if (!engine.isPlaying) ticker?.cancel()
            }
        })
        viewModelScope.launch { app.notes.observe(id).filterNotNull().collect { n ->
            if (engine.currentMediaItem == null) {
                engine.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(n.path))))
                engine.prepare()
            }
            reduce { it.copy(title=if (titleSave?.isActive == true) it.title else n.title,createdAt=n.createdAt,
                durationMs=n.durationMs,speakerCount=n.speakerCount,status=n.status,error=n.error,
                progress=TranscriptionProgress(n.stage,n.fraction).takeIf { n.status == ProcessingStatus.PROCESSING }) }
        } }
        viewModelScope.launch { app.notes.turns(id).collect { turns ->
            rows = turns.map(Turn::domain)
            reduce { it.copy(utterances=turns.map { t -> UtteranceItem(t.ordinal.toLong(),t.speaker,t.speakerName,t.startMs,t.endMs,t.text) }) }
            updatePlayer()
        } }
        viewModelScope.launch { app.settings.flow.collect { s -> reduce { it.copy(exportFormat=s.exportFormat) } } }
    }
    private fun updatePlayer() {
        val pos = engine.currentPosition.coerceAtLeast(0)
        reduce { it.copy(player=it.player.copy(isReady=engine.playbackState in setOf(Player.STATE_READY, Player.STATE_ENDED),isPlaying=engine.isPlaying,positionMs=pos),
            activeUtteranceIndex=activeUtterance(rows,pos)) }
    }
    private fun export(uri: String?) {
        val format = requestedFormat ?: return
        requestedFormat = null
        if (uri == null) return
        reduce { it.copy(isExporting=true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val n = requireNotNull(app.notes.get(id))
                    check(n.status == ProcessingStatus.DONE)
                    val turns = app.notes.turns(id).first()
                    val output = if (format == ExportFormat.JSON) jsonTranscript(turns.map(Turn::domain)) else markdown(app,n,turns)
                    requireNotNull(app.contentResolver.openOutputStream(Uri.parse(uri),"wt")).bufferedWriter(Charsets.UTF_8).use { it.write(output) }
                }
                emit(NoteEffect.ShowMessage(UiMessage.ExportDone))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { emit(NoteEffect.ShowMessage(UiMessage.ExportFailed)) }
            finally { reduce { it.copy(isExporting=false) } }
        }
    }
    override fun onIntent(intent: NoteIntent) {
        when(intent) {
            is NoteIntent.TitleChanged -> {
                titleSave?.cancel()
                reduce { it.copy(title=intent.text) }
                titleSave = app.scope.launch { delay(500); app.notes.rename(id,intent.text,normalize(intent.text)) }
            }
            is NoteIntent.UtteranceClicked -> rows.getOrNull(intent.index)?.let { engine.seekTo(it.startMs); updatePlayer() }
            is NoteIntent.SeekTo -> { engine.seekTo(intent.positionMs.coerceIn(0,state.value.durationMs)); updatePlayer() }
            NoteIntent.PlayPauseClicked -> if (engine.isPlaying) engine.pause() else {
                if (engine.playbackState == Player.STATE_ENDED) engine.seekTo(0)
                engine.play()
            }
            is NoteIntent.SpeedSelected -> { engine.setPlaybackSpeed(intent.speed.rate); reduce { it.copy(player=it.player.copy(speed=intent.speed)) } }
            NoteIntent.ExportClicked -> if (state.value.status != ProcessingStatus.DONE) emit(NoteEffect.ShowMessage(UiMessage.TranscriptNotReady))
                else if (!state.value.isExporting) {
                    val format = state.value.exportFormat
                    requestedFormat = format
                    emit(NoteEffect.PickExportDestination(exportName(state.value.title,format),format.mime))
                }
            is NoteIntent.ExportDestinationPicked -> export(intent.uri)
            NoteIntent.CancelProcessingClicked -> app.scope.launch { app.queue.cancel(id) }
            NoteIntent.RetryProcessingClicked -> app.scope.launch { app.queue.retry(id) }
            is NoteIntent.RenameSpeaker -> app.scope.launch { app.notes.renameSpeaker(id,intent.speakerNumber,intent.name.trim().ifBlank { null }) }
            NoteIntent.DeleteNoteClicked -> viewModelScope.launch { withContext(Dispatchers.IO) { app.delete(id) }; emit(NoteEffect.NavigateBack) }
        }
    }
    override fun onCleared() { engine.release(); super.onCleared() }
}
