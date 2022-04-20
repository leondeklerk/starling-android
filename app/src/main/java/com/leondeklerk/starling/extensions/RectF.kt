package com.leondeklerk.starling.extensions

import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.contains

/**
 * Enlarge a rectangle by a number of pixels in each direction.
 * @param pixels: the number of pixels to enlarge by
 * @return a new rect with increased size on each side
 */
fun RectF.enlargeBy(pixels: Float): RectF {
    return RectF(left - pixels, top - pixels, right + pixels, bottom + pixels)
}

/**
 * Check if a rectangle contains a point.
 * @param point: the point to check for.
 * @return if the point is within the rectangle or not.
 */
fun RectF.contains(point: PointF): Boolean {
    return contains(point)
}
