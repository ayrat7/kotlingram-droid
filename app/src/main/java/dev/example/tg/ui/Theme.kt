package dev.example.tg.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.example.tg.data.ThemeMode
import dev.example.tg.data.TgRepo
import dev.example.tg.media.AudioPlayer
import dev.example.tg.media.VoiceRecorder

val LocalRepo = staticCompositionLocalOf<TgRepo> { error("TgRepo не предоставлен") }
val LocalPlayer = staticCompositionLocalOf<AudioPlayer> { error("AudioPlayer не предоставлен") }
val LocalRecorder = staticCompositionLocalOf<VoiceRecorder> { error("VoiceRecorder не предоставлен") }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F6FB2), onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E4FF), onPrimaryContainer = Color(0xFF001C37),
    secondary = Color(0xFF535F70), secondaryContainer = Color(0xFFD7E3F8), onSecondaryContainer = Color(0xFF101C2B),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    surfaceContainerLow = Color(0xFFF1F3F9), surfaceContainer = Color(0xFFEBEDF3),
    surfaceContainerHigh = Color(0xFFE6E8EE), surfaceContainerHighest = Color(0xFFE0E2E8),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA2C9FF), onPrimary = Color(0xFF00315A),
    primaryContainer = Color(0xFF004A82), onPrimaryContainer = Color(0xFFD2E4FF),
    secondary = Color(0xFFBBC7DB), secondaryContainer = Color(0xFF3B4858), onSecondaryContainer = Color(0xFFD7E3F8),
    background = Color(0xFF111418), onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111418), onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    surfaceContainerLow = Color(0xFF191C20), surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F), surfaceContainerHighest = Color(0xFF32353A),
)

@Composable
fun TgTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Тёмная ли тема при данном режиме (нужно для цвета иконок системных баров). */
@Composable
fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
