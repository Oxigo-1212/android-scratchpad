package com.alam.scratchpad

import androidx.compose.ui.graphics.Color

class AppSettings {
    companion object {
        val DefaultBackgroundColor = Color.White
        val DefaultDrawingColor = Color.Black

        val AvailableDrawingColors = listOf(
            Color.Black,
            Color(0xFF1E488F), //Color.Blue,
            //Color(0xFF039C4B), //Color.Green
            Color(0xFFF44546), //Color.Red,
        )

        const val MinStrokeWidth = 1f
        const val MaxStrokeWidth = 64f
        const val DefaultStrokeWidth = 16f

        val EraserStrokeWidthMultiplier = 3f

        val SmoothingIterations = 2
    }
}
