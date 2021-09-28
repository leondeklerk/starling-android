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
import com.leondeklerk.starling.edit.Side.BOTTOM
import com.leondeklerk.starling.edit.Side.LEFT
import com.leondeklerk.starling.edit.Side.NONE
import com.leondeklerk.starling.edit.Side.RIGHT
import com.leondeklerk.starling.edit.Side.TOP
import com.leondeklerk.starling.extensions.drawCircle
import com.leondeklerk.starling.extensions.drawLine
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Class that takes in a image and provides edit options.
 * Provides scaling, translating and cropping.
 * Makes use of a [CropMoveHandler] to handle all selection box movement.
 * Originally based on https://github.com/jsibbold/zoomage
 */
class EditImageView(context: Context, attributeSet: AttributeSet?) : AppCompatImageView(
    context,
    attributeSet
) {
    // Move handler variables
    private var borderBox: Box? = null
    private val handleBounds = px(16f)
    private var moveHandler: CropMoveHandler? = null
    private var movingBox = false
    private var boxTransHandler = Handler(Looper.getMainLooper())
    private var trans = PointF()
    private var direction = Pair(NONE, NONE)
    private val boxTransRunnable = Runnable { handleAutoTranslation() }

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
    private var doubleTapScalar = 4f
    private val currentScaleFactor: Float
        get() = matrixValues[Matrix.MSCALE_X] / startValues!![Matrix.MSCALE_X]

    // Touch variables
    private val bounds = RectF()
    private var last = PointF(0f, 0f)
    private var allowStartMove = true
    private var movingOther = true
    private var previousPointerCount = 1
    private var currentPointerCount = 0
    private var scaleDetector: ScaleGestureDetector? = null
    private var gestureDetector: GestureDetector? = null
    private var doubleTap = false

    // Zooming
    private var zoomedOut = false
    private var zoomedIn = false
    private var origScale = 1f
    private var zoomLevel = 1f

    // Animation
    private var counter = 0
    private var refreshRate = 60f

    // Listeners
    private val gestureListener: GestureDetector.OnGestureListener = object : SimpleOnGestureListener() {
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            // If we lift for a second time, its a double tap
            if (e.action == MotionEvent.ACTION_UP) {
                doubleTap = true
            }
            return false
        }
    }

    // Scale listener responsible for handling scaling gestures (pinch)
    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Current scale factor
            scaleBy = startScale * detector.scaleFactor / matrixValues[Matrix.MSCALE_X]

            // The raw scale
            val projectedScale = scaleBy * matrixValues[Matrix.MSCALE_X]

            // Make the scaling bounded
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
        private const val MATRIX_DURATION = 200L
        private const val BOX_DURATION = 100L
        private const val NUMBER_OF_LINES = 3
    }

    init {
        // Setup the detectors
        scaleDetector = ScaleGestureDetector(context, scaleListener)
        gestureDetector = GestureDetector(context, gestureListener)
        ScaleGestureDetectorCompat.setQuickScaleEnabled(scaleDetector, false)

        // Calculate refresh rate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            refreshRate = context.display?.refreshRate ?: 60f
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // On detach cancel the handler
        boxTransHandler.removeCallbacksAndMessages(null)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)

        bm?.let {

            initializeValues()

            // Set up listeners
            moveHandler?.onBoundsHitListener = { delta, types ->
                handleSideTranslate(delta, types)
            }

            moveHandler?.onZoomListener = { center, out ->
                handleZoom(center, out)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable && isEnabled) {
            if (scaleType != ScaleType.MATRIX) {
                super.setScaleType(ScaleType.MATRIX)
            }

            // get the current state of the image matrix, its values, and the bounds of the drawn bitmap
            updateMMatrix()

            scaleDetector!!.onTouchEvent(event)
            gestureDetector!!.onTouchEvent(event)

            // If there is a double tap, we only execute that
            if (doubleTap) {
                // Only double tap within the bounds.
                if (!bounds.contains(event.x, event.y)) {
                    doubleTap = false
                    return true
                }
                resetHandler()
                doubleTap = false

                // If we are zoomed in, zoom out
                if (matrixValues[Matrix.MSCALE_X] != startValues!![Matrix.MSCALE_X]) {
                    applyMatrixAnimated(startMatrix)
                } else {
                    val zoomMatrix = Matrix(m)
                    zoomMatrix.postScale(
                        doubleTapScalar,
                        doubleTapScalar,
                        scaleDetector!!.focusX,
                        scaleDetector!!.focusY
                    )

                    applyMatrixAnimated(zoomMatrix)
                }
                return true
            }

            currentPointerCount = event.pointerCount

            // If the pointer count was updated, the focus likely changed
            if (currentPointerCount != previousPointerCount) {
                last = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)
            }

            previousPointerCount = currentPointerCount

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Save the current touch point
                    last = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)
                }
                MotionEvent.ACTION_MOVE -> {
                    // If there is one finger on the screen, we either do a box movement or a outside box translation
                    if (event.pointerCount == 1) {
                        if (!movingBox && allowStartMove && !movingOther) {
                            // Check if we can start a move (touching a handler)
                            moveHandler?.let {
                                movingBox = it.startMove(event.x, event.y)
                            }
                        }

                        // If we have a handler movement
                        if (movingBox) {
                            val changed = moveHandler?.onMove(event.x, event.y) ?: false
                            if (changed) {
                                invalidate()
                            }
                        } else {
                            // we are dealing with an outside box movement

                            // If we came from scaling, don't move and center
                            if (!allowStartMove) {
                                center()
                                return true
                            }

                            // Otherwise we have a one touch dragging movement
                            movingOther = true
                            handleTranslate()
                        }
                    } else {
                        // If we have two fingers, it can only be scaling, cancel all other movement
                        if (movingBox) {
                            // Reset everything
                            moveHandler?.cancel()
                            resetHandler()
                            movingBox = false
                        }

                        // Start a scaling action
                        movingOther = true
                        allowStartMove = false

                        handleScaling()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // Cancel all box related movement
                    if (movingBox) {
                        moveHandler?.endMove()
                        resetHandler()
                        movingBox = false
                    }

                    // End all scaling related movement
                    if (movingOther) {
                        movingOther = false
                        scaleBy = 1f
                        center()
                    }

                    allowStartMove = true
                }
            }

            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas)
        drawGuidelines(canvas)
        drawBorder(canvas)
    }

    /**
     * Set all the initial values of the edit screen.
     * Sets the starting matrix values,
     * and initializes the borderBox and boundaries.
     */
    private fun initializeValues() {
        val values = imageMatrix.values()
        startValues = values
        startMatrix = Matrix(imageMatrix)
        origScale = values[Matrix.MSCALE_X]
        calculatedMinScale = MIN_SCALE * values[Matrix.MSCALE_X]
        calculatedMaxScale = MAX_SCALE * values[Matrix.MSCALE_X]

        val rect = getRect(values)

        // Create the border box
        borderBox = Box(
            PointF(rect.left, rect.top),
            PointF(rect.right, rect.top),
            PointF(rect.right, rect.bottom),
            PointF(rect.left, rect.bottom)
        )

        // Calculate the width/height ratios, minimum of 1
        val heightToWidth = max(1f, height.toFloat() / width.toFloat())
        val widthToHeight = max(1f, width.toFloat() / height.toFloat())

        // Setup the CropMoveHandler
        moveHandler = CropMoveHandler(
            rect, borderBox!!, handleBounds, px(64f), widthToHeight,
            heightToWidth, px(48f), px(4f)
        )
    }

    /**
     * Get the rectangle based on the values of a matrix value array.
     * Takes the translation into account.
     * @param values the values of the matrix
     * @returns a rectangle representing the matrix borders
     */
    private fun getRect(values: FloatArray): RectF {
        val left = values[Matrix.MTRANS_X]
        val top = values[Matrix.MTRANS_Y]
        val right = imgWidth(values) + left
        val bottom = imgHeight(values) + top
        return RectF(left, top, right, bottom)
    }

    /**
     * Getter for the width of the image, scaled to the current scalar
     * @param values the values array to retrieve the scalar from.
     * @return the width
     */
    private fun imgWidth(values: FloatArray = matrixValues): Float {
        return drawable.intrinsicWidth * values[Matrix.MSCALE_X]
    }

    /**
     * Getter for the height of the image, scaled to the current scalar
     * @param values the values array to retrieve the scalar from.
     * @return the height
     */
    private fun imgHeight(values: FloatArray = matrixValues): Float {
        return drawable.intrinsicHeight * values[Matrix.MSCALE_Y]
    }

    /**
     * Handler for starting and updating the side translation.
     * This is a translation where the side of the border box touches the boundaries.
     * Which translates the image in that direction (if the image is zoomed in).
     * @param delta the movement in the x and y axis
     * @param types the types of the x and y direction
     */
    private fun handleSideTranslate(delta: PointF, types: Pair<HandlerType, HandlerType>) {
        // Make sure the type is set correctly
        if (scaleType != ScaleType.MATRIX) {
            super.setScaleType(ScaleType.MATRIX)
        }

        // Set easier names for the pairs
        val (curX, curY) = direction
        val (newX, newY) = (types)

        // Parse HandlerTypes to Sides
        val directionX = newX.parseToSide(newX)
        val directionY = newY.parseToSide(newY)

        // If the direction changed
        if (curX != directionX || curY != directionY) {
            // Set the translation variables and start a new runnable
            trans = delta
            direction = Pair(directionX, directionY)
            boxTransHandler.removeCallbacksAndMessages(null)
            boxTransHandler.postDelayed(boxTransRunnable, 1000L / refreshRate.toLong())
        }
    }

    /**
     * Executed by the boxTransRunnable.
     * Responsible for automatically translating the image in the set directions by the set amounts.
     * Will continue to (auto) translate the image until the edge of the image is at the boundary.
     * This allows the user to hold the box at the boundary and just keep translating.
     */
    private fun handleAutoTranslation() {
        // If no direction is set, there is no movement
        if (direction.first == NONE && direction.second == NONE) return

        updateMMatrix()

        var moveX = 0f
        var moveY = 0f

        // We can only translate if the image is zoomed in.
        if (matrixValues[Matrix.MSCALE_X] > origScale) {
            val matrixX = m.values()[Matrix.MTRANS_X]
            val matrixY = m.values()[Matrix.MTRANS_Y]

            val (dirX, dirY) = direction

            // Handle x movement (LEFT/RIGHT)
            if (imgWidth() > width) {
                if (dirX == LEFT) {
                    moveX = min(trans.x, 0 - matrixX)
                } else if (dirX == RIGHT) {
                    moveX = max(trans.x, -(matrixX - (width - imgWidth())))
                }
            }

            if (imgHeight() > height) {
                // Handle y movement (TOP/BOTTOM)
                if (dirY == TOP) {
                    moveY = min(trans.y, 0 - matrixY)
                } else if (dirY == BOTTOM) {
                    moveY = max(trans.y, -(matrixY - (height - imgHeight())))
                }
            }

            m.postTranslate(moveX, moveY)

            updateImageMatrix()

            // Keep translating based each 1 / refresh rate seconds.
            boxTransHandler.postDelayed(boxTransRunnable, 1000L / refreshRate.toLong())
        }
    }

    /**
     * Update the matrix containing the new matrix values.
     * Also updates the bounds and informs the moveHandler.
     */
    private fun updateMMatrix() {
        // Update all values
        m.set(imageMatrix)
        m.getValues(matrixValues)
        val rect = getRect(matrixValues)

        // Set the image bounds
        bounds.set(rect.left, rect.top, rect.right, rect.bottom)

        if (imgWidth() > width) {
            rect.left = 0f
            rect.right = width.toFloat()
        }

        if (imgHeight() > height) {
            rect.top = 0f
            rect.bottom = height.toFloat()
        }

        // Set the moveHandler bounds (restricted to the view size)
        moveHandler?.updateBounds(rect)
    }

    /**
     * Sets the imageMatrix to the new matrix.
     * Also updates all related variables.
     */
    private fun updateImageMatrix() {
        imageMatrix = m
        updateMMatrix()
    }

    /**
     * Handler for executing zooming in an zooming out based on the size and actions related to the crop box.
     * @param center the center of the box
     * @param out if zoomed out or not (in)
     */
    private fun handleZoom(center: PointF, out: Boolean) {
        // Set the basic variables.
        if (scaleType != ScaleType.MATRIX) {
            super.setScaleType(ScaleType.MATRIX)
        }

        updateMMatrix()

        // Create a new zooming matrix
        var zoomMatrix = Matrix(m)
        var zoomValues = zoomMatrix.values()

        // Update the zoom level
        updateZoomLevel(out)

        if (out) {
            // We can zoom out as long as the MIN_SCALE is not hit
            if (zoomLevel != MIN_SCALE) {
                // halve the level
                zoomLevel /= 2f
                zoomedOut = true
            }
        } else {
            // Can zoom in as long as not at the max
            if (zoomLevel != MAX_SCALE) {
                zoomLevel *= 2f
                zoomedIn = true
            }
        }

        if (zoomedIn || zoomedOut) {
            // Update the handler and calculate the exact scalar to reach the desired level.
            moveHandler?.zoomLevel = zoomLevel
            val scalar = (zoomLevel * origScale) / zoomValues[Matrix.MSCALE_X]

            // Set the matrix values
            zoomMatrix.postScale(scalar, scalar, center.x, center.y)
            zoomValues = zoomMatrix.values()

            // Make sure the image is still properly centered
            val translation = center(zoomValues, getRect(zoomValues), false)
            zoomValues[Matrix.MTRANS_X] = translation.x
            zoomValues[Matrix.MTRANS_Y] = translation.y
            zoomMatrix.setValues(zoomValues)

            if (zoomLevel == 1f) {
                // Reset the zoomMatrix
                zoomMatrix = startMatrix
            }

            applyMatrixAnimated(zoomMatrix)
        }
    }

    /**
     * Updates the current zoom level.
     * The zoom level is set to the nearest power of two,
     * based on ceiling or flooring the log of the current scale.
     * @param ceil ceil the log2 value or not (floor)
     */
    private fun updateZoomLevel(ceil: Boolean) {
        // Calculate the current scale factor (int value between MIN and MAX scale)
        val factor = round(matrixValues[Matrix.MSCALE_X] / origScale)

        // Calculate the log value
        val log = log2(factor)

        // Ceil or floor the log and get the power of 2
        zoomLevel = if (ceil) {
            2f.pow(ceil(log))
        } else {
            2f.pow(floor(log))
        }

        // Update the moveHandler
        moveHandler?.zoomLevel = zoomLevel
    }

    /**
     * Resets the autos translation handler.
     */
    private fun resetHandler() {
        boxTransHandler.removeCallbacksAndMessages(null)
        direction = Pair(NONE, NONE)
        trans = PointF()
    }

    /**
     * Handler for touch image translation.
     * Will calculate the distance the image needs to translate,
     * bounded to the edges of the view.
     * Optionally directly sets the value of the imageMatrix.
     * @param delaySet indicates if setting the value of the imageMatrix should be handled now (true) or not (false)
     */
    private fun handleTranslate(delaySet: Boolean = false) {
        val focus = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)

        // calculate the distance for translation
        val (dX, dY) = getDistance(focus, last)
        m.postTranslate(dX, dY)

        // If delaySet is set to false, update the image matrix
        if (!delaySet) {
            updateImageMatrix()
            last = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)
        }
    }

    /**
     * Get the distance between two points for both axis.
     * The values are bounded by the view bounds.
     * @return a pair containing the delta x and delta
     */
    private fun getDistance(from: PointF, to: PointF): Pair<Float, Float> {
        val dX = getAxisDistance(from.x, to.x, Pair(bounds.left, bounds.right), width)
        val dY = getAxisDistance(from.y, to.y, Pair(bounds.top, bounds.bottom), height)

        return Pair(dX, dY)
    }

    /**
     * Get the distance to translate the image based on a specific axis.
     * @param to the value to move to
     * @param from the value to move from
     * @param edges the edges of the boundary of the image
     * @param dimens the view dimens to restrict to (width/height)
     * @return the translation distance for this distance
     */
    private fun getAxisDistance(to: Float, from: Float, edges: Pair<Float, Float>, dimens: Int): Float {
        // Calculate the raw distance
        var distance = to - from
        val (min, max) = edges

        // If the image is smaller than the bounds (lef/top)
        if (max + distance < 0) {
            distance = -max
        } else if (min + distance > dimens) {
            // If the translation is larger than the bounds (right/bottom
            distance = dimens - min
        }
        return distance
    }

    /**
     * Handle touch scaling of the image.
     * Scale around the focus point with the scalar from the detector.
     * Applies the new matrix (m) to the imageMatrix.
     */
    private fun handleScaling() {
        val focusX = scaleDetector!!.focusX
        val focusY = scaleDetector!!.focusY

        // Scaling can also translate a bit
        handleTranslate(true)

        m.postScale(scaleBy, scaleBy, focusX, focusY)

        // Update all values
        updateImageMatrix()
        updateZoomLevel(true)

        last = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)
    }

    /**
     * This will center both axis of the image.
     * It will either center exactly at the center if the image is smaller than the view.
     * Or it will make sure the edges of the image are always at the edge of the view.
     */
    private fun center(
        values: FloatArray = matrixValues,
        box: RectF = bounds,
        animate: Boolean = true
    ): PointF {
        val x = centerAxis(Pair(imgWidth(values), width), Pair(box.left, box.right)) ?: values[Matrix.MTRANS_X]
        val y = centerAxis(Pair(imgHeight(values), height), Pair(box.top, box.bottom)) ?: values[Matrix.MTRANS_Y]

        if (animate) {
            animateMatrixAxis(Matrix.MTRANS_X, x)
            animateMatrixAxis(Matrix.MTRANS_Y, y)
        }

        return PointF(x, y)
    }

    /**
     * Center an axis, by centering it to the center or anchoring it to the edge of the view.
     * Will only center if the image goes beyond an edge, but should fit within the view.
     * @param imgDimens a pair containing the current image dimension and the view dimension (e.g. imgWidth/Width)
     * @param bounds a pair containing the min and max bounds of an image (top/bottom pair or left/right pair)
     */
    private fun centerAxis(imgDimens: Pair<Float, Int>, bounds: Pair<Float, Float>): Float? {
        val (curDimens, maxDimens) = imgDimens
        val (min, max) = bounds

        // If the image is larger than the view
        if (curDimens > maxDimens) {
            // The min edge is too much to the inside (left/top)
            if (min > 0) {
                return 0f
            } else if (max < maxDimens) {
                // The max edge is too much to the inside (right/bottom)
                return min + maxDimens - max
            }
        } else {
            // We should only center if the image fits in the bounds but goes beyond a bound.
            if (min >= 0 && max <= maxDimens) {
                return null
            }
            // Then we need to center at exactly half
            return (maxDimens - curDimens) / 2
        }
        return null
    }

    //region animation
    /**
     * Apply the targetMatrix to the imageMatrix, by animating the transformation.
     *
     * @param targetMatrix the target matrix to animate to.
     */
    private fun applyMatrixAnimated(targetMatrix: Matrix) {
        // Set all starting variables
        val targetValues = targetMatrix.values()
        val beginMatrix = Matrix(imageMatrix)
        beginMatrix.getValues(matrixValues)

        val xScaleDiff = targetValues[Matrix.MSCALE_X] - matrixValues[Matrix.MSCALE_X]
        val yScaleDiff = targetValues[Matrix.MSCALE_Y] - matrixValues[Matrix.MSCALE_Y]
        val xTransDiff = targetValues[Matrix.MTRANS_X] - matrixValues[Matrix.MTRANS_X]
        val yTransDiff = targetValues[Matrix.MTRANS_Y] - matrixValues[Matrix.MTRANS_Y]

        // set up the animator
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.addUpdateListener(object : AnimatorUpdateListener {
            val activeMatrix = Matrix(imageMatrix)
            val values = FloatArray(9)

            // Update the current image matrix
            override fun onAnimationUpdate(animation: ValueAnimator) {
                val currentValue = animation.animatedValue as Float
                activeMatrix.set(beginMatrix)
                activeMatrix.getValues(values)
                values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + xTransDiff * currentValue
                values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + yTransDiff * currentValue
                values[Matrix.MSCALE_X] = values[Matrix.MSCALE_X] + xScaleDiff * currentValue
                values[Matrix.MSCALE_Y] = values[Matrix.MSCALE_Y] + yScaleDiff * currentValue
                activeMatrix.setValues(values)
                imageMatrix = activeMatrix
            }
        })
        animator.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                imageMatrix = targetMatrix
                updateMMatrix()

                // If the transformation was due to zooming
                if (zoomedIn) {
                    // We can resize the box to be larger
                    moveHandler?.let {
                        val rect = it.scaleBox()
                        resizeBox(rect)
                    }
                    zoomedIn = false
                } else {
                    // Otherwise we need to restrict the border, as we potentially crossed bounds
                    moveHandler?.let {
                        resizeBox(it.restrictBorder())
                    }
                }

                if (zoomedOut) {
                    zoomedOut = false
                }
            }
        })
        animator.duration = MATRIX_DURATION
        animator.start()
    }

    /**
     * Animator responsible for animating a specific translation axis.
     * Will take the current value of the axis and animate it to the desired value.
     * @param axis the axis to animate
     * @param to the result value
     */
    private fun animateMatrixAxis(axis: Int, to: Float) {
        val animator = ValueAnimator.ofFloat(matrixValues[axis], to)
        animator.addUpdateListener(object : AnimatorUpdateListener {
            val values = FloatArray(9)
            var current = Matrix()
            override fun onAnimationUpdate(animation: ValueAnimator) {
                current.set(imageMatrix)
                current.getValues(values)
                values[axis] = animation.animatedValue as Float
                current.setValues(values)
                imageMatrix = current
            }
        })

        animator.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                updateMMatrix()
                counter++

                // If both axis are done animating
                if (counter == 2) {
                    counter = 0

                    // Resize the box.
                    moveHandler?.let {
                        resizeBox(it.restrictBorder())
                    }
                }
            }
        })

        animator.duration = MATRIX_DURATION
        animator.start()
    }

    /**
     * Will animate the resizing of the border box.
     * @param sizeTo the rectangle (box) to animate the borderBox to.
     */
    private fun resizeBox(sizeTo: RectF) {
        borderBox?.let {
            animateBoxSide(LEFT, it.left.x, sizeTo.left)
            animateBoxSide(TOP, it.top.y, sizeTo.top)
            animateBoxSide(RIGHT, it.right.x, sizeTo.right)
            animateBoxSide(BOTTOM, it.bottom.y, sizeTo.bottom)
        }
    }

    /**
     * Animator responsible for animating the side updates of the border box.
     * @param side: the side to animate
     * @param from the original value
     * @param to the new value
     */
    private fun animateBoxSide(side: Side, from: Float, to: Float) {
        val animator = ValueAnimator.ofFloat(from, to)

        animator.addUpdateListener { animation ->
            borderBox?.let {
                when (side) {
                    LEFT -> {
                        it.left.x = animation.animatedValue as Float
                    }
                    TOP -> {
                        it.top.y = animation.animatedValue as Float
                    }
                    RIGHT -> {
                        it.right.x = animation.animatedValue as Float
                    }
                    BOTTOM -> {
                        it.bottom.y = animation.animatedValue as Float
                    }
                    NONE -> {
                    }
                }
                invalidate()
            }
        }

        animator.duration = BOX_DURATION
        animator.start()
    }
    //endregion

    //region drawing
    /**
     * Draw the background around the border box.
     * Draws a darkened overlay between the border, and the image bounds.
     * @param canvas the canvas to draw on
     */
    private fun drawBackground(canvas: Canvas) {
        val borderBox = borderBox ?: return

        // The (simplified) bounds to draw from
        val imageBounds = RectF(
            0f, 0f, width.toFloat(), height.toFloat()
        )

        val paint = Paint().apply {
            this.color = Color.BLACK
            this.alpha = 150
        }

        // Create the 4 background sections
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

        // Draw the rectangles
        canvas.drawRect(leftBackground, paint)
        canvas.drawRect(topBackground, paint)
        canvas.drawRect(rightBackground, paint)
        canvas.drawRect(bottomBackground, paint)
    }

    /**
     * Draws the guidelines in the center of the box.
     * A box contains three equally spaced vertical and horizontal sections.
     * All divided by a small (guide) line.
     * @param canvas the canvas to draw on
     */
    private fun drawGuidelines(canvas: Canvas) {
        val borderBox = borderBox ?: return

        val paint = Paint().apply {
            this.color = Color.LTGRAY
        }
        paint.strokeWidth = px(1f)
        paint.alpha = 140

        // Set the space between each item
        val spaceX = borderBox.width / NUMBER_OF_LINES
        val spaceY = borderBox.height / NUMBER_OF_LINES

        // Draw the lines
        for (i in 1..NUMBER_OF_LINES) {
            val left = borderBox.left.x
            val right = borderBox.right.x
            val top = borderBox.top.y
            val bottom = borderBox.bottom.y

            val offSetX = spaceX * i
            val offSetY = spaceY * i

            canvas.drawLine(left + offSetX, top, left + offSetX, bottom, paint)
            canvas.drawLine(left, top + offSetY, right, top + offSetY, paint)
        }
    }

    /**
     * Draw the border of the box.
     * This draws the sides, the corners and the optional center figure.
     * @param canvas the canvas to draw on.
     */
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

        // Draw the sides
        canvas.drawLine(borderBox.left, whiteAlpha)
        canvas.drawLine(borderBox.top, whiteAlpha)
        canvas.drawLine(borderBox.right, whiteAlpha)
        canvas.drawLine(borderBox.bottom, whiteAlpha)

        // Draw the corners
        canvas.drawCircle(borderBox.leftTop, px(4f), white)
        canvas.drawCircle(borderBox.rightTop, px(4f), white)
        canvas.drawCircle(borderBox.rightBottom, px(4f), white)
        canvas.drawCircle(borderBox.leftBottom, px(4f), white)

        // Draw the center figure
        canvas.drawCircle(borderBox.center, px(4f), white)
    }
    //endregion

    /**
     * Helper function that creates a pixel value from a given DP value.
     * @param value the value in DP.
     * @return the DP value in pixel.
     */
    private fun px(value: Float): Float {
        // Creates pixels from a DP value
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    /**
     * Animator class that implements the animator listener interface.
     */
    private open inner class SimpleAnimatorListener : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    }
}
