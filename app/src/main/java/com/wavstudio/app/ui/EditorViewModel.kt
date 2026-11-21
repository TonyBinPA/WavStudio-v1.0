package com.wavstudio.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wavstudio.app.media.MediaProbe
import com.wavstudio.app.model.Clip
import com.wavstudio.app.model.Project
import com.wavstudio.app.model.ProjectOps
import com.wavstudio.app.model.ProjectStore
import com.wavstudio.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ProjectStore(application)
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()

    var project by mutableStateOf(store.load() ?: Project())
        private set
    var selectedClipId by mutableStateOf<String?>(null)
        private set
    var importing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    val selection: Pair<Track, Clip>? get() = selectedClipId?.let { project.findClip(it) }

    // ---- History -------------------------------------------------------------------------------

    /** Applies an edit and records it for undo. */
    fun edit(op: (Project) -> Project) {
        val before = project
        val after = op(before)
        if (after == before) return
        pushUndo(before)
        commit(after)
    }

    /** Call once at the start of a continuous gesture (e.g. dragging trim handles). */
    fun beginGesture() = pushUndo(project)

    /** Applies an edit during a gesture without adding more undo entries. */
    fun editDuringGesture(op: (Project) -> Project) = commit(op(project))

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(project)
        commit(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(project)
        commit(next)
    }

    private fun pushUndo(p: Project) {
        undoStack.addLast(p)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun commit(p: Project) {
        project = p
        if (selectedClipId != null && p.findClip(selectedClipId!!) == null) selectedClipId = null
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        val snapshot = p
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    // ---- Selection & messages ------------------------------------------------------------------

    fun select(clipId: String?) {
        selectedClipId = clipId
    }

    fun showMessage(text: String) {
        message = text
    }

    fun clearMessage() {
        message = null
    }

    // ---- Import --------------------------------------------------------------------------------

    /** Where newly imported media should go. */
    sealed interface ImportTarget {
        /** Video files to the video track, audio files to the first audio track. */
        data object Auto : ImportTarget
        /** Everything (including the sound of video files) onto an existing audio track. */
        data class AudioTrack(val trackId: String) : ImportTarget
        /** Everything (including the sound of video files) onto a new audio track. */
        data object NewAudioTrack : ImportTarget
    }

    fun import(uris: List<Uri>, target: ImportTarget = ImportTarget.Auto) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        importing = true
        viewModelScope.launch {
            val failures = mutableListOf<String>()
            val probed = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    try {
                        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) {
                        // Not every provider grants persistable access; the clip still works this session.
                    }
                    try {
                        MediaProbe.probe(app, uri)
                    } catch (e: Exception) {
                        failures += e.message ?: uri.toString()
                        null
                    }
                }
            }
            val clips = if (target == ImportTarget.Auto) {
                probed
            } else {
                probed.filter { clip ->
                    clip.hasAudio.also { if (!it) failures += "\"${clip.name}\" has no audio to put on an audio track" }
                }.map { it.copy(hasVideo = false) }
            }
            if (clips.isNotEmpty()) {
                edit { p ->
                    when (target) {
                        ImportTarget.Auto -> ProjectOps.addClips(p, clips)
                        is ImportTarget.AudioTrack -> ProjectOps.addClips(p, clips, target.trackId)
                        ImportTarget.NewAudioTrack -> ProjectOps.addAudioTrack(p, clips)
                    }
                }
                selectedClipId = clips.last().id
            }
            importing = false
            if (failures.isNotEmpty()) message = failures.joinToString("\n")
        }
    }

    fun newProject() {
        edit { Project() }
        selectedClipId = null
    }

    companion object {
        private const val MAX_HISTORY = 100
    }
}
