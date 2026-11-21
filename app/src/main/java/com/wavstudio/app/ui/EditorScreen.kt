package com.wavstudio.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wavstudio.app.export.ExportManager
import com.wavstudio.app.export.ExportState
import com.wavstudio.app.export.OutputFormat
import com.wavstudio.app.model.ProjectOps
import com.wavstudio.app.model.TrackKind

private val MEDIA_TYPES = arrayOf("video/*", "audio/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val project = vm.project
    val selection = vm.selection
    val exportState by ExportManager.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val playhead = remember { mutableLongStateOf(0L) }
    var previewMode by rememberSaveable { mutableStateOf(PreviewMode.CLIP) }
    var exportRequest by remember { mutableStateOf<ExportRequest?>(null) }
    var confirmNew by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Picker target is remembered across the round-trip to the system file picker.
    var pendingTarget by remember { mutableStateOf<EditorViewModel.ImportTarget>(EditorViewModel.ImportTarget.Auto) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.import(uris, pendingTarget)
    }
    fun pick(target: EditorViewModel.ImportTarget) {
        pendingTarget = target
        picker.launch(MEDIA_TYPES)
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun startExport(p: com.wavstudio.app.model.Project, s: com.wavstudio.app.export.ExportSettings) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ExportManager.start(context, p, s)
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    // Selecting a clip switches the preview to it.
    LaunchedEffect(vm.selectedClipId) {
        if (vm.selectedClipId != null) previewMode = PreviewMode.CLIP
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WavStudio") },
                actions = {
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo") }
                    IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo") }
                    FilledTonalButton(
                        onClick = { exportRequest = ExportRequest() },
                        enabled = !project.isEmpty || exportState is ExportState.Running,
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (exportState is ExportState.Running) "Exporting…" else "Export")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Add media") }, onClick = {
                                menuOpen = false
                                pick(EditorViewModel.ImportTarget.Auto)
                            })
                            DropdownMenuItem(text = { Text("New project") }, enabled = !project.isEmpty, onClick = {
                                menuOpen = false
                                confirmNew = true
                            })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (project.isEmpty) {
            EmptyState(
                importing = vm.importing,
                onImport = { pick(EditorViewModel.ImportTarget.Auto) },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val timelineCallbacks = TimelineCallbacks(
            onSelectClip = { id -> vm.select(if (vm.selectedClipId == id) null else id) },
            onAddToTrack = { track ->
                pick(if (track.kind == TrackKind.VIDEO) EditorViewModel.ImportTarget.Auto else EditorViewModel.ImportTarget.AudioTrack(track.id))
            },
            onAddAudioTrack = { pick(EditorViewModel.ImportTarget.NewAudioTrack) },
            onRemoveTrack = { track -> vm.edit { ProjectOps.removeTrack(it, track.id) } },
            onToggleTrackMute = { track -> vm.edit { ProjectOps.setTrackMuted(it, track.id, !track.muted) } },
            onTrackSettingsStart = vm::beginGesture,
            onTrackVolume = { track, v -> vm.editDuringGesture { ProjectOps.setTrackVolume(it, track.id, v) } },
            onTrackOffset = { track, ms -> vm.editDuringGesture { ProjectOps.setTrackOffset(it, track.id, ms) } },
        )

        val preview: @Composable (Modifier) -> Unit = { mod ->
            PreviewPane(
                project = project,
                clip = selection?.second,
                mode = previewMode,
                onModeChange = { previewMode = it },
                playhead = playhead,
                modifier = mod,
            )
        }
        val inspector: @Composable () -> Unit = {
            selection?.let { (track, clip) ->
                val id = clip.id
                ClipInspector(
                    clip = clip,
                    track = track,
                    indexInTrack = track.clips.indexOfFirst { it.id == id },
                    playheadMs = if (previewMode == PreviewMode.CLIP) playhead.longValue else -1,
                    actions = ClipActions(
                        onTrimStart = vm::beginGesture,
                        onTrim = { s, e -> vm.editDuringGesture { ProjectOps.trimClip(it, id, s, e) } },
                        onSplit = { pos -> vm.edit { ProjectOps.splitClip(it, id, pos) } },
                        onMove = { d -> vm.edit { ProjectOps.moveClip(it, id, d) } },
                        onDuplicate = { vm.edit { ProjectOps.duplicateClip(it, id) } },
                        onDelete = { vm.edit { ProjectOps.removeClip(it, id) } },
                        onToggleAudio = { vm.edit { ProjectOps.setClipAudioMuted(it, id, !clip.audioMuted) } },
                        onDetachAudio = {
                            vm.edit { ProjectOps.detachAudio(it, id) }
                            vm.showMessage("Audio moved to a new track — the video clip is now muted")
                        },
                        onExtractAudio = { exportRequest = ExportRequest(clip = clip, format = OutputFormat.M4A) },
                        onConvert = { exportRequest = ExportRequest(clip = clip) },
                        onClose = { vm.select(null) },
                    ),
                )
            }
        }

        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 840.dp // Tablets, Galaxy Z Fold inner screen, DeX
            if (vm.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (wide) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        preview(Modifier.fillMaxWidth())
                        inspector()
                    }
                    Timeline(
                        project = project,
                        selectedClipId = vm.selectedClipId,
                        callbacks = timelineCallbacks,
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    preview(Modifier.fillMaxWidth())
                    inspector()
                    Timeline(project = project, selectedClipId = vm.selectedClipId, callbacks = timelineCallbacks)
                }
            }
        }
    }

    exportRequest?.let { request ->
        ExportSheet(
            project = project,
            request = request,
            exportState = exportState,
            onStart = ::startExport,
            onDismiss = {
                exportRequest = null
                ExportManager.acknowledge()
            },
        )
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Start a new project?") },
            text = { Text("The current timeline will be cleared. You can undo this.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmNew = false
                    vm.newProject()
                }) { Text("New project") }
            },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyState(importing: Boolean, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 420.dp)) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Start editing", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Import video or audio to trim, combine clips, add music tracks, convert formats or pull the audio out of a video.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            if (importing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Button(onClick = onImport) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import media")
                }
            }
        }
    }
}
