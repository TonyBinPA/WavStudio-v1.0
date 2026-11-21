@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.wavstudio.app.export

import android.media.MediaCodec
import android.media.MediaMuxer
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerException
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/**
 * A Media3 [Muxer] backed by the platform [MediaMuxer], used for the containers Media3's own
 * muxers don't write: WebM (VP9/VP8 + Opus/Vorbis) and Ogg (Opus).
 */
class FrameworkContainerMuxer private constructor(
    path: String,
    outputFormat: Int,
) : Muxer {

    class Factory(private val container: OutputFormat) : Muxer.Factory {
        override fun create(path: String): Muxer = try {
            FrameworkContainerMuxer(path, outputFormatFor(container))
        } catch (e: Exception) {
            throw MuxerException("Could not create ${container.label} muxer", e)
        }

        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> = when (container) {
            OutputFormat.WEBM -> when (trackType) {
                C.TRACK_TYPE_VIDEO -> ImmutableList.of(MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_VP8)
                C.TRACK_TYPE_AUDIO -> ImmutableList.of(MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS)
                else -> ImmutableList.of()
            }
            OutputFormat.OGG -> if (trackType == C.TRACK_TYPE_AUDIO) ImmutableList.of(MimeTypes.AUDIO_OPUS) else ImmutableList.of()
            else -> ImmutableList.of()
        }

        private fun outputFormatFor(format: OutputFormat): Int = when (format) {
            OutputFormat.WEBM -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            OutputFormat.OGG -> MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
            else -> throw IllegalArgumentException("Unsupported container $format")
        }
    }

    private val muxer = MediaMuxer(path, outputFormat)
    private var started = false
    private val codecBufferInfo = MediaCodec.BufferInfo()

    override fun addTrack(format: Format): Int {
        if (started) throw MuxerException("Cannot add tracks after writing started", IllegalStateException())
        return try {
            muxer.addTrack(MediaFormatUtil.createMediaFormatFromFormat(format))
        } catch (e: Exception) {
            throw MuxerException("Failed to add ${format.sampleMimeType} track", e)
        }
    }

    override fun writeSampleData(trackId: Int, data: ByteBuffer, bufferInfo: BufferInfo) {
        try {
            if (!started) {
                // Media3 registers every track before writing the first sample.
                muxer.start()
                started = true
            }
            codecBufferInfo.set(data.position(), bufferInfo.size, bufferInfo.presentationTimeUs, toCodecFlags(bufferInfo.flags))
            muxer.writeSampleData(trackId, data, codecBufferInfo)
        } catch (e: Exception) {
            throw MuxerException("Failed to write sample", e)
        }
    }

    override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
        // Container metadata isn't supported by MediaMuxer for WebM/Ogg.
    }

    override fun close() {
        try {
            if (started) muxer.stop()
        } catch (e: Exception) {
            throw MuxerException("Failed to finalize file", e)
        } finally {
            muxer.release()
        }
    }

    private fun toCodecFlags(flags: Int): Int {
        var result = 0
        if (flags and C.BUFFER_FLAG_KEY_FRAME != 0) result = result or MediaCodec.BUFFER_FLAG_KEY_FRAME
        if (flags and C.BUFFER_FLAG_END_OF_STREAM != 0) result = result or MediaCodec.BUFFER_FLAG_END_OF_STREAM
        return result
    }
}
