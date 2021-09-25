package com.leondeklerk.starling.edit

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import androidx.core.view.ScaleGestureDetectorCompat

class CroppingOverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var viewBounds: RectF? = null
    private var imageBounds: RectF? = null
    private val handleBounds = px(16f)
    private var borderBox: Box? = null
    private var moveHandler: CropMoveHandler? = null
    var zoomListener: ((zoomLevel: Float, center: PointF) -> Unit)? = null
    var onMatrixChange: ((matrix: Matrix) -> Unit)? = null

    private val m = Matrix()
    private var startMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private var startValues: FloatArray? = null

    private var imageMatrix: Matrix? = null
    private var drawable: Drawable? = null

    //the adjusted scale bounds that account for an image's starting scale values
    private var calculatedMinScale = MIN_SCALE
    private var calculatedMaxScale = MAX_SCALE
    private val imgViewBounds = RectF()
    private var imgViewHeight = 0f
    private var imgViewWidth = 0f

    private var doubleTapToZoomScaleFactor = 4f
    private val last = PointF(0f, 0f)
    private var startScale = 1f
    private var scaleBy = 1f

    private var currentScaleFactor = 1f
    private var previousPointerCount = 1
    private var currentPointerCount = 0
    private var scaleDetector: ScaleGestureDetector? = null
    private var resetAnimator: ValueAnimator? = null
    private var gestureDetector: GestureDetector? = null
    private var doubleTapDetected = false
    private var singleTapDetected = false

    private val isAnimating: Boolean
        get() = resetAnimator != null && resetAnimator!!.isRunning

    private val gestureListener: GestureDetector.OnGestureListener =
        object : GestureDetector.SimpleOnGestureListener() {
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
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaleBy = 1f
        }
    }

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 8f
        private const val RESET_DURATION = 200
    }

    init {
        scaleDetector = ScaleGestureDetector(context, scaleListener)
        gestureDetector = GestureDetector(context, gestureListener)
        ScaleGestureDetectorCompat.setQuickScaleEnabled(scaleDetector, false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas)
        drawGuidelines(canvas)
        drawBorder(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable && isEnabled) {
            if (startValues == null) {
                setStartValues()
            }
            currentPointerCount = event.pointerCount

            //get the current state of the image matrix, its values, and the bounds of the drawn bitmap
            m.set(imageMatrix)
            m.getValues(matrixValues)
            updateBounds(matrixValues)
            scaleDetector!!.onTouchEvent(event)
            gestureDetector!!.onTouchEvent(event)
            if (doubleTapDetected) {
                doubleTapDetected = false
                singleTapDetected = false
                if (matrixValues[Matrix.MSCALE_X] != startValues!![Matrix.MSCALE_X]) {
                    reset()
                } else {
                    val zoomMatrix = Matrix(m)
                    zoomMatrix.postScale(
                        doubleTapToZoomScaleFactor,
                        doubleTapToZoomScaleFactor,
                        scaleDetector!!.focusX,
                        scaleDetector!!.focusY
                    )
                    animateScaleAndTranslationToMatrix(zoomMatrix)
                }
            } else if (!singleTapDetected) {
                /* if the event is a down touch, or if the number of touch points changed,
                 * we should reset our start point, as event origins have likely shifted to a
                 * different part of the screen*/
                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    currentPointerCount != previousPointerCount
                ) {
                    last[scaleDetector!!.focusX] = scaleDetector!!.focusY
                } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val focusx = scaleDetector!!.focusX
                    val focusy = scaleDetector!!.focusY
                    if (allowTranslate(event)) {
                        //calculate the distance for translation
                        val xdistance = getXDistance(focusx, last.x)
                        val ydistance = getYDistance(focusy, last.y)
                        m.postTranslate(xdistance, ydistance)
                    }

                    m.postScale(scaleBy, scaleBy, focusx, focusy)
                    currentScaleFactor = matrixValues[Matrix.MSCALE_X] / startValues!![Matrix.MSCALE_X]

                    imageMatrix = m
                    onMatrixChange?.invoke(imageMatrix!!)
                    last[focusx] = focusy
                }
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    scaleBy = 1f
                    resetImage()
                }
            }
            parent.requestDisallowInterceptTouchEvent(disallowParentTouch(event))

            //this tracks whether they have changed the number of fingers down
            previousPointerCount = currentPointerCount
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                moveHandler?.startMove(event.x, event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moveHandler?.endMove()
            }
            MotionEvent.ACTION_MOVE -> {
                val changed = moveHandler?.onMove(event.x, event.y) ?: false
                if (changed) {
                    invalidate()
                }
            }
        }
        return true
    }

    private fun disallowParentTouch(event: MotionEvent?): Boolean {
        return currentPointerCount > 1 || currentScaleFactor > 1.0f || isAnimating
    }

    private fun allowTranslate(event: MotionEvent?): Boolean {
        return currentScaleFactor > 1.0f
    }

    /**
     * Reset the image based on the specified [] mode.
     */
    private fun resetImage() {
        center()
    }

    /**
     * This helps to keep the image on-screen by animating the translation to the nearest
     * edge, both vertically and horizontally.
     */
    private fun center() {
        animateTranslationX()
        animateTranslationY()
    }
    /**
     * Reset image back to its starting size. If `animate` is false, image
     * will snap back to its original size.
     *
     * @param animate animate the image back to its starting size
     */
    /**
     * Reset image back to its original size. Will snap back to original size
     * if animation on reset is disabled via [.setAnimateOnReset].
     */
    @JvmOverloads
    fun reset(animate: Boolean = true) {
        if (animate) {
            animateToStartMatrix()
        } else {
            imageMatrix = startMatrix
            onMatrixChange?.invoke(imageMatrix!!)
        }
    }

    /**
     * Animate the matrix back to its original position after the user stopped interacting with it.
     */
    private fun animateToStartMatrix() {
        animateScaleAndTranslationToMatrix(startMatrix)
    }

    /**
     * Animate the scale and translation of the current matrix to the target
     * matrix.
     *
     * @param targetMatrix the target matrix to animate values to
     */
    private fun animateScaleAndTranslationToMatrix(targetMatrix: Matrix) {
        val targetValues = FloatArray(9)
        targetMatrix.getValues(targetValues)
        val beginMatrix = Matrix(imageMatrix)
        beginMatrix.getValues(matrixValues)

        //difference in current and original values
        val xsdiff = targetValues[Matrix.MSCALE_X] - matrixValues[Matrix.MSCALE_X]
        val ysdiff = targetValues[Matrix.MSCALE_Y] - matrixValues[Matrix.MSCALE_Y]
        val xtdiff = targetValues[Matrix.MTRANS_X] - matrixValues[Matrix.MTRANS_X]
        val ytdiff = targetValues[Matrix.MTRANS_Y] - matrixValues[Matrix.MTRANS_Y]
        resetAnimator = ValueAnimator.ofFloat(0f, 1f)
        resetAnimator?.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            val activeMatrix = Matrix(imageMatrix)
            val values = FloatArray(9)
            override fun onAnimationUpdate(animation: ValueAnimator) {
                val `val` = animation.animatedValue as Float
                activeMatrix.set(beginMatrix)
                activeMatrix.getValues(values)
                values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + xtdiff * `val`
                values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + ytdiff * `val`
                values[Matrix.MSCALE_X] = values[Matrix.MSCALE_X] + xsdiff * `val`
                values[Matrix.MSCALE_Y] = values[Matrix.MSCALE_Y] + ysdiff * `val`
                activeMatrix.setValues(values)
                imageMatrix = activeMatrix
                onMatrixChange?.invoke(imageMatrix!!)
            }
        })
        resetAnimator?.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                imageMatrix = targetMatrix
                onMatrixChange?.invoke(imageMatrix!!)
            }
        })
        resetAnimator?.duration = RESET_DURATION.toLong()
        resetAnimator?.start()
    }

    private fun animateTranslationX() {
        if (currentDisplayedWidth > imgViewWidth) {
            //the left edge is too far to the interior
            if (imgViewBounds.left > 0) {
                animateMatrixIndex(Matrix.MTRANS_X, 0f)
            } else if (imgViewBounds.right < imgViewWidth) {
                animateMatrixIndex(Matrix.MTRANS_X, imgViewBounds.left + imgViewWidth - imgViewBounds.right)
            }
        } else {
            //left edge needs to be pulled in, and should be considered before the right edge
            if (imgViewBounds.left < 0) {
                animateMatrixIndex(Matrix.MTRANS_X, 0f)
            } else if (imgViewBounds.right > imgViewWidth) {
                animateMatrixIndex(Matrix.MTRANS_X, imgViewBounds.left + imgViewWidth - imgViewBounds.right)
            }
        }
    }

    private fun animateTranslationY() {
        if (currentDisplayedHeight > imgViewHeight) {
            //the top edge is too far to the interior
            if (imgViewBounds.top > 0) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0f)
            } else if (imgViewBounds.bottom < imgViewHeight) {
                animateMatrixIndex(Matrix.MTRANS_Y, imgViewBounds.top + imgViewHeight - imgViewBounds.bottom)
            }
        } else {
            //top needs to be pulled in, and needs to be considered before the bottom edge
            if (imgViewBounds.top < 0) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0f)
            } else if (imgViewBounds.bottom > imgViewHeight) {
                animateMatrixIndex(Matrix.MTRANS_Y, imgViewBounds.top + imgViewHeight - imgViewBounds.bottom)
            }
        }
    }

    private fun animateMatrixIndex(index: Int, to: Float) {
        val animator = ValueAnimator.ofFloat(matrixValues[index], to)
        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            val values = FloatArray(9)
            var current = Matrix()
            override fun onAnimationUpdate(animation: ValueAnimator) {
                current.set(imageMatrix)
                current.getValues(values)
                values[index] = animation.animatedValue as Float
                current.setValues(values)
                imageMatrix = current
                onMatrixChange?.invoke(imageMatrix!!)
            }
        })
        animator.duration = RESET_DURATION.toLong()
        animator.start()
    }

    /**
     * Get the x distance to translate the current image.
     *
     * @param toX   the current x location of touch focus
     * @param fromX the last x location of touch focus
     * @return the distance to move the image,
     * will restrict the translation to keep the image on screen.
     */
    private fun getXDistance(toX: Float, fromX: Float): Float {
        var xdistance = toX - fromX

        // xdistance = getRestrictedXDistance(xdistance)

        //prevents image from translating an infinite distance offscreen
        if (imgViewBounds.right + xdistance < 0) {
            xdistance = -imgViewBounds.right
        } else if (imgViewBounds.left + xdistance > imgViewWidth) {
            xdistance = imgViewWidth - imgViewBounds.left
        }

        return xdistance
    }

    /**
     * Get the horizontal distance to translate the current image, but restrict
     * it to the outer bounds of the [ImageView]. If the current
     * image is smaller than the bounds, keep it within the current bounds.
     * If it is larger, prevent its edges from translating farther inward
     * from the outer edge.
     *
     * @param xDist the current desired horizontal distance to translate
     * @return the actual horizontal distance to translate with bounds restrictions
     */
    private fun getRestrictedXDistance(xDist: Float): Float {
        var restrictedXDistance = xDist
        if (currentDisplayedWidth >= imgViewWidth) {
            if (imgViewBounds.left <= 0 && imgViewBounds.left + xDist > 0 && !scaleDetector!!.isInProgress) {
                restrictedXDistance = -imgViewBounds.left
            } else if (imgViewBounds.right >= imgViewWidth && imgViewBounds.right + xDist < imgViewWidth && !scaleDetector!!.isInProgress) {
                restrictedXDistance = imgViewWidth - imgViewBounds.right
            }
        } else if (!scaleDetector!!.isInProgress) {
            if (imgViewBounds.left >= 0 && imgViewBounds.left + xDist < 0) {
                restrictedXDistance = -imgViewBounds.left
            } else if (imgViewBounds.right <= imgViewWidth && imgViewBounds.right + xDist > imgViewWidth) {
                restrictedXDistance = imgViewWidth - imgViewBounds.right
            }
        }
        return restrictedXDistance
    }

    /**
     * Get the y distance to translate the current image.
     *
     * @param toY   the current y location of touch focus
     * @param fromY the last y location of touch focus
     * @return the distance to move the image,
     * will restrict the translation to keep the image on screen.
     */
    private fun getYDistance(toY: Float, fromY: Float): Float {
        var ydistance = toY - fromY

        // ydistance = getRestrictedYDistance(ydistance)

        //prevents image from translating an infinite distance offscreen
        if (imgViewBounds.bottom + ydistance < 0) {
            ydistance = -imgViewBounds.bottom
        } else if (imgViewBounds.top + ydistance > imgViewHeight) {
            ydistance = imgViewHeight - imgViewBounds.top
        }
        return ydistance
    }

    /**
     * Get the vertical distance to translate the current image, but restrict
     * it to the outer bounds of the [ImageView]. If the current
     * image is smaller than the bounds, keep it within the current bounds.
     * If it is larger, prevent its edges from translating farther inward
     * from the outer edge.
     *
     * @param yDist the current desired vertical distance to translate
     * @return the actual vertical distance to translate with bounds restrictions
     */
    private fun getRestrictedYDistance(yDist: Float): Float {
        var restrictedYDistance = yDist
        if (currentDisplayedHeight >= imgViewHeight) {
            if (imgViewBounds.top <= 0 && imgViewBounds.top + yDist > 0 && !scaleDetector!!.isInProgress) {
                restrictedYDistance = -imgViewBounds.top
            } else if (imgViewBounds.bottom >= imgViewHeight && imgViewBounds.bottom + yDist < imgViewHeight && !scaleDetector!!.isInProgress) {
                restrictedYDistance = imgViewHeight - imgViewBounds.bottom
            }
        } else if (!scaleDetector!!.isInProgress) {
            if (imgViewBounds.top >= 0 && imgViewBounds.top + yDist < 0) {
                restrictedYDistance = -imgViewBounds.top
            } else if (imgViewBounds.bottom <= imgViewHeight && imgViewBounds.bottom + yDist > imgViewHeight) {
                restrictedYDistance = imgViewHeight - imgViewBounds.bottom
            }
        }
        return restrictedYDistance
    }

    private open inner class SimpleAnimatorListener : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    }

    /**
     * Update the bounds of the displayed image based on the current matrix.
     *
     * @param values the image's current matrix values.
     */
    private fun updateBounds(values: FloatArray) {
        if (drawable != null) {
            imgViewBounds[values[Matrix.MTRANS_X], values[Matrix.MTRANS_Y], drawable!!.intrinsicWidth * values[Matrix
                .MSCALE_X] + values[Matrix.MTRANS_X]] =
                drawable!!.intrinsicHeight * values[Matrix.MSCALE_Y] + values[Matrix.MTRANS_Y]
        }
    }

    /**
     * Get the width of the displayed image.
     *
     * @return the current width of the image as displayed (not the width of the [ImageView] itself.
     */
    private val currentDisplayedWidth: Float
        get() = drawable!!.intrinsicWidth * matrixValues[Matrix.MSCALE_X]

    /**
     * Get the height of the displayed image.
     *
     * @return the current height of the image as displayed (not the height of the [ImageView] itself.
     */
    private val currentDisplayedHeight: Float
        get() = if (drawable != null) drawable!!.intrinsicHeight * matrixValues[Matrix.MSCALE_Y] else 0f

    /**
     * Remember our starting values so we can animate our image back to its original position.
     */
    private fun setStartValues() {
        startValues = FloatArray(9)
        startMatrix = Matrix(imageMatrix)
        startMatrix.getValues(startValues)
        calculatedMinScale = MIN_SCALE * startValues!![Matrix.MSCALE_X]
        calculatedMaxScale = MAX_SCALE * startValues!![Matrix.MSCALE_X]
    }

    fun setDrawable(drawable: Drawable) {
        this.drawable = drawable
    }

    /**
     * NON matrix stuff
     */

    fun setData(
        viewBounds: RectF, xInset: Float, yInset: Float, imageMatrix: Matrix, drawable: Drawable, width:
        Float, height: Float
    ) {
        this.viewBounds = viewBounds

        this.imageMatrix = imageMatrix
        this.drawable = drawable
        this.imgViewWidth = width
        this.imgViewHeight = height

        imageBounds = RectF(
            viewBounds.left + xInset,
            viewBounds.top + yInset,
            viewBounds.right - xInset,
            viewBounds.bottom - yInset
        )

        borderBox = Box(
            PointF(imageBounds!!.left, imageBounds!!.top),
            PointF(imageBounds!!.right, imageBounds!!.top),
            PointF(imageBounds!!.right, imageBounds!!.bottom),
            PointF(imageBounds!!.left, imageBounds!!.bottom)
        )

        // moveHandler = CropMoveHandler(imageBounds!!, borderBox!!, handleBounds, px(64f))

        invalidate()
    }

    private fun drawBackground(canvas: Canvas) {
        val bounds = imageBounds ?: return
        val borderBox = borderBox ?: return

        val paint = Paint().apply {
            this.color = Color.BLACK
            this.alpha = 150
        }

        val leftBackground = RectF(bounds.left, bounds.top, borderBox.left.x, bounds.bottom)

        val topBackground = RectF(
            borderBox.left.x,
            bounds.top,
            borderBox.right.x,
            borderBox.top.y
        )
        val rightBackground = RectF(borderBox.right.x, bounds.top, bounds.right, bounds.bottom)

        val bottomBackground = RectF(
            borderBox.left.x,
            borderBox.bottom.y,
            borderBox.right.x, bounds.bottom
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
}