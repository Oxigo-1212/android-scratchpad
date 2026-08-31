package com.alam.scratchpad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class DrawingStorageTest {
    @Test
    fun codecRoundTripsPathsAndRejectsUnsupportedVersions() {
        val paths = listOf(
            DrawPath(
                points = listOf(Offset(-12.5f, 8f), Offset(30f, 42.25f)),
                color = Color.Black,
                strokeWidth = 16f
            ),
            DrawPath(
                points = listOf(Offset(1f, 2f), Offset(3f, 4f), Offset(5f, 6f)),
                color = Color(0xFFF44546),
                strokeWidth = 48f
            )
        )

        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { DrawingCodec.write(it, paths) }
        }.toByteArray()

        val restored = DataInputStream(ByteArrayInputStream(bytes)).use(DrawingCodec::read)
        assertEquals(paths, restored)

        bytes[7] = 2
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use(DrawingCodec::read)
            fail("Unsupported versions must be rejected")
        } catch (_: IOException) {
        }
    }
}
