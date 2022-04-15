package com.leondeklerk.starling.edit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.text.Editable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputLayout
import com.leondeklerk.starling.R
import com.leondeklerk.starling.extensions.dpToPixels
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class PaintView(context: Context, attributeSet: AttributeSet?) : View(
    context,
    attributeSet
) {

    private val layerList: ArrayList<DrawLayer> = ArrayList()
    private var path = Path()
    private var brush = Paint()
    private lateinit var brushStyle: BrushStyle
    private lateinit var textStyle: TextStyle
    private var movingText = false
    private var scaleDetector: ScaleGestureDetector
    private var scaling = false
    private var allowTouch = true
    private var last = PointF()
    private var scaleBy = 1f
    private var startScalar = 1f
    private var currentScale = 1f
    private var scalingActive = false
    private var scalingPoint = PointF()
    private var bitmap: Bitmap? = null
    private lateinit var canvas: Canvas
    private lateinit var srcBitmap: Bitmap
    private var activeLayer: TextLayer? = null
    private var gestureDetector: GestureDetector
    private var animator: ValueAnimator? = null
    private var textLayers: List<TextLayer> = ArrayList()
    private var undoBuffer: ArrayList<Pair<ActionType, DrawLayer>> = ArrayList()
    private var redoBuffer: ArrayList<Pair<ActionType, DrawLayer>> = ArrayList()
    private var drawing = false

    private val gestureListener: GestureDetector.OnGestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            activeLayer?.checkDeleted(PointF(e.x, e.y))
            activeLayer?.checkEdit(PointF(e.x, e.y))
            return super.onSingleTapUp(e)
        }
    }

    // Scale listener responsible for handling scaling gestures (pinch)
    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Current scale factor
            scaleBy = startScalar * detector.scaleFactor / currentScale

            // The raw scale
            val projectedScale = scaleBy * currentScale

            // Make the scaling bounded
            if (projectedScale < 0.4f) {
                scaleBy = 0.4f / currentScale
            } else if (projectedScale > 16f) {
                scaleBy = 16f / currentScale
            }

            return false
        }

        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            startScalar = currentScale
            scaling = true
            detector?.let {
                scalingPoint = PointF(detector.focusX, detector.focusY)
            }
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaleBy = 1f
            scaling = false
        }
    }

    private val textLayerListener = object : TextLayer.TextLayerListener {
        override fun onPressed(layer: TextLayer) {
            animateTextAlpha(layer, 0, 255)
        }

        override fun onReleased(layer: TextLayer) {
            animateTextAlpha(layer, 255, 0)
        }

        override fun onDeleted(layer: TextLayer) {
            layerList.remove(layer)
            redoBuffer.clear()
            undoBuffer.add(Pair(ActionType.DELETE, layer))
            MainScope().launch {
                drawLayers()
            }
            layerList.forEachIndexed { index, drawLayer -> drawLayer.index = index }
            createTextLayerList()
        }

        override fun onEdit(layer: TextLayer) {
            val color = Color.HSVToColor(floatArrayOf(textStyle.hue, textStyle.saturation, textStyle.value))
            val modal = createTextModal(color, layer.text) { text: String -> editTextLayer(text, layer) }
            modal.show()
        }
    }

    fun animateTextAlpha(layer: TextLayer, from: Int, to: Int) {
//        animator?.cancel()
        animator = ValueAnimator.ofInt(from, to).apply {
            duration = 100L
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                layer.let {
                    it.setFadeLevel(value)
                    MainScope().launch {
                        drawLayers()
                    }
                }
            }
            start()
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        scaleDetector = ScaleGestureDetector(context, scaleListener)
        gestureDetector = GestureDetector(context, gestureListener)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let { c ->
            bitmap?.let { b ->
                c.drawBitmap(b, 0f, 0f, null)
            }
        }
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun drawLayers() {
        bitmap?.let {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            var index = 0
            while (index < layerList.size) {
                layerList[index].draw(canvas)
                index++
            }
            invalidate()
        }
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun drawLayer(layer: TextLayer) {
        // TODO useful?
        layer.draw(canvas)
        invalidate()
    }

    private fun createTextLayerList() {
        textLayers = layerList.filterIsInstance<TextLayer>().asReversed()
    }

    /**
     * Finds the active text layer based on the location.
     * If a current layer is set, this is return.
     * Else it finds the topmost layer that is currently touched.
     * @param location the current touch location.
     */
    private fun findActiveTextLayer(location: PointF) {
        // TODO: animator per layer
        val newLayer = textLayers.find { textLayer -> textLayer.isOnLayer(location) }

        if (newLayer == null && activeLayer != null) {
            activeLayer?.release()
        } else if (newLayer != null) {
            if (newLayer != activeLayer) {
                activeLayer?.release()
                newLayer.press()
            }
        }

        activeLayer = newLayer
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (scaling) {
            // We are no longer moving
            if (movingText) {
                movingText = false
            }

// Only allow scaling via selecting first?
//            if (!scalingActive) {
//                findActiveTextLayer(PointF(scaleDetector.focusX, scaleDetector.focusY))
//            }

            activeLayer?.let { layer ->
                if (!scalingActive) {
                    scalingActive = true
                    undoBuffer.add(Pair(ActionType.SCALE, layer.mapToScale(1f)))
                }

                layer.scale(scaleBy)
                currentScale = layer.currentScale
                MainScope().launch {
                    drawLayers()
                }
            }
            return true
        }

        if (event.pointerCount > 1) {
            allowTouch = false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!allowTouch) return true

                findActiveTextLayer(PointF(event.x, event.y))

                activeLayer?.let { layer ->
                    movingText = true
                    last = PointF(event.x, event.y)
                    undoBuffer.add(Pair(ActionType.TRANSLATE, layer.mapToScale(1f)))
                    currentScale = layer.currentScale
                }

                if (movingText) return true

                path = Path()
                brush = getBrush(brushStyle)

                path.moveTo(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!allowTouch) return true
                if (movingText) {
                    activeLayer?.let { layer ->
                        val dX = event.x - last.x
                        val dY = event.y - last.y
                        layer.translate(dX, dY)
                        last = PointF(event.x, event.y)
                    }
                } else {
                    if (!drawing) {
                        drawing = true
                        val layer = PaintLayer(layerList.size, path, brush)
                        layerList.add(layer)

                        undoBuffer.add(Pair(ActionType.ADD, layer))
                        // TODO: do you want to revert the last undo anyway or only if it was the last action
                        redoBuffer.clear()
                    }
                    path.lineTo(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                drawing = false
                allowTouch = true
                scalingActive = false
                movingText = false
            }
        }

        MainScope().launch {
            drawLayers()
        }
        createTextLayerList()

        return true
    }

    fun setBitmap(src: Bitmap) {
        srcBitmap = src
        bitmap = Bitmap.createBitmap(width, height, src.config)
        canvas = Canvas(bitmap!!)
    }

    fun setBrushStyle(style: BrushStyle) {
        brushStyle = style
        brush = getBrush(style)
    }

    fun setTextStyle(style: TextStyle) {
        textStyle = style
    }

    fun undo() {
        val action = undoBuffer.removeLastOrNull()
        action?.let {
            var redoAction = action
            when (action.first) {
                ActionType.ADD -> {
                    layerList.removeLastOrNull()
                }
                ActionType.TRANSLATE, ActionType.SCALE, ActionType.EDIT -> {
                    val layer = action.second as TextLayer
                    layer.setState(false, 0)
                    activeLayer = null
                    val old = layerList.set(action.second.index, layer)
                    redoAction = Pair(action.first, old)
                    createTextLayerList()
                }
                ActionType.DELETE -> {
                    layerList.add(action.second.index, action.second)
                    layerList.forEachIndexed { index, drawLayer -> drawLayer.index = index }
                }
            }

            redoBuffer.add(redoAction)
        }

        MainScope().launch {
            drawLayers()
        }
    }

    fun redo() {
        val action = redoBuffer.removeLastOrNull()
        action?.let {
            var layer = action.second
            var undoAction = action
            when (action.first) {
                ActionType.ADD -> {
                    layerList.add(layer)
                    layerList.forEachIndexed { index, drawLayer -> drawLayer.index = index }
                }
                ActionType.TRANSLATE, ActionType.SCALE, ActionType.EDIT -> {
                    layer = layer as TextLayer
                    layer.setState(false, 0)
                    activeLayer = null
                    val old = layerList.set(action.second.index, layer)
                    undoAction = Pair(action.first, old)
                    createTextLayerList()
                }
                ActionType.DELETE -> {
                    layerList.remove(layer)
                    layerList.forEachIndexed { index, drawLayer -> drawLayer.index = index }
                    createTextLayerList()
                }
            }

            undoBuffer.add(undoAction)
        }

        MainScope().launch {
            drawLayers()
        }
    }

    fun reset() {
        undoBuffer.clear()
        layerList.clear()
        createTextLayerList()
        // TODO: improve?
        MainScope().launch {
            drawLayers()
        }
    }

    private fun createTextModal(color: Int, text: String, onDoneListener: (text: String) -> Unit): BottomSheetDialog {
        val modal = BottomSheetDialog(context, R.style.Starling_Widget_BottomSheet_TextInput_Overlay)
        modal.apply {
            setContentView(R.layout.modal_add_text)

            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            val textInputLayout = findViewById<TextInputLayout>(R.id.edit_text_field_layout)
            textInputLayout?.editText?.setTextColor(color)
            textInputLayout?.editText?.text = Editable.Factory.getInstance().newEditable(text)
            textInputLayout?.requestFocus()
            textInputLayout?.editText?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val result = textInputLayout.editText?.text?.toString()
                    if (!result.isNullOrBlank()) {
                        onDoneListener(result)
                    }
                    dismiss()
                }
                true
            }

            var keyboardVisible = WindowInsetsCompat.toWindowInsetsCompat(rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime())

            setOnApplyWindowInsetsListener { _, insets ->
                val visible = WindowInsetsCompat.toWindowInsetsCompat(insets).isVisible(WindowInsetsCompat.Type.ime())
                if (keyboardVisible != visible) {
                    keyboardVisible = visible
                    if (!visible) {
                        dismiss()
                    }
                }
                insets
            }
        }
        return modal
    }

    fun editTextLayer(text: String, layer: TextLayer) {
        val color = Color.HSVToColor(floatArrayOf(textStyle.hue, textStyle.saturation, textStyle.value))
        layer.updateText(text, color, textStyle.size)
        MainScope().launch {
            drawLayers()
        }
    }

    fun addText() {
        val color = Color.HSVToColor(floatArrayOf(textStyle.hue, textStyle.saturation, textStyle.value))
        val modal = createTextModal(color, "") { text -> addLayerText(text) }
        modal.show()
    }

    fun addLayerText(text: String) {
        val cX = (right - left) / 2f
        val cY = (bottom - top) / 2f
        val color = Color.HSVToColor(floatArrayOf(textStyle.hue, textStyle.saturation, textStyle.value))
        val layer = TextLayer(layerList.size, text, color, textStyle.size, context, textLayerListener)
            .createLayout()
            .createOrigin(PointF(cX, cY))
        layerList.add(layer)
        createTextLayerList()

        undoBuffer.add(Pair(ActionType.ADD, layer))
        // TODO: do you want to revert the last undo anyway or only if it was the last action
        redoBuffer.clear()

        MainScope().launch {
            drawLayers()
        }
    }

    fun getBitmap(): Bitmap {
        val result = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, srcBitmap.config)
        val resultCanvas = Canvas(result)
        val scale = srcBitmap.width.toFloat() / width.toFloat()
        val list = layerList.map { layer -> layer.mapToScale(scale) }

        var index = 0
        while (index < list.size) {
            list[index].draw(resultCanvas)
            index++
        }

        return result
    }

    private fun getBrush(style: BrushStyle): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.HSVToColor(floatArrayOf(style.hue, style.saturation, style.value))
        paint.strokeWidth = dpToPixels(style.size)
        paint.style = Paint.Style.STROKE

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
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        paint.alpha = (style.alpha * 255).toInt()
        return paint
    }

    enum class ActionType {
        ADD,
        TRANSLATE,
        SCALE,
        DELETE,
        EDIT
    }
}
