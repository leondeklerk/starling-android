package com.leondeklerk.starling.edit

import android.graphics.PointF

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

    fun near(x: Float, y: Float, bounds: Float): Boolean {
        // Vertical line
        if (start.x == end.x) {
            val xIn = x >= start.x - bounds && x <= start.x + bounds
            val yIn = (y <= start.y && y >= end.y) || (y >= start.y && y <= end.y)
            return xIn && yIn
        } else if (start.y == end.y) {
            // Horizontal line
            val yIn = y >= start.y - bounds && y <= start.y + bounds
            val xIn = (x <= start.x && x >= end.x) || (x >= start.x && x <= end.x)
            return yIn && xIn
        }

        return false
    }
}
