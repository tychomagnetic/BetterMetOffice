package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.data.model.CurrentWeather
import com.example.ui.theme.BentoCanvas

@Composable
fun WeatherBackground(
    current: CurrentWeather?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BentoCanvas,
                        Color(0xFFF3EDF7)
                    )
                )
            )
    ) {
        content()
    }
}

