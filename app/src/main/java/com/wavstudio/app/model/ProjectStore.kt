package com.wavstudio.app.model

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Autosaves the current project as JSON in the app's private storage. */
class ProjectStore(context: Context) {
    private val file = File(context.filesDir, "project.json")

    fun load(): Project? = try {
        if (file.exists()) fromJson(JSONObject(file.readText())) else null
    } catch (e: Exception) {
        Log.w(TAG, "Could not restore project", e)
        null
    }

    fun save(project: Project) {
        try {
            val tmp = File(file.parentFile, "project.json.tmp")
            tmp.writeText(toJson(project).toString())
            tmp.renameTo(file)
        } catch (e: Exception) {
            Log.w(TAG, "Could not save project", e)
        }
    }

    companion object {
        private const val TAG = "ProjectStore"

        fun toJson(project: Project): JSONObject = JSONObject().put(
            "tracks",
            JSONArray().apply {
                project.tracks.forEach { t ->
                    put(
                        JSONObject()
                            .put("id", t.id).put("kind", t.kind.name).put("name", t.name)
                            .put("volume", t.volume.toDouble()).put("muted", t.muted).put("offsetMs", t.offsetMs)
                            .put("clips", JSONArray().apply { t.clips.forEach { put(clipToJson(it)) } }),
                    )
                }
            },
        )

        private fun clipToJson(c: Clip) = JSONObject()
            .put("id", c.id).put("uri", c.uri).put("name", c.name).put("mimeType", c.mimeType ?: "")
            .put("sourceDurationMs", c.sourceDurationMs).put("hasVideo", c.hasVideo).put("hasAudio", c.hasAudio)
            .put("width", c.width).put("height", c.height).put("rotation", c.rotationDegrees)
            .put("trimStartMs", c.trimStartMs).put("trimEndMs", c.trimEndMs).put("audioMuted", c.audioMuted)

        fun fromJson(json: JSONObject): Project {
            val tracksJson = json.getJSONArray("tracks")
            val tracks = (0 until tracksJson.length()).map { i ->
                val t = tracksJson.getJSONObject(i)
                val clipsJson = t.getJSONArray("clips")
                Track(
                    id = t.getString("id"),
                    kind = TrackKind.valueOf(t.getString("kind")),
                    name = t.getString("name"),
                    volume = t.optDouble("volume", 1.0).toFloat(),
                    muted = t.optBoolean("muted"),
                    offsetMs = t.optLong("offsetMs"),
                    clips = (0 until clipsJson.length()).map { j ->
                        val c = clipsJson.getJSONObject(j)
                        Clip(
                            id = c.getString("id"),
                            uri = c.getString("uri"),
                            name = c.getString("name"),
                            mimeType = c.optString("mimeType").ifEmpty { null },
                            sourceDurationMs = c.getLong("sourceDurationMs"),
                            hasVideo = c.getBoolean("hasVideo"),
                            hasAudio = c.getBoolean("hasAudio"),
                            width = c.optInt("width"),
                            height = c.optInt("height"),
                            rotationDegrees = c.optInt("rotation"),
                            trimStartMs = c.getLong("trimStartMs"),
                            trimEndMs = c.getLong("trimEndMs"),
                            audioMuted = c.optBoolean("audioMuted"),
                        )
                    },
                )
            }
            return Project(tracks)
        }
    }
}
