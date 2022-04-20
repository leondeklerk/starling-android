package com.leondeklerk.starling.edit.draw

import android.graphics.Color

/**
 * Data class representing the style of a text layer.
 * Contains the HSV color attributes as well as the size.
 * @param hue the hue attribute of the color
 * @param saturation the saturation attribute of the HSV color
 * @param value the value attribute of the HSV color
 * @param size the text size in dp
 */
data class TextStyle(
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val size: Float
) {
    val color: Int
        get() {
            return Color.HSVToColor(floatArrayOf(hue, saturation, value))
        }
}
