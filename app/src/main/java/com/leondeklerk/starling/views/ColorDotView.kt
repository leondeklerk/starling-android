package com.leondeklerk.starling.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Simple view that draws a filled circle with a stroke.
 */
class ColorDotView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var color: Int = Color.LTGRAY
        set(value) {
            paint.color = value
            field = value
            invalidate()
        }

    var radius: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLUE
    }

    private var cx: Float = 0f
    private var cy: Float = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cx, cy, radius, paint)
    }
}
