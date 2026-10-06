package com.sirterrific.tubetamer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TubeTamerColors = darkColorScheme(
    primary = Color(0xFFE53935),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
)

@Composable
fun TubeTamerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TubeTamerColors, content = content)
}
