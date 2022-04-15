package com.leondeklerk.starling.edit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
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
import timber.log.Timber

sealed class DrawLayer(open var index: Int, val type: DrawLayerType) {
    abstract fun draw(canvas: Canvas?)
    abstract fun mapToScale(scalar: Float): DrawLayer
}

data class PaintLayer(override var index: Int, val path: Path, val brush: Paint) : DrawLayer(index, DrawLayerType.PAINT) {
    override fun draw(canvas: Canvas?) {
        canvas?.drawPath(path, brush)
    }

    /**
     * Map the current layer to a scaled copy of itself.
     */
    override fun mapToScale(scalar: Float): PaintLayer {
        val scaleMatrix = Matrix()
        scaleMatrix.setScale(scalar, scalar)
        val scaledPath = Path(path)
        scaledPath.transform(scaleMatrix)
        val scaledBrush = Paint(brush)
        scaledBrush.strokeWidth *= scalar
        return PaintLayer(index, scaledPath, scaledBrush)
    }
}

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
    val origin: PointF = PointF()
    var layout: StaticLayout? = null
    private var borderPaint = Paint()
    private var deletePaint = Paint()

    private val density: Float = context.resources.displayMetrics.density
    private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_delete_24)
    private var alpha: Int = 0

    val width: Int
        get() {
            return layout?.width ?: 0
        }

    val height: Int
        get() {
            return layout?.height ?: 0
        }

    val center: PointF
        get() {
            return PointF(origin.x + width / 2f, origin.y + height / 2f)
        }

    private val bounds: RectF
        get() {
            return RectF(origin.x, origin.y, origin.x + width, origin.y + height).enlargeBy(12 * density)
        }

    private val iconTouchBounds: Rect
        get() {
            return getIconBounds().enlargeBy(4 * density)
        }

    init {
        borderPaint.style = Paint.Style.STROKE
        borderPaint.color = Color.LTGRAY
        borderPaint.alpha = alpha
        borderPaint.strokeWidth = BORDER_WIDTH * density
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

            // Pressed state
            it.drawRoundRect(bounds, BORDER_CORNER_RADIUS * density, BORDER_CORNER_RADIUS * density, borderPaint)
            it.drawCircle(bounds.left, bounds.top, ICON_RADIUS * density, deletePaint)
            deleteIcon?.draw(it)
        }
    }

    override fun mapToScale(scalar: Float): TextLayer {
        return TextLayer(index, text, color, textSize * scalar, context, listener, false)
            .createLayout()
            .setOrigin(PointF(origin.x * scalar, origin.y * scalar))
            .setFadeLevel(alpha)
    }

    fun scale(scalar: Float): TextLayer {
        currentScale *= scalar
        textSize *= scalar
        val prevHeight = height
        val prevWidth = width
        createLayout()
        val diffH = (height - prevHeight) / 2f
        val diffW = (width - prevWidth) / 2f
        origin.offset(-diffW, -diffH)
        deleteIcon?.bounds = getIconBounds()
        return this
    }

    fun updateText(newText: String, newColor: Int, newSize: Float): TextLayer {
        text = newText
        textSize = newSize * currentScale
        color = newColor
        val prevHeight = height
        val prevWidth = width
        createLayout()
        val diffH = (height - prevHeight) / 2f
        val diffW = (width - prevWidth) / 2f
        origin.offset(-diffW, -diffH)
        deleteIcon?.bounds = getIconBounds()
        return this
    }

    private fun getIconBounds(): Rect {
        val offset = (ICON_RADIUS * ICON_SCALE) * density
        val left = bounds.left - offset
        val right = bounds.left + offset
        val top = bounds.top - offset
        val bottom = bounds.top + offset
        return RectF(left, top, right, bottom).toRect()
    }

    fun translate(dX: Float, dY: Float): TextLayer {
        origin.offset(dX, dY)
        deleteIcon?.bounds = getIconBounds()
        return this
    }

    private fun getPaint(): TextPaint {
        val textPaint = TextPaint()
        textPaint.isAntiAlias = true
        textPaint.isFilterBitmap = true
        textPaint.textSize = textSize * context.resources.displayMetrics.scaledDensity
        textPaint.color = color
        return textPaint
    }

    fun createOrigin(startCenter: PointF): TextLayer {
        origin.x = startCenter.x - width / 2f
        origin.y = startCenter.y - width / 2f
        deleteIcon?.bounds = getIconBounds()
        return this
    }

    private fun setOrigin(dst: PointF): TextLayer {
        origin.set(dst.x, dst.y)
        deleteIcon?.bounds = getIconBounds()
        return this
    }

    fun createLayout(): TextLayer {
        val paint = getPaint()
        val length = paint.measureText(text).toInt()
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, length)
        layout = builder.build()
        return this
    }

    fun isOnLayer(location: PointF): Boolean {
        return bounds.contains(location.x, location.y) || (pressed && iconTouchBounds.contains(location))
    }

    fun release(): TextLayer {
        pressed = false
        listener.onReleased(this)
        return this
    }

    fun checkDeleted(location: PointF): Boolean {
        Timber.d("Check delete")
        if (iconTouchBounds.contains(location)) {
            // We can call the destroy listener
            listener.onDeleted(this)
            return true
        }
        return false
    }

    fun checkEdit(location: PointF): Boolean {
        if (bounds.contains(location)) {
            listener.onEdit(this)
            return true
        }
        return false
    }

    fun setState(active: Boolean, alpha: Int): TextLayer {
        pressed = active
        setFadeLevel(alpha)
        return this
    }

    fun press(): TextLayer {
        pressed = true
        listener.onPressed(this)
        return this
    }

    fun setFadeLevel(value: Int): TextLayer {
        alpha = value
        borderPaint.alpha = alpha
        deletePaint.alpha = alpha
        deleteIcon?.alpha = alpha
        return this
    }

    companion object {
        const val ICON_RADIUS = 12
        const val ICON_SCALE = 0.9f
        const val BORDER_CORNER_RADIUS = 2
        const val BORDER_WIDTH = 4
    }

    interface TextLayerListener {
        fun onPressed(layer: TextLayer) {}
        fun onReleased(layer: TextLayer) {}
        fun onDeleted(layer: TextLayer) {}
        fun onEdit(layer: TextLayer) {}
    }
}
