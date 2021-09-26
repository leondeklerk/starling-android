package com.leondeklerk.starling.edit

import android.animation.Animator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.values
import androidx.core.view.ScaleGestureDetectorCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

class EditImageView(context: Context, attributeSet: AttributeSet?) : AppCompatImageView(
    context,
    attributeSet
) {
    // Move handler variables
    private var borderBox: Box? = null
    private val handleBounds = px(16f)
    private var moveHandler: CropMoveHandler? = null
    private var moving = false

    // Matrix variables
    private val m = Matrix()
    private var startMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private var startValues: FloatArray? = null

    // Scale variables
    private var calculatedMinScale = MIN_SCALE
    private var calculatedMaxScale = MAX_SCALE
    private var startScale = 1f
    private var scaleBy = 1f
    private var doubleTapToZoomScaleFactor = 4f
    private var isScaling = false

    // Touch variables
    private val bounds = RectF()
    private val last = PointF(0f, 0f)
    private var currentScaleFactor = 1f
    private var previousPointerCount = 1
    private var currentPointerCount = 0
    private var scaleDetector: ScaleGestureDetector? = null
    private var resetAnimator: ValueAnimator? = null
    private var gestureDetector: GestureDetector? = null
    private var doubleTapDetected = false
    private var singleTapDetected = false

    // Zooming
    private var zoomedOut = false
    private var zoomedIn = false
    private var origScale = 1f
    private var zoomLevel = 1f

    // Animation
    private var counter = 0

    // Listeners
    private val gestureListener: GestureDetector.OnGestureListener = object : SimpleOnGestureListener() {
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                doubleTapDetected = true
            }
            singleTapDetected = false
            return false
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            singleTapDetected = true
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            singleTapDetected = false
            return false
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
    }

    private var myHandler = Handler(Looper.getMainLooper())

    private var transX = 0f
    private var transY = 0f
    private var xDir = CropMoveHandler.HandlerType.NONE
    private var yDir = CropMoveHandler.HandlerType.NONE

    private val handlerRunnable = object : Runnable {
        override fun run() {
            if (xDir == CropMoveHandler.HandlerType.NONE && yDir == CropMoveHandler.HandlerType.NONE) return

            m.set(imageMatrix)
            m.getValues(matrixValues)
            updateBounds(matrixValues)

            var moveX = 0f
            var moveY = 0f

            if (matrixValues[Matrix.MSCALE_X] > origScale) {
                val matrixX = m.values()[Matrix.MTRANS_X]
                val matrixY = m.values()[Matrix.MTRANS_Y]

                if (xDir == CropMoveHandler.HandlerType.LEFT) {
                    moveX = min(transX, 0 - matrixX)
                } else if (xDir == CropMoveHandler.HandlerType.RIGHT) {
                    moveX = max(transX, -(matrixX - (width - imgWidth)))
                }

                if (yDir == CropMoveHandler.HandlerType.TOP) {
                    moveY = min(transY, 0 - matrixY)
                } else if (yDir == CropMoveHandler.HandlerType.BOTTOM) {
                    moveY = max(transY, -(matrixY - (height - imgHeight)))
                }

                m.postTranslate(moveX, moveY)

                imageMatrix = m
                m.set(imageMatrix)
                m.getValues(matrixValues)
                updateBounds(matrixValues)

                myHandler.postDelayed(this, 1000L / refreshRate.toLong())
            }
        }
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {

            //calculate value we should scale by, ultimately the scale will be startScale*scaleFactor
            scaleBy = startScale * detector.scaleFactor / matrixValues[Matrix.MSCALE_X]

            //what the scaling should end up at after the transformation
            val projectedScale = scaleBy * matrixValues[Matrix.MSCALE_X]

            //clamp to the min/max if it's going over
            if (projectedScale < calculatedMinScale) {
                scaleBy = calculatedMinScale / matrixValues[Matrix.MSCALE_X]
            } else if (projectedScale > calculatedMaxScale) {
                scaleBy = calculatedMaxScale / matrixValues[Matrix.MSCALE_X]
            }

            return false
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            startScale = matrixValues[Matrix.MSCALE_X]
            isScaling = true
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            scaleBy = 1f
        }
    }

    // Image dimensions
    private val imgWidth: Float
        get() = if (drawable != null) drawable.intrinsicWidth * matrixValues[Matrix.MSCALE_X] else 0f

    private val imgHeight: Float
        get() = if (drawable != null) drawable.intrinsicHeight * matrixValues[Matrix.MSCALE_Y] else 0f

    var refreshRate = 60f

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 8f
        private const val RESET_DURATION = 200
    }

    init {
        scaleDetector = ScaleGestureDetector(context, scaleListener)
        gestureDetector = GestureDetector(context, gestureListener)
        ScaleGestureDetectorCompat.setQuickScaleEnabled(scaleDetector, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            refreshRate = context.display?.refreshRate ?: 60f
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        myHandler.removeCallbacksAndMessages(null)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)

        bm?.let {
            val values = imageMatrix.values()
            origScale = values[Matrix.MSCALE_X]

            setStartValues()

            val left = values[Matrix.MTRANS_X]
            val top = values[Matrix.MTRANS_Y]
            val right = bm.width * values[Matrix.MSCALE_X] + left
            val bottom = bm.height * values[Matrix.MSCALE_Y] + top

            val imageBounds = RectF(left, top, right, bottom)

            borderBox = Box(
                PointF(left, top),
                PointF(right, top),
                PointF(right, bottom),
                PointF(left, bottom)
            )

            val heightToWidth = max(1f, height.toFloat() / width.toFloat())
            val widthToHeight = max(1f, width.toFloat() / height.toFloat())

            moveHandler = CropMoveHandler(
                imageBounds, borderBox!!, handleBounds, px(64f), widthToHeight,
                heightToWidth, px(48f), px(4f)
            )
            // moveHandler?.onBoundsHitListener = { coords, xType, yType ->
            moveHandler?.onBoundsHitListener = { dX, dY, xType, yType ->
                if (scaleType != ScaleType.MATRIX) {
                    super.setScaleType(ScaleType.MATRIX)
                }

                if (xDir != xType || yDir != yType) {
                    transX = dX
                    transY = dY
                    xDir = xType
                    yDir = yType
                    myHandler.removeCallbacksAndMessages(null)
                    myHandler.postDelayed(handlerRunnable, 1000L / refreshRate.toLong())
                }
            }
            moveHandler?.onZoomListener = { center, zoomOut ->

                if (scaleType != ScaleType.MATRIX) {
                    super.setScaleType(ScaleType.MATRIX)
                }

                m.set(imageMatrix)
                m.getValues(matrixValues)
                updateBounds(matrixValues)

                val factor = round(matrixValues[Matrix.MSCALE_X] / origScale)

                if (zoomOut) {
                    zoomLevel = when (factor) {
                        0f -> 1f
                        1f, 2f, 4f, 8f -> factor
                        3f -> 4f
                        5f, 6f, 7f -> 8f
                        else -> 8f
                    }

                    moveHandler?.zoomLevel = zoomLevel

                    if (zoomLevel != MIN_SCALE) {
                        zoomLevel /= 2f
                        moveHandler?.zoomLevel = zoomLevel
                        zoomedOut = true

                        var zoomMatrix = Matrix(m)
                        var zoomValues = zoomMatrix.values()
                        val scalar = (zoomLevel * origScale) / zoomValues[Matrix.MSCALE_X]

                        zoomMatrix.postScale(scalar, scalar, center.x, center.y)
                        zoomValues = zoomMatrix.values()
                        val imWidth = drawable.intrinsicWidth * zoomValues[Matrix.MSCALE_X]

                        val imHeight = drawable.intrinsicHeight * zoomValues[Matrix.MSCALE_Y]

                        val imgLeft = zoomValues[Matrix.MTRANS_X]
                        val imgTop = zoomValues[Matrix.MTRANS_Y]
                        val imgRight = imWidth + zoomValues[Matrix.MTRANS_X]
                        val imgBottom = imHeight + zoomValues[Matrix.MTRANS_Y]

                        val zoomBounds = RectF()

                        zoomBounds.set(imgLeft, imgTop, imgRight, imgBottom)

                        // Center X
                        if (imWidth > width) {
                            //the left edge is too far to the interior
                            if (zoomBounds.left > 0) {
                                zoomValues[Matrix.MTRANS_X] = 0f
                            } else if (zoomBounds.right < width) {
                                zoomValues[Matrix.MTRANS_X] = zoomBounds.left + width - zoomBounds.right
                            }
                        } else {
                            zoomValues[Matrix.MTRANS_X] = (width - imWidth) / 2
                        }

                        // center y
                        if (imHeight > height) {
                            //the top edge is too far to the interior
                            if (zoomBounds.top > 0) {
                                zoomValues[Matrix.MTRANS_Y] = 0f
                            } else if (zoomBounds.bottom < height) {
                                zoomValues[Matrix.MTRANS_Y] = zoomBounds.top + height - zoomBounds.bottom
                            }
                        } else {
                            zoomValues[Matrix.MTRANS_Y] = (height - imHeight) / 2
                        }

                        if (zoomLevel == 1f) {
                            zoomMatrix = startMatrix
                        }

                        zoomMatrix.setValues(zoomValues)

                        animateMatrixTransformation(zoomMatrix)
                    }
                } else {
                    zoomLevel = when (factor) {
                        0f -> 1f
                        1f, 2f, 4f, 8f -> factor
                        3f -> 2f
                        5f, 6f, 7f -> 4f
                        else -> 8f
                    }
                    moveHandler?.zoomLevel = zoomLevel

                    if (zoomLevel != MAX_SCALE) {
                        zoomLevel *= 2f
                        moveHandler?.zoomLevel = zoomLevel
                        zoomedIn = true

                        val zoomMatrix = Matrix(m)
                        val zoomValues = zoomMatrix.values()
                        val scalar = (zoomLevel * origScale) / zoomValues[Matrix.MSCALE_X]

                        zoomMatrix.postScale(scalar, scalar, center.x, center.y)

                        animateMatrixTransformation(zoomMatrix)
                    }
                }
            }

            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas)
        drawGuidelines(canvas)
        drawBorder(canvas)
    }

    var maxPointers = 0
    var noMoveAllowed = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable && isEnabled) {
            if (scaleType != ScaleType.MATRIX) {
                super.setScaleType(ScaleType.MATRIX)
            }

            if (event.pointerCount > maxPointers) {
                maxPointers = event.pointerCount
            }

            if (event.pointerCount != 2 && event.pointerCount < maxPointers) {
                maxPointers = 0
                noMoveAllowed = true
            }

            if (noMoveAllowed && event.action != MotionEvent.ACTION_DOWN) {
                centerX()
                centerY()
                return true
            }

            //get the current state of the image matrix, its values, and the bounds of the drawn bitmap
            m.set(imageMatrix)
            m.getValues(matrixValues)
            updateBounds(matrixValues)

            scaleDetector!!.onTouchEvent(event)
            gestureDetector!!.onTouchEvent(event)

            // handle a double tap
            if (doubleTapDetected) {
                doubleTapDetected = false
                singleTapDetected = false
                if (matrixValues[Matrix.MSCALE_X] != startValues!![Matrix.MSCALE_X]) {
                    animateMatrixTransformation(startMatrix)
                } else {
                    val zoomMatrix = Matrix(m)
                    zoomMatrix.postScale(
                        doubleTapToZoomScaleFactor,
                        doubleTapToZoomScaleFactor,
                        scaleDetector!!.focusX,
                        scaleDetector!!.focusY
                    )

                    animateMatrixTransformation(zoomMatrix)
                }
                return true
            }

            if (event.pointerCount == 1) {
                // This handles the crop Move handler, only valid when there is one pointer
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        noMoveAllowed = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // if we are not moving, check if we can start a movement
                        // Only start a move if we moved at least x
                        if (!moving) {
                            moveHandler?.let {
                                moving = it.startMove(event.x, event.y)
                            }
                        }

                        // If we have a handler movement
                        if (moving) {
                            val changed = moveHandler?.onMove(event.x, event.y) ?: false
                            if (changed) {
                                invalidate()
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        moveHandler?.endMove()
                        moving = false
                    }
                }
            } else {
                // Cancel the move handler and allow for other movement, if applicable
                moveHandler?.cancel()
                moving = false
            }

            // If we are moving with the handler, there is no other action possible.
            if (moving) {
                return true
            }

            currentPointerCount = event.pointerCount

            if (!doubleTapDetected && !singleTapDetected) {
                /* if the event is a down touch, or if the number of touch points changed,
                 * we should reset our start point, as event origins have likely shifted to a
                 * different part of the screen*/
                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    currentPointerCount != previousPointerCount
                ) {
                    last.x = scaleDetector!!.focusX
                    last.y = scaleDetector!!.focusY
                } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val focusX = scaleDetector!!.focusX
                    val focusY = scaleDetector!!.focusY
                    if (currentScaleFactor > 1.0f) {
                        //calculate the distance for translation
                        val xDist = getXDistance(focusX, last.x)
                        val yDist = getYDistance(focusY, last.y)

                        m.postTranslate(xDist, yDist)
                    }

                    m.postScale(scaleBy, scaleBy, focusX, focusY)
                    currentScaleFactor = matrixValues[Matrix.MSCALE_X] / startValues!![Matrix.MSCALE_X]

                    imageMatrix = m
                    m.set(imageMatrix)
                    m.getValues(matrixValues)
                    updateBounds(matrixValues)

                    val factor = round(matrixValues[Matrix.MSCALE_X] / origScale)
                    zoomLevel = when (factor) {
                        0f -> 1f
                        1f, 2f, 4f, 8f -> factor
                        3f -> 4f
                        5f, 6f, 7f -> 8f
                        else -> 8f
                    }
                    moveHandler?.zoomLevel = zoomLevel

                    last[focusX] = focusY
                }
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    scaleBy = 1f
                    centerX()
                    centerY()
                }
            }

            //this tracks whether they have changed the number of fingers down
            previousPointerCount = currentPointerCount
            return true
        }
        // return true
        return super.onTouchEvent(event)
    }

    private fun drawBackground(canvas: Canvas) {
        val borderBox = borderBox ?: return

        val imageBounds = RectF(
            0f, 0f, width.toFloat(), height.toFloat()
        )

        val paint = Paint().apply {
            this.color = Color.BLACK
            this.alpha = 150
        }

        val leftBackground = RectF(imageBounds.left, imageBounds.top, borderBox.left.x, imageBounds.bottom)

        val topBackground = RectF(
            borderBox.left.x,
            imageBounds.top,
            borderBox.right.x,
            borderBox.top.y
        )
        val rightBackground = RectF(borderBox.right.x, imageBounds.top, imageBounds.right, imageBounds.bottom)

        val bottomBackground = RectF(
            borderBox.left.x,
            borderBox.bottom.y,
            borderBox.right.x,
            imageBounds.bottom
        )

        canvas.drawRect(leftBackground, paint)
        canvas.drawRect(topBackground, paint)
        canvas.drawRect(rightBackground, paint)
        canvas.drawRect(bottomBackground, paint)
    }

    private fun drawGuidelines(canvas: Canvas) {
        val borderBox = borderBox ?: return

        val paint = Paint().apply {
            this.color = Color.LTGRAY
        }
        paint.strokeWidth = px(1f)
        paint.alpha = 140

        val thirdWidth = borderBox.width / 3
        val thirdHeight = borderBox.height / 3
        val vert1 = Line(
            PointF(borderBox.left.x + thirdWidth, borderBox.top.y),
            PointF(borderBox.left.x + thirdWidth, borderBox.bottom.y)
        )

        val vert2 = Line(
            PointF(borderBox.left.x + 2 * thirdWidth, borderBox.top.y),
            PointF(borderBox.left.x + 2 * thirdWidth, borderBox.bottom.y)
        )

        val hor1 = Line(
            PointF(borderBox.left.x, borderBox.top.y + thirdHeight),
            PointF(borderBox.right.x, borderBox.top.y + thirdHeight)
        )

        val hor2 = Line(
            PointF(borderBox.left.x, borderBox.top.y + 2 * thirdHeight),
            PointF(borderBox.right.x, borderBox.top.y + 2 * thirdHeight)
        )

        canvas.drawLine(vert1.start.x, vert1.start.y, vert1.end.x, vert1.end.y, paint)
        canvas.drawLine(vert2.start.x, vert2.start.y, vert2.end.x, vert2.end.y, paint)
        canvas.drawLine(hor1.start.x, hor1.start.y, hor1.end.x, hor1.end.y, paint)
        canvas.drawLine(hor2.start.x, hor2.start.y, hor2.end.x, hor2.end.y, paint)
    }

    private fun drawBorder(canvas: Canvas) {
        val borderBox = borderBox ?: return

        val white = Paint().apply {
            this.color = Color.WHITE
            this.strokeWidth = px(2f)
        }

        val whiteAlpha = Paint().apply {
            this.color = Color.LTGRAY
            this.strokeWidth = px(1f)
            this.alpha = 140
        }

        drawLine(canvas, borderBox.leftBottom, borderBox.leftTop, whiteAlpha)
        drawLine(canvas, borderBox.leftTop, borderBox.rightTop, whiteAlpha)
        drawLine(canvas, borderBox.rightTop, borderBox.rightBottom, whiteAlpha)
        drawLine(canvas, borderBox.rightBottom, borderBox.leftBottom, whiteAlpha)

        drawCircle(canvas, borderBox.leftTop, px(4f), white)
        drawCircle(canvas, borderBox.rightTop, px(4f), white)
        drawCircle(canvas, borderBox.rightBottom, px(4f), white)
        drawCircle(canvas, borderBox.leftBottom, px(4f), white)

        drawCircle(canvas, borderBox.center, px(4f), white)
    }

    private fun drawCircle(canvas: Canvas, point: PointF, radius: Float, paint: Paint) {
        canvas.drawCircle(point.x, point.y, radius, paint)
    }

    private fun drawLine(canvas: Canvas, a: PointF, b: PointF, paint: Paint) {
        canvas.drawLine(a.x, a.y, b.x, b.y, paint)
    }

    private fun px(value: Float): Float {
        // Creates pixels from a DP value
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private fun setStartValues() {
        startValues = FloatArray(9)
        startMatrix = Matrix(imageMatrix)
        startMatrix.getValues(startValues)
        calculatedMinScale = MIN_SCALE * startValues!![Matrix.MSCALE_X]
        calculatedMaxScale = MAX_SCALE * startValues!![Matrix.MSCALE_X]
    }

    /**
     * Update the bounds of the displayed image based on the current matrix.
     *
     * @param values the image's current matrix values.
     */
    private fun updateBounds(values: FloatArray) {

        var imgLeft = values[Matrix.MTRANS_X]
        var imgTop = values[Matrix.MTRANS_Y]
        var imgRight = imgWidth + values[Matrix.MTRANS_X]
        var imgBottom = imgHeight + values[Matrix.MTRANS_Y]

        bounds.set(imgLeft, imgTop, imgRight, imgBottom)

        if (imgWidth > width) {
            imgLeft = 0f
            imgRight = width.toFloat()
        }

        if (imgHeight > height) {
            imgTop = 0f
            imgBottom = height.toFloat()
        }

        moveHandler?.updateBounds(RectF(imgLeft, imgTop, imgRight, imgBottom))
    }

    /**
     * Animate the scale and translation of the current matrix to the target
     * matrix.
     *
     * @param targetMatrix the target matrix to animate values to
     */
    private fun animateMatrixTransformation(targetMatrix: Matrix) {
        val targetValues = FloatArray(9)
        targetMatrix.getValues(targetValues)
        val beginMatrix = Matrix(imageMatrix)
        beginMatrix.getValues(matrixValues)

        //difference in current and original values
        val xsDiff = targetValues[Matrix.MSCALE_X] - matrixValues[Matrix.MSCALE_X]
        val ysDiff = targetValues[Matrix.MSCALE_Y] - matrixValues[Matrix.MSCALE_Y]
        val xtDiff = targetValues[Matrix.MTRANS_X] - matrixValues[Matrix.MTRANS_X]
        val ytDiff = targetValues[Matrix.MTRANS_Y] - matrixValues[Matrix.MTRANS_Y]
        resetAnimator = ValueAnimator.ofFloat(0f, 1f)
        resetAnimator?.addUpdateListener(object : AnimatorUpdateListener {
            val activeMatrix = Matrix(imageMatrix)
            val values = FloatArray(9)
            override fun onAnimationUpdate(animation: ValueAnimator) {
                val `val` = animation.animatedValue as Float
                activeMatrix.set(beginMatrix)
                activeMatrix.getValues(values)
                values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + xtDiff * `val`
                values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + ytDiff * `val`
                values[Matrix.MSCALE_X] = values[Matrix.MSCALE_X] + xsDiff * `val`
                values[Matrix.MSCALE_Y] = values[Matrix.MSCALE_Y] + ysDiff * `val`
                activeMatrix.setValues(values)
                imageMatrix = activeMatrix
            }
        })
        resetAnimator?.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                imageMatrix = targetMatrix
                m.set(imageMatrix)
                m.getValues(matrixValues)
                updateBounds(matrixValues)

                if (zoomedIn) {
                    moveHandler?.let {
                        val rect = it.scaleBox()
                        resizeBox(rect)
                    }
                    zoomedIn = false
                }

                moveHandler?.restrictBorder()

                if (zoomedOut) {
                    zoomedOut = false
                }
            }
        })
        resetAnimator?.duration = RESET_DURATION.toLong()
        resetAnimator?.start()
    }

    /**
     * Get the x distance to translate the current image.
     *
     * @param toX   the current x location of touch focus
     * @param fromX the last x location of touch focus
     * @return the distance to move the image,
     */
    private fun getXDistance(toX: Float, fromX: Float): Float {
        var xdistance = toX - fromX

        //prevents image from translating an infinite distance offscreen
        if (bounds.right + xdistance < 0) {
            xdistance = -bounds.right
        } else if (bounds.left + xdistance > width) {
            xdistance = width - bounds.left
        }
        return xdistance
    }

    /**
     * Get the y distance to translate the current image.
     *
     * @param toY   the current y location of touch focus
     * @param fromY the last y location of touch focus
     * @return the distance to move the image,
     */
    private fun getYDistance(toY: Float, fromY: Float): Float {
        var ydistance = toY - fromY

        //prevents image from translating an infinite distance offscreen
        if (bounds.bottom + ydistance < 0) {
            ydistance = -bounds.bottom
        } else if (bounds.top + ydistance > height) {
            ydistance = height - bounds.top
        }
        return ydistance
    }

    private fun centerX() {
        if (imgWidth > width) {
            //the left edge is too far to the interior
            if (bounds.left > 0) {
                animateMatrixIndex(Matrix.MTRANS_X, 0f)
            } else if (bounds.right < width) {
                animateMatrixIndex(Matrix.MTRANS_X, bounds.left + width - bounds.right)
            }
        } else {
            animateMatrixIndex(Matrix.MTRANS_X, (width - imgWidth) / 2)
        }
    }

    private fun centerY() {
        if (imgHeight > height) {
            //the top edge is too far to the interior
            if (bounds.top > 0) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0f)
            } else if (bounds.bottom < height) {
                animateMatrixIndex(Matrix.MTRANS_Y, bounds.top + height - bounds.bottom)
            }
        } else {
            animateMatrixIndex(Matrix.MTRANS_Y, (height - imgHeight) / 2)
        }
    }

    private fun animateMatrixIndex(index: Int, to: Float) {
        val animator = ValueAnimator.ofFloat(matrixValues[index], to)
        animator.addUpdateListener(object : AnimatorUpdateListener {
            val values = FloatArray(9)
            var current = Matrix()
            override fun onAnimationUpdate(animation: ValueAnimator) {
                current.set(imageMatrix)
                current.getValues(values)
                values[index] = animation.animatedValue as Float
                current.setValues(values)
                imageMatrix = current
            }
        })

        animator.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                m.set(imageMatrix)
                m.getValues(matrixValues)
                updateBounds(matrixValues)
                counter++

                if (counter == 2) {
                    counter = 0
                    moveHandler?.restrictBorder()
                }
            }
        })

        animator.duration = RESET_DURATION.toLong()
        animator.start()
    }

    private fun resizeBox(sizeTo: RectF) {
        borderBox?.let {
            animateBoxSide(CropMoveHandler.HandlerType.LEFT, it.left.x, sizeTo.left)
            animateBoxSide(CropMoveHandler.HandlerType.TOP, it.top.y, sizeTo.top)
            animateBoxSide(CropMoveHandler.HandlerType.RIGHT, it.right.x, sizeTo.right)
            animateBoxSide(CropMoveHandler.HandlerType.BOTTOM, it.bottom.y, sizeTo.bottom)
        }
    }

    private fun animateBoxSide(side: CropMoveHandler.HandlerType, from: Float, to: Float) {
        val animator = ValueAnimator.ofFloat(from, to)

        animator.addUpdateListener { animation ->
            when (side) {
                CropMoveHandler.HandlerType.LEFT -> {
                    borderBox?.left?.x = animation.animatedValue as Float
                }
                CropMoveHandler.HandlerType.TOP -> {
                    borderBox?.top?.y = animation.animatedValue as Float
                }
                CropMoveHandler.HandlerType.RIGHT -> {
                    borderBox?.right?.x = animation.animatedValue as Float
                }
                CropMoveHandler.HandlerType.BOTTOM -> {
                    borderBox?.bottom?.y = animation.animatedValue as Float
                }
            }
            invalidate()
        }

        animator.duration = RESET_DURATION.toLong()
        animator.start()
    }

    private open inner class SimpleAnimatorListener : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    }
}