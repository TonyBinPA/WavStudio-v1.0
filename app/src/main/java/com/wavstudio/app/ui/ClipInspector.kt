package com.wavstudio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavstudio.app.model.Clip
import com.wavstudio.app.model.MIN_CLIP_MS
import com.wavstudio.app.model.Track
import com.wavstudio.app.model.TrackKind

data class ClipActions(
    val onTrimStart: () -> Unit,
    val onTrim: (startMs: Long, endMs: Long) -> Unit,
    val onSplit: (sourceMs: Long) -> Unit,
    val onMove: (delta: Int) -> Unit,
    val onDuplicate: () -> Unit,
    val onDelete: () -> Unit,
    val onToggleAudio: () -> Unit,
    val onDetachAudio: () -> Unit,
    val onExtractAudio: () -> Unit,
    val onConvert: () -> Unit,
    val onClose: () -> Unit,
)

/** Editing controls for the selected clip. [playheadMs] is the clip preview position (source time). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClipInspector(
    clip: Clip,
    track: Track,
    indexInTrack: Int,
    playheadMs: Long,
    actions: ClipActions,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val playheadInside = playheadMs > clip.trimStartMs + MIN_CLIP_MS && playheadMs < clip.trimEndMs - MIN_CLIP_MS

    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindIcon(video = clip.hasVideo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(clip.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${track.name} · source ${formatTime(clip.sourceDurationMs)}" +
                            if (clip.hasVideo && clip.width > 0) " · ${clip.displayWidth}×${clip.displayHeight}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = actions.onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }

            // ---- Trim ----
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Trim", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    "In ${formatTime(clip.trimStartMs)} · Out ${formatTime(clip.trimEndMs)} · ${formatTime(clip.durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            RangeSlider(
                value = clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat(),
                onValueChange = { range ->
                    if (!dragging) {
                        actions.onTrimStart()
                        dragging = true
                    }
                    actions.onTrim(range.start.toLong(), range.endInclusive.toLong())
                },
                onValueChangeFinished = { dragging = false },
                valueRange = 0f..clip.sourceDurationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Set in at playhead", Icons.Default.FirstPage, enabled = playheadMs < clip.trimEndMs - MIN_CLIP_MS) {
                    actions.onTrimStart()
                    actions.onTrim(playheadMs, clip.trimEndMs)
                }
                ActionChip("Set out at playhead", Icons.AutoMirrored.Filled.LastPage, enabled = playheadMs > clip.trimStartMs + MIN_CLIP_MS) {
                    actions.onTrimStart()
                    actions.onTrim(clip.trimStartMs, playheadMs)
                }
                ActionChip("Split at playhead", Icons.AutoMirrored.Filled.CallSplit, enabled = playheadInside) { actions.onSplit(playheadMs) }
            }

            // ---- Arrange ----
            Text("Arrange", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Move earlier", Icons.AutoMirrored.Filled.ArrowBack, enabled = indexInTrack > 0) { actions.onMove(-1) }
                ActionChip("Move later", Icons.AutoMirrored.Filled.ArrowForward, enabled = indexInTrack < track.clips.lastIndex) { actions.onMove(1) }
                ActionChip("Duplicate", Icons.Default.ContentCopy) { actions.onDuplicate() }
                ActionChip("Delete", Icons.Default.Delete) { actions.onDelete() }
            }

            // ---- Audio / format ----
            Text("Audio & format", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (clip.hasAudio) {
                    ActionChip(
                        if (clip.audioMuted) "Unmute clip" else "Mute clip",
                        if (clip.audioMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    ) { actions.onToggleAudio() }
                }
                if (track.kind == TrackKind.VIDEO && clip.isAudible) {
                    ActionChip("Detach audio to new track", Icons.Default.LinkOff) { actions.onDetachAudio() }
                }
                if (clip.hasAudio) {
                    ActionChip("Extract audio file…", Icons.Default.AudioFile) { actions.onExtractAudio() }
                }
                ActionChip("Convert clip…", Icons.Default.Transform) { actions.onConvert() }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}
