package com.alam.scratchpad

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt

class DrawingExporter(private val controller: DrawingController) {
    fun writePng(output: OutputStream) {
        val (width, height) = dimensions()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            render(Canvas(bitmap))
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("Could not encode PNG")
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun writePdf(output: OutputStream) {
        val (width, height) = dimensions()
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            render(page.canvas)
            document.finishPage(page)
            val pdf = ByteArrayOutputStream().also(document::writeTo)
            val project = ByteArrayOutputStream().also {
                DrawingProjectCodec.write(
                    it,
                    controller.getDrawPaths(),
                    controller.getImportedImage()?.asAndroidBitmap(),
                )
            }
            EditablePdf.embed(pdf.toByteArray(), project.toByteArray(), output)
        } finally {
            document.close()
        }
    }

    private fun dimensions(): Pair<Int, Int> {
        val size = controller.getSize()
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        if (width <= 0 || height <= 0) throw IOException("Canvas is not ready")
        return width to height
    }

    private fun render(canvas: Canvas) {
        val size = controller.getSize()
        val scale = controller.getScale()
        val offset = controller.getOffset()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        canvas.drawColor(controller.getDrawingBackground().toArgb())
        controller.getImportedImage()?.let { image ->
            val bounds = fitImageRect(image.width, image.height, size)
            val topLeft = mapToViewport(bounds.topLeft, size, scale, offset)
            val bottomRight = mapToViewport(bounds.bottomRight, size, scale, offset)
            canvas.drawBitmap(
                image.asAndroidBitmap(),
                null,
                RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
        for (drawPath in controller.getDrawPaths()) {
            val path = Path()
            drawPath.points.forEachIndexed { index, point ->
                val mapped = mapToViewport(point, size, scale, offset)
                if (index == 0) path.moveTo(mapped.x, mapped.y) else path.lineTo(mapped.x, mapped.y)
            }
            paint.color = controller.getDisplayColor(drawPath.color).toArgb()
            paint.strokeWidth = drawPath.strokeWidth * scale
            canvas.drawPath(path, paint)
        }
    }
}

internal fun mapToViewport(point: Offset, size: Size, scale: Float, offset: Offset) = Offset(
    (point.x + offset.x - size.width / 2) * scale + size.width / 2,
    (point.y + offset.y - size.height / 2) * scale + size.height / 2,
)

internal fun fitImageRect(imageWidth: Int, imageHeight: Int, canvasSize: Size): Rect {
    require(imageWidth > 0 && imageHeight > 0 && canvasSize.width > 0 && canvasSize.height > 0)
    val scale = minOf(canvasSize.width / imageWidth, canvasSize.height / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return Rect(
        offset = Offset((canvasSize.width - width) / 2, (canvasSize.height - height) / 2),
        size = Size(width, height),
    )
}
