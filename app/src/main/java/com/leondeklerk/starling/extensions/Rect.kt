package com.leondeklerk.starling.extensions

import android.graphics.PointF
import android.graphics.Rect
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint

/**
 * Enlarge a rectangle by a number of pixels in each direction.
 * @param pixels: the number of pixels to enlarge by
 * @return a new rect with increased size on each side
 */
fun Rect.enlargeBy(pixels: Float): Rect {
    val px = pixels.toInt()
    return Rect(left - px, top - px, right + px, bottom + px)
}

/**
 * Check if a rectangle contains a point.
 * @param point: the point to check for.
 * @return if the point is within the rectangle or not.
 */
fun Rect.contains(point: PointF): Boolean {
    return (contains(point.toPoint()))
}
