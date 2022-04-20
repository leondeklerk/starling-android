package com.leondeklerk.starling.edit.draw.layers

import android.graphics.Canvas

/**
 * Base class for an entity representing a drawing layer on a canvas.
 * Each layer has an index and a type.
 * @param index the index of this layer on the canvas within all the list of layers
 * @param type the type of drawing layer (e.g. text, paint)
 */
sealed class DrawLayer(open var index: Int, val type: DrawLayerType) {
    /**
     * Draw this layer on the provided canvas
     * @param canvas the canvas to draw this layer on.
     */
    abstract fun draw(canvas: Canvas?)

    /**
     * Create a new layer that is scaled by the given scalar.
     * @param scalar the scalar to scale the current layer with.
     */
    abstract fun mapToScale(scalar: Float): DrawLayer
}
