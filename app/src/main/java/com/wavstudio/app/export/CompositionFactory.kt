@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.wavstudio.app.export

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.wavstudio.app.model.Clip
import com.wavstudio.app.model.Project
import com.wavstudio.app.model.Track

/**
 * Turns a [Project] into a Media3 [Composition]:
 *  - the main track becomes the first sequence (clips concatenated, each trimmed with a clipping
 *    configuration and scaled to a common frame size);
 *  - each audio track becomes an extra audio-only sequence that is mixed in.
 */
object CompositionFactory {

    class NothingToExportException(message: String) : Exception(message)

    fun build(project: Project, settings: ExportSettings, forPreview: Boolean = false): Composition {
        val audioOnly = settings.format.isAudioOnly
        val main = project.mainTrack
        val audioTracks = project.audioTracks.filter { it.clips.isNotEmpty() && !it.muted && it.volume > 0f }
        val mainAudible = !main.muted && main.volume > 0f && main.clips.any { it.isAudible }
        val sequences = mutableListOf<EditedMediaItemSequence>()

        if (!audioOnly) {
            if (main.clips.isEmpty()) throw NothingToExportException("Add a video clip to export a video")
            val first = main.clips.first()
            val (width, height) = OutputGeometry.outputSize(first.displayWidth, first.displayHeight, settings.resolution)
            val presentation = Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT)
            val trackTypes = if (mainAudible) setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO) else setOf(C.TRACK_TYPE_VIDEO)
            val builder = EditedMediaItemSequence.Builder(trackTypes)
            // Declaring TRACK_TYPE_AUDIO makes Transformer fill silence for clips without (or with muted) audio.
            main.clips.forEach { clip ->
                val removeAudio = !mainAudible || !clip.isAudible
                builder.addItem(
                    editedItem(
                        clip,
                        removeAudio = removeAudio,
                        removeVideo = false,
                        audioProcessors = if (removeAudio) emptyList() else volumeProcessors(main),
                        videoEffects = listOf(presentation),
                    ),
                )
            }
            sequences += builder.build()
        } else if (mainAudible) {
            // Audio-only export of the main track: keep each clip's sound, replace silent clips with gaps.
            val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            main.clips.forEach { clip ->
                if (clip.isAudible) {
                    builder.addItem(editedItem(clip, removeAudio = false, removeVideo = true, audioProcessors = volumeProcessors(main)))
                } else {
                    builder.addGap(clip.durationMs * 1000)
                }
            }
            sequences += builder.build()
        }

        audioTracks.forEach { track ->
            val audible = track.clips.filter { it.isAudible }
            if (audible.isEmpty()) return@forEach
            val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            if (track.offsetMs > 0) builder.addGap(track.offsetMs * 1000)
            track.clips.forEach { clip ->
                if (clip.isAudible) {
                    builder.addItem(editedItem(clip, removeAudio = false, removeVideo = true, audioProcessors = volumeProcessors(track)))
                } else {
                    builder.addGap(clip.durationMs * 1000)
                }
            }
            sequences += builder.build()
        }

        if (sequences.isEmpty()) {
            throw NothingToExportException(
                if (audioOnly) "There is no audio to export — add audio or unmute a track" else "Nothing to export",
            )
        }

        val compositionAudioProcessors = mutableListOf<AudioProcessor>()
        if (!forPreview && (settings.format == OutputFormat.OGG || settings.format == OutputFormat.WEBM)) {
            // Opus encoders only accept 48 kHz (and a few lower rates); resample the final mix.
            compositionAudioProcessors += SonicAudioProcessor().apply { setOutputSampleRateHz(48_000) }
        }
        return Composition.Builder(sequences)
            .setEffects(Effects(compositionAudioProcessors, emptyList()))
            .build()
    }

    private fun editedItem(
        clip: Clip,
        removeAudio: Boolean,
        removeVideo: Boolean,
        audioProcessors: List<AudioProcessor> = emptyList(),
        videoEffects: List<Effect> = emptyList(),
    ): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(clip.uri.toUri())
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs)
                    .setEndPositionMs(clip.trimEndMs)
                    .build(),
            )
            .build()
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(removeAudio)
            .setRemoveVideo(removeVideo)
            .setEffects(Effects(audioProcessors, if (removeVideo) emptyList() else videoEffects))
            .build()
    }

    /** A gain stage for the track volume (supports mono through 7.1 sources). */
    private fun volumeProcessors(track: Track): List<AudioProcessor> {
        if (track.volume == 1f) return emptyList()
        val processor = ChannelMixingAudioProcessor()
        for (channels in 1..8) {
            processor.putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(channels, channels).scaleBy(track.volume))
        }
        return listOf(processor)
    }
}
