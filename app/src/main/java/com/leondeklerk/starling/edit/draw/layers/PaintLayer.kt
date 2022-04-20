package com.leondeklerk.starling.edit.draw.layers

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

/**
 * Data class representing a paint drawing layer on a canvas.
 * [DrawLayer] of type [DrawLayerType.PAINT]
 * @param index the index of this layer on the canvas within all the list of layers
 * @param path the touch path representing a brush line on the canvas
 * @param brush the paint used to draw the line on the canvas
 */
data class PaintLayer(override var index: Int, val path: Path, val brush: Paint) : DrawLayer(index, DrawLayerType.PAINT) {
    override fun draw(canvas: Canvas?) {
        canvas?.drawPath(path, brush)
    }

    /**
     * Map the current layer to a scaled copy of itself.
     * @param scalar the scalar that is used to scale the current layer with.
     */
    override fun mapToScale(scalar: Float): PaintLayer {
        // Create a scaling matrix
        val scaleMatrix = Matrix()
        scaleMatrix.setScale(scalar, scalar)

        // Scale the path
        val scaledPath = Path(path)
        scaledPath.transform(scaleMatrix)

        // Scale the brush
        val scaledBrush = Paint(brush)
        scaledBrush.strokeWidth *= scalar
        return PaintLayer(index, scaledPath, scaledBrush)
    }
}
