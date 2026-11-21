@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class],
)

package com.wavstudio.app.ui

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.PlayerView
import com.wavstudio.app.R
import com.wavstudio.app.export.CompositionFactory
import com.wavstudio.app.export.ExportSettings
import com.wavstudio.app.export.OutputFormat
import com.wavstudio.app.model.Clip
import com.wavstudio.app.model.Project
import kotlinx.coroutines.delay

enum class PreviewMode { CLIP, PROJECT }

/**
 * Video/audio preview. In [PreviewMode.CLIP] it plays the selected clip's source file and keeps
 * playback inside the trim range; in [PreviewMode.PROJECT] it plays the whole edit (all tracks
 * mixed) with Media3's CompositionPlayer.
 *
 * @param playhead receives the current position: source time in clip mode, timeline time in project mode.
 */
@Composable
fun PreviewPane(
    project: Project,
    clip: Clip?,
    mode: PreviewMode,
    onModeChange: (PreviewMode) -> Unit,
    playhead: MutableLongState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { clipPlayer.release() } }

    var projectPlayer by remember { mutableStateOf<CompositionPlayer?>(null) }
    var projectError by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // ---- Clip mode ----
    LaunchedEffect(clip?.uri, mode) {
        if (mode != PreviewMode.CLIP) {
            clipPlayer.pause()
            return@LaunchedEffect
        }
        if (clip == null) {
            clipPlayer.clearMediaItems()
            return@LaunchedEffect
        }
        clipPlayer.setMediaItem(MediaItem.fromUri(clip.uri.toUri()))
        clipPlayer.prepare()
        clipPlayer.seekTo(clip.trimStartMs)
    }
    // Seek to whichever trim handle moved so the user sees the frame they're cutting at.
    var lastTrim by remember { mutableStateOf<Triple<String, Long, Long>?>(null) }
    LaunchedEffect(clip?.id, clip?.trimStartMs, clip?.trimEndMs) {
        val c = clip ?: return@LaunchedEffect
        val previous = lastTrim
        lastTrim = Triple(c.id, c.trimStartMs, c.trimEndMs)
        if (mode != PreviewMode.CLIP || previous == null) return@LaunchedEffect
        when {
            previous.first != c.id -> clipPlayer.seekTo(c.trimStartMs) // another clip of the same file
            previous.second != c.trimStartMs -> clipPlayer.seekTo(c.trimStartMs)
            previous.third != c.trimEndMs -> clipPlayer.seekTo((c.trimEndMs - 1).coerceAtLeast(c.trimStartMs))
        }
    }

    // ---- Project mode ----
    DisposableEffect(mode) {
        if (mode == PreviewMode.PROJECT) {
            val player = CompositionPlayer.Builder(context).build()
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    projectError = "Preview error: ${error.errorCodeName}"
                }
            })
            projectPlayer = player
        }
        onDispose {
            projectPlayer?.release()
            projectPlayer = null
        }
    }
    LaunchedEffect(projectPlayer, project) {
        val player = projectPlayer ?: return@LaunchedEffect
        projectError = null
        if (project.isEmpty) return@LaunchedEffect
        try {
            val format = if (project.hasVideo) OutputFormat.MP4 else OutputFormat.M4A
            player.setComposition(CompositionFactory.build(project, ExportSettings(format = format), forPreview = true))
            player.prepare()
        } catch (e: Exception) {
            projectError = e.message ?: "Can't preview this project"
        }
    }

    val activePlayer: Player? = if (mode == PreviewMode.CLIP) clipPlayer else projectPlayer

    // Poll position, stop at the trim end in clip mode.
    val currentClip by rememberUpdatedState(clip)
    LaunchedEffect(activePlayer, mode) {
        val player = activePlayer ?: return@LaunchedEffect
        while (true) {
            val c = currentClip
            val pos = player.currentPosition
            if (mode == PreviewMode.CLIP && c != null && player.isPlaying && pos >= c.trimEndMs) {
                player.pause()
                player.seekTo(c.trimEndMs)
            }
            playhead.longValue = player.currentPosition
            isPlaying = player.isPlaying
            durationMs = if (mode == PreviewMode.CLIP) c?.sourceDurationMs ?: 0 else player.duration.coerceAtLeast(0)
            delay(50)
        }
    }

    Column(modifier) {
        val showsVideo = if (mode == PreviewMode.CLIP) clip?.hasVideo == true else project.hasVideo
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.player_view, FrameLayout(ctx), false) as PlayerView).apply {
                        useController = false
                    }
                },
                update = { view -> if (view.player !== activePlayer) view.player = activePlayer },
                modifier = Modifier.fillMaxSize(),
            )
            val placeholder = when {
                mode == PreviewMode.PROJECT && projectError != null -> projectError
                mode == PreviewMode.PROJECT && project.isEmpty -> "Add media to preview your project"
                mode == PreviewMode.CLIP && clip == null -> "Select a clip on the timeline"
                !showsVideo -> null
                else -> ""
            }
            if (placeholder == null) {
                Icon(Icons.Default.GraphicEq, contentDescription = "Audio only", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(72.dp))
            } else if (placeholder.isNotEmpty()) {
                Text(placeholder, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledIconButton(
                onClick = {
                    val player = activePlayer ?: return@FilledIconButton
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        if (mode == PreviewMode.CLIP && clip != null) {
                            val pos = player.currentPosition
                            if (pos < clip.trimStartMs || pos >= clip.trimEndMs - 50) player.seekTo(clip.trimStartMs)
                        } else if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0)
                        }
                        player.play()
                    }
                },
                enabled = activePlayer != null && (mode == PreviewMode.PROJECT && !project.isEmpty || mode == PreviewMode.CLIP && clip != null),
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play")
            }
            Text(
                "${formatTime(playhead.longValue)} / ${formatTime(durationMs)}",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow(Modifier.width(200.dp)) {
                SegmentedButton(
                    selected = mode == PreviewMode.CLIP,
                    onClick = { onModeChange(PreviewMode.CLIP) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {},
                ) { Text("Clip") }
                SegmentedButton(
                    selected = mode == PreviewMode.PROJECT,
                    onClick = { onModeChange(PreviewMode.PROJECT) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {},
                ) { Text("Project") }
            }
        }
        if (durationMs > 0) {
            Slider(
                value = playhead.longValue.coerceIn(0, durationMs).toFloat(),
                onValueChange = { v ->
                    activePlayer?.seekTo(v.toLong())
                    playhead.longValue = v.toLong()
                },
                valueRange = 0f..durationMs.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Small leading icon used for clip/track kinds. */
@Composable
fun KindIcon(video: Boolean, modifier: Modifier = Modifier) {
    Icon(if (video) Icons.Default.Movie else Icons.Default.GraphicEq, contentDescription = null, modifier = modifier)
}
