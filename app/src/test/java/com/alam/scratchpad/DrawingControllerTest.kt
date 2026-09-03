package com.alam.scratchpad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun zoomIsUnboundedAndFitShowsAllStrokes() {
        val controller = DrawingController(DrawingModel())
        controller.setSize(androidx.compose.ui.geometry.Size(1000f, 500f))
        controller.updateScale(10f)
        controller.updateScale(10f)
        assertEquals(100f, controller.getScale())

        controller.restoreDrawPaths(listOf(
            DrawPath(listOf(Offset(0f, 0f), Offset(2000f, 1000f)), Color.Black, 0f)
        ))
        controller.fitDrawingToView()

        assertEquals(0.45f, controller.getScale())
        assertEquals(Offset(-500f, -250f), controller.getOffset())
        assertTrue(controller.getScale() < 1f)
    }

    @Test
    fun undoAndRedoDrawingAndGroupedMove() {
        val controller = DrawingController(DrawingModel())
        val original = DrawPath(listOf(Offset(10f, 10f), Offset(20f, 20f)), Color.Black, 4f)
        controller.restoreDrawPaths(listOf(original))

        controller.beginPathMove()
        controller.movePaths(setOf(0), Offset(5f, 0f))
        controller.movePaths(setOf(0), Offset(5f, 0f))
        controller.endPathMove()
        controller.undo()
        assertEquals(original, controller.getDrawPaths().single())

        controller.redo()
        assertEquals(
            listOf(Offset(20f, 10f), Offset(30f, 20f)),
            controller.getDrawPaths().single().points,
        )
    }
}
