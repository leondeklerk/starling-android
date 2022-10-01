package com.leondeklerk.starling.media

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.graphics.minus
import com.leondeklerk.starling.extensions.times
import com.leondeklerk.starling.media.gestures.SwipeDirection
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MediaPagerLayout(context: Context, attributeSet: AttributeSet?) : FrameLayout(context, attributeSet) {

    // Listeners
    var onDismissStart: (() -> Unit)? = null
    var onDismissEnd: ((completed: Boolean) -> Unit)? = null
    var onDismissState: ((state: Float) -> Unit)? = null
    var onContainerSizeListener: ((width: Int, height: Int) -> Unit)? = null
    var onScale: ((scaleX: Float, scaleY: Float) -> Unit)? = null
    var onTranslate: ((dX: Float, dY: Float) -> Unit)? = null
    var onReset: (() -> Unit)? = null
    var onEnableScroll: ((enable: Boolean) -> Unit)? = null

    // Static state
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var dismissSlop = 0f

    // Action state
    private var sScale = PointF()
    private var sTranslation = PointF()
    private var start = PointF()
    private var direction = SwipeDirection.NONE
    private var distance = 0f
    private var previous = PointF()
    private var dismissInitiated = false
    private var initialized = false
    private var scalingStarted = false

    var ready = false
    var touchCaptured = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dismissSlop = h / THRESHOLD_DIVIDER

        onContainerSizeListener?.invoke(w, h)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        if (!ready || (ready && (!initialized && event.action != ACTION_DOWN))) {
            return true
        }

        if (event.pointerCount > 1) {
            onEnableScroll?.invoke(false)
            scalingStarted = true
            return super.dispatchTouchEvent(event)
        }

        val allowDismiss = !scalingStarted && !touchCaptured

        when (event.action) {
            ACTION_DOWN -> onActionDown(event)
            ACTION_MOVE -> onActionMove(event, allowDismiss)?.also { return true }
            ACTION_CANCEL, ACTION_UP -> onActionEnd(allowDismiss)
        }

        return super.dispatchTouchEvent(event)
    }

    /**
     * Handler for an action down event.
     * @param event the motion event
     */
    private fun onActionDown(event: MotionEvent) {
        previous = PointF(event.rawX, event.rawY)
        initialized = true
        start = PointF(event.rawX, event.rawY)
        sScale = PointF(1f, 1f)
        sTranslation = PointF(0f, 0f)
    }

    private fun onActionMove(event: MotionEvent, allowDismiss: Boolean): Boolean? {
        val delta = PointF(event.rawX, event.rawY) - previous

        val current = PointF(event.rawX, event.rawY)
        previous = current
        distance = getDistance(current)
        if (allowDismiss && direction == SwipeDirection.NONE && distance >= touchSlop) {
            direction = getDirection(current)

            if (direction == SwipeDirection.DOWN) {
                dismissInitiated = true
                onDismissStart?.invoke()
            }
        }

        if (allowDismiss && direction == SwipeDirection.DOWN) {
            val fraction = min(max(0f, distance - touchSlop), dismissSlop) / dismissSlop
            val currentScale = START_SCALE + (END_SCALE - START_SCALE) * fraction

            val scalar = sScale * currentScale
            onScale?.invoke(scalar.x, scalar.y)
            onTranslate?.invoke(delta.x, delta.y)

            onDismissState?.invoke(fraction)

            return true
        }
        return null
    }

    /**
     * End the current movement action and reset the state.
     * @param allowDismiss if dismiss is allowed or not
     */
    private fun onActionEnd(allowDismiss: Boolean) {

        if (dismissInitiated && allowDismiss) {
            if (distance >= dismissSlop) {
                onDismissEnd?.invoke(true)
            } else {
                onReset?.invoke()
                onDismissEnd?.invoke(false)
            }
        }

        scalingStarted = false
        dismissInitiated = false
        distance = 0f
        start = PointF()
        direction = SwipeDirection.NONE
        initialized = false
    }

    /**
     * Get the distance between the start point and the current value.
     * @param current the current location
     * @return the distance
     */
    private fun getDistance(current: PointF): Float {
        val diff = current - start
        val dx = diff.x
        val dy = diff.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Finds the angle between the current point and the start point.
     * Angles go from 0 -> 360 ccw.
     *
     * @param current the current point
     * @return the angle between the two points
     */
    private fun getDirection(current: PointF): SwipeDirection {
        val rad = atan2((start.y - current.y).toDouble(), (current.x - start.x).toDouble()) + Math.PI
        val angle = (rad * 180 / Math.PI + 180) % 360
        return SwipeDirection.from(angle)
    }

    companion object {
        private const val START_SCALE = 1f
        private const val END_SCALE = 0.85f
        private const val THRESHOLD_DIVIDER = 18f
    }
}
