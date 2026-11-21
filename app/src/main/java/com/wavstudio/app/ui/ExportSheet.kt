package com.wavstudio.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wavstudio.app.export.AudioQuality
import com.wavstudio.app.export.ExportManager
import com.wavstudio.app.export.ExportSettings
import com.wavstudio.app.export.ExportState
import com.wavstudio.app.export.OutputFormat
import com.wavstudio.app.export.Resolution
import com.wavstudio.app.export.VideoCodec
import com.wavstudio.app.model.Clip
import com.wavstudio.app.model.Project

/** What the export sheet was opened for. */
data class ExportRequest(val clip: Clip? = null, val format: OutputFormat? = null)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(
    project: Project,
    request: ExportRequest,
    exportState: ExportState,
    onStart: (Project, ExportSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var clipOnly by remember { mutableStateOf(request.clip != null) }
    val scopeProject = if (clipOnly && request.clip != null) Project.singleClip(request.clip) else project
    val scopeHasVideo = scopeProject.hasVideo
    var settings by remember {
        mutableStateOf(ExportSettings(format = request.format ?: if (project.hasVideo) OutputFormat.MP4 else OutputFormat.M4A))
    }
    LaunchedEffect(scopeHasVideo) {
        if (!scopeHasVideo && !settings.format.isAudioOnly) settings = settings.copy(format = OutputFormat.M4A)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Export", style = MaterialTheme.typography.headlineSmall)

            when (exportState) {
                is ExportState.Running -> {
                    Text(exportState.stage + (exportState.progress?.let { " · ${(it * 100).toInt()}%" } ?: "…"))
                    if (exportState.progress != null) {
                        LinearProgressIndicator(progress = { exportState.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(
                        "You can leave the app — the export continues in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { ExportManager.cancel() }) { Text("Cancel export") }
                    return@Column
                }
                is ExportState.Success -> {
                    val output = exportState.output
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Saved ${output.displayName}", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Location: ${output.location}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(output.uri, output.mimeType)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                            }
                        }) { Text("Open") }
                        OutlinedButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType(output.mimeType)
                                .putExtra(Intent.EXTRA_STREAM, output.uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, "Share ${output.displayName}"))
                        }) { Text("Share") }
                        OutlinedButton(onClick = { ExportManager.acknowledge() }) { Text("Export again") }
                    }
                    return@Column
                }
                is ExportState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(exportState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                ExportState.Cancelled -> Text("Export cancelled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExportState.Idle -> Unit
            }

            if (request.clip != null) {
                Label("What to export")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !clipOnly, onClick = { clipOnly = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) {
                        Text("Whole project")
                    }
                    SegmentedButton(selected = clipOnly, onClick = { clipOnly = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) {
                        Text("This clip")
                    }
                }
            }

            Label("Video formats")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputFormat.entries.filter { !it.isAudioOnly }.forEach { f ->
                    FormatChip(f, selected = settings.format == f, enabled = scopeHasVideo) { settings = settings.copy(format = f) }
                }
            }
            if (!scopeHasVideo) {
                Hint("Video formats need a clip on the video track.")
            }
            Label("Audio-only formats (separates the audio from video)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputFormat.entries.filter { it.isAudioOnly }.forEach { f ->
                    FormatChip(f, selected = settings.format == f, enabled = true) { settings = settings.copy(format = f) }
                }
            }
            Hint(settings.format.description)

            if (settings.format == OutputFormat.MP4) {
                Label("Video codec")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoCodec.entries.forEach { c ->
                        FilterChip(selected = settings.videoCodec == c, onClick = { settings = settings.copy(videoCodec = c) }, label = { Text(c.label) })
                    }
                }
                if (settings.videoCodec != VideoCodec.H264) {
                    Hint("If this device has no ${settings.videoCodec.label} encoder, WavStudio falls back to a supported codec.")
                }
            }
            if (!settings.format.isAudioOnly) {
                Label("Resolution")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Resolution.entries.forEach { r ->
                        FilterChip(selected = settings.resolution == r, onClick = { settings = settings.copy(resolution = r) }, label = { Text(r.label) })
                    }
                }
            }
            if (settings.format != OutputFormat.WAV) {
                Label("Audio quality")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { q ->
                        FilterChip(selected = settings.audioQuality == q, onClick = { settings = settings.copy(audioQuality = q) }, label = { Text(q.label) })
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onStart(scopeProject, settings) },
                enabled = !scopeProject.isEmpty,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export ${settings.format.label}")
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.titleSmall)

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun FormatChip(format: OutputFormat, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(".${format.extension}") },
    )
}
