package com.alam.scratchpad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import java.util.ArrayDeque

class DrawingController(private val model: DrawingModel) {
    private data class Snapshot(val paths: List<DrawPath>, val image: ImageBitmap?)

    private val undoHistory = ArrayDeque<Snapshot>()
    private val redoHistory = ArrayDeque<Snapshot>()
    private var movingPaths = false
    private var moveRecorded = false

    fun setPenMode() {
        model.drawingMode = DrawingMode.Pen
        //model.strokeWidth = AppSettings.DefaultStrokeWidth
        //model.drawingColor = AppSettings.DefaultDrawingColor
        model.drawingColor = AppSettings.AvailableDrawingColors[model.cycleDrawingColorCurrentIndex]
    }
    fun setEraseMode() {
        model.drawingMode = DrawingMode.Erase
        model.drawingColor = AppSettings.DefaultBackgroundColor
    }
    fun setSelectMode() {
        model.drawingMode = DrawingMode.Select
        clearPoints()
    }
    fun clearAll() {
        if (model.paths.isNotEmpty() || model.importedImage != null) recordUndo()
        model.points.clear()
        model.paths.clear()
        model.importedImage = null
        model.offset = Offset.Zero
        setPenMode()
    }
    fun cycleDrawingColor() {
        val nextIndex = (model.cycleDrawingColorCurrentIndex + 1) % AppSettings.AvailableDrawingColors.size
        model.cycleDrawingColorCurrentIndex = nextIndex
        model.drawingColor = AppSettings.AvailableDrawingColors[nextIndex]
    }
    fun setStrokeWidth(strokeWidth: Float) {
        model.strokeWidth = strokeWidth.coerceIn(
            AppSettings.MinStrokeWidth,
            AppSettings.MaxStrokeWidth,
        )
    }
    fun setDarkCanvas(enabled: Boolean) {
        model.darkCanvas = enabled
    }
    fun toggleDarkCanvas(): Boolean {
        model.darkCanvas = !model.darkCanvas
        return model.darkCanvas
    }
    fun updateScale(delta: Float) {
        val scale = model.scale * delta
        if (scale.isFinite() && scale > 0f) model.scale = scale
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
        if (model.points.isEmpty()) return
        recordUndo()
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
        undoHistory.clear()
        redoHistory.clear()
    }
    fun replaceDrawing(paths: List<DrawPath>?, image: ImageBitmap?) {
        recordUndo()
        clearPoints()
        if (paths != null) {
            model.paths.clear()
            model.paths.addAll(paths)
        }
        model.importedImage = image
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
    fun setImportedImage(image: ImageBitmap?) {
        model.importedImage = image
    }
    fun getImportedImage(): ImageBitmap? = model.importedImage
    fun getPathsIn(area: List<Offset>): Set<Int> = model.paths.mapIndexedNotNull { index, path ->
        index.takeIf {
            path.points.isNotEmpty() && path.points.all { point -> pointInPolygon(point, area) }
        }
    }.toSet()
    fun getBounds(indices: Set<Int>): Rect? {
        val paths = indices.mapNotNull(model.paths::getOrNull).filter { it.points.isNotEmpty() }
        if (paths.isEmpty()) return null
        return Rect(
            left = paths.minOf { path -> path.points.minOf { it.x } - path.strokeWidth / 2 },
            top = paths.minOf { path -> path.points.minOf { it.y } - path.strokeWidth / 2 },
            right = paths.maxOf { path -> path.points.maxOf { it.x } + path.strokeWidth / 2 },
            bottom = paths.maxOf { path -> path.points.maxOf { it.y } + path.strokeWidth / 2 },
        )
    }
    fun movePaths(indices: Set<Int>, amount: Offset) {
        if (amount == Offset.Zero || indices.isEmpty()) return
        if (!movingPaths || !moveRecorded) {
            recordUndo()
            moveRecorded = true
        }
        for (index in indices) {
            model.paths[index] = model.paths[index].copy(
                points = model.paths[index].points.map { it + amount }
            )
        }
    }
    fun beginPathMove() {
        movingPaths = true
        moveRecorded = false
    }
    fun endPathMove() {
        movingPaths = false
        moveRecorded = false
    }
    fun undo() {
        if (undoHistory.isEmpty()) return
        pushHistory(redoHistory, snapshot())
        applySnapshot(undoHistory.removeLast())
    }
    fun redo() {
        if (redoHistory.isEmpty()) return
        pushHistory(undoHistory, snapshot())
        applySnapshot(redoHistory.removeLast())
    }
    fun getOffset(): Offset {
        return model.offset
    }
    fun getScale(): Float {
        return model.scale
    }
    fun fitDrawingToView() {
        val viewport = model.size
        if (viewport.width <= 0f || viewport.height <= 0f) return
        val pathBounds = getBounds(model.paths.indices.toSet())
        val imageBounds = model.importedImage?.let {
            fitImageRect(it.width, it.height, viewport)
        }
        val bounds = when {
            pathBounds == null -> imageBounds
            imageBounds == null -> pathBounds
            else -> Rect(
                minOf(pathBounds.left, imageBounds.left),
                minOf(pathBounds.top, imageBounds.top),
                maxOf(pathBounds.right, imageBounds.right),
                maxOf(pathBounds.bottom, imageBounds.bottom),
            )
        }
        if (bounds == null) {
            model.scale = 1f
            model.offset = Offset.Zero
            return
        }
        model.scale = minOf(
            1f,
            viewport.width * 0.9f / bounds.width.coerceAtLeast(1f),
            viewport.height * 0.9f / bounds.height.coerceAtLeast(1f),
        )
        model.offset = Offset(viewport.width / 2, viewport.height / 2) - bounds.center
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
    fun getSelectedStrokeWidth() = model.strokeWidth
    fun getDrawingColorIndex(): Int {
        return model.cycleDrawingColorCurrentIndex
    }

    private fun snapshot() = Snapshot(model.paths.toList(), model.importedImage)

    private fun recordUndo() {
        pushHistory(undoHistory, snapshot())
        redoHistory.clear()
    }

    private fun pushHistory(history: ArrayDeque<Snapshot>, snapshot: Snapshot) {
        if (history.size == HISTORY_LIMIT) history.removeFirst()
        history.addLast(snapshot)
    }

    private fun applySnapshot(snapshot: Snapshot) {
        clearPoints()
        model.paths.clear()
        model.paths.addAll(snapshot.paths)
        model.importedImage = snapshot.image
        endPathMove()
        setPenMode()
    }

    companion object {
        private const val HISTORY_LIMIT = 100
    }
}

internal fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var previous = polygon.last()
    for (current in polygon) {
        if ((current.y > point.y) != (previous.y > point.y) &&
            point.x < (previous.x - current.x) * (point.y - current.y) /
            (previous.y - current.y) + current.x
        ) {
            inside = !inside
        }
        previous = current
    }
    return inside
}
