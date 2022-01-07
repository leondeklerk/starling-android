package com.leondeklerk.starling.edit

import android.animation.Animator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.values
import androidx.core.view.ScaleGestureDetectorCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Extended image view that allows scaling and translating.
 * Provides a set of listeners for use with a [CropMoveHandler]
 * in an [EditView].
 * Provides (indirect) support for external zooming and (auto) translating.
 * Will additionally support rotation.
 * Originally based on https://github.com/jsibbold/zoomage
 */
class InteractiveImageView(context: Context, attributeSet: AttributeSet?) : AppCompatImageView(
    context,
    attributeSet
) {
    // Matrix variables
    private val m = Matrix()
    private var startMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    var startValues: FloatArray? = null

    // Scale variables
    private var calculatedMinScale = MIN_SCALE
    private var calculatedMaxScale = MAX_SCALE
    private var startScale = 1f
    private var scaleBy = 1f
    private var doubleTapScalar = 4f

    // Touch variables
    private val bounds = RectF()
    private var last = PointF(0f, 0f)
    private var allowStartMove = true
    private var previousPointerCount = 1
    private var currentPointerCount = 0
    private var scaleDetector: ScaleGestureDetector? = null
    private var gestureDetector: GestureDetector? = null
    private var doubleTap = false
    private var firstSingleMove = true

    var allowTranslation = false

    // Zooming
    private var zoomedOut = false
    private var zoomedIn = false
    private var origScale = 1f
    private var zoomLevel = 1f

    // Animation
    private var counter = 0

    // Listeners
    var onMatrixAppliedListener: (() -> Unit)? = null
    var onAxisCenteredListener: (() -> Unit)? = null
    var onBitmapSetListener: (() -> Unit)? = null
    var onZoomLevelChangeListener: ((level: Float) -> Unit)? = null
    var onZoomedInListener: (() -> Unit)? = null
    var onMMatrixUpdateListener: ((values: FloatArray) -> Unit)? = null

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
        private const val MATRIX_DURATION = 100L
    }

    init {
        // Setup the detectors
        scaleDetector = ScaleGestureDetector(context, scaleListener)
        gestureDetector = GestureDetector(context, gestureListener)
        ScaleGestureDetectorCompat.setQuickScaleEnabled(scaleDetector, false)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)

        bm?.let {
            initializeValues()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable && isEnabled) {
            updateDetectors((event))

            // If there is a double tap, we only execute that
            if (doubleTap) {
                checkDoubleTap(event)
                return true
            }

            updatePointerData(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    onActionDown()
                }
                MotionEvent.ACTION_MOVE -> {
                    // If there is one finger on the screen, we should drag the image
                    if (event.pointerCount == 1) {
                        onSinglePointerMove()
                    } else {
                        onMultiPointerMove()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    onActionUp()
                }
            }

            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Updates the scale and gesture detector with the most up to date motion event.
     * @param event the new motion event to add to the detectors.
     */
    fun updateDetectors(event: MotionEvent) {
        prepareData()
        scaleDetector!!.onTouchEvent(event)
        gestureDetector!!.onTouchEvent(event)
    }

    /**
     * Checks if a double tap action was executed.
     * If so, will call for the double tap to be handled.
     * Will only handle a double tap within the given bounds.
     * @param event the associated last motion event.
     * @param box option parameters to indicate the bounds to check within, defaults to the image bounds.
     * @return if a tap was executed or not.
     */
    fun checkDoubleTap(event: MotionEvent, box: RectF = bounds): Boolean {
        if (doubleTap) {
            // Only double tap within the bounds.
            if (!box.contains(event.x, event.y)) {
                doubleTap = false
                return false
            }

            handleDoubleTap()

            return true
        }
        return false
    }

    /**
     * Indicates that a pointer was placed on the screen.
     * Will update the last pointer location.
     */
    fun onActionDown() {
        // Save the current touch point
        last = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)
    }

    /**
     * Executes the associated actions for a single pointer move.
     * Will translate the image based on the motion.
     * Will only translate if the focus point is within the bounds.
     * @param box optional bounds the touch event needs to be within, default to the image bounds
     */
    fun onSinglePointerMove(box: RectF = bounds) {
        // If we came from scaling, don't move and center
        if (!allowStartMove) {
            center()
        } else {
            // If this is the first move, we have to check if its within bounds
            if (firstSingleMove) {
                if (box.contains(last.x, last.y)) {
                    firstSingleMove = false
                    handleTouchTranslate()
                }
            } else {
                // Otherwise just handle movement
                handleTouchTranslate()
            }
        }
    }

    /**
     * Executes a multi touch movement.
     * Will scale and translate the image based on the motion.
     * Only works if the focus is within the bounds.
     * @param box optional bounds to check for, default to image bounds.
     */
    fun onMultiPointerMove(box: RectF = bounds) {
        // Start a scaling action
        if (box.contains(scaleDetector!!.focusX, scaleDetector!!.focusY)) {
            allowStartMove = false
            handleTouchScaling()
        }
    }

    /**
     * Handler for when the last pointer leaves the screen.
     * Essentially resets all touch related variables and centers the image.
     */
    fun onActionUp() {
        // End all scaling related movement
        scaleBy = 1f
        center()
        allowStartMove = true
        firstSingleMove = true
    }

    /**
     * Helper function to update the data associated with pointers.
     * Will save the current and previous pointer count.
     * Additionally responsible for updating the last focus point,
     * if focus shifted.
     * @param event the motion event to retrieve the pointer count from.
     */
    fun updatePointerData(event: MotionEvent) {
        currentPointerCount = event.pointerCount

        // If the pointer count was updated, the focus likely changed
        if (currentPointerCount != previousPointerCount) {
            last = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)
        }

        previousPointerCount = currentPointerCount
    }

    /**
     * Will translate the image based on the delta x and delta y given.
     * Will only move in the direction given (LEFT/RIGHT and TOP/BOTTOM).
     * Only moves until the border of the image is at the edge of the view.
     * @param direction the x and y direction to move in
     * @param delta the x and y delta to translate with
     * @return if the image moved or not
     */
    fun translateImage(direction: Pair<Side, Side>, delta: PointF): Boolean {
        prepareData()

        var moveX = 0f
        var moveY = 0f

        // We can only translate if the image is zoomed in.
        if (matrixValues[Matrix.MSCALE_X] > origScale) {
            val matrixX = m.values()[Matrix.MTRANS_X]
            val matrixY = m.values()[Matrix.MTRANS_Y]

            val (dirX, dirY) = direction

            // Handle x movement (LEFT/RIGHT)
            if (imgWidth() > width) {
                if (dirX == Side.LEFT) {
                    moveX = min(delta.x, 0 - matrixX)
                } else if (dirX == Side.RIGHT) {
                    moveX = max(delta.x, -(matrixX - (width - imgWidth())))
                }
            }

            // Handle y movement (TOP/BOTTOM)
            if (imgHeight() > height) {
                if (dirY == Side.TOP) {
                    moveY = min(delta.y, 0 - matrixY)
                } else if (dirY == Side.BOTTOM) {
                    moveY = max(delta.y, -(matrixY - (height - imgHeight())))
                }
            }

            m.postTranslate(moveX, moveY)

            updateImageMatrix()

            // Indicate if we moved or not
            return moveX != 0f || moveY != 0f
        }
        return false
    }

    /**
     * Handler for executing zooming in an zooming out based on the size and actions related to the crop box.
     * @param center the center of the box
     * @param out if zoomed out or not (in)
     */
    fun zoomImage(center: PointF, out: Boolean) {
        prepareData()

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
            // Update the zoom level and calculate the exact scalar to reach the desired level.
            onZoomLevelChangeListener?.invoke(zoomLevel)
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
     * Calls the onZoomLevelChangedListener with the (new) zoomLevel
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

        // Update the zoom level
        onZoomLevelChangeListener?.invoke(zoomLevel)
    }

    /**
     * Set all the initial values of the edit screen.
     * Sets the starting matrix values.
     * Invokes the onBitmapSetListener.
     */
    private fun initializeValues() {
        val values = imageMatrix.values()
        startValues = values
        startMatrix = Matrix(imageMatrix)
        origScale = values[Matrix.MSCALE_X]
        calculatedMinScale = MIN_SCALE * values[Matrix.MSCALE_X]
        calculatedMaxScale = MAX_SCALE * values[Matrix.MSCALE_X]
        updateMMatrix()

        onBitmapSetListener?.invoke()
    }

    /**
     * Update the matrix containing the new matrix values.
     * Also updates the bounds and invokes the onMMatrixUpdateListener.
     *
     */
    private fun updateMMatrix() {
        // Update all values
        m.set(imageMatrix)
        m.getValues(matrixValues)
        val rect = getRect(matrixValues)

        // Set the image bounds
        bounds.set(rect.left, rect.top, rect.right, rect.bottom)

        onMMatrixUpdateListener?.invoke(matrixValues)
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
    fun imgWidth(values: FloatArray = matrixValues): Float {
        return drawable.intrinsicWidth * values[Matrix.MSCALE_X]
    }

    /**
     * Getter for the height of the image, scaled to the current scalar
     * @param values the values array to retrieve the scalar from.
     * @return the height
     */
    fun imgHeight(values: FloatArray = matrixValues): Float {
        return drawable.intrinsicHeight * values[Matrix.MSCALE_Y]
    }

    /**
     * Helper function that makes sure that the correct scale type is set.
     * Additionally makes sure the manipulation matrix m is up to date.
     */
    private fun prepareData() {
        if (scaleType != ScaleType.MATRIX) {
            super.setScaleType(ScaleType.MATRIX)
        }

        updateMMatrix()
    }

    /**
     * Handler for a double tap action.
     * Will scale the image to the double tap scaling value,
     * and animate the transition.
     * If already zoomed in, will reset to the starting values.
     */
    private fun handleDoubleTap() {
        doubleTap = false

        // If we are zoomed in, zoom out
        if (matrixValues[Matrix.MSCALE_X] != startValues!![Matrix.MSCALE_X]) {
            applyMatrixAnimated(startMatrix)
        } else {
            val zoomMatrix = Matrix(m)

            // Scale with an offset based on the margin.
            zoomMatrix.postScale(
                doubleTapScalar,
                doubleTapScalar,
                -marginLeft + scaleDetector!!.focusX,
                -marginTop + scaleDetector!!.focusY
            )

            val zoomValues = zoomMatrix.values()

            // Make sure the image is still properly centered
            val translation = center(zoomValues, getRect(zoomValues), false)
            zoomValues[Matrix.MTRANS_X] = translation.x
            zoomValues[Matrix.MTRANS_Y] = translation.y

            zoomMatrix.setValues(zoomValues)

            applyMatrixAnimated(zoomMatrix)
        }
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
     * Handler for touch image translation.
     * Will calculate the distance the image needs to translate,
     * bounded to the edges of the view.
     * Optionally directly sets the value of the imageMatrix.
     * @param delaySet indicates if setting the value of the imageMatrix should be handled now (true) or not (false)
     */
    private fun handleTouchTranslate(delaySet: Boolean = false) {
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
    private fun handleTouchScaling() {
        val focusX = scaleDetector!!.focusX
        val focusY = scaleDetector!!.focusY

        // Scaling can also translate a bit
        handleTouchTranslate(true)

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
            if (min > 0f) {
                return 0f
            } else if (max < maxDimens) {
                // The max edge is too much to the inside (right/bottom)
                return min + maxDimens - max
            }
        } else {
            // We should only center if the image fits in the bounds but goes beyond a bound.
            if (allowTranslation && min >= 0f && max <= maxDimens) {
                return null
            }
            // Then we need to center at exactly half
            return (maxDimens - curDimens) / 2
        }
        return null
    }

    /**
     * Apply the targetMatrix to the imageMatrix, by animating the transformation.
     * Will either call the zoomed in listener on finish or the matrix applied listener.
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

                if (zoomedIn) {
                    // We can resize the box to be larger
                    onZoomedInListener?.invoke()
                    zoomedIn = false
                } else {
                    // Update the zoomLevel (double tap case mostly)
                    updateZoomLevel(true)

                    // If set call the on applied listener
                    onMatrixAppliedListener?.invoke()
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
     * Calls the onAxisCenteredListener on finish
     * @param axis the axis to animate
     * @param to the result value
     */
    private fun animateMatrixAxis(axis: Int, to: Float) {
        val animator = ValueAnimator.ofFloat(matrixValues[axis], to)
        animator.interpolator = AccelerateDecelerateInterpolator()
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
                    onAxisCenteredListener?.invoke()
                }
            }
        })

        animator.duration = MATRIX_DURATION
        animator.start()
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
