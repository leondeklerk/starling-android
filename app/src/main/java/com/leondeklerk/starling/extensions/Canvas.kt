package com.leondeklerk.starling.extensions

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import com.leondeklerk.starling.edit.Line

/**
 * Helper function that will draw a line based on a Line class.
 * @param line the line to draw
 * @param paint the paint to draw with
 */
fun Canvas.drawLine(line: Line, paint: Paint) {
    this.drawLine(line.start.x, line.start.y, line.end.x, line.end.y, paint)
}

/**
 * Helper function to draw a circle given a point, radius and paint.
 * @param center the center of the circle to draw
 * @param radius the radius of the circle
 * @param paint the paint to draw with
 */
fun Canvas.drawCircle(center: PointF, radius: Float, paint: Paint) {
    this.drawCircle(center.x, center.y, radius, paint)
}
