package com.alam.scratchpad

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingPointerTrackerTest {
    @Test
    fun stylusReplacesFingerAndRejectsLaterPalmTouches() {
        val tracker = DrawingPointerTracker()

        assertTrue(tracker.onPointerDown(1, MotionEvent.TOOL_TYPE_FINGER))
        assertTrue(tracker.onPointerDown(2, MotionEvent.TOOL_TYPE_STYLUS))
        assertFalse(tracker.onPointerDown(3, MotionEvent.TOOL_TYPE_FINGER))

        assertEquals(2, tracker.pointerId)
        assertTrue(tracker.isStylus)
        assertTrue(tracker.isActive(2))
        assertFalse(tracker.isActive(3))
    }

    @Test
    fun detectsEraserToolAndStylusSideButton() {
        val tracker = DrawingPointerTracker()

        assertTrue(tracker.onPointerDown(1, MotionEvent.TOOL_TYPE_ERASER))
        assertTrue(tracker.isEraser)
        tracker.clear()

        assertTrue(
            tracker.onPointerDown(
                1,
                MotionEvent.TOOL_TYPE_STYLUS,
                MotionEvent.BUTTON_STYLUS_PRIMARY,
            )
        )
        assertTrue(tracker.isEraser)
        tracker.clear()

        assertTrue(
            tracker.onPointerDown(
                1,
                MotionEvent.TOOL_TYPE_STYLUS,
                MotionEvent.BUTTON_SECONDARY,
            )
        )
        assertTrue(tracker.isEraser)
    }
}
