package com.wavstudio.app.ui

import java.util.Locale

/** Formats milliseconds as m:ss.cc (or h:mm:ss.cc for long media). */
fun formatTime(ms: Long): String {
    val clamped = ms.coerceAtLeast(0)
    val totalSeconds = clamped / 1000
    val centis = (clamped % 1000) / 10
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, centis)
    } else {
        String.format(Locale.US, "%d:%02d.%02d", m, s, centis)
    }
}
