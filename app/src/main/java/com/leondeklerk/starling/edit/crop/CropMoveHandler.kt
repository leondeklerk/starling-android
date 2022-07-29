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
import kotlin.math.max
import kotlin.math.min

typealias PairF = Pair<Float, Float>

/**
 * The CropMoveHandler is responsible for starting, handling and stopping movement of the CropBox.
 * @param bounds: The bounds of the image
 * @param box: the box drawn over the image
 * @param handlerBounds: the pixel radius around a handler that counts as touching the handler
 * @param threshold: the threshold after which a auto movement will start
 * @param baseTranslate: the base translation which the box will auto translate.
 */
class CropMoveHandler(
    var bounds: RectF,
    private val box: Box,
    private val handlerBounds: Float,
    private val threshold: Float,
    private val baseTranslate: Float
) {

    var onZoomListener: ((center: PointF, zoomOut: Boolean) -> Unit)? = null
    var onBoundsHitListener: ((delta: PointF, types: Pair<HandlerType, HandlerType>) -> Unit)? = null
    var zoomLevel = 1f
    var aspectRatio: AspectRatio = AspectRatio.FREE

    private var zoomOutHandler = Handler(Looper.getMainLooper())
    private val zoomOutRunnable = Runnable {
        checkZoomOut()
    }
    private var zoomOutRunning = false

    private var initialMove = false
    private var moving = false
    private var pointerX = 0f
    private var pointerY = 0f
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
     * Stores the initial pointer value as the value of the handler.
     * @param x: The X of the touch event
     * @param y The y coordinate of the touch event.
     * @return if a handler was activated or not.
     */
    fun startMove(x: Float, y: Float): Boolean {
        initialMove = true
        moving = true
        movingHandler = when {
            box.leftTop.near(x, y, handlerBounds) -> {
                pointerX = box.l
                pointerY = box.t
                LEFT_TOP
            }
            box.rightTop.near(x, y, handlerBounds) -> {
                pointerX = box.r
                pointerY = box.t
                RIGHT_TOP
            }
            box.rightBottom.near(x, y, handlerBounds) -> {
                pointerX = box.r
                pointerY = box.b
                RIGHT_BOTTOM
            }
            box.leftBottom.near(x, y, handlerBounds) -> {
                pointerX = box.l
                pointerY = box.b
                LEFT_BOTTOM
            }
            box.top.near(x, y, handlerBounds) -> {
                pointerY = box.t
                TOP
            }
            box.right.near(x, y, handlerBounds) -> {
                pointerX = box.r
                RIGHT
            }
            box.bottom.near(x, y, handlerBounds) -> {
                pointerY = box.b
                BOTTOM
            }
            box.left.near(x, y, handlerBounds) -> {
                pointerX = box.l
                LEFT
            }
            box.isWithin(x, y) -> {
                initialMove = false
                boxMoveStart.x = x
                boxMoveStart.y = y
                borderBoxStart = box.copy()
                BOX
            }
            else -> {
                initialMove = false
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
        borderBoxStart = box.copy()
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
     * Scales the box 75% of the screen width.
     * The height is automatically adjusted based on the aspect ratio.
     * If the height will not fit, the width is adjusted to a lower value.
     * If this scales outside the border, the whole box is translated to fit.
     * Free aspect ratio equates to 75% height and width
     * @return the new rectangle representing the updated box.
     */
    fun scaleBox(): RectF {
        // Start with a 75% and height based on the aspect ratio.
        var projectedWidth = bounds.width() * 0.75f
        var projectedHeight = aspectRatio.ratio(projectedWidth)

        // If the height does not fit, set it to the max value and calculate the corresponding width.
        if (projectedHeight > bounds.height()) {
            val diff = bounds.height() - projectedHeight
            projectedHeight = bounds.height()
            projectedWidth += aspectRatio.ratioInverted(diff)
        }

        // For free just use 75% for both.
        if (aspectRatio == AspectRatio.FREE) {
            projectedWidth = bounds.width() * 0.75f
            projectedHeight = bounds.height() * 0.75f
        }

        // Resize and translate the box.
        val result = box.copy().growTo(projectedWidth, projectedHeight)
        return result.move(
            getAxisDiff(LEFT, RIGHT, result),
            getAxisDiff(TOP, BOTTOM, result)
        ).rect
    }

    /**
     * Updates the border to ensure that it fits within the current bounds.
     * First makes sure that the box fits within the bounds,
     * then translates it to make sure it is placed within the bounds.
     * @return A rectangle representing the updated borders
     */
    fun updateBorder(): RectF {
        // If the box fits, just translate it.
        if (box.width <= bounds.width() && box.height <= bounds.height()) {
            return box.copy().move(
                getAxisDiff(LEFT, RIGHT),
                getAxisDiff(TOP, BOTTOM)
            ).rect
        }

        var wDiff = 0f
        var hDiff = 0f
        val result = box.copy()
        if (aspectRatio == AspectRatio.FREE) {
            if (box.width > bounds.width()) {
                wDiff = box.width - bounds.width()
            }

            if (box.height > bounds.height()) {
                hDiff = box.height - bounds.height()
            }

            result.shrink(wDiff / 2f, hDiff / 2f)
        } else {
            // First shrink the box with respect to the ratio
            wDiff = box.width - bounds.width()
            hDiff = aspectRatio.ratioInverted(box.height - bounds.height())
            val diff = max(wDiff, hDiff)
            result.shrink(diff / 2f, aspectRatio.ratio(diff) / 2f)
        }

        return result.move(
            getAxisDiff(LEFT, RIGHT, result),
            getAxisDiff(TOP, BOTTOM, result)
        ).rect
    }

    /**
     * Handles the touch movements.
     * Checks the type of handler that was activated and executes its specific movements.
     * Also responsible for handling the automatic zoom out.
     *
     * @param x the x of the touch
     * @param y the y of the touch event
     * @return if moved or not
     */
    fun onMove(x: Float, y: Float): Boolean {
        if (!moving) return false

        // Check if we are hitting a bound, handle auto zooming out
        if (zoomLevel != 1f && movingHandler != BOX && movingHandler != NONE && !((x > bounds.left && x < bounds.right) && (y > bounds.top && y < bounds.bottom))) {
            if (!zoomOutRunning) {
                zoomOutRunning = true
                zoomOutHandler.postDelayed(zoomOutRunnable, 500)
            }
        } else {
            zoomOutRunning = false
            zoomOutHandler.removeCallbacksAndMessages(null)
        }

        val isFree = aspectRatio == AspectRatio.FREE

        // The first move should use the current values of the box instead of the touch coordinates due to the handler bounds.
        if (initialMove) {
            initialMove = false
        } else {
            pointerX = x
            pointerY = y
        }

        // For each handler get the delta of the main handler,
        // Then based on the aspect ratio calculate the max possible delta in relation to the two dependent handlers.
        when (movingHandler) {
            LEFT -> {
                var delta = deltaMainHandler(pointerX, LEFT)

                if (!isFree) {
                    delta = deltaDependentHandler(delta, TOP, false)
                    delta = deltaDependentHandler(delta, BOTTOM, true)

                    box.t += aspectRatio.ratio(delta) / 2f
                    box.b -= aspectRatio.ratio(delta) / 2f
                }

                box.l += delta
            }
            TOP -> {
                var delta = deltaMainHandler(pointerY, TOP)

                if (!isFree) {
                    delta = deltaDependentHandler(delta, LEFT, false)
                    delta = deltaDependentHandler(delta, RIGHT, true)

                    box.l += aspectRatio.ratioInverted(delta) / 2f
                    box.r -= aspectRatio.ratioInverted(delta) / 2f
                }

                box.t += delta
            }
            RIGHT -> {
                var delta = deltaMainHandler(pointerX, RIGHT)

                if (!isFree) {
                    delta = deltaDependentHandler(delta, TOP, true)
                    delta = deltaDependentHandler(delta, BOTTOM, false)

                    box.t -= aspectRatio.ratio(delta) / 2f
                    box.b += aspectRatio.ratio(delta) / 2f
                }

                box.r += delta
            }
            BOTTOM -> {
                var delta = deltaMainHandler(pointerY, BOTTOM)

                if (!isFree) {
                    delta = deltaDependentHandler(delta, LEFT, true)
                    delta = deltaDependentHandler(delta, RIGHT, false)

                    box.l -= aspectRatio.ratioInverted(delta) / 2f
                    box.r += aspectRatio.ratioInverted(delta) / 2f
                }

                box.b += delta
            }
            LEFT_TOP -> {
                if (isFree) {
                    val xDelta = deltaMainHandler(pointerX, LEFT)
                    val yDelta = deltaMainHandler(pointerY, TOP)

                    box.l += xDelta
                    box.t += yDelta
                    return true
                }

                val diff = min(pointerX - box.l, pointerY - box.t)
                val xDelta = deltaMainHandler(box.l + diff, LEFT)
                val yDelta = deltaMainHandler(box.t + aspectRatio.ratio(xDelta), TOP)

                box.l += aspectRatio.ratioInverted(yDelta)
                box.t += yDelta
            }
            RIGHT_TOP -> {
                if (isFree) {
                    val xDelta = deltaMainHandler(pointerX, RIGHT)
                    val yDelta = deltaMainHandler(pointerY, TOP)

                    box.r += xDelta
                    box.t += yDelta
                    return true
                }

                val diff = min(pointerX - box.r, pointerY + box.t)
                val xDelta = deltaMainHandler(box.r + diff, RIGHT)
                val yDelta = deltaMainHandler(box.t + aspectRatio.ratio(xDelta * -1), TOP)

                box.r += aspectRatio.ratioInverted(yDelta * -1)
                box.t += yDelta
            }
            RIGHT_BOTTOM -> {
                if (isFree) {
                    val xDelta = deltaMainHandler(pointerX, RIGHT)
                    val yDelta = deltaMainHandler(pointerY, BOTTOM)

                    box.r += xDelta
                    box.b += yDelta
                    return true
                }

                val diff = min(pointerX - box.r, pointerY - box.b)
                val xDelta = deltaMainHandler(box.r + diff, RIGHT)
                val yDelta = deltaMainHandler(box.b + aspectRatio.ratio(xDelta), BOTTOM)

                box.r += aspectRatio.ratioInverted(yDelta)
                box.b += yDelta
            }
            LEFT_BOTTOM -> {
                if (isFree) {
                    val xDelta = deltaMainHandler(pointerX, LEFT)
                    val yDelta = deltaMainHandler(pointerY, BOTTOM)

                    box.l += xDelta
                    box.b += yDelta
                    return true
                }

                val diff = min(pointerX - box.l, pointerY + box.b)
                val xDelta = deltaMainHandler(box.l + diff, LEFT)
                val yDelta = deltaMainHandler(box.b + aspectRatio.ratio(xDelta * -1), BOTTOM)

                box.l += aspectRatio.ratioInverted(yDelta * -1)
                box.b += yDelta
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
     * Calculate the delta value based on the requested value, and the current value retrieved from the handler.
     * Will first check if the new value is within the inner and outer bound.
     * If not the value is either restricted to the outer bound or the inner bound.
     * @param pointerValue: the new value of the handler.
     * @param type: the handler type to retrieve the handler from.
     * @return the delta distance this handler can move.
     */
    private fun deltaMainHandler(pointerValue: Float, type: HandlerType): Float {
        // Get the required properties from the handler
        val (_, value, outBound, inBound, _, _, isOuter) = getHandler(type)

        // Range is inverted based on the handler
        val range = if (isOuter) {
            inBound..outBound
        } else {
            outBound..inBound
        }

        return if (pointerValue in range) {
            pointerValue - value
        } else {
            // Check the outer bound
            if ((isOuter && pointerValue > outBound) || (!isOuter && pointerValue < outBound)) {
                outBound - value
            } else {
                inBound - value
            }
        }
    }

    /**
     * Update the given delta based on the restrictions of the dependent handler.
     * Will either further restrict or keep the current delta,
     * based on the outer and center bound of the handler.
     * Center bound is used as there is a second dependent handler also moving towards the center.
     * Accounts for the aspect ratio, as dependent handlers can only move according to the ratio of the main.
     * @param delta: the current maximum delta
     * @param type: the handler type of the dependent handler.
     * @param invert: indicates if the movement should be inverted or not (e.g left inwards is a + value, while right inwards is a - value)
     * @return the updated delta value.
     */
    private fun deltaDependentHandler(delta: Float, type: HandlerType, invert: Boolean): Float {
        // Get the required handler properties for a dependent handler
        val (_, value, outBound, _, centerBound, isHorizontal, isOuter) = getHandler(type)

        // Apply the ratio compared to the main handler
        var diff = if (isHorizontal) {
            aspectRatio.ratio(delta) / 2f
        } else {
            aspectRatio.ratioInverted(delta) / 2f
        }

        // Account for sides moving mirrored.
        val projected = if (invert) {
            value - diff
        } else {
            value + diff
        }

        val startDiff = diff

        // Constraints: centerBound >= outerHandler <= outerBound
        if (isOuter) {
            if (projected > outBound) {
                diff = outBound - value
            } else if (projected < centerBound) {
                diff = centerBound - value
            }
            // Constraints:  outerBound >= innerHandler <= centerBound
        } else {
            if (projected < outBound) {
                diff = outBound - value
            } else if (projected > centerBound) {
                diff = centerBound - value
            }
        }

        // Make sure to revert any direction as it does not apply to the main handler.
        if (invert && diff != startDiff) {
            diff *= -1
        }

        // Invert the ratio to get the delta for the main handler
        return if (isHorizontal) {
            aspectRatio.ratioInverted(diff) * 2f
        } else {
            aspectRatio.ratio(diff) * 2f
        }
    }

    /**
     * Retrieve all handler data based on the given type and the source box.
     * @param type: the type of the handler to retrieve
     * @param src: the source box to retrieve the bound values from.
     * @return a [CropHandler] containing all data for the current handler.
     */
    private fun getHandler(type: HandlerType, src: Box = box): CropHandler {
        return when (type) {
            LEFT -> {
                CropHandler(type, src.l, bounds.left, src.innerBound.right, src.centerBound.left, isHorizontal = false, isOuter = false)
            }
            TOP -> {
                CropHandler(type, src.t, bounds.top, src.innerBound.bottom, src.centerBound.top, isHorizontal = true, isOuter = false)
            }
            RIGHT -> {
                CropHandler(type, src.r, bounds.right, src.innerBound.left, src.centerBound.right, isHorizontal = false, isOuter = true)
            }
            BOTTOM -> {
                CropHandler(type, src.b, bounds.bottom, src.innerBound.top, src.centerBound.bottom, isHorizontal = true, isOuter = true)
            }
            else -> {
                throw UnknownError()
            }
        }
    }

    /**
     * Checks if the current zoom level is not one,
     * will invoke the zoom listener and if applicable start a new zoom check runnable.
     */
    private fun checkZoomOut() {
        onZoomListener?.invoke(box.center, true)
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
            val boxWidth = box.width
            val boxHeight = box.height

            val boundsWidth = bounds.width()
            val boundsHeight = bounds.height()

            // Check if width and height are smaller than half
            val xSmall = boxWidth <= (boundsWidth / 2)
            val ySmall = boxHeight <= (boundsHeight / 2)

            if (xSmall && ySmall) {
                // Call the listener
                it(box.center, false)
            }
        }
    }

    /**
     * Based on the given handler types and the box,
     * calculate how much the handlers need to move to fit within the current bounds.
     * First checks the inner handler, then the outer handler.
     * Only intended for boxes that fit within the bounds.
     * @param inHandlerType the inner handler type (left/top) to retrieve the handler
     * @param outHandlerType the outer handler type (right/bottom) to retrieve the handler
     * @param source: the source box data, defaults to [box]
     * @return the delta distance that the box is outside of the bounds.
     */
    private fun getAxisDiff(
        inHandlerType: HandlerType,
        outHandlerType: HandlerType,
        source: Box = box
    ): Float {
        val i = getHandler(inHandlerType, source)
        val o = getHandler(outHandlerType, source)
        var diff = 0f

        if (i.value < i.outBound) {
            diff = i.outBound - i.value
        } else if (o.value > o.outBound) {
            diff = o.outBound - o.value
        }
        return diff
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
        // TODO, improve?
        var restrictedX = moveX - boxMoveStart.x
        var restrictedY = moveY - boxMoveStart.y

        // Set the deltas to the (currently) unrestricted values
        val deltaX = restrictedX
        val deltaY = restrictedY

        val startAxisX = AxisData(Pair(borderBoxStart.l, borderBoxStart.r), Pair(bounds.left, bounds.right))
        val startAxisY = AxisData(Pair(borderBoxStart.t, borderBoxStart.b), Pair(bounds.top, bounds.bottom))

        // Calculated the restricted values
        restrictedX = capDelta(restrictedX, startAxisX)
        restrictedY = capDelta(restrictedY, startAxisY)

        // Move the box itself
        box.l = borderBoxStart.l + restrictedX
        box.r = borderBoxStart.r + restrictedX

        box.t = borderBoxStart.t + restrictedY
        box.b = borderBoxStart.b + restrictedY

        val axisX = AxisData(Pair(box.l, box.r), Pair(bounds.left, bounds.right))
        val axisY = AxisData(Pair(box.t, box.b), Pair(bounds.top, bounds.bottom))

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
