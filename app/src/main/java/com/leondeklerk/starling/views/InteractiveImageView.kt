package com.leondeklerk.starling.views

import android.animation.Animator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
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
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.values
import androidx.core.view.ScaleGestureDetectorCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.leondeklerk.starling.views.enums.Side
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/**
 * Extended image view that allows scaling, rotation, and translating.
 * Provides a set of listeners which can be used to create composite screens
 * Provides (indirect) support for external zooming and (auto) translating.
 */
class InteractiveImageView(context: Context, attributeSet: AttributeSet?) : AppCompatImageView(
    context,
    attributeSet
) {
    // Matrix variables
    private var startMatrix = Matrix()

    // Scale variables
    private var calculatedMinScale = MIN_SCALE
    private var calculatedMaxScale = MAX_SCALE

    // Original start scale
    private var startScale = 1f
    private var scaleBy = 1f
    private var doubleTapScalar = 4f
    private var startScalar = 1f

    // The current scaling applied to the image
    var currentScale = 1f

    // The default scale relative to the current rotation.
    var baseScale = 1f

    // Rotation
    var rotated = 0f

    // Touch variables
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
    private var zoomLevel = 1f

    // Animation
    private var resetting = false

    // Listeners
    var onBitmapSetListener: ((bitmap: Bitmap) -> Unit)? = null
    var onZoomLevelChangeListener: ((level: Float) -> Unit)? = null
    var onZoomedInListener: (() -> Unit)? = null
    var onTapListener: (() -> Unit)? = null
    var onResetListener: (() -> Unit)? = null
    var onImageUpdate: (() -> Unit)? = null
    var onTouchHandler: ((event: MotionEvent) -> Boolean)? = null
    var onRequireLock: ((require: Boolean) -> Unit)? = null

    private val gestureListener: GestureDetector.OnGestureListener = object : SimpleOnGestureListener() {
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            // If we lift for a second time, its a double tap
            if (e.action == MotionEvent.ACTION_UP) {
                doubleTap = true
            }
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            onTapListener?.invoke()
            return super.onSingleTapConfirmed(e)
        }
    }

    // Scale listener responsible for handling scaling gestures (pinch)
    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Current scale factor
            scaleBy = startScalar * detector.scaleFactor / currentScale

            // The raw scale
            val projectedScale = scaleBy * currentScale

            // Make the scaling bounded
            if (projectedScale < calculatedMinScale) {
                scaleBy = calculatedMinScale / currentScale
            } else if (projectedScale > calculatedMaxScale) {
                scaleBy = calculatedMaxScale / currentScale
            }

            return false
        }

        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            startScalar = currentScale
            return super.onScaleBegin(detector)
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
        ScaleGestureDetectorCompat.setQuickScaleEnabled(scaleDetector!!, false)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        // Make sure the initial load is correct
        scaleType = ScaleType.FIT_CENTER

        super.setImageBitmap(bm)

        bm?.let {
            initializeValues()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable && isEnabled) {

            // If a touch handler is set, override local touch.
            onTouchHandler?.let { handler ->
                return handler.invoke(event)
            }

            updateDetectors((event))

            // Get the current image bounding box.
            val boundingBox = getRect(imageMatrix)

            // If there is a double tap, we only execute that
            if (doubleTap) {
                checkDoubleTap(event, boundingBox)
                onRequireLock?.invoke(startScale != currentScale)
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
                        return onSinglePointerMove(boundingBox)
                    } else {
                        onMultiPointerMove(boundingBox)
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
        checkMatrixType()
        scaleDetector!!.onTouchEvent(event)
        gestureDetector!!.onTouchEvent(event)
    }

    /**
     * Checks if a double tap action was executed.
     * If so, will call for the double tap to be handled.
     * Will only handle a double tap if the image itself was tapped.
     * @param event the associated last motion event.
     * @param boundingBox the bounding box of the image.
     * @return if a tap was executed or not.
     */
    fun checkDoubleTap(event: MotionEvent, boundingBox: RectF): Boolean {
        if (doubleTap) {
            // Only double tap within the bounds.
            if (!boundingBox.contains(event.x, event.y)) {
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
     * Will only translate if the start focus point is within the image bounds.
     * @param boundingBox: the bounding box of the current image.
     * @return if the move was executed or not
     */
    fun onSinglePointerMove(boundingBox: RectF): Boolean {
        if (!allowTranslation && currentScale == startScale) {
            return false
        } else {
            onRequireLock?.invoke(true)
        }

        // If we came from scaling, don't move and center
        if (!allowStartMove) {
            center(imageMatrix, true)
        } else {
            // If this is the first move, we have to check if its within bounds
            if (firstSingleMove) {
                if (boundingBox.contains(last.x, last.y)) {
                    firstSingleMove = false
                    handleTouchTranslate()
                }
            } else {
                // Otherwise just handle movement
                handleTouchTranslate()
            }
        }

        return true
    }

    /**
     * Executes a multi touch movement.
     * Will scale and translate the image based on the motion.
     * Only works if the focus is within the current image bounds.
     * @param boundingBox the current image bounding box.
     */
    fun onMultiPointerMove(boundingBox: RectF) {
        // Start a scaling action
        if (boundingBox.contains(scaleDetector!!.focusX, scaleDetector!!.focusY)) {
            onRequireLock?.invoke(true)
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
        center(imageMatrix, true)
        allowStartMove = true
        firstSingleMove = true
        onRequireLock?.invoke(startScale != currentScale)
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
        val modifyMatrix = getModifyMatrix()

        var moveX = 0f
        var moveY = 0f

        // We can only translate if the image is zoomed in.
        if (currentScale > baseScale) {
            val rect = getRect(modifyMatrix)
            val matrixX = rect.left
            val matrixY = rect.top

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

            // Create the matrix and apply it
            modifyMatrix.postTranslate(moveX, moveY)
            applyMatrix(modifyMatrix, false)

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
        var modifyMatrix = getModifyMatrix()

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
            val scalar = (zoomLevel * baseScale) / currentScale

            // Set the matrix values
            modifyMatrix.postScale(scalar, scalar, center.x, center.y)
            currentScale *= scalar

            // Make sure the image is still properly centered
            val translation = center(modifyMatrix, false)
            modifyMatrix.postTranslate(translation.x, translation.y)

            if (zoomLevel == 1f) {
                // Reset the image to the base matrix
                modifyMatrix = getBaseMatrix()
            }

            applyMatrix(modifyMatrix, true)
        }
    }

    /**
     * Returns the startMatrix relative to the current rotation.
     * Applies the current rotation to the startMatrix and makes sure the baseScale it is scaled to the correct baseScale.
     * @returns the base matrix for this rotation.
     */
    private fun getBaseMatrix(): Matrix {
        val baseMatrix = Matrix(startMatrix)
        // Scale to the base scale for this rotation
        val scaleBy = baseScale / startScale
        baseMatrix.postScale(scaleBy, scaleBy, width / 2f, height / 2f)
        // Rotate with the current rotation
        baseMatrix.postRotate(rotated, width / 2f, height / 2f)
        currentScale = baseScale
        return baseMatrix
    }

    /**
     * Resets the image to the start image matrix and informs any listeners.
     */
    fun reset() {
        // Reset touch
        last = PointF(0f, 0f)
        onZoomLevelChangeListener?.invoke(1f)
        resetting = true

        // Restore the original (calculated) values
        baseScale = startScale
        currentScale = baseScale
        calculatedMinScale = MIN_SCALE * baseScale
        calculatedMaxScale = MAX_SCALE * baseScale
        rotated = 0f

        applyMatrix(startMatrix, true)
    }

    /**
     * Rotate the image 90 degrees.
     * Automatically updates the scales relative to this rotation.
     */
    fun rotateImage() {
        val modifyMatrix = getModifyMatrix()

        // Cap rotation
        rotated = (rotated + 90f) % 360F

        modifyMatrix.postRotate(90f, width / 2f, height / 2f)
        val newWidth = imgWidth()
        val newHeight = imgHeight()

        // If image is too small we need to enlarge it to the base size
        if (newWidth < width && newHeight < height) {
            // Calculate the scaling needed
            val widthScale = newWidth / width
            val heightScale = newHeight / height
            val newScale = max(widthScale, heightScale)
            val scaleBy = 1f / newScale

            // Apply the scaling tot he current matrix
            val bounded = getBoundedRect(modifyMatrix)
            modifyMatrix.postScale(scaleBy, scaleBy, bounded.centerX(), bounded.centerY())
            currentScale *= scaleBy
        }

        // Update the calculated scales for the current rotation
        calculatedMinScale = MIN_SCALE * min(currentScale * (width / imgWidth()), currentScale * (height / imgHeight()))
        calculatedMaxScale = MAX_SCALE * min(currentScale * (width / imgWidth()), currentScale * (height / imgHeight()))
        // BaseScale is equal to the minScale since MIN_SCALE == 1
        baseScale = calculatedMinScale

        applyMatrix(modifyMatrix, false)
        onImageUpdate?.invoke()
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
        val factor = round(currentScale / baseScale)

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
     * Set all the initial values of the image.
     * Sets the starting matrix values, and the starting scale.
     * Invokes the onBitmapSetListener.
     */
    private fun initializeValues() {
        val values = imageMatrix.values()
        startMatrix = Matrix(imageMatrix)

        // Set the scales
        startScale = values[Matrix.MSCALE_X]
        baseScale = startScale
        currentScale = startScale
        calculatedMinScale = MIN_SCALE * currentScale
        calculatedMaxScale = MAX_SCALE * currentScale

        onBitmapSetListener?.invoke(drawable.toBitmap())
    }

    /**
     * Gets the bounding box of an image matrix.
     * @param reference the reference matrix to calculate the bounding box on.
     * @returns the bounding box of the matrix.
     */
    fun getRect(reference: Matrix): RectF {
        val values = reference.values()
        val left = when (rotated) {
            90f -> values[Matrix.MTRANS_X] - imgWidth()
            180f -> values[Matrix.MTRANS_X] - imgWidth()
            270f -> values[Matrix.MTRANS_X]
            else -> values[Matrix.MTRANS_X]
        }

        val top = when (rotated) {
            90f -> values[Matrix.MTRANS_Y]
            180f -> values[Matrix.MTRANS_Y] - imgHeight()
            270f -> values[Matrix.MTRANS_Y] - imgHeight()
            else -> values[Matrix.MTRANS_Y]
        }
        val right = imgWidth() + left
        val bottom = imgHeight() + top
        return RectF(left, top, right, bottom)
    }

    /**
     * Get the boundingBox rectangle restricted to the borders of the view.
     * @param refMatrix: the reference matrix to retrieve the bounding box for.
     * @return the bounded bounding box of the matrix.
     */
    fun getBoundedRect(refMatrix: Matrix): RectF {
        val rect = getRect(refMatrix)
        rect.left = max(0f, rect.left)
        rect.top = max(0f, rect.top)
        rect.right = min(rect.left + width, rect.right)
        rect.bottom = min(rect.top + height, rect.bottom)
        return rect
    }

    /**
     * Checks if the image was touched.
     * @return if the current image is changed or not.
     */
    fun isTouched(): Boolean {
        return !imageMatrix.equals(startMatrix)
    }

    /**
     * Apply the matrix to the image,
     * either with an animation or directly.
     * @param to: the new matrix to apply to the image.
     * @param animate: indicates if the application should be animated or directly.
     */
    private fun applyMatrix(to: Matrix, animate: Boolean) {
        if (animate) {
            animateMatrix(to)
        } else {
            imageMatrix = to
        }
    }

    /**
     * Getter for the width of the image, scaled to the current scalar, takes rotation into account.
     * @return the width of the image at this scale.
     */
    private fun imgWidth(): Float {
        val radians = rotated.toDouble() * (PI / 180)
        return abs(sin(radians) * (drawable.intrinsicHeight * currentScale) + cos(radians) * (drawable.intrinsicWidth * currentScale)).toFloat()
    }

    /**
     * Getter for the height of the image, scaled to the current scalar taking rotation into account.
     * @return the height of the image at this scale.
     */
    private fun imgHeight(): Float {
        val radians = rotated.toDouble() * (PI / 180)
        return abs(sin(radians) * (drawable.intrinsicWidth * currentScale) + cos(radians) * (drawable.intrinsicHeight * currentScale)).toFloat()
    }

    /**
     * Helper function that makes sure that the correct scale type is set.
     */
    private fun checkMatrixType() {
        if (scaleType != ScaleType.MATRIX) {
            super.setScaleType(ScaleType.MATRIX)
        }
    }

    /**
     * Returns a copy of the current imageMatrix as a modify matrix.
     * Additionally makes sure the correct scaleType is set.
     */
    private fun getModifyMatrix(): Matrix {
        checkMatrixType()
        return Matrix(imageMatrix)
    }

    /**
     * Handler for a double tap action.
     * Will scale the image to the double tap scaling value and animate the transition.
     * If already zoomed in, will reset to the base matrix for this rotation.
     */
    private fun handleDoubleTap() {
        doubleTap = false

        // If we are zoomed in, zoom out
        if (currentScale != baseScale) {
            val matrix = getBaseMatrix()
            applyMatrix(matrix, true)
        } else {
            val modifyMatrix = getModifyMatrix()

            // Scale with an offset based on the margin.
            modifyMatrix.postScale(
                doubleTapScalar,
                doubleTapScalar,
                -marginLeft + scaleDetector!!.focusX,
                -marginTop + scaleDetector!!.focusY
            )
            currentScale *= doubleTapScalar

            // Make sure the image is still properly centered
            val translation = center(modifyMatrix, false)
            modifyMatrix.postTranslate(translation.x, translation.y)

            applyMatrix(modifyMatrix, true)
        }
    }

    /**
     * Handler for touch image translation.
     * Will calculate the distance the image needs to translate,
     * bounded to the edges of the view.
     * Optionally directly sets the value of the imageMatrix.
     * @param delaySet indicates if setting the value of the imageMatrix should be handled now (true) or not (false)
     */
    private fun handleTouchTranslate(delaySet: Boolean = false): PointF {
        if (!allowTranslation && startScale == currentScale) {
            return PointF(0f, 0f)
        }

        val focus = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)

        // calculate the distance for translation
        val (dX, dY) = getDistance(focus, last)

        // If delaySet is set to false, update the image matrix
        if (!delaySet) {
            val modifyMatrix = getModifyMatrix()
            modifyMatrix.postTranslate(dX, dY)
            applyMatrix(modifyMatrix, false)
            last = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)
        }
        return PointF(dX, dY)
    }

    /**
     * Get the distance between two points for both axis.
     * The values are bounded by the view bounds.
     * @return a pair containing the delta x and delta
     */
    private fun getDistance(from: PointF, to: PointF): Pair<Float, Float> {
        val boundingBox = getRect(imageMatrix)
        val dX = getAxisDistance(from.x, to.x, Pair(boundingBox.left, boundingBox.right), width)
        val dY = getAxisDistance(from.y, to.y, Pair(boundingBox.top, boundingBox.bottom), height)

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
        val modifyMatrix = getModifyMatrix()
        val focusX = scaleDetector!!.focusX
        val focusY = scaleDetector!!.focusY

        // Scaling can also translate
        val translation = handleTouchTranslate(true)
        modifyMatrix.postTranslate(translation.x, translation.y)
        modifyMatrix.postScale(scaleBy, scaleBy, focusX, focusY)
        currentScale *= scaleBy

        // Update all values
        applyMatrix(modifyMatrix, false)
        updateZoomLevel(true)

        last = PointF(scaleDetector!!.focusX, scaleDetector!!.focusY)
    }

    /**
     * This will center both axis of the image.
     * It will either center exactly at the center if the image is smaller than the view.
     * Or it will make sure the edges of the image are always at the edge of the view.
     * Can immediately apply the centering, or not.
     * @param refMatrix: the reference matrix of the current or projected image.
     * @param animate: to immediately apply the transformation or not
     * @return a PointF containing the calculated translation.
     */
    private fun center(
        refMatrix: Matrix,
        animate: Boolean
    ): PointF {
        val boundingBox = getRect(refMatrix)
        val dX = centerAxis(Pair(imgWidth(), width), Pair(boundingBox.left, boundingBox.right))
        val dY = centerAxis(Pair(imgHeight(), height), Pair(boundingBox.top, boundingBox.bottom))

        // apply the translation
        if (animate) {
            val modifyMatrix = getModifyMatrix()
            modifyMatrix.postTranslate(dX, dY)
            applyMatrix(modifyMatrix, true)
        }

        return PointF(dX, dY)
    }

    /**
     * Center an axis, by centering it to the center or anchoring it to the edge of the view.
     * Will only center if the image goes beyond an edge, but should fit within the view.
     * @param imgDimens a pair containing the current image dimension and the view dimension (e.g. imgWidth/Width)
     * @param bounds a pair containing the min and max bounds of an image (top/bottom pair or left/right pair)
     */
    private fun centerAxis(imgDimens: Pair<Float, Int>, bounds: Pair<Float, Float>): Float {
        val (curDimens, maxDimens) = imgDimens
        val (min, max) = bounds

        // If the image is larger than the view
        if (curDimens > maxDimens) {
            // The min edge is too much to the inside (left/top)
            if (min > 0f) {
                return -min
            } else if (max < maxDimens) {
                // The max edge is too much to the inside (right/bottom)
                return maxDimens - max
            }
        } else {
            // We should only center if the image fits in the bounds but goes beyond a bound.
            if (allowTranslation && min >= 0f && max <= maxDimens) {
                return 0f
            }
            // Then we need to center at exactly half
            return (maxDimens / 2) - (min + ((max - min) / 2))
        }
        return 0f
    }

    /**
     * Apply the targetMatrix to the imageMatrix, by animating the transformation.
     * Will either call the zoomed in listener on finish or the matrix applied listener.
     * @param to the target matrix to animate to.
     */
    private fun animateMatrix(to: Matrix) {
        // Set all starting variables
        val targetValues = to.values()
        val from = Matrix(imageMatrix)
        val fromValues = from.values()

        val xScaleDiff = targetValues[Matrix.MSCALE_X] - fromValues[Matrix.MSCALE_X]
        val yScaleDiff = targetValues[Matrix.MSCALE_Y] - fromValues[Matrix.MSCALE_Y]
        val xSkewDiff = targetValues[Matrix.MSKEW_X] - fromValues[Matrix.MSKEW_X]
        val ySkewDiff = targetValues[Matrix.MSKEW_Y] - fromValues[Matrix.MSKEW_Y]
        val xTransDiff = targetValues[Matrix.MTRANS_X] - fromValues[Matrix.MTRANS_X]
        val yTransDiff = targetValues[Matrix.MTRANS_Y] - fromValues[Matrix.MTRANS_Y]

        // set up the animator
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.addUpdateListener(object : AnimatorUpdateListener {
            val activeMatrix = Matrix(from)
            val values = FloatArray(9)

            // Update the current image matrix
            override fun onAnimationUpdate(animation: ValueAnimator) {
                val currentValue = animation.animatedValue as Float
                activeMatrix.set(from)
                activeMatrix.getValues(values)
                values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + xTransDiff * currentValue
                values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + yTransDiff * currentValue
                values[Matrix.MSCALE_X] = values[Matrix.MSCALE_X] + xScaleDiff * currentValue
                values[Matrix.MSCALE_Y] = values[Matrix.MSCALE_Y] + yScaleDiff * currentValue
                values[Matrix.MSKEW_X] = values[Matrix.MSKEW_X] + xSkewDiff * currentValue
                values[Matrix.MSKEW_Y] = values[Matrix.MSKEW_Y] + ySkewDiff * currentValue
                activeMatrix.setValues(values)
                imageMatrix = activeMatrix
            }
        })
        animator.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                imageMatrix = to
                onImageUpdate?.invoke()

                if (zoomedIn) {
                    // We can resize the box to be larger
                    onZoomedInListener?.invoke()
                    zoomedIn = false
                } else {
                    // Update the zoomLevel (double tap case mostly)
                    updateZoomLevel(true)
                }

                if (zoomedOut) {
                    zoomedOut = false
                }

                if (resetting) {
                    resetting = false
                    onResetListener?.invoke()
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
