package com.alam.scratchpad

import android.util.AtomicFile
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class DrawingStorage(directory: File) {
    private val atomicFile = AtomicFile(File(directory, FILE_NAME))

    fun load(): List<DrawPath> {
        return try {
            atomicFile.openRead().use { input ->
                DrawingCodec.read(DataInputStream(BufferedInputStream(input)))
            }
        } catch (_: FileNotFoundException) {
            emptyList()
        }
    }

    fun save(paths: List<DrawPath>) {
        val fileOutput = atomicFile.startWrite()
        try {
            val output = DataOutputStream(BufferedOutputStream(fileOutput))
            DrawingCodec.write(output, paths)
            output.flush()
            atomicFile.finishWrite(fileOutput)
        } catch (error: Exception) {
            atomicFile.failWrite(fileOutput)
            throw error
        }
    }

    companion object {
        private const val FILE_NAME = "drawing.bin"
    }
}

internal object DrawingCodec {
    private const val MAGIC = 0x53504144 // SPAD
    private const val VERSION = 1
    private const val MAX_PATHS = 100_000
    private const val MAX_POINTS_PER_PATH = 1_000_000
    private const val MAX_TOTAL_POINTS = 5_000_000

    fun write(output: DataOutput, paths: List<DrawPath>) {
        if (paths.size > MAX_PATHS) invalid("Too many paths")

        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeInt(paths.size)

        var totalPoints = 0
        for (path in paths) {
            if (path.points.isEmpty() || path.points.size > MAX_POINTS_PER_PATH) {
                invalid("Invalid point count")
            }
            if (path.points.size > MAX_TOTAL_POINTS - totalPoints) invalid("Too many points")
            totalPoints += path.points.size
            if (!path.strokeWidth.isFinite() || path.strokeWidth <= 0f) {
                invalid("Invalid stroke width")
            }

            output.writeInt(path.color.toArgb())
            output.writeFloat(path.strokeWidth)
            output.writeInt(path.points.size)
            for (point in path.points) {
                if (!point.x.isFinite() || !point.y.isFinite()) invalid("Invalid point")
                output.writeFloat(point.x)
                output.writeFloat(point.y)
            }
        }
    }

    fun read(input: DataInput): List<DrawPath> {
        if (input.readInt() != MAGIC) invalid("Invalid drawing file")
        if (input.readInt() != VERSION) invalid("Unsupported drawing version")

        val pathCount = input.readInt()
        if (pathCount !in 0..MAX_PATHS) invalid("Invalid path count")

        val paths = ArrayList<DrawPath>(pathCount)
        var totalPoints = 0
        repeat(pathCount) {
            val color = Color(input.readInt())
            val strokeWidth = input.readFloat()
            if (!strokeWidth.isFinite() || strokeWidth <= 0f) {
                invalid("Invalid stroke width")
            }

            val pointCount = input.readInt()
            if (pointCount !in 1..MAX_POINTS_PER_PATH) invalid("Invalid point count")
            if (pointCount > MAX_TOTAL_POINTS - totalPoints) invalid("Too many points")
            totalPoints += pointCount

            val points = ArrayList<Offset>(pointCount)
            repeat(pointCount) {
                val x = input.readFloat()
                val y = input.readFloat()
                if (!x.isFinite() || !y.isFinite()) invalid("Invalid point")
                points += Offset(x, y)
            }
            paths += DrawPath(points, color, strokeWidth)
        }
        return paths
    }

    private fun invalid(message: String): Nothing {
        throw IOException(message)
    }
}
