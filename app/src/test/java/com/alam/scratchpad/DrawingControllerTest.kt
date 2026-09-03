package com.alam.scratchpad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

    @Test
    fun selectsAndMovesOnlyPathsInsideRectangle() {
        val controller = DrawingController(DrawingModel())
        controller.restoreDrawPaths(
            listOf(
                DrawPath(listOf(Offset(10f, 10f), Offset(20f, 20f)), Color.Black, 4f),
                DrawPath(listOf(Offset(80f, 80f), Offset(90f, 90f)), Color.Black, 4f),
                DrawPath(listOf(Offset(20f, 20f), Offset(60f, 60f)), Color.Black, 4f),
            )
        )

        val selected = controller.getPathsIn(
            listOf(Offset(0f, 0f), Offset(30f, 0f), Offset(30f, 30f), Offset(0f, 30f))
        )
        controller.movePaths(selected, Offset(5f, -5f))

        assertEquals(setOf(0), selected)
        assertEquals(listOf(Offset(15f, 5f), Offset(25f, 15f)), controller.getDrawPaths()[0].points)
        assertEquals(listOf(Offset(80f, 80f), Offset(90f, 90f)), controller.getDrawPaths()[1].points)
        assertEquals(listOf(Offset(20f, 20f), Offset(60f, 60f)), controller.getDrawPaths()[2].points)
        assertEquals(Rect(13f, 3f, 27f, 17f), controller.getBounds(selected))
    }
}
