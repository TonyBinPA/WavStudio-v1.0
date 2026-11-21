package com.wavstudio.app.model

import java.util.UUID

/** Pure, immutable editing operations on a [Project]. Every function returns a new project. */
object ProjectOps {

    private fun Project.mapTrack(trackId: String, transform: (Track) -> Track): Project =
        copy(tracks = tracks.map { if (it.id == trackId) transform(it) else it })

    private fun Project.mapClip(clipId: String, transform: (Clip) -> Clip): Project =
        copy(tracks = tracks.map { t -> t.copy(clips = t.clips.map { if (it.id == clipId) transform(it) else it }) })

    /** Adds clips, sending video files to the main track and audio-only files to [audioTrackId] (or a new track). */
    fun addClips(project: Project, clips: List<Clip>, audioTrackId: String? = null): Project {
        val video = clips.filter { it.hasVideo }
        val audio = clips.filter { !it.hasVideo && it.hasAudio }
        var result = project
        if (video.isNotEmpty()) {
            result = result.mapTrack(result.mainTrack.id) { it.copy(clips = it.clips + video) }
        }
        if (audio.isNotEmpty()) {
            val target = result.audioTracks.firstOrNull { it.id == audioTrackId }
                ?: result.audioTracks.firstOrNull()
            result = if (target == null) {
                addAudioTrack(result, audio)
            } else {
                result.mapTrack(target.id) { it.copy(clips = it.clips + audio) }
            }
        }
        return result
    }

    fun addAudioTrack(project: Project, clips: List<Clip> = emptyList(), name: String? = null): Project {
        val trackName = name ?: "Audio ${project.audioTracks.size + 1}"
        return project.copy(tracks = project.tracks + Track(kind = TrackKind.AUDIO, name = trackName, clips = clips))
    }

    fun removeTrack(project: Project, trackId: String): Project {
        if (project.mainTrack.id == trackId) return project // The video track is permanent.
        return project.copy(tracks = project.tracks.filterNot { it.id == trackId })
    }

    fun setTrackVolume(project: Project, trackId: String, volume: Float): Project =
        project.mapTrack(trackId) { it.copy(volume = volume.coerceIn(0f, 2f)) }

    fun setTrackMuted(project: Project, trackId: String, muted: Boolean): Project =
        project.mapTrack(trackId) { it.copy(muted = muted) }

    /** Sets the trim range of a clip, clamped to the source and to [MIN_CLIP_MS]. */
    fun trimClip(project: Project, clipId: String, startMs: Long, endMs: Long): Project =
        project.mapClip(clipId) { clip ->
            val maxStart = (clip.sourceDurationMs - MIN_CLIP_MS).coerceAtLeast(0)
            val start = startMs.coerceIn(0, maxStart)
            val end = endMs.coerceIn(start + MIN_CLIP_MS, clip.sourceDurationMs.coerceAtLeast(start + MIN_CLIP_MS))
            clip.copy(trimStartMs = start, trimEndMs = end)
        }

    /**
     * Splits a clip in two at [sourcePositionMs] (a position in the source file). Returns the
     * project unchanged if either half would be shorter than [MIN_CLIP_MS].
     */
    fun splitClip(project: Project, clipId: String, sourcePositionMs: Long): Project {
        val (track, clip) = project.findClip(clipId) ?: return project
        if (sourcePositionMs - clip.trimStartMs < MIN_CLIP_MS || clip.trimEndMs - sourcePositionMs < MIN_CLIP_MS) {
            return project
        }
        val first = clip.copy(trimEndMs = sourcePositionMs)
        val second = clip.copy(id = UUID.randomUUID().toString(), trimStartMs = sourcePositionMs)
        return project.mapTrack(track.id) { t ->
            t.copy(clips = t.clips.flatMap { if (it.id == clipId) listOf(first, second) else listOf(it) })
        }
    }

    fun duplicateClip(project: Project, clipId: String): Project {
        val (track, clip) = project.findClip(clipId) ?: return project
        val copy = clip.copy(id = UUID.randomUUID().toString())
        return project.mapTrack(track.id) { t ->
            t.copy(clips = t.clips.flatMap { if (it.id == clipId) listOf(it, copy) else listOf(it) })
        }
    }

    fun removeClip(project: Project, clipId: String): Project =
        project.copy(tracks = project.tracks.map { t -> t.copy(clips = t.clips.filterNot { it.id == clipId }) })

    /** Moves a clip one place earlier (delta = -1) or later (delta = +1) within its track. */
    fun moveClip(project: Project, clipId: String, delta: Int): Project {
        val (track, _) = project.findClip(clipId) ?: return project
        val index = track.clips.indexOfFirst { it.id == clipId }
        val target = (index + delta).coerceIn(0, track.clips.lastIndex)
        if (target == index) return project
        val list = track.clips.toMutableList()
        list.add(target, list.removeAt(index))
        return project.mapTrack(track.id) { it.copy(clips = list) }
    }

    fun setClipAudioMuted(project: Project, clipId: String, muted: Boolean): Project =
        project.mapClip(clipId) { it.copy(audioMuted = muted) }

    /**
     * Separates the audio from a video clip: the video clip is muted and an audio-only copy of
     * the same range is placed on a new audio track, padded so it stays in sync with the video.
     */
    fun detachAudio(project: Project, clipId: String): Project {
        val (track, clip) = project.findClip(clipId) ?: return project
        if (track.kind != TrackKind.VIDEO || !clip.isAudible) return project
        val audioClip = clip.copy(
            id = UUID.randomUUID().toString(),
            hasVideo = false,
            audioMuted = false,
            name = "${clip.name} (audio)",
        )
        val muted = setClipAudioMuted(project, clipId, true)
        val offsetMs = clipStartOnTimeline(muted, clipId)
        val name = "Detached audio ${muted.audioTracks.size + 1}"
        return muted.copy(
            tracks = muted.tracks + Track(kind = TrackKind.AUDIO, name = name, clips = listOf(audioClip)),
        ).let { p -> if (offsetMs > 0) setTrackOffset(p, p.tracks.last().id, offsetMs) else p }
    }

    /** Position (ms) on the timeline where a clip begins. */
    fun clipStartOnTimeline(project: Project, clipId: String): Long {
        val (track, _) = project.findClip(clipId) ?: return 0
        var position = track.offsetMs
        for (c in track.clips) {
            if (c.id == clipId) return position
            position += c.durationMs
        }
        return position
    }

    /** Sets the silent lead-in before the first clip of an audio track. */
    fun setTrackOffset(project: Project, trackId: String, offsetMs: Long): Project =
        project.mapTrack(trackId) { if (it.kind == TrackKind.AUDIO) it.copy(offsetMs = offsetMs.coerceAtLeast(0)) else it }
}
