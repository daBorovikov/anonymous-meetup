package com.example.anonymousmeetup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

@Composable
fun ScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = listOf(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors))
    ) {
        content()
    }
}
