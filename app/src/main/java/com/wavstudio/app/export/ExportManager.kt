package com.wavstudio.app.export

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.wavstudio.app.model.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExportState {
    data object Idle : ExportState
    /** [progress] is 0..1, or null when indeterminate. */
    data class Running(val progress: Float?, val stage: String, val settings: ExportSettings) : ExportState
    data class Success(val output: ExportOutput) : ExportState
    data class Failed(val message: String) : ExportState
    data object Cancelled : ExportState
}

/**
 * Process-wide owner of the running export so it survives configuration changes and the user
 * leaving the editor. [ExportService] keeps the process in the foreground while it runs.
 */
object ExportManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()
    private var job: Job? = null

    val isRunning: Boolean get() = _state.value is ExportState.Running

    fun start(context: Context, project: Project, settings: ExportSettings) {
        if (isRunning) return
        val app = context.applicationContext
        _state.value = ExportState.Running(0f, "Preparing", settings)
        job = scope.launch {
            try {
                val output = Exporter(app).export(project, settings) { progress, stage ->
                    _state.value = ExportState.Running(progress, stage, settings)
                }
                _state.value = ExportState.Success(output)
            } catch (e: CancellationException) {
                _state.value = ExportState.Cancelled
            } catch (e: Throwable) {
                Log.e("ExportManager", "Export failed", e)
                _state.value = ExportState.Failed(Exporter.describe(e))
            }
        }
        try {
            ContextCompat.startForegroundService(app, Intent(app, ExportService::class.java))
        } catch (e: Exception) {
            // Export still runs while the app is visible; it just isn't protected in the background.
            Log.w("ExportManager", "Could not start foreground service", e)
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Clears a finished/failed state once the UI has shown it. */
    fun acknowledge() {
        if (!isRunning) _state.value = ExportState.Idle
    }
}
