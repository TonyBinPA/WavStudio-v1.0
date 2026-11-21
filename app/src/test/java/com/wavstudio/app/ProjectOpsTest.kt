package com.wavstudio.app

import com.wavstudio.app.model.Clip
import com.wavstudio.app.model.MIN_CLIP_MS
import com.wavstudio.app.model.Project
import com.wavstudio.app.model.ProjectOps
import com.wavstudio.app.model.TrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectOpsTest {

    private fun video(name: String, ms: Long = 10_000, audio: Boolean = true) =
        Clip(uri = "content://v/$name", name = name, mimeType = "video/mp4", sourceDurationMs = ms, hasVideo = true, hasAudio = audio, width = 1920, height = 1080)

    private fun audio(name: String, ms: Long = 30_000) =
        Clip(uri = "content://a/$name", name = name, mimeType = "audio/mpeg", sourceDurationMs = ms, hasVideo = false, hasAudio = true)

    @Test
    fun addClips_routesVideoToMainTrackAndAudioToNewAudioTrack() {
        val p = ProjectOps.addClips(Project(), listOf(video("a"), audio("song"), video("b")))
        assertEquals(listOf("a", "b"), p.mainTrack.clips.map { it.name })
        assertEquals(1, p.audioTracks.size)
        assertEquals(listOf("song"), p.audioTracks[0].clips.map { it.name })
        assertEquals(TrackKind.AUDIO, p.audioTracks[0].kind)
    }

    @Test
    fun addClips_appendsToRequestedAudioTrack() {
        var p = ProjectOps.addAudioTrack(ProjectOps.addAudioTrack(Project()))
        val second = p.audioTracks[1].id
        p = ProjectOps.addClips(p, listOf(audio("vo")), second)
        assertTrue(p.audioTracks[0].clips.isEmpty())
        assertEquals("vo", p.audioTracks[1].clips.single().name)
    }

    @Test
    fun trim_clampsToSourceAndMinimumLength() {
        val clip = video("a", 5_000)
        val p = ProjectOps.addClips(Project(), listOf(clip))
        val trimmed = ProjectOps.trimClip(p, clip.id, -500, 99_000).mainTrack.clips.single()
        assertEquals(0, trimmed.trimStartMs)
        assertEquals(5_000, trimmed.trimEndMs)

        val tiny = ProjectOps.trimClip(p, clip.id, 2_000, 2_010).mainTrack.clips.single()
        assertEquals(2_000, tiny.trimStartMs)
        assertEquals(2_000 + MIN_CLIP_MS, tiny.trimEndMs)
        assertEquals(MIN_CLIP_MS, tiny.durationMs)
    }

    @Test
    fun split_producesTwoAdjacentHalves() {
        val clip = video("a", 10_000)
        var p = ProjectOps.addClips(Project(), listOf(clip))
        p = ProjectOps.trimClip(p, clip.id, 1_000, 9_000)
        p = ProjectOps.splitClip(p, clip.id, 4_000)
        val (first, second) = p.mainTrack.clips
        assertEquals(1_000, first.trimStartMs)
        assertEquals(4_000, first.trimEndMs)
        assertEquals(4_000, second.trimStartMs)
        assertEquals(9_000, second.trimEndMs)
        assertNotEquals(first.id, second.id)
        assertEquals(8_000, p.mainTrack.durationMs)
    }

    @Test
    fun split_outsideClipOrTooCloseToEdge_isNoOp() {
        val clip = video("a", 10_000)
        val p = ProjectOps.addClips(Project(), listOf(clip))
        assertSame(p, ProjectOps.splitClip(p, clip.id, 50))
        assertSame(p, ProjectOps.splitClip(p, clip.id, 12_000))
    }

    @Test
    fun move_reordersWithinTrack() {
        val a = video("a"); val b = video("b"); val c = video("c")
        var p = ProjectOps.addClips(Project(), listOf(a, b, c))
        p = ProjectOps.moveClip(p, c.id, -1)
        assertEquals(listOf("a", "c", "b"), p.mainTrack.clips.map { it.name })
        p = ProjectOps.moveClip(p, a.id, -1) // already first
        assertEquals(listOf("a", "c", "b"), p.mainTrack.clips.map { it.name })
    }

    @Test
    fun detachAudio_mutesVideoAndCreatesSyncedAudioTrack() {
        val a = video("a", 4_000); val b = video("b", 6_000)
        var p = ProjectOps.addClips(Project(), listOf(a, b))
        p = ProjectOps.trimClip(p, b.id, 1_000, 5_000)
        p = ProjectOps.detachAudio(p, b.id)

        val mutedVideo = p.mainTrack.clips[1]
        assertTrue(mutedVideo.audioMuted)
        assertFalse(mutedVideo.isAudible)

        val track = p.audioTracks.single()
        val detached = track.clips.single()
        assertFalse(detached.hasVideo)
        assertTrue(detached.isAudible)
        assertEquals(1_000, detached.trimStartMs)
        assertEquals(5_000, detached.trimEndMs)
        assertEquals(4_000, track.offsetMs) // starts where clip b starts on the timeline
    }

    @Test
    fun detachAudio_onSilentClip_isNoOp() {
        val a = video("a", audio = false)
        val p = ProjectOps.addClips(Project(), listOf(a))
        assertSame(p, ProjectOps.detachAudio(p, a.id))
    }

    @Test
    fun removeTrack_neverRemovesVideoTrack() {
        val p = ProjectOps.addAudioTrack(Project())
        assertSame(p, ProjectOps.removeTrack(p, p.mainTrack.id))
        assertEquals(1, ProjectOps.removeTrack(p, p.audioTracks[0].id).tracks.size)
    }

    @Test
    fun projectDuration_isLongestTrackIncludingOffset() {
        var p = ProjectOps.addClips(Project(), listOf(video("a", 10_000), audio("m", 8_000)))
        p = ProjectOps.setTrackOffset(p, p.audioTracks[0].id, 5_000)
        assertEquals(13_000, p.durationMs)
    }

    @Test
    fun singleClipProject_placesAudioOnAudioTrack() {
        assertTrue(Project.singleClip(video("v")).hasVideo)
        val p = Project.singleClip(audio("a"))
        assertFalse(p.hasVideo)
        assertEquals(1, p.audioTracks.single().clips.size)
    }
}
