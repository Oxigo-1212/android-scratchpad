package com.alam.scratchpad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class EditablePdfTest {
    @Test
    fun pdfRoundTripsEditableStrokes() {
        val paths = listOf(
            DrawPath(listOf(Offset(1f, 2f), Offset(3f, 4f)), Color.Black, 8f)
        )
        val project = ByteArrayOutputStream().also {
            DrawingProjectCodec.write(it, paths, null)
        }.toByteArray()
        val pdf = ByteArrayOutputStream().also { output ->
            PDDocument().use {
                it.addPage(PDPage())
                it.save(output)
            }
        }.toByteArray()
        val editablePdf = ByteArrayOutputStream().also {
            EditablePdf.embed(pdf, project, it)
        }.toByteArray()

        val restored = EditablePdf.readProject(ByteArrayInputStream(editablePdf))

        assertEquals(paths, restored?.paths)
    }
}
