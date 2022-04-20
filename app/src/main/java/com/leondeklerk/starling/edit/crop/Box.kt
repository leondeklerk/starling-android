package com.leondeklerk.starling.edit.crop

import android.graphics.PointF
import android.graphics.RectF
import com.google.gson.Gson

/**
 * Data class representing a box.
 * Each box consists of 4 corners ([PointF]) and for sides ([Line].
 * @param leftTop: the left top corner (origin)
 * @param rightTop the right top corner of the box
 * @param rightBottom the right bottom corner (largest x/y values)
 * @param leftBottom the left bottom corner
 */
data class Box(
    val leftTop: PointF,
    val rightTop: PointF,
    val rightBottom: PointF,
    val leftBottom: PointF
) {
    var top: Line = Line(leftTop, rightTop)
    var right: Line = Line(rightTop, rightBottom)
    var bottom: Line = Line(rightBottom, leftBottom)
    var left: Line = Line(leftBottom, leftTop)

    val height: Float
        get() {
            return bottom.y - top.y
        }

    val width: Float
        get() {
            return right.x - left.x
        }

    val center: PointF
        get() {
            val centX = left.x + (right.x - left.x) / 2
            val centY = top.y + (bottom.y - top.y) / 2
            return PointF(centX, centY)
        }

    /**
     * Returns the rectangle representation of the box
     * @return RectF containing the box.
     */
    fun getRect(): RectF {
        return RectF(left.x, top.y, right.x, bottom.y)
    }

    /**
     * Check if a specific point is within the box.
     * @param x: the x coordinate of the point
     * @param y the y coordinate of the point
     * @return if the point is within the box or not
     */
    fun isWithin(x: Float, y: Float): Boolean {
        return (x >= leftTop.x && x <= rightBottom.x) && (y >= leftTop.y && y <= rightBottom.y)
    }

    /**
     * Copy a border box into a new instance
     * @return a new box instance
     */
    fun copy(): Box {
        val json = Gson().toJson(this)
        return Gson().fromJson(json, Box::class.java)
    }
}
