package com.wavstudio.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FallbackDark = darkColorScheme(primary = Color(0xFF9FA8FF), secondary = Color(0xFFFFD54F))
private val FallbackLight = lightColorScheme(primary = Color(0xFF3949AB), secondary = Color(0xFFB28704))

/** Material 3 theme that follows One UI's colour palette (dynamic colour) and dark mode. */
@Composable
fun WavStudioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    // minSdk is 29; dynamic colour exists from Android 12 (always true on Android 17 / One UI 9).
    val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) FallbackDark else FallbackLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
