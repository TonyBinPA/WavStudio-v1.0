@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.wavstudio.app.export

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.wavstudio.app.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExportOutput(val uri: Uri, val displayName: String, val mimeType: String, val location: String)

/** Renders a project with Media3 Transformer, post-processes (WAV) and saves it to shared storage. */
class Exporter(private val context: Context) {

    suspend fun export(
        project: Project,
        settings: ExportSettings,
        onProgress: (progress: Float?, stage: String) -> Unit,
    ): ExportOutput {
        val format = settings.format
        val workDir = File(context.cacheDir, "export").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val composition = CompositionFactory.build(project, settings)
            val renderExt = if (format == OutputFormat.WAV) "m4a" else format.extension
            val rendered = File(workDir, "render.$renderExt")
            val renderShare = if (format == OutputFormat.WAV) 0.85f else 1f

            onProgress(0f, "Rendering")
            render(composition, rendered, settings) { p -> onProgress(p?.times(renderShare), "Rendering") }

            val finalFile = if (format == OutputFormat.WAV) {
                val wav = File(workDir, "out.wav")
                withContext(Dispatchers.IO) {
                    WavDecoder.decodeToWav(rendered, wav) { p -> onProgress(renderShare + p * (1 - renderShare), "Writing WAV") }
                }
                wav
            } else {
                rendered
            }

            onProgress(null, "Saving")
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = "WavStudio_$stamp.${format.extension}"
            val uri = withContext(Dispatchers.IO) {
                MediaStoreSaver.save(context, finalFile, name, format.mimeType, format.isAudioOnly)
            }
            return ExportOutput(uri, name, format.mimeType, MediaStoreSaver.relativePath(format.isAudioOnly))
        } finally {
            // NonCancellable so temp files are removed even when the export was cancelled.
            withContext(NonCancellable + Dispatchers.IO) { workDir.deleteRecursively() }
        }
    }

    private suspend fun render(
        composition: Composition,
        output: File,
        settings: ExportSettings,
        onProgress: (Float?) -> Unit,
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val format = settings.format
            val (audioMime, videoMime) = when (format) {
                OutputFormat.WEBM -> MimeTypes.AUDIO_OPUS to MimeTypes.VIDEO_VP9
                OutputFormat.OGG -> MimeTypes.AUDIO_OPUS to null
                OutputFormat.M4A, OutputFormat.WAV -> MimeTypes.AUDIO_AAC to null
                OutputFormat.MP4 -> MimeTypes.AUDIO_AAC to when (settings.videoCodec) {
                    VideoCodec.H264 -> MimeTypes.VIDEO_H264
                    VideoCodec.HEVC -> MimeTypes.VIDEO_H265
                    VideoCodec.AV1 -> MimeTypes.VIDEO_AV1
                }
            }
            // WAV is decoded from an intermediate AAC render, so use the highest bitrate for it.
            val audioBitrate = if (format == OutputFormat.WAV) AudioQuality.BEST.bitrate else settings.audioQuality.bitrate
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(audioBitrate).build())
                .setEnableFallback(true)
                .build()

            val handler = Handler(Looper.getMainLooper())
            lateinit var poll: Runnable
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(audioMime)
                .apply { videoMime?.let { setVideoMimeType(it) } }
                .setEncoderFactory(encoderFactory)
                .apply {
                    if (format == OutputFormat.WEBM || format == OutputFormat.OGG) {
                        setMuxerFactory(FrameworkContainerMuxer.Factory(format))
                    }
                }
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            val holder = ProgressHolder()
            poll = Runnable {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) handler.postDelayed(poll, 250)
            }
            cont.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(poll)
                    transformer.cancel()
                }
            }
            transformer.start(composition, output.absolutePath)
            handler.postDelayed(poll, 250)
        }
    }

    companion object {
        /** Turns an export failure into a short, user-facing message. */
        fun describe(error: Throwable): String = when (error) {
            is CompositionFactory.NothingToExportException -> error.message ?: "Nothing to export"
            is ExportException -> when (error.errorCode) {
                ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
                ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
                    "This device can't encode the chosen format. Try MP4 with H.264, or a lower resolution."
                ExportException.ERROR_CODE_DECODER_INIT_FAILED,
                ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                    "One of the clips uses a format this device can't decode."
                ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
                ExportException.ERROR_CODE_IO_NO_PERMISSION ->
                    "A source clip is no longer available. Re-import it and try again."
                else -> "Export failed (${error.errorCodeName})"
            }
            else -> error.message ?: "Export failed"
        }
    }
}
