package com.leondeklerk.starling.edit

import android.graphics.PointF
import com.google.gson.Gson

data class Box(val leftTop: PointF, val rightTop: PointF, val rightBottom: PointF, val leftBottom: PointF) {
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

    fun getLines(): List<Line> {
        return listOf(top, right, bottom, left)
    }

    fun getPoints(): List<PointF> {
        return listOf(leftTop, rightTop, rightBottom, leftBottom)
    }

    fun isWithin(x: Float, y: Float): Boolean {
        return (x >= leftTop.x && x <= rightBottom.x) && (y >= leftTop.y && y <= rightBottom.y)
    }

    fun copy(): Box {
        val json = Gson().toJson(this)
        return Gson().fromJson(json, Box::class.java)
    }
}

