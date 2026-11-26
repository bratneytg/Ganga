package com.bratneytg.ganga.ui

import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Colores de Ganga: amarillo de etiqueta de precio sobre tinta azul oscura. */
object Palette {
    val tag = Color(0xFFFFD60A)
    val ink = Color(0xFF101828)

    @Composable
    fun deal(): Color = if (isSystemInDarkTheme()) Color(0xFF38C793) else Color(0xFF0B7A55)

    @Composable
    fun onDeal(): Color = if (isSystemInDarkTheme()) Color(0xFF06180F) else Color.White

    @Composable
    fun dealWash(): Color = if (isSystemInDarkTheme()) Color(0xFF10281F) else Color(0xFFE3F4EC)

    @Composable
    fun warn(): Color = if (isSystemInDarkTheme()) Color(0xFFF0B34D) else Color(0xFF9A5B00)

    @Composable
    fun warnWash(): Color = if (isSystemInDarkTheme()) Color(0xFF2E2410) else Color(0xFFFDF1DA)

    @Composable
    fun chart(): Color = if (isSystemInDarkTheme()) Color(0xFF6F7F9C) else Color(0xFF7B8AA3)
}

object Fonts {
    /** Condensada como la tipografía de las etiquetas de estantería. Viene con Android. */
    val condensedBold: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
}

private val LightColors = lightColorScheme(
    primary = Palette.ink,
    onPrimary = Color.White,
    background = Color(0xFFF2F4F7),
    onBackground = Palette.ink,
    surface = Color.White,
    onSurface = Palette.ink,
    surfaceVariant = Color(0xFFE6EAF0),
    onSurfaceVariant = Color(0xFF475467),
    outline = Color(0xFFD5DAE2),
    error = Color(0xFFB42318),
)

private val DarkColors = darkColorScheme(
    primary = Palette.tag,
    onPrimary = Palette.ink,
    background = Color(0xFF0D1320),
    onBackground = Color(0xFFEEF1F6),
    surface = Color(0xFF151D2E),
    onSurface = Color(0xFFEEF1F6),
    surfaceVariant = Color(0xFF1E2840),
    onSurfaceVariant = Color(0xFF9AA6B8),
    outline = Color(0xFF2A3550),
    error = Color(0xFFFF8A7A),
)

@Composable
fun GangaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
