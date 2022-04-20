package com.leondeklerk.starling.edit.crop

import android.graphics.PointF

/**
 * A data class consisting of a start and end point, representing a line.
 * Contains helper functions to get/set values based on the axis.
 * @param start: the start point of the line
 * @param end: the end point of the line
 */
data class Line(val start: PointF, val end: PointF) {

    var x: Float
        get() = start.x
        set(value) {
            start.x = value
            end.x = value
        }

    var y: Float
        get() = start.y
        set(value) {
            start.y = value
            end.y = value
        }

    /**
     * Retrieve the axis value of the line based on the specified axis.
     * @param type a string containing the desired axis type (x/y).
     * @throws UnknownError when incorrect axis strings are given
     * @return the value of the specified axis.
     */
    fun getByType(type: String): Float {
        return when (type) {
            "x" -> {
                x
            }
            "y" -> {
                y
            }
            else -> throw UnknownError()
        }
    }

    /**
     * Set the axis value of the line based on the specified axis.
     * @param value the new axis value
     * @param type a string containing the axis type (x/y).
     * @throws UnknownError when incorrect axis strings are given
     */
    fun setByType(value: Float, type: String) {
        when (type) {
            "x" -> {
                x = value
            }
            "y" -> {
                y = value
            }
            else -> throw UnknownError()
        }
    }

    /**
     * Checks if a specific point ([x], [y]) is within [offset] pixels of the line.
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
