package com.leondeklerk.starling.edit.draw

import android.graphics.Color

/**
 * Data class representing the data needed to create a brush paint style.
 * Contains a type, size, HSV color and the transparency.
 * @param type the type of brush (pencil/marker/eraser)
 * @param hue the hue value of the HSV color
 * @param saturation the saturation value of the HSV color
 * @param value the value of the HSV color
 * @param size the pixel size of the brush
 * @param alpha the transparency of the current brush
 */
data class BrushStyle(
    val type: BrushType,
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val size: Float,
    val alpha: Float
) {
    val color: Int
        get() {
            return Color.HSVToColor(floatArrayOf(hue, saturation, value))
        }
}
