package com.leondeklerk.starling.edit.crop

import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import com.leondeklerk.starling.edit.crop.HandlerType.BOTTOM
import com.leondeklerk.starling.edit.crop.HandlerType.BOX
import com.leondeklerk.starling.edit.crop.HandlerType.LEFT
import com.leondeklerk.starling.edit.crop.HandlerType.LEFT_BOTTOM
import com.leondeklerk.starling.edit.crop.HandlerType.LEFT_TOP
import com.leondeklerk.starling.edit.crop.HandlerType.NONE
import com.leondeklerk.starling.edit.crop.HandlerType.RIGHT
import com.leondeklerk.starling.edit.crop.HandlerType.RIGHT_BOTTOM
import com.leondeklerk.starling.edit.crop.HandlerType.RIGHT_TOP
import com.leondeklerk.starling.edit.crop.HandlerType.TOP
import java.lang.Float.max

typealias PairF = Pair<Float, Float>

/**
 * The CropMoveHandler is responsible for starting, handling and stopping movement of the CropBox.
 * @param bounds: The bounds of the image
 * @param borderBox: the box drawn over the image
 * @param handlerBounds: the pixel radius around a handler that counts as touching the handler
 * @param minDimens: the minimum distance edges need to have
 * @param threshold: the threshold after which a auto movement will start
 * @param baseTranslate: the base translation which the box will auto translate.
 */
class CropMoveHandler(
    private var bounds: RectF,
    private val borderBox: Box,
    private val handlerBounds: Float,
    private val minDimens: Float,
    private val threshold: Float,
    private val baseTranslate: Float
) {

    var onZoomListener: ((center: PointF, zoomOut: Boolean) -> Unit)? = null
    var onBoundsHitListener: ((delta: PointF, types: Pair<HandlerType, HandlerType>) -> Unit)? = null
    var zoomLevel = 1f

    private var zoomOutHandler = Handler(Looper.getMainLooper())
    private val zoomOutRunnable = Runnable {
        checkZoomOut()
    }
    private var zoomOutRunning = false

    private var moving = false
    private var movingHandler = NONE
    private var boxMoveStart = PointF()
    private lateinit var borderBoxStart: Box
    private var curDirectionX = NONE
    private var curDirectionY = NONE

    private var translateX = 0f
    private var translateY = 0f

    companion object {
        const val X_TYPE = "x"
        const val Y_TYPE = "y"
    }

    /**
     * Axis data contains the data of the axis.
     * @param sides: the axis coordinate value (x for left/right, y for top/bottom) for the sides
     * @param bounds: the axis coordinate value for the bounds.
     */
    data class AxisData(val sides: PairF, val bounds: PairF)

    /**
     * Starts a moving interaction.
     * Checks if and which handler is touched, based on location and handlerBounds.
     * Sets the type of handler that was activated (if so).
     * @param x: The X of the touch event
     * @param y The y coordinate of the touch event.
     * @return if a handler was activated or not.
     */
    fun startMove(x: Float, y: Float): Boolean {
        moving = true
        movingHandler = when {
            borderBox.leftTop.near(x, y, handlerBounds) -> {
                LEFT_TOP
            }
            borderBox.rightTop.near(x, y, handlerBounds) -> {
                RIGHT_TOP
            }
            borderBox.rightBottom.near(x, y, handlerBounds) -> {
                RIGHT_BOTTOM
            }
            borderBox.leftBottom.near(x, y, handlerBounds) -> {
                LEFT_BOTTOM
            }
            borderBox.top.near(x, y, handlerBounds) -> {
                TOP
            }
            borderBox.right.near(x, y, handlerBounds) -> {
                RIGHT
            }
            borderBox.bottom.near(x, y, handlerBounds) -> {
                BOTTOM
            }
            borderBox.left.near(x, y, handlerBounds) -> {
                LEFT
            }
            borderBox.isWithin(x, y) -> {
                boxMoveStart.x = x
                boxMoveStart.y = y
                borderBoxStart = borderBox.copy()
                BOX
            }
            else -> {
                moving = false
                NONE
            }
        }

        return moving
    }

    /**
     * Handle the end of a touch event.
     * Resets the current movement details.
     */
    fun endMove() {
        // Stop zoom out handling
        zoomOutHandler.removeCallbacksAndMessages(null)
        zoomOutRunning = false

        // Reset the box properties and direction
        boxMoveStart = PointF()
        borderBoxStart = borderBox.copy()
        curDirectionX = NONE
        curDirectionY = NONE

        if (moving) {
            checkZoomIn()
            moving = false
        }
    }

    /**
     * Cancels all movement
     */
    fun cancel() {
        zoomOutHandler.removeCallbacksAndMessages(null)
        zoomOutRunning = false

        moving = false
        movingHandler = NONE
        curDirectionX = NONE
        curDirectionY = NONE
    }

    /**
     * Update the bounds to reflect image changes.
     */
    fun updateBounds(newBounds: RectF) {
        bounds = newBounds
    }

    /**
     * Scales the box to double the size.
     * If this scales outside the border, the whole box is translated to fit.
     */
    fun scaleBox(): RectF {
        val boxRect = borderBox.getRect()
        val width = boxRect.width() / 2f
        val height = boxRect.height() / 2f
        var left = boxRect.left - width
        var right = boxRect.right + width
        var top = boxRect.top - height
        var bottom = boxRect.bottom + height

        // Calculated the X translation if outside the border
        var dX = 0f
        if (left < bounds.left) {
            dX = bounds.left - left
        } else if (right > bounds.right) {
            dX = bounds.right - right
        }

        // Calculated the Y translation if outside the bounds
        var dY = 0f
        if (top < bounds.top) {
            dY = bounds.top - top
        } else if (bottom > bounds.bottom) {
            dY = bounds.bottom - bottom
        }

        // Translate the box to fit it within the bounds
        left += dX
        right += dX

        top += dY
        bottom += dY

        return RectF(left, top, right, bottom)
    }

    /**
     * Updates the border to ensure that it fits within the current bounds.
     * @return A rectF containing the updated borders
     */
    fun updateBorder(): RectF {
        val imgWidth = bounds.right - bounds.left
        val imgHeight = bounds.bottom - bounds.top

        val widthPair = Pair(borderBox.width, imgWidth)
        val heightPair = Pair(borderBox.height, imgHeight)

        // Get the updated sides
        val (left, right) = getUpdatedSide(widthPair, AxisData(Pair(borderBox.left.x, borderBox.right.x), Pair(bounds.left, bounds.right)))
        val (top, bottom) = getUpdatedSide(heightPair, AxisData(Pair(borderBox.top.y, borderBox.bottom.y), Pair(bounds.top, bounds.bottom)))

        return RectF(left, top, right, bottom)
    }

    /**
     * Reset the cropper to its initial state.
     * @return the bounds of the image
     */
    fun reset(): RectF {
        return bounds
    }

    /**
     * Checks if the current zoom level is not one,
     * will invoke the zoom listener and if applicable start a new zoom check runnable.
     */
    private fun checkZoomOut() {
        onZoomListener?.invoke(borderBox.center, true)
        if (zoomLevel != 1f) {
            // Start a new runnable to check zoom out (to prevent needing to move if holding on an edge)
            zoomOutHandler.postDelayed(zoomOutRunnable, 1000)
        }
    }

    /**
     * Check if the box is small enough to automatically zoom in.
     */
    private fun checkZoomIn() {
        onZoomListener?.let {
            val boxWidth = borderBox.width
            val boxHeight = borderBox.height

            val boundsWidth = bounds.width()
            val boundsHeight = bounds.height()

            // Check if width and height are smaller than half
            val xSmall = boxWidth <= (boundsWidth / 2)
            val ySmall = boxHeight <= (boundsHeight / 2)

            if (xSmall && ySmall) {
                // Call the listener
                it(borderBox.center, false)
            }
        }
    }

    /**
     * Handles the touch movements.
     * Checks the type of handler that was activated and executes its specific movements.
     *
     * @param x the x of the touch
     * @param y the y of the touch event
     * @return if moved or not
     */
    fun onMove(x: Float, y: Float): Boolean {
        if (!moving) return false

        val (boundX, boundY) = withinBounds(x, y)

        // Line handlers can use the min and max edge functions
        when (movingHandler) {
            TOP -> {
                handleMaxEdge(borderBox.bottom, borderBox.top, boundY, Y_TYPE)
            }
            RIGHT -> {
                handleMinEdge(borderBox.left, borderBox.right, boundX, X_TYPE)
            }
            BOTTOM -> {
                handleMinEdge(borderBox.top, borderBox.bottom, boundY, Y_TYPE)
            }
            LEFT -> {
                handleMaxEdge(borderBox.right, borderBox.left, boundX, X_TYPE)
            }
            LEFT_TOP -> {
                // A corner is just two line movements at the same time
                handleMaxEdge(borderBox.right, borderBox.left, boundX, X_TYPE)
                handleMaxEdge(borderBox.bottom, borderBox.top, boundY, Y_TYPE)
            }
            RIGHT_TOP -> {
                handleMinEdge(borderBox.left, borderBox.right, boundX, X_TYPE)
                handleMaxEdge(borderBox.bottom, borderBox.top, boundY, Y_TYPE)
            }
            RIGHT_BOTTOM -> {
                handleMinEdge(borderBox.left, borderBox.right, boundX, X_TYPE)
                handleMinEdge(borderBox.top, borderBox.bottom, boundY, Y_TYPE)
            }
            LEFT_BOTTOM -> {
                handleMaxEdge(borderBox.right, borderBox.left, boundX, X_TYPE)
                handleMinEdge(borderBox.top, borderBox.bottom, boundY, Y_TYPE)
            }
            BOX -> {
                handleBoxMove(x, y)
            }
            NONE -> {
                return false
            }
        }

        return true
    }

    /**
     * Check if a value is within the bound set on constructing.
     * If not cap it to the max of the bounds.
     *
     * @param x the x value of the point to test
     * @param y the y value of the point to test
     * @return a pair containing the capped values.
     */
    private fun withinBounds(x: Float, y: Float): PairF {
        var boundedX = x
        var boundedY = y
        var hitBounds = false

        // Check the left and right bounds (x)
        when {
            x < bounds.left -> {
                hitBounds = true
                boundedX = bounds.left
            }
            x > bounds.right -> {
                hitBounds = true
                boundedX = bounds.right
            }
        }

        // check the top and bottom (y) bounds
        when {
            y < bounds.top -> {
                hitBounds = true
                boundedY = bounds.top
            }
            y > bounds.bottom -> {
                hitBounds = true
                boundedY = bounds.bottom
            }
        }

        // If we hit a bound, we can zoom out
        if (hitBounds && movingHandler != BOX && movingHandler != NONE) {
            if (!zoomOutRunning) {
                zoomOutRunning = true
                zoomOutHandler.postDelayed(zoomOutRunnable, 500)
            }
        } else {
            zoomOutRunning = false
            zoomOutHandler.removeCallbacksAndMessages(null)
        }

        return Pair(boundedX, boundedY)
    }

    /**
     * Handles the movement of an edge that can have a maximum value.
     * Calculates the difference and checks if the new value is within the bounds.
     * A max edge is for example the top edge (max value is bottom edge - minDimens).
     * Sets the value (by type) to the calculated max.
     *
     * @param maxEdge the line that is the maximum value
     * @param moveEdge the edge that is moved by the user interaction
     * @param moveTo the touch value
     * @param type indicates if this is an X or Y movement
     * @return if the max edge was hit or not
     */
    private fun handleMaxEdge(maxEdge: Line, moveEdge: Line, moveTo: Float, type: String): Boolean {
        val maxEdgeVal = maxEdge.getByType(type)

        // The distance between the lines must always be equal or larger than the minDimens
        val diff = maxEdgeVal - moveTo

        // If the distance between the lines is larger than the max distance just set it
        return if (diff >= minDimens) {
            moveEdge.setByType(moveTo, type)
            false
        } else {
            // Otherwise set it to the max allowed value
            moveEdge.setByType(maxEdgeVal - minDimens, type)
            true
        }
    }

    /**
     * Handles the movement of an edge that can have a minimum value.
     * Calculates the difference and checks if the new value is within the bounds.
     * A min edge is for example the right edge (min value is left edge + minDimens).
     * Sets the value (by type) to the calculated min.
     *
     * @param minEdge the line that is the minimum value
     * @param moveEdge the edge that is moved by the user interaction
     * @param moveTo the touch value
     * @param type indicates if this is an X or Y movement
     * @return if the min edge was hit or not
     */
    private fun handleMinEdge(minEdge: Line, moveEdge: Line, moveTo: Float, type: String): Boolean {
        val minEdgeVal = minEdge.getByType(type)

        // if the new value is still larger than the minimum height
        return if (moveTo >= minEdgeVal + minDimens) {
            moveEdge.setByType(moveTo, type)
            false
        } else {
            // Otherwise it is the minimum line + the min value (minDimens)
            moveEdge.setByType(minEdgeVal + minDimens, type)
            true
        }
    }

    /**
     * Calculates movements for the whole borderBox. Will move both right and left by the difference between
     * the box location at the start of the movement and the touchX (capped at the bounds).
     * Does the same for the top, bottom according to the touch y.
     * Additionally calculates the direction and translation when the box is at the bounds,
     * in order to invoke the auto movement of the image itself.
     *
     * @param moveX The new x point to calculate the distance from
     * @param moveY the y touch coordinate
     */
    private fun handleBoxMove(moveX: Float, moveY: Float) {
        var restrictedX = moveX - boxMoveStart.x
        var restrictedY = moveY - boxMoveStart.y

        // Set the deltas to the (currently) unrestricted values
        val deltaX = restrictedX
        val deltaY = restrictedY

        val startAxisX = AxisData(Pair(borderBoxStart.left.x, borderBoxStart.right.x), Pair(bounds.left, bounds.right))
        val startAxisY = AxisData(Pair(borderBoxStart.top.y, borderBoxStart.bottom.y), Pair(bounds.top, bounds.bottom))

        // Calculated the restricted values
        restrictedX = capDelta(restrictedX, startAxisX)
        restrictedY = capDelta(restrictedY, startAxisY)

        // Move the box itself
        borderBox.left.x = borderBoxStart.left.x + restrictedX
        borderBox.right.x = borderBoxStart.right.x + restrictedX

        borderBox.top.y = borderBoxStart.top.y + restrictedY
        borderBox.bottom.y = borderBoxStart.bottom.y + restrictedY

        val axisX = AxisData(Pair(borderBox.left.x, borderBox.right.x), Pair(bounds.left, bounds.right))
        val axisY = AxisData(Pair(borderBox.top.y, borderBox.bottom.y), Pair(bounds.top, bounds.bottom))

        // Update the auto translation and direction on the X axis
        val xChanged = updateAutoTranslationAxis(
            curDirectionX,
            translateX,
            deltaX,
            axisX,
            Pair(LEFT, RIGHT),
            X_TYPE
        )

        // Update the auto translation and direction on the Y axis
        val yChanged = updateAutoTranslationAxis(
            curDirectionY,
            translateY,
            deltaY,
            axisY,
            Pair(TOP, BOTTOM),
            Y_TYPE
        )

        // If there was a change, call the listener
        if (xChanged || yChanged) {
            val delta = PointF(translateX, translateY)
            val types = Pair(curDirectionX, curDirectionY)
            onBoundsHitListener?.invoke(delta, types)
        }
    }

    /**
     * Update the sides on one axis of a box to the bounds.
     * Will translate the whole box on the axis if the box fits within the image.
     * Will restrict a side to a bound if the image does not fit the image (anymore).
     * @param props: the box and image axis properties (width/height)
     * @param axisData: the box sides and bounds information for this axis (x/y)
     * @return A pair containing the updated sides.
     */
    private fun getUpdatedSide(props: PairF, axisData: AxisData): PairF {
        val (boxProp, imgProp) = props
        val sides = axisData.sides
        val bounds = axisData.bounds

        var (sideA, sideB) = sides

        // Check if this axis fits the image
        if (boxProp.toInt() <= imgProp.toInt()) {
            var diff = 0f
            // Check which side is out of bounds and calculate the translation
            if (sides.first < bounds.first) {
                diff = bounds.first - sides.first
            } else if (sides.second > bounds.second) {
                diff = bounds.second - sides.second
            }

            // Apply translation
            sideA += diff
            sideB += diff
        } else {
            // If the box does not fit, restrict the out of bound(s) side(s)
            if (sides.first < bounds.first) {
                sideA = bounds.first
            }

            if (sides.second > bounds.second) {
                sideB = bounds.second
            }
        }

        return Pair(sideA, sideB)
    }

    /**
     * Checks if a delta translation is within bounds.
     * A minus delta is a movement towards the origin (left, top).
     * A positive delta is away from the origin (right, bottom).
     * Checks where the box will end up and restrict to the correct bound.
     * @param delta: the movement delta
     * @param axisData: the sides and bounds data of the axis
     * @return the restricted delta value.
     */
    private fun capDelta(delta: Float, axisData: AxisData): Float {
        val boxSides = axisData.sides
        val bounds = axisData.bounds

        // Check origin side
        if (delta < 0) {
            // Restrict on the inner bound
            if (boxSides.first + delta <= bounds.first) {
                return bounds.first - boxSides.first
            }
        } else {
            // Restrict on the outside bound
            if (boxSides.second + delta >= bounds.second) {
                return bounds.second - boxSides.second
            }
        }
        return delta
    }

    /**
     * Determines the (new) direction the auto translation will go in.
     * Determines the translation speed based.
     * Checks which side hits a bound and sets the direction to towards that bound.
     * @param curDirection: the direction currently moving in
     * @param curTranslation: the current translation value
     * @param delta: the current movement delta
     * @param axisData: the data of the sides and bounds of this axis
     * @param options: the direction options available for this axis (left/right, top/bottom)
     * @param axis: string representing the axis currently selected (x/y)
     * @return if the direction changed or not
     */
    private fun updateAutoTranslationAxis(
        curDirection: HandlerType,
        curTranslation: Float,
        delta: Float,
        axisData: AxisData,
        options: Pair<HandlerType, HandlerType>,
        axis: String
    ): Boolean {

        val sides = axisData.sides
        val bounds = axisData.bounds

        var newDirection = curDirection
        var translation = curTranslation
        var changed = false

        val zoomMultiplier = max(1f, zoomLevel / 2f)

        // If not currently moving, determine a direction
        if (curDirection == NONE) {
            // If moving towards the origin (left/top)
            if (delta <= -threshold && sides.first == bounds.first) {
                newDirection = options.first
                translation = baseTranslate * zoomMultiplier
                changed = true
            } else if (delta >= threshold && sides.second == bounds.second) {
                // Moving from the origin (right/bottom)
                newDirection = options.second
                translation = -baseTranslate * zoomMultiplier
                changed = true
            }
        } else {
            if (curDirection == options.first) {
                // Stop moving if we are no longer within the threshold or at inner the bound (left/top)
                if (delta > -threshold || sides.first != bounds.first) {
                    newDirection = NONE
                    changed = true
                }
            } else if (curDirection == options.second) {
                // Stop moving if we are no longer within the threshold or at outer the bound (right/bottom)
                if (delta < threshold || sides.second != bounds.second) {
                    newDirection = NONE
                    changed = true
                }
            }
        }

        // Update the moving data
        if (axis == X_TYPE) {
            curDirectionX = newDirection
            translateX = translation
        } else {
            curDirectionY = newDirection
            translateY = translation
        }

        return changed
    }

    /**
     * Add extra helper function to the PointF class to check if a point (touchX, touchY) is within a radius (bounds)
     * of the existing point.
     *
     * @param touchX: The x coordinate to test for
     * @param touchY: the y coordinate to test for
     * @returns if the touch point is within bounds of the point.
     */
    private fun PointF.near(touchX: Float, touchY: Float, bounds: Float): Boolean {
        // Is it within the left bounds
        val xIn = (touchX <= x + bounds && touchX >= x - bounds)
        val yIn = (touchY <= y + bounds && touchY >= y - bounds)
        return xIn && yIn
    }
}
