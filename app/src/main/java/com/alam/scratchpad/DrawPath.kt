package com.alam.scratchpad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
) {
    val path by lazy { pathFromPoints(points) }

    companion object {
        fun pathFromPoints(points: List<Offset>): Path {
            return Path().apply {
                for ((index, point) in points.withIndex()) {
                    if (index == 0) {
                        moveTo(point.x, point.y)
                    } else {
                        lineTo(point.x, point.y)
                    }
                }
            }
        }
    }
}
