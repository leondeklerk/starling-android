package com.leondeklerk.starling.edit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.leondeklerk.starling.extensions.drawCircle
import com.leondeklerk.starling.extensions.drawLine

/**
 * Custom view that draws a resizable grid over an imageView.
 * Support the movement of the whole box, all sides and all corners.
 * Will automatically restrict based on bounds and fire a zoom event
 * when smaller than half the image (X and Y).
 * Uses a [CropMoveHandler] to handle box movements.
 * Intended to be used with an [EditView]
 */
class CropHandlerView(context: Context, attributeSet: AttributeSet?) : View(
    context,
    attributeSet
) {
    private var borderBox: Box? = null
    private val handleBounds = px(16f)
    private var moveHandler: CropMoveHandler? = null

    var boundsHitHandler: ((delta: PointF, types: Pair<HandlerType, HandlerType>) -> Unit)? = null
    var zoomHandler: ((center: PointF, out: Boolean) -> Unit)? = null
    val cropBox: RectF
        get() = borderBox?.getRect() ?: RectF()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas?.let {
            drawBackground(canvas)
            drawBorder(canvas)
            drawGuidelines(canvas)
        }
    }

    /**
     * Sets up the border [Box] and the [CropMoveHandler].
     * Also responsible for registering the listeners,
     * and initiating the first draw.
     */
    fun setInitialValues(rect: RectF) {
        borderBox = Box(
            PointF(rect.left, rect.top),
            PointF(rect.right, rect.top),
            PointF(rect.right, rect.bottom),
            PointF(rect.left, rect.bottom)
        )

        moveHandler = CropMoveHandler(
            rect, borderBox!!, handleBounds, px(64f), px(56f), px(8f)
        )

        // Set up listeners
        moveHandler?.onBoundsHitListener = { delta, types ->
            boundsHitHandler?.invoke(delta, types)
        }

        moveHandler?.onZoomListener = { center, out ->
            zoomHandler?.invoke(center, out)
        }

        invalidate()
    }

    /**
     * Updates the current image bounds.
     * @param rect: the rectangle containing the current image bounds.
     */
    fun updateBounds(rect: RectF) {
        // Set the moveHandler bounds (restricted to the view size)
        moveHandler?.updateBounds(rect)
    }

    /**
     * Responsible for updating the border of the box, either restricting it if it is to large.
     * Or moving the whole box so it fits within the image again.
     * @param duration the length of the resizing/translation animation.
     */
    fun onAxisCentered(duration: Long) {
        moveHandler?.let { moveHandler ->
            // Update the box based on the updated borders.
            animateBoxUpdate(moveHandler.updateBorder(), duration)
        }
    }

    /**
     * Responsible for enlarging the box after the image was zoomed in based on box size.
     * @param duration the duration of the rescaling animation.
     */
    fun onZoomedIn(duration: Long) {
        moveHandler?.let {
            animateBoxUpdate(it.scaleBox(), duration)
        }
    }

    /**
     * Updates the border to make sure it fits within the bounds.
     * @param duration the length of the animation.
     */
    fun onRestrictBorder(duration: Long) {
        moveHandler?.let {
            animateBoxUpdate(it.updateBorder(), duration)
        }
    }

    /**
     * Starts a move.
     * Will check if the move is near a handler to actually start it.
     * @param point the focus point.
     * @return if movement was started or not.
     */
    fun startMove(point: PointF): Boolean {
        moveHandler?.let {
            return it.startMove(point.x, point.y)
        }
        return false
    }

    /**
     * Executes on movement.
     * Responsible for translating the box/edges/corners.
     * @param point the coordinates of the movements.
     */
    fun onMove(point: PointF) {
        val changed = moveHandler?.onMove(point.x, point.y) ?: false
        if (changed) {
            invalidate()
        }
    }

    /**
     * Ends movement (gracefully)
     */
    fun endMove() {
        moveHandler?.endMove()
    }

    /**
     * Cancels all movement
     */
    fun cancelMove() {
        moveHandler?.cancel()
    }

    /**
     * Helper function to update the moveHandler zoomLevel.
     * @param zoomLevel the new zoom level
     */
    fun setZoomLevel(zoomLevel: Float) {
        moveHandler?.zoomLevel = zoomLevel
    }

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
        val spaceX = borderBox.width / 3
        val spaceY = borderBox.height / 3

        // Draw the lines
        for (i in 1..3) {
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
     * Will animate the updating of the border box.
     * @param sizeTo the rectangle (box) to animate the borderBox to.
     */
    private fun animateBoxUpdate(sizeTo: RectF, duration: Long) {
        borderBox?.let {
            animateBoxSide(Side.LEFT, it.left.x, sizeTo.left, duration)
            animateBoxSide(Side.TOP, it.top.y, sizeTo.top, duration)
            animateBoxSide(Side.RIGHT, it.right.x, sizeTo.right, duration)
            animateBoxSide(Side.BOTTOM, it.bottom.y, sizeTo.bottom, duration)
        }
    }

    /**
     * Animator responsible for animating the side updates of the border box.
     * @param side: the side to animate
     * @param from the original value
     * @param to the new value
     */
    private fun animateBoxSide(side: Side, from: Float, to: Float, duration: Long) {
        val animator = ValueAnimator.ofFloat(from, to)

        animator.addUpdateListener { animation ->
            borderBox?.let {
                when (side) {
                    Side.LEFT -> {
                        it.left.x = animation.animatedValue as Float
                    }
                    Side.TOP -> {
                        it.top.y = animation.animatedValue as Float
                    }
                    Side.RIGHT -> {
                        it.right.x = animation.animatedValue as Float
                    }
                    Side.BOTTOM -> {
                        it.bottom.y = animation.animatedValue as Float
                    }
                    Side.NONE -> {
                    }
                }
                invalidate()
            }
        }

        animator.duration = duration
        animator.start()
    }
}
