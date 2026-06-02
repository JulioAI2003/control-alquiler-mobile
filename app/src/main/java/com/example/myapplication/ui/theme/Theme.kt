// ─── ui/theme/Theme.kt ───────────────────────────────────────────────────────
package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary          = Color(0xFF1565C0),
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    secondary        = Color(0xFF37474F),
    onSecondary      = Color.White,
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
