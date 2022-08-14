package com.leondeklerk.starling.edit.crop

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.graphics.toRect
import com.leondeklerk.starling.edit.EditView
import com.leondeklerk.starling.extensions.dpToPx
import com.leondeklerk.starling.extensions.drawCircle
import com.leondeklerk.starling.extensions.drawLine

/**
 * Custom view that draws a resizable grid over an imageView.
 * Support the movement of the whole box, all sides and all corners.
 * Will automatically restrict based on bounds and fire zoom event
 * when smaller than half the image (X and Y).
 * Uses a [CropMoveHandler] to handle box movements.
 * Intended to be used by a [CropView] in an [EditView]
 */
class CropOverlayView(context: Context, attributeSet: AttributeSet?) : View(
    context,
    attributeSet
) {
    private var box: Box? = null
    private var moveHandler: CropMoveHandler? = null
    private var startBox: Box? = null
    private var setOnAnimate = false
    private var aspectRatio: AspectRatio = AspectRatio.FREE
    private var bounds = RectF()

    var onBoundsHitHandler: ((delta: PointF, types: Pair<HandlerType, HandlerType>) -> Unit)? = null
    var onZoomHandler: ((center: PointF, out: Boolean) -> Unit)? = null
    val outline: Rect
        get() = box?.rect?.toRect() ?: Rect()

    var zoomLevel: Float
        get() = moveHandler?.zoomLevel ?: 1f
        set(value) {
            moveHandler?.zoomLevel = value
        }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas?.let {
            drawBackground(canvas)
            drawBorder(canvas)
            drawGuidelines(canvas)
        }
    }

    /**
     * Upon updating the aspect ratio,
     * reinitialize the components.
     * @param aspectRatio the new aspect ratio
     * @param duration: the animation duration.
     */
    fun updateRatio(aspectRatio: AspectRatio, duration: Long) {
        initialize(bounds, aspectRatio, duration, true)
    }

    /**
     * Responsible for storing the current aspect ratio and bounds.
     * Will update the original aspect ratio based on the image bounds.
     * Creates a new [Box] component and related [CropMoveHandler] component.
     * If reinitializing, will animate from the previous box to the new value.
     * @param rect: the image bounds
     * @param aspectRatio the current aspect ratio
     * @param duration the duration of animation when resetting
     * @param animate: indicates if the initialization should be animated or not.
     */
    fun initialize(
        rect: RectF,
        aspectRatio: AspectRatio,
        duration: Long = 0L,
        animate: Boolean = false
    ) {
        this.aspectRatio = aspectRatio
        this.bounds = rect

        if (aspectRatio == AspectRatio.ORIGINAL) {
            this.aspectRatio.xRatio = rect.width().toInt()
            this.aspectRatio.yRatio = rect.height().toInt()
        }

        val targetRect = Box.from(rect, aspectRatio, dpToPx(MIN_SIZE_DP)).rect

        if (animate) {
            setOnAnimate = true
            animateBoxUpdate(targetRect, duration)
        } else {
            setupComponents()
        }
    }

    /**
     * Updates the current image bounds.
     * @param rect: the rectangle containing the current image bounds.
     */
    fun updateBounds(rect: RectF) {
        bounds = rect
        // Set the moveHandler bounds (restricted to the view size)
        moveHandler?.updateBounds(rect)
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
    fun restrictBorder(duration: Long) {
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
     * Resets the cropper by re-initializing the component with an animation.
     * @param bounds: the new bounds of the reset component.
     * @param duration: the duration of the animation.
     */
    fun reset(bounds: RectF, duration: Long) {
        initialize(bounds, aspectRatio, duration, true)
    }

    /**
     * Checks if the current crop box was changed from the initial one.
     * @return if this box was touched or not.
     */
    fun isTouched(): Boolean {
        return startBox?.rect != box?.rect
    }

    /**
     * Instantiate all related component of the crop overlay.
     * Creates a new box and handler,
     * makes sure everything is drawn to the screen.
     */
    private fun setupComponents() {
        createBox()
        createMoveHandler()
        invalidate()
    }

    /**
     * Creates a new box based on the bounds and ratio,
     * also creates a initial copy.
     */
    private fun createBox() {
        box = Box.from(bounds, aspectRatio, dpToPx(MIN_SIZE_DP))
        startBox = box!!.copy()
    }

    /**
     * Creates the move handler based on the box,
     * registers the listeners of the handler.
     */
    private fun createMoveHandler() {
        moveHandler = CropMoveHandler(
            bounds, box!!, dpToPx(HANDLER_BOUNDS_DP), dpToPx(THRESHOLD_DP), dpToPx(BASE_TRANSLATE_DP)
        )

        moveHandler?.aspectRatio = aspectRatio

        // Set up listeners
        moveHandler?.onBoundsHitListener = { delta, types ->
            onBoundsHitHandler?.invoke(delta, types)
        }

        moveHandler?.onZoomListener = { center, out ->
            onZoomHandler?.invoke(center, out)
        }
    }

    /**
     * Draw the background around the border box.
     * Draws a darkened overlay between the border, and the image bounds.
     * @param canvas the canvas to draw on
     */
    private fun drawBackground(canvas: Canvas) {
        val borderBox = box ?: return

        // The (simplified) bounds to draw from
        val imageBounds = RectF(
            0f, 0f, width.toFloat(), height.toFloat()
        )

        val paint = Paint().apply {
            this.color = BACKGROUND_COLOR
            this.alpha = BACKGROUND_ALPHA
        }

        // Create the 4 background sections
        val leftBackground = RectF(imageBounds.left, imageBounds.top, borderBox.l, imageBounds.bottom)

        val topBackground = RectF(
            borderBox.l,
            imageBounds.top,
            borderBox.r,
            borderBox.t
        )
        val rightBackground = RectF(borderBox.r, imageBounds.top, imageBounds.right, imageBounds.bottom)

        val bottomBackground = RectF(
            borderBox.l,
            borderBox.b,
            borderBox.r,
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
        val borderBox = box ?: return

        val paint = Paint().apply {
            this.color = GUIDELINES_COLOR
        }
        paint.strokeWidth = dpToPx(GUIDELINES_WIDTH_DP)
        paint.alpha = GUIDELINES_ALPHA

        // Set the space between each item
        val spaceX = borderBox.width / GUIDELINES_COUNT
        val spaceY = borderBox.height / GUIDELINES_COUNT

        // Draw the lines
        for (i in 1..GUIDELINES_COUNT) {
            val left = borderBox.l
            val right = borderBox.r
            val top = borderBox.t
            val bottom = borderBox.b

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
        val borderBox = box ?: return

        val white = Paint().apply {
            this.color = BORDER_CORNER_COLOR
            this.strokeWidth = dpToPx(BORDER_CORNER_WIDTH_DP)
        }

        val whiteAlpha = Paint().apply {
            this.color = BORDER_COLOR
            this.strokeWidth = dpToPx(BORDER_WIDTH_DP)
            this.alpha = BORDER_ALPHA
        }

        // Draw the sides
        canvas.drawLine(borderBox.left, whiteAlpha)
        canvas.drawLine(borderBox.top, whiteAlpha)
        canvas.drawLine(borderBox.right, whiteAlpha)
        canvas.drawLine(borderBox.bottom, whiteAlpha)

        // Draw the corners
        canvas.drawCircle(borderBox.leftTop, dpToPx(BORDER_CORNER_RADIUS), white)
        canvas.drawCircle(borderBox.rightTop, dpToPx(BORDER_CORNER_RADIUS), white)
        canvas.drawCircle(borderBox.rightBottom, dpToPx(BORDER_CORNER_RADIUS), white)
        canvas.drawCircle(borderBox.leftBottom, dpToPx(BORDER_CORNER_RADIUS), white)

        // Draw the center figure
        canvas.drawCircle(borderBox.center, dpToPx(BORDER_CORNER_RADIUS), white)
    }

    /**
     * Will animate the updating of the border box.
     * @param sizeTo the rectangle (box) to animate the borderBox to.
     */
    private fun animateBoxUpdate(sizeTo: RectF, duration: Long) {
        box?.let {
            animateBoxSide(HandlerType.LEFT, it.l, sizeTo.left, duration)
            animateBoxSide(HandlerType.TOP, it.t, sizeTo.top, duration)
            animateBoxSide(HandlerType.RIGHT, it.r, sizeTo.right, duration)
            animateBoxSide(HandlerType.BOTTOM, it.b, sizeTo.bottom, duration)
        }
    }

    /**
     * Animator responsible for animating the side updates of the border box.
     * @param side: the side to animate
     * @param from the original value
     * @param to the new value
     */
    private fun animateBoxSide(side: HandlerType, from: Float, to: Float, duration: Long) {
        val animator = ValueAnimator.ofFloat(from, to)

        animator.addUpdateListener { animation ->
            box?.let {
                when (side) {
                    HandlerType.LEFT -> it.l = animation.animatedValue as Float
                    HandlerType.TOP -> it.t = animation.animatedValue as Float
                    HandlerType.RIGHT -> it.r = animation.animatedValue as Float
                    HandlerType.BOTTOM -> it.b = animation.animatedValue as Float
                    else -> {}
                }
                invalidate()
            }
        }

        animator.doOnEnd {
            if (setOnAnimate) {
                setOnAnimate = false
                setupComponents()
            }
        }

        animator.duration = duration
        animator.start()
    }

    companion object {
        private const val MIN_SIZE_DP = 64f
        private const val THRESHOLD_DP = 56f
        private const val HANDLER_BOUNDS_DP = 16f
        private const val BASE_TRANSLATE_DP = 8f
        private const val BACKGROUND_COLOR = Color.BLACK
        private const val BACKGROUND_ALPHA = 150
        private const val GUIDELINES_COLOR = Color.LTGRAY
        private const val GUIDELINES_WIDTH_DP = 1f
        private const val GUIDELINES_ALPHA = 140
        private const val GUIDELINES_COUNT = 3
        private const val BORDER_CORNER_COLOR = Color.WHITE
        private const val BORDER_CORNER_WIDTH_DP = 2f
        private const val BORDER_CORNER_RADIUS = 4f
        private const val BORDER_COLOR = Color.LTGRAY
        private const val BORDER_WIDTH_DP = 1f
        private const val BORDER_ALPHA = 140
    }
}
