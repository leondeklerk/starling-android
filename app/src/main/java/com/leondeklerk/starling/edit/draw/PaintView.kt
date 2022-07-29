package com.leondeklerk.starling.edit.draw

import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.ObservableArrayList
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputLayout
import com.leondeklerk.starling.R
import com.leondeklerk.starling.edit.draw.layers.DrawLayer
import com.leondeklerk.starling.edit.draw.layers.PaintLayer
import com.leondeklerk.starling.edit.draw.layers.TextLayer
import com.leondeklerk.starling.extensions.dpToPixels
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * A custom canvas that uses different [DrawLayer] types to draw lines and text.
 * Allows for undo/redo/clear actions as well as different styles of layers.
 * Includes animation logic for showing/hiding [TextLayer] borders,
 * as well as logic for editing and deleting.
 */
class PaintView(context: Context, attributeSet: AttributeSet?) : View(
    context,
    attributeSet
) {
    private lateinit var brushStyle: BrushStyle
    private lateinit var textStyle: TextStyle
    private lateinit var canvas: Canvas
    private lateinit var srcBitmap: Bitmap

    private val layerList: ObservableArrayList<DrawLayer> = ObservableArrayList()
    private var path = Path()
    private var brush = Paint()
    private var movingText = false
    private var cancelMove = false
    private var scaleDetector: ScaleGestureDetector
    private var scaling = false
    private var last = PointF()
    private var scaleBy = 1f
    private var startScalar = 1f
    private var currentScale = 1f
    private var scalingActive = false
    private var bitmap: Bitmap? = null
    private var activeLayer: TextLayer? = null
    private var gestureDetector: GestureDetector
    private var animatorMap = HashMap<Int, ValueAnimator>()
    private var textLayers: List<TextLayer> = ArrayList()
    private var undoBuffer: ArrayList<Pair<ActionType, DrawLayer>> = ArrayList()
    private var redoBuffer: ArrayList<Pair<ActionType, DrawLayer>> = ArrayList()
    private var drawing = false

    // Create a listener that can handle single taps
    private val gestureListener: GestureDetector.OnGestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Check if the an active layer needs to be deleted or updated.
            activeLayer?.checkDeleted(PointF(e.x, e.y))
            activeLayer?.checkEdit(PointF(e.x, e.y))
            return super.onSingleTapUp(e)
        }
    }

    // Scale listener responsible for handling scaling gestures on a text layer
    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Current scalar
            scaleBy = startScalar * detector.scaleFactor / currentScale

            // Calculate raw scale
            val projectedScale = scaleBy * currentScale

            // Make the scaling bounded
            if (projectedScale < MIN_SCALE) {
                scaleBy = MIN_SCALE / currentScale
            } else if (projectedScale > MAX_SCALE) {
                scaleBy = MAX_SCALE / currentScale
            }

            return false
        }

        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            startScalar = currentScale
            scaling = true
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            scaleBy = 1f
            scaling = false
        }
    }

    // Create the text layer listener object, responsible for handling text actions
    private val textLayerListener = object : TextLayer.TextLayerListener {
        override fun onPressed(layer: TextLayer) {
            // On pressed, animate showing the border
            animateTextAlpha(layer, ALPHA_MIN, ALPHA_MAX)
        }

        override fun onReleased(layer: TextLayer) {
            // On release, animate hiding the border
            animateTextAlpha(layer, ALPHA_MAX, ALPHA_MIN)
        }

        override fun onDeleted(layer: TextLayer) {
            // Remove the layer from the list
            layerList.remove(layer)

            // Update the redo/undo buffers
            storeAction(ActionType.DELETE, layer)

            // Draw to canvas
            drawLayers()
        }

        override fun onEdit(layer: TextLayer) {
            // On editing a layer, show the text edit/add modal
            val modal = createTextModal(textStyle.color, layer.text) { text: String -> editTextLayer(text, layer) }
            modal.show()
        }
    }

    init {
        // Set up the view and detectors
        setLayerType(LAYER_TYPE_HARDWARE, null)
        scaleDetector = ScaleGestureDetector(context, scaleListener)
        gestureDetector = GestureDetector(context, gestureListener)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let { c ->
            // Draw the bitmap with all layers to the canvas
            bitmap?.let { b ->
                c.drawBitmap(b, 0f, 0f, null)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        // If scaling, handle no other movements
        if (scaling) {
            onScale()
            return true
        }

        if (cancelMove && event.action != MotionEvent.ACTION_UP) {
            return true
        }

        // Only allow one pointer at the same time if not scaling
        if (event.pointerCount > 1) {
            cancelMove = true
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onActionDown(event)
            }
            MotionEvent.ACTION_MOVE -> {
                onActionMove(event)
            }
            MotionEvent.ACTION_UP -> {
                onActionUp()
            }
        }

        return true
    }

    /**
     * Reset this overlay to its initial state.
     * Clears all layers and draws the empty canvas.
     */
    fun reset() {
        undoBuffer.clear()
        redoBuffer.clear()
        layerList.clear()
        textLayers = listOf()
        activeLayer = null

        drawLayers()
    }

    /**
     * Defines if the current view was drawn on or not.
     * @return if the view was edited or not.
     */
    fun isTouched(): Boolean {
        return !layerList.isEmpty()
    }

    /**
     * Creates and shows a text modal.
     * A text modal can be used to enter text which will be displayed by a [TextLayer].
     */
    fun showTextModal() {
        val modal = createTextModal(textStyle.color, "") { text -> addTextLayer(text) }
        modal.show()
    }

    /**
     * Get the result bitmap containing all drawing layers.
     * Creates a bitmap based on the source width, height, and config.
     * Maps each layer to a scaled version of itself to fit the new result bitmap.
     * Each layer is subsequently drawn to the created bitmap.
     * @return the result bitmap containing all draw layers.
     */
    fun getBitmap(): Bitmap {
        // Create the result bitmap and canvas
        val result = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, srcBitmap.config)
        val resultCanvas = Canvas(result)

        // Scale each layer to the current bitmap size
        val scale = srcBitmap.width.toFloat() / width.toFloat()
        val list = layerList.map { layer -> layer.mapToScale(scale) }

        // Draw the layers
        list.forEach { layer -> layer.draw(resultCanvas) }

        return result
    }

    /**
     * Set the source bitmap for this overlay.
     * Will create a new bitmap based on the source config and the current width/height.
     * The new bitmap is used to draw the layers on before writing the bitmap to the view canvas.
     * @param src: the source bitmap that is used for its properties
     */
    fun setBitmap(src: Bitmap) {
        srcBitmap = src
        bitmap = Bitmap.createBitmap(width, height, src.config)
        canvas = Canvas(bitmap!!)
    }

    /**
     * Set the current brush style for any subsequent line that is drawn.
     * @param style the style of the brush to paint with
     */
    fun setBrushStyle(style: BrushStyle) {
        brushStyle = style
        brush = getBrush(style)
    }

    /**
     * Set the current text style (color/size) for any text added or edited.
     * @param style the text style containing the current size and color.
     */
    fun setTextStyle(style: TextStyle) {
        textStyle = style
    }

    /**
     * Revert an action from the list of actions.
     * Add/Delete actions can be reversed by re-adding or deleting the layer.
     * Translate/Scale/Edit actions can be reversed by restoring the previous layer.
     * Each reversed action is added to the redo buffer, and the list of text layers is updated.
     */
    fun undo() {
        val action = undoBuffer.removeLastOrNull()
        action?.let {
            // Cancel all animations
            animatorMap.forEach { (_, animator) -> animator.cancel() }

            var redoAction = action
            val layer = action.second

            // Reset the state of the layer
            if (layer is TextLayer) {
                layer.setState(false, 0, false)
                activeLayer = null
            }

            // Based on the type of action, restore the original layer state
            when (action.first) {
                ActionType.ADD -> layerList.remove(layer)

                ActionType.TRANSLATE, ActionType.SCALE, ActionType.EDIT -> {
                    val old = layerList.set(layer.index, layer)
                    redoAction = Pair(action.first, old)
                }
                ActionType.DELETE -> layerList.add(layer)
            }

            // Update state
            redoBuffer.add(redoAction)
            createTextLayerList()
            drawLayers()
        }
    }

    /**
     * Revert any reverted actions.
     * Uses the redoBuffer by re-executing any reverted actions.
     * Each action is then stored back into the undoBuffer.
     */
    fun redo() {
        val action = redoBuffer.removeLastOrNull()
        action?.let {
            // Cancel all animations as no layers can be active
            animatorMap.forEach { (_, animator) -> animator.cancel() }
            var undoAction = action
            val layer = action.second

            // Unset any state for the layer
            if (layer is TextLayer) {
                layer.setState(false, 0, false)
                activeLayer = null
            }

            // Restore the action based on type
            when (action.first) {
                ActionType.ADD -> layerList.add(layer)
                ActionType.TRANSLATE, ActionType.SCALE, ActionType.EDIT -> {
                    val old = layerList.set(layer.index, layer)
                    undoAction = Pair(action.first, old)
                }
                ActionType.DELETE -> layerList.remove(layer)
            }

            // Update the state
            undoBuffer.add(undoAction)
            createTextLayerList()
            drawLayers()
        }
    }

    /**
     * Scaling handler, disables moving text (if active), and starts scaling the current active layer.
     * On the start of scaling, the initial layer state is saved in the undo buffer.
     */
    private fun onScale() {
        // Stop moving if currently doing
        if (movingText) {
            movingText = false
        }

        // Check if there is an active layer
        activeLayer?.let { layer ->
            // Set the scaling state
            if (!scalingActive) {
                scalingActive = true
                storeAction(ActionType.SCALE, layer.mapToScale(1f))
            }

            // Scale the layer
            layer.scale(scaleBy)
            currentScale = layer.currentScale

            // Draw the updated layers
            drawLayers()
        }
    }

    /**
     * On action down handler, takes in the touch input and determines which action this is.
     * Tries to find the active layer based on the coordinates.
     * If an active layer is found the touch is interpreted as a translation action.
     * Otherwise it is interpreted as a drawing action.
     * @param event: the current touch event.
     */
    private fun onActionDown(event: MotionEvent) {
        findActiveTextLayer(PointF(event.x, event.y))

        // If an active layer was found, we are translating
        activeLayer?.let { layer ->
            // Update the state
            movingText = true
            last = PointF(event.x, event.y)
            currentScale = layer.currentScale
            storeAction(ActionType.TRANSLATE, layer.mapToScale(1f))
        }

        // If we are moving, nothing else needs to happen
        if (movingText) return

        // Set up the variables for a drawing action
        path = Path()
        brush = getBrush(brushStyle)
        path.moveTo(event.x, event.y)
    }

    /**
     * Handles pointer movement.
     * If in a translation action, move the current active layer.
     * If in a drawing action, add the new point to the current path.
     * @param event the current touch event
     */
    private fun onActionMove(event: MotionEvent) {
        if (movingText) {
            // Translate the current active layer
            activeLayer?.let { layer ->
                val dX = event.x - last.x
                val dY = event.y - last.y
                layer.translate(dX, dY)
                last = PointF(event.x, event.y)
            }
        } else {
            // If not already drawing, add the new layer
            if (!drawing) {
                drawing = true
                val layer = PaintLayer(layerList.size, path, brush)
                layerList.add(layer)
                storeAction(ActionType.ADD, layer)
            }
            // Else just update the path
            path.lineTo(event.x, event.y)
        }
        drawLayers()
    }

    /**
     * On action up event, reset the state.
     */
    private fun onActionUp() {
        drawing = false
        scalingActive = false
        movingText = false
        cancelMove = false
    }

    /**
     * Stores a new action to the undo buffer.
     * As the action timeline needs to be linear, on adding a new action reset the redo buffer.
     * When there is an add or delete action, recreate the text layer list.
     * @param action the type of action
     * @param layer the layer to store in the buffer
     */
    private fun storeAction(action: ActionType, layer: DrawLayer) {
        // When a new layer is added or removed, update the list of text layers
        if (action == ActionType.ADD || action == ActionType.DELETE) {
            createTextLayerList()
        }

        undoBuffer.add(Pair(action, layer))
        // After executing a new action, reset the redo buffer to keep one linear timeline
        redoBuffer.clear()
    }

    /**
     * Draw all layers to a local bitmap.
     * After drawing to the bitmap, draw this bitmap to the view canvas in one operation.
     */
    private fun drawLayers() {
        MainScope().launch {
            bitmap?.let {
                // Clear the current canvas
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                val textLayers = layerList.filterIsInstance<TextLayer>()
                val drawLayers = layerList.filterIsInstance<PaintLayer>()

                drawLayers.forEach { layer -> layer.draw(canvas) }
                textLayers.forEach { layer -> layer.draw(canvas) }

                invalidate()
            }
        }
    }

    /**
     * Animate a text layer, fading in and out the border on selection and deselection.
     * Each animator is stored based on the layer index.
     * On starting a new animation any old animation is cancelled, and the layer is informed.
     */
    private fun animateTextAlpha(layer: TextLayer, from: Int, to: Int) {
        animatorMap[layer.index]?.cancel()
        // Create a new animator
        val animator = ValueAnimator.ofInt(from, to).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animation ->
                // Update teh fade level and draw on the canvas.
                val value = animation.animatedValue as Int
                layer.let {
                    it.setFadeLevel(value)
                    drawLayers()
                }
            }
            // Inform the layer of starting and ending the animation
            doOnStart { layer.setAnimating(true) }
            doOnEnd { layer.setAnimating(false) }
        }

        // Save and start the animator
        animatorMap[layer.index] = animator
        animator.start()
    }

    /**
     * Filter the list of layers and set the list of text layers.
     * The list is used when finding the active layer.
     */
    private fun createTextLayerList() {
        // Reverse as the last added layer is on top (activated first)
        textLayers = layerList.filterIsInstance<TextLayer>().asReversed()
    }

    /**
     * Finds the active text layer based on the location.
     * If there already is a different active layer it is released.
     * Any new layer is pressed and stored in activeLayer.
     * @param location the current touch location.
     */
    private fun findActiveTextLayer(location: PointF) {
        val newLayer = textLayers.find { textLayer -> textLayer.isOnLayer(location) }

        // release any active layer
        if (newLayer == null && activeLayer != null) {
            activeLayer?.release()
        } else if (newLayer != null) {
            // Only release if this is a different layer
            if (newLayer != activeLayer) {
                activeLayer?.release()
                newLayer.press()
            }
        }

        activeLayer = newLayer
    }

    /**
     * Create a BottomSheetDialog containing a text field to ask the user for a text input.
     * Will automatically close the dialog based on the IME visibility.
     * @param color the current text color to display
     * @param text the default text value of the input field
     * @param onDoneListener the listener that is invoked when the dialog is dismissed via the action done key
     */
    private fun createTextModal(color: Int, text: String, onDoneListener: (text: String) -> Unit): BottomSheetDialog {
        val modal = BottomSheetDialog(context, R.style.Starling_Widget_BottomSheet_TextInput_Overlay)
        modal.apply {
            setContentView(R.layout.modal_add_text)

            // Set the keyboard parameters
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

            // Find the text view and update its initial state
            val textInputLayout = findViewById<TextInputLayout>(R.id.edit_text_field_layout)
            textInputLayout?.editText?.setTextColor(color)
            textInputLayout?.editText?.text = Editable.Factory.getInstance().newEditable(text)
            textInputLayout?.editText?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // If the action done button was used, check if there was an actual value
                    val result = textInputLayout.editText?.text?.toString()
                    if (!result.isNullOrBlank()) {
                        onDoneListener(result)
                    }
                    dismiss()
                }
                true
            }

            // Get the initial keyboard state
            var keyboardVisible = WindowInsetsCompat.toWindowInsetsCompat(rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime())

            setOnApplyWindowInsetsListener { _, insets ->
                // Determine the current keyboard state
                val visible = WindowInsetsCompat.toWindowInsetsCompat(insets).isVisible(WindowInsetsCompat.Type.ime())
                if (keyboardVisible != visible) {
                    keyboardVisible = visible
                    // On closing the keyboard, dismiss the dialog
                    if (!visible) {
                        dismiss()
                    }
                }
                insets
            }

            // Request focus of the input field
            textInputLayout?.requestFocus()
        }
        return modal
    }

    /**
     * Edit a text layer with a new string.
     * @param text the new text value
     * @param layer the layer to update
     */
    private fun editTextLayer(text: String, layer: TextLayer) {
        storeAction(ActionType.EDIT, layer.mapToScale(1f))
        layer.updateText(text, textStyle.color, textStyle.size)
        drawLayers()
    }

    /**
     * Add a new text layer based on the given string.
     * @param text the text of the new text layer
     */
    private fun addTextLayer(text: String) {
        // Calculate the center of the new layout
        val cX = (right - left) / 2f
        val cY = (bottom - top) / 2f

        // Create the layer
        val layer = TextLayer(layerList.size, text, textStyle.color, textStyle.size, context, textLayerListener)
            .createLayout()
            .createOrigin(PointF(cX, cY))

        layerList.add(layer)
        storeAction(ActionType.ADD, layer)

        drawLayers()
    }

    /**
     * Get the current paint based on the current brush style.
     * Responsible for converting the size to density pixels, as well as setting the stroke type and shapes.
     * @param style the brush style to convert to a paint object
     * @return a paint object representing the provided style.
     */
    private fun getBrush(style: BrushStyle): Paint {
        // Create the paint with the correct flags
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Set the basic paint properties
        paint.color = style.color
        paint.strokeWidth = dpToPixels(style.size)
        paint.style = Paint.Style.STROKE

        // Pencil uses rounded shapes, eraser/marker use squared shapes
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

        // Set the alpha (convert from 0.0 - 1.0 range to 0 - 255)
        paint.alpha = (style.alpha * 255).toInt()
        return paint
    }

    companion object {
        private const val MAX_SCALE = 16f
        private const val MIN_SCALE = 0.4f
        private const val ANIMATION_DURATION = 100L
        private const val ALPHA_MAX = 255
        private const val ALPHA_MIN = 0
    }
}
