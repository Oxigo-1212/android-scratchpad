package com.alam.scratchpad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

class DrawingController(private val model: DrawingModel) {
    fun setPenMode() {
        model.drawingMode = DrawingMode.Pen
        //model.strokeWidth = AppSettings.DefaultStrokeWidth
        //model.drawingColor = AppSettings.DefaultDrawingColor
        model.drawingColor = AppSettings.AvailableDrawingColors[model.cycleDrawingColorCurrentIndex]
        model.strokeWidth = AppSettings.AvailableStrokeWidths[model.cycleStrokeWidthCurrentIndex]
    }
    fun setEraseMode() {
        model.drawingMode = DrawingMode.Erase
        model.strokeWidth = AppSettings.AvailableStrokeWidths[model.cycleStrokeWidthCurrentIndex]
        model.drawingColor = AppSettings.DefaultBackgroundColor
    }
    fun clearAll() {
        model.points.clear()
        model.paths.clear()
        model.offset = Offset.Zero
        setPenMode()
    }
    fun cycleDrawingColor() {
        val nextIndex = (model.cycleDrawingColorCurrentIndex + 1) % AppSettings.AvailableDrawingColors.size
        model.cycleDrawingColorCurrentIndex = nextIndex
        model.drawingColor = AppSettings.AvailableDrawingColors[nextIndex]
    }
    fun cycleStrokeWidth() {
        val nextIndex = (model.cycleStrokeWidthCurrentIndex + 1) % AppSettings.AvailableStrokeWidths.size
        model.cycleStrokeWidthCurrentIndex = nextIndex
        model.strokeWidth = AppSettings.AvailableStrokeWidths[nextIndex]
    }
    fun setDarkCanvas(enabled: Boolean) {
        model.darkCanvas = enabled
    }
    fun toggleDarkCanvas(): Boolean {
        model.darkCanvas = !model.darkCanvas
        return model.darkCanvas
    }
    fun updateScale(delta: Float) {
        model.scale *= delta
        if (AppSettings.LimitScaling) {
            model.scale = model.scale.coerceIn(AppSettings.MinScaling, AppSettings.MaxScaling)
        }
    }
    fun updateOffset(delta: Offset) {
        model.offset += delta / model.scale
    }
    fun onOrientationChanged() {
        model.offset = Offset(
            model.fullSize.height / 2 + (model.offset.x - model.fullSize.width / 2),
            model.fullSize.width / 2 + (model.offset.y - model.fullSize.height / 2)
        )
    }
    fun addPoint(point: Offset) {
        model.points.add(point)
    }
    fun clearPoints() {
        model.points.clear()
    }
    private fun getSmoothedPoints(): List<Offset> {
        return Utilities.chaikinSmoothing(model.points, AppSettings.SmoothingIterations)
    }
    fun getPointsPath(): Path {
        return DrawPath.pathFromPoints(getSmoothedPoints())
    }
    fun addPointsToPaths() {
        model.paths += DrawPath(
            points = getSmoothedPoints(),
            color = model.drawingColor,
            strokeWidth = getStrokeWidth()
        )
        clearPoints()
    }
    fun restoreDrawPaths(paths: List<DrawPath>) {
        clearPoints()
        model.paths.clear()
        model.paths.addAll(paths)
    }
    fun getMappedOffset(point: Offset): Offset {
        return Offset(
            point.x / model.scale - model.offset.x + model.size.width / 2 - model.size.width / 2 / model.scale,
            point.y / model.scale - model.offset.y + model.size.height / 2 - model.size.height / 2 / model.scale
        )
    }
    fun setFullSize(size: Size) {
        model.fullSize = size
    }
    fun setSize(size: Size) {
        model.size = size
    }
    fun getSize(): Size {
        return model.size
    }
    fun getDrawingMode(): DrawingMode {
        return model.drawingMode
    }
    fun getDrawPaths(): List<DrawPath> {
        return model.paths
    }
    fun getOffset(): Offset {
        return model.offset
    }
    fun getScale(): Float {
        return model.scale
    }
    fun getDrawingBackground(): Color {
        return if (model.darkCanvas) Color.Black else Color.White
    }
    fun getDisplayColor(color: Color): Color {
        if (!model.darkCanvas) return color
        return when (color) {
            Color.Black -> Color.White
            Color.White -> Color.Black
            else -> color
        }
    }
    fun getDrawingColor(): Color {
        return model.drawingColor
    }
    fun getStrokeWidth(): Float {
        return if (model.drawingMode != DrawingMode.Erase) {
            model.strokeWidth
        } else {
            model.strokeWidth * AppSettings.EraserStrokeWidthMultiplier / model.scale
        }
    }
    fun getStrokeWidthIndex(): Int {
        return model.cycleStrokeWidthCurrentIndex
    }
    fun getDrawingColorIndex(): Int {
        return model.cycleDrawingColorCurrentIndex
    }
}