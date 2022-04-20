package com.leondeklerk.starling.edit.draw.layers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import com.leondeklerk.starling.R
import com.leondeklerk.starling.extensions.contains
import com.leondeklerk.starling.extensions.enlargeBy

/**
 * Data class representing a text layer on a canvas.
 * [DrawLayer] of type [DrawLayerType.TEXT].
 * Each text layer has a associated pressed state indicating if it is active or not.
 * A text layer can be scaled, translated, deleted, and edited.
 * Uses a [StaticLayout] to represent the actual text.
 * @param index the index of this layer on the canvas within all the list of layers
 * @param text the current string of text of this layer
 * @param color the text color of this layer
 * @param textSize the size of the text
 * @param context the current view context of the layer, used to retrieve density information
 * @param listener a listener object containing interaction listeners for delete/update/translate/scale actions
 * @param pressed a boolean representing the current state of the layer (active/not active)
 */
data class TextLayer(
    override var index: Int,
    var text: String,
    var color: Int,
    var textSize: Float,
    val context: Context,
    val listener: TextLayerListener,
    var pressed: Boolean = false
) : DrawLayer(index, DrawLayerType.TEXT) {

    var currentScale: Float = 1f
        private set

    private var layout: StaticLayout? = null
    private val origin: PointF = PointF()
    private var borderPaint = Paint()
    private var deletePaint = Paint()
    private val density: Float = context.resources.displayMetrics.density
    private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_delete_24)
    private var alpha: Int = 0
    private var animating = false

    /**
     * Current width of the text itself
     */
    private val width: Int
        get() {
            return layout?.width ?: 0
        }

    /**
     * Current height of the text itself
     */
    private val height: Int
        get() {
            return layout?.height ?: 0
        }

    /**
     * The current boundaries of this layer offset by [BOUNDS_OFFSET] dp.
     */
    private val bounds: RectF
        get() {
            return RectF(origin.x, origin.y, origin.x + width, origin.y + height).enlargeBy(BOUNDS_OFFSET * density)
        }

    /**
     * The current bounds of the delete icon,
     * enlarged by [ICON_BOUNDS_OFFSET] dp.
     */
    private val iconTouchBounds: Rect
        get() {
            return getIconBounds().enlargeBy(ICON_BOUNDS_OFFSET * density)
        }

    init {
        // Initialize the paint for the border
        borderPaint.style = Paint.Style.STROKE
        borderPaint.color = Color.LTGRAY
        borderPaint.alpha = alpha
        borderPaint.strokeWidth = BORDER_WIDTH * density

        // Initialize the delete icon and background
        deletePaint.color = Color.LTGRAY
        deletePaint.alpha = alpha
        deleteIcon?.setTint(Color.BLACK)
        deleteIcon?.mutate()
        deleteIcon?.alpha = alpha
    }

    override fun draw(canvas: Canvas?) {
        canvas?.let { it ->
            it.save()
            it.translate(origin.x, origin.y)
            layout?.draw(it)
            it.restore()

            if (pressed) {
                // In the pressed state draw the bounds and icon
                it.drawRoundRect(bounds, BORDER_CORNER_RADIUS * density, BORDER_CORNER_RADIUS * density, borderPaint)
                it.drawCircle(bounds.left, bounds.top, ICON_RADIUS * density, deletePaint)
                deleteIcon?.draw(it)
            }
        }
    }

    override fun mapToScale(scalar: Float): TextLayer {
        return TextLayer(index, text, color, textSize * scalar, context, listener, false)
            .createLayout()
            .setOrigin(PointF(origin.x * scalar, origin.y * scalar))
            .setFadeLevel(alpha)
    }

    /**
     * Part of the construction chain of a TextLayer,
     * creates a layout based on the current text, color, and size.
     * @return the instance of this layer
     */
    fun createLayout(): TextLayer {
        val paint = getPaint()
        val length = paint.measureText(text).toInt()
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, length)
        layout = builder.build()
        return this
    }

    /**
     * Part of the construction chain of a TextLayer,
     * calculates and sets the origin based on a starting center point.
     * @param startCenter the point representing the center of the new layer.
     * @return the instance of this layer
     */
    fun createOrigin(startCenter: PointF): TextLayer {
        // Calculate the origin in relation to the center based on width/height
        origin.x = startCenter.x - width / 2f
        origin.y = startCenter.y - width / 2f
        deleteIcon?.bounds = getIconBounds()
        return this
    }

    /**
     * Scale the current textSize by the scalar.
     * Create a new layout and update the origin and bounds.
     * @param scalar amount of scaling that will be applied
     * @return this instance
     */
    fun scale(scalar: Float): TextLayer {
        // Set the correct sizes
        currentScale *= scalar
        textSize *= scalar

        // Create a new layout
        val prevHeight = height
        val prevWidth = width
        createLayout()

        // Update the dimensional properties
        val diffH = (height - prevHeight) / 2f
        val diffW = (width - prevWidth) / 2f
        origin.offset(-diffW, -diffH)
        deleteIcon?.bounds = getIconBounds()

        return this
    }

    /**
     * Update the current text with a new string, color, and size.
     * Creates a new layout an updates the dimensions of the current layer.
     * @param newText the updated text
     * @param newColor the (new) color of the text
     * @param newSize the (new) size of the text
     * @return this instance
     */
    fun updateText(newText: String, newColor: Int, newSize: Float): TextLayer {
        // Update the text, color and size
        text = newText
        textSize = newSize * currentScale
        color = newColor
        // Create a new layout
        val prevHeight = height
        val prevWidth = width
        createLayout()

        // Update the dimensions
        val diffH = (height - prevHeight) / 2f
        val diffW = (width - prevWidth) / 2f
        origin.offset(-diffW, -diffH)
        deleteIcon?.bounds = getIconBounds()

        return this
    }

    /**
     * Translate this layer by a given x and y.
     * A translation only needs an update of the origin.
     * @param dX the x translation value
     * @param dY the y translation value
     * @return this instance
     */
    fun translate(dX: Float, dY: Float): TextLayer {
        origin.offset(dX, dY)
        deleteIcon?.bounds = getIconBounds()

        return this
    }

    /**
     * Check if a point is within the current bounds.
     * If the layer is active (pressed, not animating), the iconTouchBounds also count as current bounds.
     * @param location the point to check for.
     * @return true if within bounds, false if not
     */
    fun isOnLayer(location: PointF): Boolean {
        return bounds.contains(location.x, location.y) || ((pressed && !animating) && iconTouchBounds.contains(location))
    }

    /**
     * Set to current state to pressed and invoke the onPress listener.
     * @return the current instance
     */
    fun press(): TextLayer {
        pressed = true
        listener.onPressed(this)
        return this
    }

    /**
     * Release the current layer (un-press) and invoke the listener.
     * @return the current layer instance
     */
    fun release(): TextLayer {
        pressed = false
        listener.onReleased(this)
        return this
    }

    /**
     * Checks if a given location is within the icon touch bounds.
     * If so the delete listener is invoked.
     * @param location the location to check for.
     * @return true if within bounds, false if not
     */
    fun checkDeleted(location: PointF): Boolean {
        // If the delete icon was touched, invoke the listener
        if ((pressed && !animating) && iconTouchBounds.contains(location)) {
            listener.onDeleted(this)
            return true
        }
        return false
    }

    /**
     * Checks if a given location is within the bounds,
     * if so the edit listener is invoked.
     * @param location the given location to check for.
     * @return true if within bounds, false if not
     */
    fun checkEdit(location: PointF): Boolean {
        if ((pressed && !animating) && bounds.contains(location) && !iconTouchBounds.contains(location)) {
            listener.onEdit(this)
            return true
        }
        return false
    }

    /**
     * Set the current state of the layer.
     * This includes the pressed state and the alpha.
     * @param active if the current layer is pressed or not
     * @param alpha the new alpha value of the border/icon paint
     * @param isAnimating if the layer is animating or not
     * @return the current instance
     */
    fun setState(active: Boolean, alpha: Int, isAnimating: Boolean): TextLayer {
        pressed = active
        setFadeLevel(alpha)
        setAnimating(isAnimating)
        return this
    }

    /**
     * Set the current fade level of the layer.
     * @param value the border/icon paint alpha of this layer
     * @return the current instance
     */
    fun setFadeLevel(value: Int): TextLayer {
        alpha = value
        borderPaint.alpha = alpha
        deletePaint.alpha = alpha
        deleteIcon?.alpha = alpha
        return this
    }

    fun setAnimating(value: Boolean): TextLayer {
        animating = value
        return this
    }

    /**
     * Set the origin of this layer.
     * @param dst the new origin value
     * @return the current layer instance
     */
    private fun setOrigin(dst: PointF): TextLayer {
        origin.set(dst.x, dst.y)
        deleteIcon?.bounds = getIconBounds()
        return this
    }

    /**
     * Get the bounds of the icon based on the radius, scale, and current layer bounds.
     * @return a rectangle representing the bounds of the icon.
     */
    private fun getIconBounds(): Rect {
        // The offset is the distance between the left side of the icon and the left side of the bounds.
        // Which is actually the radius of the icon * scale relative to the density.
        // As the center is at the left-top point of the bounds.
        val offset = (ICON_RADIUS * ICON_SCALE) * density

        val left = bounds.left - offset
        val right = bounds.left + offset
        val top = bounds.top - offset
        val bottom = bounds.top + offset

        return RectF(left, top, right, bottom).toRect()
    }

    /**
     * Create a [TextPaint] object based on the current size and color.
     * @return the paint to be used in the layout.
     */
    private fun getPaint(): TextPaint {
        val textPaint = TextPaint()
        textPaint.isAntiAlias = true
        textPaint.isFilterBitmap = true
        textPaint.textSize = textSize * context.resources.displayMetrics.scaledDensity
        textPaint.color = color

        return textPaint
    }

    companion object {
        const val ICON_RADIUS = 12
        const val ICON_SCALE = 0.9f
        const val BORDER_CORNER_RADIUS = 2
        const val BORDER_WIDTH = 4
        const val BOUNDS_OFFSET = 12
        const val ICON_BOUNDS_OFFSET = 4
    }

    interface TextLayerListener {
        fun onPressed(layer: TextLayer) {}
        fun onReleased(layer: TextLayer) {}
        fun onDeleted(layer: TextLayer) {}
        fun onEdit(layer: TextLayer) {}
    }
}
