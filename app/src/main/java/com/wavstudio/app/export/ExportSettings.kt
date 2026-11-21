package com.wavstudio.app.export

enum class OutputFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val isAudioOnly: Boolean,
    val description: String,
) {
    MP4("MP4", "mp4", "video/mp4", false, "Video · most compatible"),
    WEBM("WebM", "webm", "video/webm", false, "Video · VP9 + Opus"),
    M4A("M4A", "m4a", "audio/mp4", true, "Audio · AAC"),
    WAV("WAV", "wav", "audio/wav", true, "Audio · uncompressed PCM"),
    OGG("OGG", "ogg", "audio/ogg", true, "Audio · Opus"),
}

enum class VideoCodec(val label: String) {
    H264("H.264 (AVC)"),
    HEVC("H.265 (HEVC)"),
    AV1("AV1"),
}

enum class Resolution(val label: String, val shortSide: Int?) {
    ORIGINAL("Original", null),
    UHD("2160p", 2160),
    FHD("1080p", 1080),
    HD("720p", 720),
    SD("480p", 480),
}

enum class AudioQuality(val label: String, val bitrate: Int) {
    LOW("128 kbps", 128_000),
    MEDIUM("192 kbps", 192_000),
    HIGH("256 kbps", 256_000),
    BEST("320 kbps", 320_000),
}

data class ExportSettings(
    val format: OutputFormat = OutputFormat.MP4,
    /** Only used for MP4 — WebM always uses VP9. */
    val videoCodec: VideoCodec = VideoCodec.H264,
    val resolution: Resolution = Resolution.ORIGINAL,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
)
