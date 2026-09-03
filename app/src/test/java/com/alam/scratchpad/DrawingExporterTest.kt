package com.alam.scratchpad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingExporterTest {
    @Test
    fun mapsDrawingCoordinatesToTheVisibleViewport() {
        val mapped = mapToViewport(
            point = Offset(20f, 40f),
            size = Size(100f, 200f),
            scale = 2f,
            offset = Offset(10f, -10f),
        )

        assertEquals(10f, mapped.x)
        assertEquals(-40f, mapped.y)
    }

    @Test
    fun fitsImportedImageInsideCanvasAndCapsDecodeSize() {
        assertEquals(Rect(0f, 75f, 300f, 225f), fitImageRect(400, 200, Size(300f, 300f)))
        assertEquals(1, imageSampleSize(2048, 1024))
        assertEquals(4, imageSampleSize(8192, 4096))
    }
}
