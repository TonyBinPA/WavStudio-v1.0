package com.wavstudio.app.media

import androidx.core.net.toUri
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import com.wavstudio.app.model.Clip

/** Reads duration, track and size information from media the user imports. */
object MediaProbe {

    class UnsupportedMediaException(message: String) : Exception(message)

    fun probe(context: Context, uri: Uri): Clip {
        val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "Clip"
        val mime = context.contentResolver.getType(uri)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)
            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val hasVideo = meta(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val hasAudio = meta(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            if (durationMs <= 0 || (!hasVideo && !hasAudio)) {
                throw UnsupportedMediaException("\"$name\" has no playable audio or video")
            }
            return Clip(
                uri = uri.toString(),
                name = name,
                mimeType = mime,
                sourceDurationMs = durationMs,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                rotationDegrees = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
            )
        } catch (e: UnsupportedMediaException) {
            throw e
        } catch (e: Exception) {
            throw UnsupportedMediaException("Couldn't read \"$name\": ${e.message ?: e.javaClass.simpleName}")
        } finally {
            retriever.release()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private val thumbnails = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** A small frame from a video clip at [timeMs], cached in memory. Call off the main thread. */
    fun thumbnail(context: Context, uri: String, timeMs: Long, heightPx: Int = 160): Bitmap? {
        val key = "$uri@$timeMs@$heightPx"
        thumbnails.get(key)?.let { return it }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri.toUri())
            val frame = retriever.getScaledFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                heightPx * 16 / 9,
                heightPx,
            )
            frame?.also { thumbnails.put(key, it) }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
