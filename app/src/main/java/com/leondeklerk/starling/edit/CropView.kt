package com.leondeklerk.starling.edit

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toRect
import com.leondeklerk.starling.databinding.ViewCropBinding

/**
 * A wrapper View that defines and handles all controls related to cropping an image.
 * Uses a [CropOverlayView] to draw and move a crop box on the screen.
 * Responsible for propagating the correct events from and to an [EditView]
 * Additionally this view contains control buttons for cropping:
 * box aspect ratio, rotate, and reset.
 */
class CropView(context: Context, attributeSet: AttributeSet?) : ConstraintLayout(
    context,
    attributeSet
) {
    private var binding: ViewCropBinding = ViewCropBinding.inflate(LayoutInflater.from(context), this, true)
    private var overlay = binding.overlay
    private var moving = false

    var onBoundsHitHandler: ((delta: PointF, types: Pair<HandlerType, HandlerType>) -> Unit)? = null
    var onZoomHandler: ((center: PointF, out: Boolean) -> Unit)? = null
    var onButtonReset: (() -> Unit)? = null
    var onButtonRotate: (() -> Unit)? = null
    var onButtonAspect: (() -> Unit)? = null
    var onTouchHandler: ((event: MotionEvent) -> Boolean)? = null

    companion object {
        private const val BOX_DURATION = 100L
    }

    init {
        binding.buttonAspect.setOnClickListener {
            onButtonAspect?.invoke()
        }

        binding.buttonRotate.setOnClickListener {
            onButtonRotate?.invoke()
        }

        binding.buttonReset.setOnClickListener {
            onButtonReset?.invoke()
        }

        overlay.onZoomHandler = { center, out ->
            onZoomHandler?.invoke(center, out)
        }

        overlay.onBoundsHitHandler = { delta, types ->
            onBoundsHitHandler?.invoke(delta, types)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)

        // If touch is overridden via an outside component use that
        onTouchHandler?.let {
            return it.invoke(event)
        }

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (!moving) {
                        moving = startMove(PointF(event.x, event.y))
                    }

                    // Can only move if movement was started.
                    if (moving) {
                        move(PointF(event.x, event.y))
                    }
                } else {
                    if (moving) {
                        cancelMove()
                        moving = false
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                endMove()
                moving = false
            }
        }
        return true
    }

    /**
     * Checks if a new move can be started.
     * @param location: the current pointerlocation.
     * @return if movement was started or not.
     */
    fun startMove(location: PointF): Boolean {
        return overlay.startMove(location)
    }

    /**
     * Execute a move on the crop overlay.
     * @param location: the coordinates of the touch point.
     */
    fun move(location: PointF) {
        overlay.onMove(location)
    }

    /**
     * Cancel the current crop movement.
     */
    fun cancelMove() {
        overlay.cancelMove()
    }

    /**
     * End the current crop movement.
     */
    fun endMove() {
        overlay.endMove()
    }

    /**
     * Retrieves the current rectangle of the crop box.
     * @return the rectangle containing the box.
     */
    fun getCropperRect(): Rect {
        return overlay.cropBox.toRect()
    }

    /**
     * Checks if the current overlay was changed by the users.
     * @return if the overlay is changed or not.
     */
    fun isTouched(): Boolean {
        return overlay.isTouched()
    }

    /**
     * Resets the overlay to its initial position.
     * Takes [BOX_DURATION] to animate.
     */
    fun reset() {
        overlay.reset(BOX_DURATION)
    }

    /**
     * Updates the zoom level of the overlay.
     * @param level: the new zom level of the image.
     */
    fun setZoomLevel(level: Float) {
        overlay.setZoomLevel(level)
    }

    /**
     * Calls the overlay onZoomIn listener,
     * and sets the animation duration.
     */
    fun onZoomedIn() {
        overlay.onZoomedIn(BOX_DURATION * 2L)
    }

    /**
     * Updates the bounds of the overlay based on the new image bounds.
     * @param bounds: the new bounds of the image.
     */
    fun updateBounds(bounds: RectF) {
        overlay.updateBounds(bounds)
    }

    /**
     * Restricts the border of the overlay to the current bounds.
     * Produces an animation of [BOX_DURATION] length.
     */
    fun restrictBorder() {
        overlay.restrictBorder(BOX_DURATION)
    }

    /**
     * Initializes the [CropOverlayView].
     * @param bounds: the bounds of the image to overlay.
     */
    fun initialize(bounds: RectF) {
        overlay.setInitialValues(bounds)
    }
}
