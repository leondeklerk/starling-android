package com.leondeklerk.starling.edit

import android.animation.PointFEvaluator
import android.animation.ValueAnimator
import android.graphics.PointF
import android.graphics.RectF
import android.view.animation.LinearInterpolator
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.BOTTOM
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.BOX
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.LEFT
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.LEFT_BOTTOM
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.LEFT_TOP
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.NONE
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.RIGHT
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.RIGHT_BOTTOM
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.RIGHT_TOP
import com.leondeklerk.starling.edit.CropMoveHandler.HandlerType.TOP
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.abs

class CropMoveHandler(
    private var bounds: RectF,
    private val borderBox: Box,
    private val handlerBounds: Float,
    private val minDimens: Float,
    private val heightToWidth: Float,
    private val widthToHeight: Float,
    private val threshold: Float,
    private val baseTranslate: Float
) {

    var onZoomListener: ((center: PointF, zoomOut: Boolean) -> Unit)? = null
    var onBoundsHitListener: ((coords: PointF, xType: HandlerType, yType: HandlerType) -> Unit)? = null
    var zoomLevel = 1f

    companion object {
        const val X_TYPE = "x"
        const val Y_TYPE = "y"
    }

    enum class HandlerType {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT,
        LEFT_TOP,
        RIGHT_TOP,
        RIGHT_BOTTOM,
        LEFT_BOTTOM,
        BOX,
        NONE
    }

    private var moving = false
    private var movingHandler = NONE
    private var boxMoveStart = PointF()
    private lateinit var beforeZoomBox: Box
    private lateinit var beforeZoomBounds: RectF
    private lateinit var borderBoxStart: Box

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
            // borderBox.center.near(x, y, handlerBounds) -> { // Center movement controls
            borderBox.isWithin(x, y) -> { // Boundary movement control

                // Save the current location data
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
    fun endMove(): Boolean {
        boxMoveStart = PointF()
        borderBoxStart = borderBox.copy()

        if (moving) {
            checkZoom()
            moving = false
            return true
        }

        return false
    }

    fun cancel() {
        moving = false
        movingHandler = NONE
    }

    fun updateBounds(newBounds: RectF) {
        bounds = newBounds
    }

    fun restrictBorder() {
        if (borderBox.left.x < bounds.left) {
            borderBox.left.x = bounds.left
        }

        if (borderBox.top.y < bounds.top) {
            borderBox.top.y = bounds.top
        }

        if (borderBox.right.x > bounds.right) {
            borderBox.right.x = bounds.right
        }

        if (borderBox.bottom.y > bounds.bottom) {
            borderBox.bottom.y = bounds.bottom
        }
    }

    fun scaleBox() {
        // Fix centering?
        val diffHor = (borderBox.right.x - borderBox.left.x) * (2f - 1f)
        val diffVert = (borderBox.bottom.y - borderBox.top.y) * (2f - 1f)

        borderBox.left.x = max(borderBox.left.x - diffHor / 2f, bounds.left)
        borderBox.top.y = max(borderBox.top.y - diffVert / 2f, bounds.top)
        borderBox.right.x = min(borderBox.right.x + diffHor / 2f, bounds.right)
        borderBox.bottom.y = min(borderBox.bottom.y + diffVert / 2f, bounds.bottom)
    }

    private fun checkZoom() {
        onZoomListener?.let {
            val boxWidth = borderBox.width
            val boxHeight = borderBox.height

            val boundsWidth = bounds.width()
            val boundsHeight = bounds.height()

            val xSmall = boxWidth <= (boundsWidth / 2)
            val ySmall = boxHeight <= (boundsHeight / 2)

            if (xSmall && ySmall) {
                it(borderBox.center, false)
            }

            boundsHitCounter = 0
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

        if (boundsHitCounter > 20) {
            onZoomListener?.invoke(borderBox.center, true)
            boundsHitCounter = 0
        }

        return true
    }

    private var boundsHitCounter = 0

    /**
     * Check if a value is within the bound set on constructing.
     * If not cap it to the max of the bounds.
     *
     * @param x the x value of the point to test
     * @param y the y value of the point to test
     * @return a pair containing the capped values.
     */
    private fun withinBounds(x: Float, y: Float): Pair<Float, Float> {
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

        if (hitBounds && movingHandler != BOX) {
            boundsHitCounter++
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
     */
    private fun handleMaxEdge(maxEdge: Line, moveEdge: Line, moveTo: Float, type: String) {
        val maxEdgeVal = maxEdge.getByType(type)

        // The distance between the lines must always be equal or larger than the minDimens
        val diff = maxEdgeVal - moveTo

        // If the distance between the lines is larger than the max distance just set it
        if (diff >= minDimens) {
            moveEdge.setByType(moveTo, type)
        } else {
            // Otherwise set it to the max allowed value
            moveEdge.setByType(maxEdgeVal - minDimens, type)
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
     */
    private fun handleMinEdge(minEdge: Line, moveEdge: Line, moveTo: Float, type: String) {
        val minEdgeVal = minEdge.getByType(type)

        // if the new value is still larger than the minimum height
        if (moveTo >= minEdgeVal + minDimens) {
            moveEdge.setByType(moveTo, type)
        } else {
            // Otherwise it is the minimum line + the min value (minDimens)
            moveEdge.setByType(minEdgeVal + minDimens, type)
        }
    }

    var xDir = NONE
    var yDir = NONE

    var animator: ValueAnimator? = null

    /**
     * Calculates movements for the whole borderBox. Will move both right and left by the difference between
     * the box location at the start of the movement and the touchX (capped at the bounds).
     * Does the same for the top, bottom according to the touch y.
     *
     * @param moveX The new x point to calculate the distance from
     * @param moveY the y touch coordinate
     */
    private fun handleBoxMove(moveX: Float, moveY: Float) {
        var dX = moveX - boxMoveStart.x
        var dY = moveY - boxMoveStart.y

        val bX = dX
        val bY = dY

        var translateX = 0f
        var translateY = 0f

        var xType: HandlerType
        var yType: HandlerType

        val zoomMultiplier = max(1f, zoomLevel / 2f)
        val xMultiplier = heightToWidth * zoomMultiplier
        val yMultiplier = widthToHeight * zoomMultiplier

        // If the move is to the left
        if (dX < 0) {
            xType = LEFT
            // If the move is beyond the bounds
            if (borderBoxStart.left.x + dX <= bounds.left) {
                val diff = bounds.left - borderBoxStart.left.x
                translateX = baseTranslate * xMultiplier

                // Set the dX to the max value it could have
                dX = diff
            }
        } else {
            xType = RIGHT
            // If the right move is beyond the bounds
            if (borderBoxStart.right.x + dX >= bounds.right) {
                val diff = bounds.right - borderBoxStart.right.x
                translateX = -baseTranslate * xMultiplier
                // Cap at the right bound
                dX = diff
            }
        }

        // If the move is to the top
        if (dY < 0) {
            yType = TOP
            // If beyond the top
            if (borderBoxStart.top.y + dY <= bounds.top) {
                val diff = bounds.top - borderBoxStart.top.y
                translateY = baseTranslate * yMultiplier
                // Cap at top
                dY = diff
            }
        } else {
            yType = BOTTOM
            // If beyond bottom
            if (borderBoxStart.bottom.y + dY >= bounds.bottom) {
                val diff = bounds.bottom - borderBoxStart.bottom.y
                translateY = -baseTranslate * yMultiplier
                // cap at bottom
                dY = diff
            }
        }

        borderBox.left.x = borderBoxStart.left.x + dX
        borderBox.right.x = borderBoxStart.right.x + dX

        borderBox.top.y = borderBoxStart.top.y + dY
        borderBox.bottom.y = borderBoxStart.bottom.y + dY

        if (abs(bY) <= threshold) {
            yType = NONE
            translateY = 0f
        }

        if (abs(bX) <= threshold) {
            xType = NONE
            translateX = 0f
        }

        if (xDir != xType || yDir != yType) {
            animator?.cancel()
        }

        xDir = xType
        yDir = yType

        animator = ValueAnimator.ofObject(PointFEvaluator(), PointF(), PointF(translateX, translateY))
        animator?.interpolator = LinearInterpolator()
        animator?.duration = 100

        animator?.addUpdateListener { animation ->
            val curPoint = animation.animatedValue as PointF
            onBoundsHitListener?.invoke(curPoint, xType, yType)
        }

        animator?.start()
    }

    /**
     * Add extra helper function to the PointF class to check if a point (touchX, touchY) is within a radius (bounds)
     * of the exsiting point.
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