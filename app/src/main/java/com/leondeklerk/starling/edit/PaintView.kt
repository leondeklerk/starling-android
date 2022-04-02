package com.leondeklerk.starling.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.leondeklerk.starling.extensions.dpToPixels
import com.leondeklerk.starling.extensions.enlargeBy
import timber.log.Timber

class PaintView(context: Context, attributeSet: AttributeSet?) : View(
    context,
    attributeSet
) {

    private val pathList: ArrayList<Path> = ArrayList()
    private val brushList: ArrayList<BrushStyle> = ArrayList()
    private val textList: ArrayList<TextObject> = ArrayList()

    private var path = Path()
    private var brush = Paint()
    private lateinit var brushStyle: BrushStyle
    private var drawUpTo = 0
    private var movingText = false
    private var scaleDetector: ScaleGestureDetector? = null
    private var scaling = false
    private var allowTouch = true
    private var last = PointF()
    private var scaleBy = 1f
    private var startScalar = 1f
    private var currentScale = 1f
    private var scalingActive = false
    private var scalingPoint = PointF()
    private var bitmap: Bitmap? = null
//    private var canvas: Canvas? = null
//    private val bitmapPaint: Paint

    // Scale listener responsible for handling scaling gestures (pinch)
    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Current scale factor
            scaleBy = startScalar * detector.scaleFactor / currentScale

            // The raw scale
            val projectedScale = scaleBy * currentScale

            // Make the scaling bounded
            if (projectedScale < 0.4f) {
                scaleBy = 0.4f / currentScale
            } else if (projectedScale > 4f) {
                scaleBy = 4f / currentScale
            }

            return false
        }

        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            startScalar = currentScale
            scaling = true
            detector?.let {
                scalingPoint = PointF(detector.focusX, detector.focusY)
            }
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaleBy = 1f
            scaling = false
        }
    }

    data class TextObject(
        var layout: StaticLayout,
        var origin: PointF,
        var width: Int,
        var height: Int,
        var text: String,
        var scale: Float,
        var dpOffset: Float
    ) {
        val center: PointF
            get() {
                return PointF(origin.x + width / 2f, origin.y + height / 2f)
            }

        val bounds: RectF
            get() {
                return RectF(origin.x, origin.y, origin.x + width, origin.y + height).enlargeBy((12 * dpOffset).toInt())
            }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        scaleDetector = ScaleGestureDetector(context, scaleListener)
//        bitmapPaint = Paint(Paint.DITHER_FLAG)
//        bitmapPaint.isAntiAlias = true
//        bitmapPaint.isFilterBitmap = true
    }

//    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
//        super.onSizeChanged(w, h, oldw, oldh)
//        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
//        canvas = Canvas(bitmap!!)
//    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            drawOnCanvas(canvas)
        }
    }

    fun drawOnCanvas(canvas: Canvas, scalePath: Boolean = false) {
        var index = 0
        while (index < drawUpTo) {
            val path = pathList[index]
            val paint = getBrush(brushList[index])
            bitmap?.let {
                if (scalePath) {
                    val scale = it.width.toFloat() / width.toFloat()
                    val m = Matrix()
                    m.setScale(scale, scale)
                    path.transform(m)
                }
            }
            canvas?.drawPath(path, paint)

            index++
        }
        canvas?.save()
        var rectangle = RectF()
        var textObject = textList.firstOrNull()
        textObject?.let {
            val origin = textObject.origin
            rectangle = textObject.bounds
            canvas?.translate(origin.x, origin.y)
            textObject.layout.draw(canvas)
        }
        canvas?.restore()
        textObject?.let {
            canvas?.drawPoint(it.center.x, it.center.y, brush)
            canvas?.drawRect(rectangle, brush)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector?.onTouchEvent(event)

        if (scaling) {
            val textObject = textList.firstOrNull()
            textObject?.let { obj ->
                scaleDetector?.let {

                    if (!scalingActive) {
                        if (obj.bounds.contains(it.focusX, it.focusY)) {
                            scalingActive = true
                        }
                    }

                    if (scalingActive) {

                        val textObject = textList.firstOrNull()
                        textObject?.let { obj ->
                            obj.scale *= scaleBy
                            currentScale = obj.scale

                            val paint = getPaint()
                            paint.textSize *= obj.scale
                            val length = paint.measureText(obj.text).toInt()
                            val builder = StaticLayout.Builder.obtain(obj.text, 0, obj.text.length, paint, length)
                            val layout = builder.build()

                            val diffH = (layout.height - obj.height) / 2f
                            val diffW = (layout.width - obj.width) / 2f
                            obj.origin.offset(-diffW, -diffH)
                            obj.layout = layout
                            obj.height = layout.height
                            obj.width = layout.width
                            invalidate()
                        }
                    }
                }
            }
            return true
        }

        if (event.pointerCount > 1) {
            allowTouch = false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!allowTouch) return true

                val textObject = textList.firstOrNull()
                textObject?.let { obj ->
                    if (obj.bounds.contains(event.x, event.y)) {
                        movingText = true
                        last = PointF(event.x, event.y)
                    }
                }

                if (movingText) return true

                if (drawUpTo < pathList.size) {
                    val removePaths = pathList.slice(drawUpTo until pathList.size)
                    pathList.removeAll(removePaths)

                    val removeStyles = brushList.slice(drawUpTo until brushList.size)
                    brushList.removeAll(removeStyles)
                }

                path = Path()
                pathList.add(path)

                brush = getBrush(brushStyle)
                brushList.add(brushStyle)

                drawUpTo = pathList.size

                path.moveTo(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!allowTouch) return true
                if (movingText) {
                    val textObject = textList.firstOrNull()
                    textObject?.let { obj ->
                        val dX = event.x - last.x
                        val dY = event.y - last.y
                        obj.origin.offset(dX, dY)
                        obj.center.offset(dX, dY)
                        last = PointF(event.x, event.y)
                    }
                } else {
                    path.lineTo(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                allowTouch = true
                scalingActive = false
                if (movingText) {
                    movingText = false
                } else {
                    // Make sure everything is in the initial state after movement
                    path = Path()
                    brush = getBrush(brushStyle)
                }
            }
        }

        invalidate()
        return true
    }

    fun setSize(bmWidth: Int, bmHeight: Int) {
        Timber.d("Size set")
        bitmap = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888)
//        canvas = Canvas(bitmap!!)
    }

    fun setBrush(style: BrushStyle) {
        brushStyle = style
        brush = getBrush(style)
    }

    fun undo() {
        drawUpTo = Integer.max(0, drawUpTo - 1)
        invalidate()
    }

    fun redo() {
        drawUpTo = Integer.min(pathList.size, drawUpTo + 1)
        invalidate()
    }

    fun reset() {
        drawUpTo = 0
        pathList.clear()
        brushList.clear()
        textList.clear()
        invalidate()
    }

    fun addText() {
        val layout = getLayout()
        val layoutHeight = layout.height
        val layoutWidth = layout.width
        val cX = (right - left) / 2f
        val cY = (bottom - top) / 2f
        val oX = cX - layoutWidth / 2f
        val oY = cY - layoutHeight / 2f
        textList.add(TextObject(layout, PointF(oX, oY), layoutWidth, layoutHeight, "test text", 1f, resources.displayMetrics.density))
        invalidate()
    }

    fun getBitmap(): Bitmap {
        bitmap?.let {
            drawOnCanvas(Canvas(it), true)
            return it
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    private fun getPaint(): TextPaint {
        val textPaint = TextPaint()
        textPaint.isAntiAlias = true
        textPaint.isFilterBitmap = true
        textPaint.textSize = 24 * resources.displayMetrics.density
        textPaint.color = Color.BLUE
        return textPaint
    }

    private fun getLayout(): StaticLayout {
        val paint = getPaint()
        val length = paint.measureText(("test text")).toInt()
        val builder = StaticLayout.Builder.obtain("test text", 0, 9, paint, length)
        return builder.build()
    }

    private fun getBrush(style: BrushStyle): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.HSVToColor(floatArrayOf(style.hue, style.saturation, style.value))
        paint.strokeWidth = dpToPixels(style.size)
        paint.style = Paint.Style.STROKE

        if (style.type == BrushType.PENCIL) {
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
        } else {
            paint.strokeCap = Paint.Cap.SQUARE
            paint.strokeJoin = Paint.Join.BEVEL
        }

        if (style.type == BrushType.ERASER) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        paint.alpha = (style.alpha * 255).toInt()
        return paint
    }
}
