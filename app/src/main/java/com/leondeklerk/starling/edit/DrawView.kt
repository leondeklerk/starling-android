package com.leondeklerk.starling.edit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawView(context: Context, attributeSet: AttributeSet?) : View(
    context,
    attributeSet
) {

    private val pathList: ArrayList<Path> = ArrayList()
    private val paintList: ArrayList<Paint> = ArrayList()

    init {
        initialize()
    }

    private fun initialize() {
        pathList.add(Path())
        paintList.add(createPaint())
    }

    private fun createPaint(): Paint {
        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        return paint
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var index = 0
        while (index < pathList.size) {
            val path = pathList[index]
            val paint = paintList[index]
            canvas.drawPath(path, paint)
            index++
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val newPath = Path()
                newPath.moveTo(event.x, event.y)
                pathList.add(newPath)
                paintList.add(createPaint())
            }
            MotionEvent.ACTION_MOVE -> {
                val x: Float = event.x
                val y: Float = event.y
                val path = pathList.last()
                path.lineTo(x, y)
            }
        }
        // Invalidate the whole view. If the view is visible.
        invalidate()
        return true
    }

    fun reset() {
        pathList.clear()
        paintList.clear()
        initialize()
        invalidate()
    }
}
