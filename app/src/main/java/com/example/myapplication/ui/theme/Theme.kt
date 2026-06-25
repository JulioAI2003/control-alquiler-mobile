// ─── ui/theme/Theme.kt ───────────────────────────────────────────────────────
package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary            = Color(0xFF8A6A12),  // dorado profundo (acentos/botones)
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFF7EFD8),  // dorado muy claro
    onPrimaryContainer = Color(0xFF4A3A0C),  // texto sobre dorado claro
    secondary          = Color(0xFFC8A24B),  // champagne (acento secundario)
    onSecondary        = Color(0xFF15151A),
    background       = Color(0xFFF5F5F5),
    surface          = Color.White,
    onSurface        = Color(0xFF212121),
    onBackground     = Color(0xFF212121),
    error            = Color(0xFFD32F2F),
    onError          = Color.White,
    outline          = Color(0xFFBDBDBD)
)

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content     = content
    )
}
