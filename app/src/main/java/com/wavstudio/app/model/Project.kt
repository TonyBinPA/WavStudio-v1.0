package com.wavstudio.app.model

import java.util.UUID

/** Minimum length (ms) a clip may be trimmed or split down to. */
const val MIN_CLIP_MS = 100L

/**
 * One piece of source media placed on a track. Times are in milliseconds of the *source* file:
 * the clip plays the range [trimStartMs, trimEndMs).
 */
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val mimeType: String?,
    val sourceDurationMs: Long,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = sourceDurationMs,
    /** When true the clip's own audio is dropped (e.g. after "Detach audio"). */
    val audioMuted: Boolean = false,
) {
    val durationMs: Long get() = trimEndMs - trimStartMs

    /** True if this clip contributes sound to an export. */
    val isAudible: Boolean get() = hasAudio && !audioMuted

    /** Width/height after applying the rotation stored in the file's metadata. */
    val displayWidth: Int get() = if (rotationDegrees % 180 != 0) height else width
    val displayHeight: Int get() = if (rotationDegrees % 180 != 0) width else height
}

enum class TrackKind { VIDEO, AUDIO }

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val kind: TrackKind,
    val name: String,
    val clips: List<Clip> = emptyList(),
    /** Linear gain applied to the whole track (0.0 – 2.0). */
    val volume: Float = 1f,
    val muted: Boolean = false,
    /** Silence before the first clip (audio tracks only), used to position music or detached audio. */
    val offsetMs: Long = 0,
) {
    val durationMs: Long get() = if (clips.isEmpty()) 0 else offsetMs + clips.sumOf { it.durationMs }
}

/**
 * The editing project. [tracks] always starts with the main video track; any further tracks are
 * audio tracks that are mixed on top of it (music, voice-over, detached audio, ...).
 */
data class Project(
    val tracks: List<Track> = listOf(Track(kind = TrackKind.VIDEO, name = "Video")),
) {
    init {
        require(tracks.isNotEmpty() && tracks[0].kind == TrackKind.VIDEO) { "First track must be the video track" }
        require(tracks.drop(1).all { it.kind == TrackKind.AUDIO }) { "Only one video track is supported" }
    }

    val mainTrack: Track get() = tracks[0]
    val audioTracks: List<Track> get() = tracks.drop(1)
    val isEmpty: Boolean get() = tracks.all { it.clips.isEmpty() }
    val hasVideo: Boolean get() = mainTrack.clips.isNotEmpty()
    val durationMs: Long get() = tracks.maxOf { it.durationMs }

    fun findClip(clipId: String): Pair<Track, Clip>? {
        for (track in tracks) {
            track.clips.firstOrNull { it.id == clipId }?.let { return track to it }
        }
        return null
    }

    companion object {
        /** A project containing just [clip] — used to export a single clip (e.g. "Extract audio"). */
        fun singleClip(clip: Clip): Project = if (clip.hasVideo) {
            Project(listOf(Track(kind = TrackKind.VIDEO, name = "Video", clips = listOf(clip))))
        } else {
            Project(
                listOf(
                    Track(kind = TrackKind.VIDEO, name = "Video"),
                    Track(kind = TrackKind.AUDIO, name = "Audio", clips = listOf(clip)),
                ),
            )
        }
    }
}
