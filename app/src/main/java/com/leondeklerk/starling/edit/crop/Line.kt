package com.leondeklerk.starling.edit.crop

import android.graphics.PointF

/**
 * A data class consisting of a start and end point, representing a line.
 * Contains a helper function to detect if a given point is within a specified offset of the line.
 * @param start: the start point of the line
 * @param end: the end point of the line
 */
data class Line(val start: PointF, val end: PointF) {

    /**
     * Checks if a specific point ([x], [y]) is within [offset] of the line.
     * @param x the x axis of the location that needs to be checked
     * @param y the y axis of the location that needs to be checked
     * @param offset the offset from the lines x/y values where [x] and [y] must be within
     * @return true if within the bounds, false if not
     */
    fun near(x: Float, y: Float, offset: Float): Boolean {
        // Vertical line
        if (start.x == end.x) {
            val xIn = x >= start.x - offset && x <= start.x + offset
            val yIn = (y <= start.y && y >= end.y) || (y >= start.y && y <= end.y)
            return xIn && yIn
        } else if (start.y == end.y) {
            // Horizontal line
            val yIn = y >= start.y - offset && y <= start.y + offset
            val xIn = (x <= start.x && x >= end.x) || (x >= start.x && x <= end.x)
            return yIn && xIn
        }

        return false
    }
}
