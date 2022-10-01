package com.leondeklerk.starling.extensions

import android.graphics.PointF

operator fun PointF.times(scalar: Float): PointF {
    return PointF(x * scalar, y * scalar)
}

operator fun PointF.minus(other: PointF): PointF {
    return PointF(x - other.x, y - other.y)
}

/**
 * Add extra helper function to the PointF class to check if a point (touchX, touchY) is within a radius (bounds)
 * of the existing point.
 *
 * @param touchX: The x coordinate to test for
 * @param touchY: the y coordinate to test for
 * @returns if the touch point is within bounds of the point.
 */
fun PointF.near(touchX: Float, touchY: Float, bounds: Float): Boolean {
    // Is it within the left bounds
    val xIn = (touchX <= x + bounds && touchX >= x - bounds)
    val yIn = (touchY <= y + bounds && touchY >= y - bounds)
    return xIn && yIn
}
