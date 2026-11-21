package com.wavstudio.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wavstudio.app.media.MediaProbe
import com.wavstudio.app.model.Clip
import com.wavstudio.app.model.Project
import com.wavstudio.app.model.Track
import com.wavstudio.app.model.TrackKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ClipHeight = 64.dp
private val MinClipWidth = 56.dp

data class TimelineCallbacks(
    val onSelectClip: (String) -> Unit,
    val onAddToTrack: (Track) -> Unit,
    val onAddAudioTrack: () -> Unit,
    val onRemoveTrack: (Track) -> Unit,
    val onToggleTrackMute: (Track) -> Unit,
    val onTrackSettingsStart: () -> Unit,
    val onTrackVolume: (Track, Float) -> Unit,
    val onTrackOffset: (Track, Long) -> Unit,
)

/** Multi-track timeline: the main video track followed by any number of audio tracks. */
@Composable
fun Timeline(
    project: Project,
    selectedClipId: String?,
    callbacks: TimelineCallbacks,
    modifier: Modifier = Modifier,
) {
    var dpPerSecond by remember { mutableFloatStateOf(24f) }
    var settingsTrackId by remember { mutableStateOf<String?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Timeline", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(formatTime(project.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { dpPerSecond = (dpPerSecond / 1.5f).coerceAtLeast(2f) }) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
            }
            IconButton(onClick = { dpPerSecond = (dpPerSecond * 1.5f).coerceAtMost(240f) }) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
            }
        }

        project.tracks.forEach { track ->
            TrackRow(
                track = track,
                selectedClipId = selectedClipId,
                dpPerSecond = dpPerSecond,
                callbacks = callbacks,
                onOpenSettings = { settingsTrackId = track.id },
            )
        }

        OutlinedButton(onClick = callbacks.onAddAudioTrack, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add audio track")
        }
    }

    val settingsTrack = project.tracks.firstOrNull { it.id == settingsTrackId }
    if (settingsTrack != null) {
        TrackSettingsDialog(
            track = settingsTrack,
            maxOffsetMs = maxOf(project.mainTrack.durationMs, 60_000L),
            callbacks = callbacks,
            onDismiss = { settingsTrackId = null },
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    selectedClipId: String?,
    dpPerSecond: Float,
    callbacks: TimelineCallbacks,
    onOpenSettings: () -> Unit,
) {
    val isVideo = track.kind == TrackKind.VIDEO
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindIcon(video = isVideo, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(track.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.volume != 1f) {
                Text("  ${(track.volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { callbacks.onToggleTrackMute(track) }) {
                Icon(
                    if (track.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (track.muted) "Unmute track" else "Mute track",
                    tint = if (track.muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Tune, contentDescription = "Track settings") }
            if (!isVideo) {
                IconButton(onClick = { callbacks.onRemoveTrack(track) }) { Icon(Icons.Default.Delete, contentDescription = "Delete track") }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            if (track.offsetMs > 0 && track.clips.isNotEmpty()) {
                item(key = "offset") {
                    Box(
                        Modifier
                            .width(widthFor(track.offsetMs, dpPerSecond, Dp(8f)))
                            .height(ClipHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(formatTime(track.offsetMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            items(track.clips, key = { it.id }) { clip ->
                ClipCard(
                    clip = clip,
                    trackMuted = track.muted,
                    selected = clip.id == selectedClipId,
                    width = widthFor(clip.durationMs, dpPerSecond, MinClipWidth),
                    onClick = { callbacks.onSelectClip(clip.id) },
                )
            }
            item(key = "add") {
                Card(
                    onClick = { callbacks.onAddToTrack(track) },
                    modifier = Modifier.size(ClipHeight),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = if (isVideo) "Add video clips" else "Add audio to ${track.name}")
                    }
                }
            }
        }
    }
}

private fun widthFor(durationMs: Long, dpPerSecond: Float, min: Dp): Dp =
    Dp(durationMs / 1000f * dpPerSecond).coerceAtLeast(min)

@Composable
private fun ClipCard(clip: Clip, trackMuted: Boolean, selected: Boolean, width: Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val thumbnail by produceState<Bitmap?>(null, clip.uri, clip.trimStartMs, clip.hasVideo) {
        value = if (clip.hasVideo) withContext(Dispatchers.IO) { MediaProbe.thumbnail(context, clip.uri, clip.trimStartMs) } else null
    }
    Box(
        Modifier
            .width(width)
            .height(ClipHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (clip.hasVideo) {
                    Brush.horizontalGradient(listOf(colors.primaryContainer, colors.primaryContainer))
                } else {
                    Brush.horizontalGradient(listOf(colors.tertiaryContainer, colors.secondaryContainer))
                },
            )
            .then(if (selected) Modifier.border(BorderStroke(3.dp, colors.primary), RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        thumbnail?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (!clip.hasVideo) {
            KindIcon(video = false, modifier = Modifier.align(Alignment.Center).size(28.dp).padding(2.dp))
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(clip.name, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatTime(clip.durationMs), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        if (!clip.isAudible || trackMuted) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = "No audio",
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
            )
        }
        if (selected) {
            Box(Modifier.fillMaxHeight().width(4.dp).background(colors.primary).align(Alignment.CenterStart))
        }
    }
}

@Composable
private fun TrackSettingsDialog(track: Track, maxOffsetMs: Long, callbacks: TimelineCallbacks, onDismiss: () -> Unit) {
    var gestureStarted by remember { mutableStateOf(false) }
    fun startGesture() {
        if (!gestureStarted) {
            callbacks.onTrackSettingsStart()
            gestureStarted = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(track.name) },
        text = {
            Column {
                Text("Volume: ${(track.volume * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = track.volume,
                    onValueChange = {
                        startGesture()
                        callbacks.onTrackVolume(track, (it * 20).toInt() / 20f)
                    },
                    valueRange = 0f..2f,
                )
                if (track.kind == TrackKind.AUDIO) {
                    Spacer(Modifier.height(8.dp))
                    Text("Starts at: ${formatTime(track.offsetMs)}", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = track.offsetMs.coerceAtMost(maxOffsetMs).toFloat(),
                        onValueChange = {
                            startGesture()
                            callbacks.onTrackOffset(track, (it.toLong() / 100) * 100)
                        },
                        valueRange = 0f..maxOffsetMs.toFloat(),
                    )
                    Text(
                        "Delay the start of this track to line it up with the video.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
