package com.leondeklerk.starling.edit

import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.FragmentPaintStyleBinding
import com.leondeklerk.starling.databinding.ViewDrawBinding
import java.lang.Integer.max
import java.lang.Integer.min

class DrawView(context: Context, attributeSet: AttributeSet?) : RelativeLayout(
    context,
    attributeSet
) {

    private val pathList: ArrayList<Path> = ArrayList()
    private val brushList: ArrayList<BrushStyle> = ArrayList()

    private var path = Path()

    private var binding: ViewDrawBinding = ViewDrawBinding.inflate(LayoutInflater.from(context), this, true)
    private val styleModal = BrushStyleModal()

    private var brush = Paint()

    private var brushStyle: BrushStyle
    private var drawUpTo = 0

    private var bounds: Rect? = null
    private var outOfBounds = false
    private var last = PointF()
    private var drawOnBounds = false

    init {
        brushStyle = styleModal.getBrushStyle()

        styleModal.onCloseListener = { style ->
            brushStyle = style
            brush = getBrush(style)
        }

        binding.buttonUndo.setOnClickListener {
            undo()
        }

        binding.buttonClear.setOnClickListener {
            reset()
        }

        binding.buttonRedo.setOnClickListener {
            redo()
        }

        binding.buttonText.setOnClickListener {
            drawOnBounds = !drawOnBounds
        }

        binding.buttonStyle.setOnClickListener {
            styleModal.show((context as AppCompatActivity).supportFragmentManager, BrushStyleModal.TAG)
        }

        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun getBrush(style: BrushStyle): Paint {
        val paint = Paint()
        paint.color = Color.HSVToColor(floatArrayOf(style.hue, style.saturation, style.value))
        paint.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, style.size, resources.displayMetrics)
        paint.style = Paint.Style.STROKE
        paint.isDither = true

        if (style.type == BrushType.PENCIL) {
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
        } else {
            paint.strokeCap = Paint.Cap.SQUARE
            paint.strokeJoin = Paint.Join.BEVEL
        }

        if (style.type == BrushType.ERASER) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            paint.xfermode = null
        }

        paint.alpha = (style.alpha * 255).toInt()
        return paint
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        var index = 0
        while (index < drawUpTo) {
            val path = pathList[index]
            val paint = getBrush(brushList[index])
            canvas?.drawPath(path, paint)
            index++
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var curX = event.x
        var curY = event.y
        bounds?.let {
            if (!it.contains(curX.toInt(), curY.toInt())) {
                // Draw up to the edge
                if (!outOfBounds && event.action == MotionEvent.ACTION_MOVE) {
                    var xBound = curX
                    if (curX >= it.right) {
                        xBound = it.right.toFloat()
                    } else if (curX <= it.left) {
                        xBound = it.left.toFloat()
                    }

                    var yBound = curY
                    if (curY >= it.bottom) {
                        yBound = it.bottom.toFloat()
                    } else if (curY <= it.top) {
                        yBound = it.top.toFloat()
                    }

                    if (!drawOnBounds) {
                        path.lineTo(xBound, yBound)
                    } else {
                        curX = xBound
                        curY = yBound
                    }
                }

                if (!drawOnBounds) {
                    outOfBounds = true
                    invalidate()

                    last = PointF(curX, curY)
                    return true
                }
            }
        }

        if (outOfBounds) {
            bounds?.let {
                var xBound = last.x
                if (last.x >= it.right) {
                    xBound = it.right.toFloat()
                } else if (last.x <= it.left) {
                    xBound = it.left.toFloat()
                }

                var yBound = last.y
                if (last.y >= it.bottom) {
                    yBound = it.bottom.toFloat()
                } else if (last.y <= it.top) {
                    yBound = it.top.toFloat()
                }
                path.moveTo(xBound, yBound)
            }
            outOfBounds = false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {

                if (drawUpTo < pathList.size) {
                    val removePaths = pathList.slice(drawUpTo until pathList.size)
                    pathList.removeAll(removePaths)

                    val removeStyles = brushList.slice(drawUpTo until brushList.size)
                    brushList.removeAll(removeStyles)
                }

                path = Path()
                pathList.add(path)

                brush = getBrush(brushStyle)
                brushList.add(brushStyle)

                drawUpTo = pathList.size

                path.moveTo(curX, curY)
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(curX, curY)
            }
            MotionEvent.ACTION_UP -> {
                // Make sure everything is in the initial state after movement
                path = Path()
                brush = getBrush(brushStyle)
                outOfBounds = false
            }
        }

        last = PointF(curX, curY)

        invalidate()
        return true
    }

    private fun undo() {
        drawUpTo = max(0, drawUpTo - 1)
        invalidate()
    }

    private fun redo() {
        drawUpTo = min(pathList.size, drawUpTo + 1)
        invalidate()
    }

    fun setBounds(newBounds: Rect) {
        bounds = newBounds
    }

    fun reset() {
        drawUpTo = 0
        pathList.clear()
        brushList.clear()
        invalidate()
    }
}

class BrushStyleModal : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentPaintStyleBinding
    private var hue: Float = 0f
    private var saturation: Float = 1f
    private var value: Float = 1f
    private var brushType = BrushType.PENCIL
    private var dotSize = 8f
    private var alpha = 1f

    var onCloseListener: ((style: BrushStyle) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPaintStyleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setOnShowListener { dialog ->
            (dialog as BottomSheetDialog).let {
                val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout
                val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
                bottomSheetBehavior.isDraggable = false
            }
        }

        binding.sliderHue.trackGradient = getGradient(GradientType.HUE, 360f, 1f)
        binding.sliderSaturation.trackGradient = getGradient(GradientType.SATURATION, hue, 1f)
        binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, hue, saturation)
        setColorDot()

        binding.sliderHue.addOnChangeListener { _, sliderValue, _ ->
            hue = sliderValue
            binding.sliderSaturation.trackGradient = getGradient(GradientType.SATURATION, hue, 1f)
            binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, hue, saturation)
            setColorDot()
        }

        binding.sliderSaturation.addOnChangeListener { _, sliderValue, _ ->
            saturation = sliderValue
            binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, hue, saturation)
            setColorDot()
        }

        binding.sliderValue.addOnChangeListener { _, sliderValue, _ ->
            value = sliderValue
            setColorDot()
        }

        binding.sliderSize.addOnChangeListener { _, value, _ ->
            dotSize = value
            setColorDot()
        }

        binding.brushType.addOnButtonCheckedListener { _, checkedId, _ ->
            when (checkedId) {
                R.id.button_pencil -> {
                    brushType = BrushType.PENCIL
                    alpha = 1f
                    setColorEnabled(true)
                }
                R.id.button_marker -> {
                    brushType = BrushType.MARKER
                    alpha = 0.3f
                    setColorEnabled(true)
                }
                R.id.button_eraser -> {
                    brushType = BrushType.ERASER
                    alpha = 0f
                    setColorEnabled(false)
                }
            }
        }
    }

    private fun setColorEnabled(enabled: Boolean) {
        binding.sliderHue.isEnabled = enabled
        binding.sliderSaturation.isEnabled = enabled
        binding.sliderValue.isEnabled = enabled
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCloseListener?.invoke(getBrushStyle())
    }

    private fun setColorDot() {
        val color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        binding.sizeColorPreview.color = color
        binding.sizeColorPreview.radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dotSize, resources.displayMetrics)
    }

    fun getBrushStyle(): BrushStyle {
        return BrushStyle(brushType, hue, saturation, value, dotSize, alpha)
    }

    private fun getGradient(type: GradientType, hue: Float, saturation: Float): GradientDrawable {
        val spaceSize = when (type) {
            GradientType.HUE -> 360
            GradientType.VALUE, GradientType.SATURATION -> 100
        }

        val colors = IntArray(spaceSize)
        for (i in 0 until spaceSize) {
            colors[i] = when (type) {
                GradientType.HUE -> Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
                GradientType.SATURATION -> Color.HSVToColor(floatArrayOf(hue, i / 100f, 1f))
                GradientType.VALUE -> Color.HSVToColor(floatArrayOf(hue, saturation, i / 100f))
            }
        }

        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
    }

    companion object {
        const val TAG = "BrushStyleDialog"
    }
}

enum class BrushType {
    PENCIL,
    MARKER,
    ERASER
}

enum class GradientType {
    HUE,
    SATURATION,
    VALUE
}

data class BrushStyle(
    val type: BrushType,
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val size: Float,
    val alpha: Float
)
