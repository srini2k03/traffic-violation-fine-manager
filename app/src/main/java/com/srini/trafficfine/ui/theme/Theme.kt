package com.srini.trafficfine.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandBlue = Color(0xFF0B3D91)
private val SignalAmber = Color(0xFFFFB300)
private val AlertRed = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF575E71),
    tertiary = SignalAmber,
    error = AlertRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6F),
    primaryContainer = Color(0xFF00429C),
    onPrimaryContainer = Color(0xFFD9E2FF),
    tertiary = SignalAmber,
    error = Color(0xFFFFB4AB),
)

/** Status colours that stay consistent regardless of dynamic theming. */
object StatusColors {
    val Paid = Color(0xFF2E7D32)
    val Pending = Color(0xFFEF6C00)
    val Cancelled = Color(0xFF757575)
}

@Composable
fun TrafficFineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
