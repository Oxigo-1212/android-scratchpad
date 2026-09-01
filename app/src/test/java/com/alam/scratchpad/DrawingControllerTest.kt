package com.alam.scratchpad

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DrawingControllerTest {
    @Test
    fun darkCanvasReversesBlackAndWhiteOnly() {
        val controller = DrawingController(DrawingModel())
        val blue = Color(0xFF1E488F)

        assertEquals(Color.Black, controller.getDrawingBackground())
        assertEquals(Color.White, controller.getDisplayColor(Color.Black))
        assertEquals(Color.Black, controller.getDisplayColor(Color.White))
        assertEquals(blue, controller.getDisplayColor(blue))

        assertFalse(controller.toggleDarkCanvas())
        assertEquals(Color.White, controller.getDrawingBackground())
        assertEquals(Color.Black, controller.getDisplayColor(Color.Black))
        assertEquals(Color.White, controller.getDisplayColor(Color.White))
    }

    @Test
    fun selectedStrokeWidthSurvivesToolChanges() {
        val controller = DrawingController(DrawingModel())

        controller.setStrokeWidth(27f)
        controller.setEraseMode()
        controller.setPenMode()

        assertEquals(27f, controller.getSelectedStrokeWidth())
    }
}
