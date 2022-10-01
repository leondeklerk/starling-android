package com.leondeklerk.starling.edit

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRect
import androidx.core.graphics.transform
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.EditControlsBinding
import com.leondeklerk.starling.databinding.EditOverlayContainerBinding
import com.leondeklerk.starling.databinding.EditOverlayCropBinding
import com.leondeklerk.starling.databinding.EditOverlayDrawBinding
import com.leondeklerk.starling.databinding.ViewEditContainerBinding
import com.leondeklerk.starling.edit.ContainerMode.EDIT
import com.leondeklerk.starling.edit.ContainerMode.VIEW
import com.leondeklerk.starling.edit.crop.CropView
import com.leondeklerk.starling.edit.crop.HandlerType
import com.leondeklerk.starling.edit.draw.DrawView
import com.leondeklerk.starling.extensions.applyMargin
import com.leondeklerk.starling.extensions.dpToPx
import com.leondeklerk.starling.extensions.gone
import com.leondeklerk.starling.extensions.visible
import com.leondeklerk.starling.views.InteractiveImageView
import com.leondeklerk.starling.views.enums.Side
import com.leondeklerk.starling.views.enums.Side.NONE
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Class that takes in a image and provides edit options.
 * Provides scaling, translating and cropping.
 * Makes use of a [CropView] to handle all selection box rendering and movement.
 * Uses a [DrawView] in order to draw on the image.
 * Makes use of a [InteractiveImageView] for scaling and translating the image itself.
 */
class EditContainer(context: Context, attributeSet: AttributeSet?) : ConstraintLayout(
    context,
    attributeSet
) {
    // Move handler variables
    private var boxTransHandler = Handler(Looper.getMainLooper())
    private var trans = PointF()
    private var direction = Pair(NONE, NONE)
    private val boxTransRunnable = Runnable { handleAutoTranslation() }

    // Touch variables
    private var movingBox = false
    private var movingOther = false

    // State variables
    private var isSaving = false
    private var overlayMode = OverlayMode.CROP
    private var containerMode = VIEW
    private var isUserInput = true

    // Animation
    private var refreshRate = 90F

    // Views
    private var binding: ViewEditContainerBinding = ViewEditContainerBinding.inflate(LayoutInflater.from(context), this, true)
    private var imageView: InteractiveImageView? = null
    private var overlayContainer: EditOverlayContainerBinding? = null
    private var cropper: CropView? = null
    private var drawer: DrawView? = null
    private var saveOverlay: View? = null
    private var controls: EditControlsBinding? = null

    // Event hooks
    var onCancel: (() -> Unit)? = null
    var onSave: ((bitmap: Bitmap, copy: Boolean) -> Unit)? = null
    var onTapListener: ((mode: ContainerMode) -> Unit)? = null
    var onModeChange: ((mode: ContainerMode) -> Unit)? = null
    var onScaledStateListener: ((scaled: Boolean) -> Unit)? = null

    /**
     * Simple data class combining bitmap data.
     * @param bitmapSize: the bitmap width or height value
     * @param startScale: the starting scale of the bitmap in an imageView
     * @param normalizedScale: the current bitmap scale normalized from 1 to max zoom size.
     */
    data class BitmapData(val bitmapSize: Int, val startScale: Float, val normalizedScale: Float)

    init {
        binding.controlsStub.setOnInflateListener { _, inflated ->
            val controlsBinding = EditControlsBinding.bind(inflated)
            controls = controlsBinding
            initializeControls()
        }

        binding.overlayStub.setOnInflateListener { _, inflated ->
            val overlayBinding = EditOverlayContainerBinding.bind(inflated)
            overlayContainer = overlayBinding
            overlayBinding.cropperStub.setOnInflateListener { _, overlay ->
                cropper = EditOverlayCropBinding.bind(overlay).cropper
                setCropLayer()
            }

            overlayBinding.drawStub.setOnInflateListener { _, overlay ->
                drawer = EditOverlayDrawBinding.bind(overlay).drawOverlay
                setDrawLayer()
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        post {
            // After the layout is complete initialize the component
            initialize()
        }
    }

    /**
     * Initialize the current container mode and set the basic listeners and handler on the image view.
     */
    private fun initialize() {
        initializeMode(true)

        imageView?.onTapListener = {
            onTapListener?.invoke(containerMode)
        }

        imageView?.onTouchHandler = { event ->
            onTouchEvent(event)
        }

        imageView?.onBitmapSetListener = { _ ->
            onBitmapSet()
        }

        imageView?.onScaledStateListener = { scaled ->
            if (!isEdit()) {
                onScaledStateListener?.invoke(scaled)
            }
        }
    }

    /**
     * Initializes teh current container mode.
     * @param initial indicates if this is a setup on load or via user action. (Defaults to false)
     */
    private fun initializeMode(initial: Boolean = false) {
        when (containerMode) {
            EDIT -> initializeEdit(initial)
            VIEW -> initializeView(initial)
        }
    }

    /**
     * Initialize the container in edit mode.
     * Shows the edit overlays, applies margin to the image view,
     * and initializes the controls
     * @param
     */
    private fun initializeEdit(initial: Boolean) {
        binding.controlsContainer.visible()
        binding.controlsStub.visible()
        binding.overlayStub.visible()
        initializeControls()
        initializeBase(initial, Rect(dpToPx(32), dpToPx(48), dpToPx(32), dpToPx(64)), false)
    }

    /**
     * Initialize the container in view mode.
     * Hides the edit layer and if set, clears the overlays.
     * Makes the image view ful screen and disables touch without scaling
     */
    private fun initializeView(initial: Boolean) {
        binding.controlsContainer.gone()
        overlayContainer?.root?.gone()
        if (!initial) {
            clearOverlays()
        }
        initializeBase(initial, Rect(), true)
    }

    /**
     * Shares setup handler of both container modes.
     * Responsible for setting the general properties of the image view.
     * @param initial indicates if this is an initial load (opening the pager), if so no action required.
     * @param margins the margins to apply to the internal image.
     * @param initialScale if translation without scaling should be disabled or not
     */
    private fun initializeBase(initial: Boolean, margins: Rect, initialScale: Boolean) {
        if (!initial) {
            imageView?.apply {
                initialScaleOnly = initialScale
                scaleType = ImageView.ScaleType.FIT_CENTER
                applyMargin(margins)
                post {
                    reinitialize()
                }
            }
        }
    }

    /**
     * Initializes the new overlay.
     * If an old mode was set, it is cleared.
     * @param to the new (different) overlay mode
     */
    private fun initializeOverlay(to: OverlayMode) {
        // On change check if the current overlay is dirty or not.
        if (to != overlayMode) {
            when (overlayMode) {
                OverlayMode.CROP -> {
                    if (imageView?.isTouched() == true || cropper?.isTouched() == true) {
                        handleUnsavedOverlay(to, ::clearCropper, R.id.overlay_mode_crop)
                        return
                    }
                    clearCropper()
                }
                OverlayMode.DRAW -> {
                    if (drawer?.isTouched() == true) {
                        handleUnsavedOverlay(to, ::clearDraw, R.id.overlay_mode_draw)
                        return
                    }
                    clearDraw()
                }
            }
            overlayMode = to
        }

        when (to) {
            OverlayMode.CROP -> setCropLayer()
            OverlayMode.DRAW -> setDrawLayer()
        }
    }

    /**
     * If the current overlay is dirty, it needs to be reset before switching can happen.
     * This requires user input via a dialog.
     * @param to the new potential mode
     * @param clearOverlay function to clear the current overlay
     * @param chipId the id of the mode selector button which should be selected if the current change is cancelled
     */
    private fun handleUnsavedOverlay(to: OverlayMode, clearOverlay: () -> Unit, chipId: Int) {
        showUnsavedChanges({
            clearOverlay.invoke()
            overlayMode = to
            initializeOverlay(to)
        }) {
            isUserInput = false
            controls?.overlayModeSelector?.check(chipId)
        }
    }

    /**
     * Helper function to clear all overlays
     */
    private fun clearOverlays() {
        clearCropper()
        clearDraw()
    }

    /**
     * Clear the cropper overlay and remove the cropper specific bindings.
     */
    private fun clearCropper() {
        cropper?.apply {
            reset(getRect())
            gone()
        }
        clearImageBindings()
    }

    /**
     * Reset the draw overlay.
     */
    private fun clearDraw() {
        drawer?.apply {
            reset()
            gone()
        }
    }

    /**
     * Initialize teh crop layer.
     * Sets up the cropper specific bindings and values.
     * Also responsible for registering the correct handlers on the image view.
     */
    private fun setCropLayer() {
        cropper?.let { cropper ->
            cropper.visible()

            val rect = getRect()
            cropper.initialize(rect)

            setImageCropperBindings(cropper)

            cropper.onButtonReset = {
                imageView?.reset()
            }

            cropper.onButtonRotate = {
                imageView?.rotateImage()
            }

            cropper.onTouchHandler = { event ->
                onTouchEvent(event)
            }

            cropper.onBoundsHitHandler = { delta, types ->
                handleSideTranslate(delta, types)
            }

            cropper.onZoomHandler = { center, out ->
                // Revert the offset application.
                center.offset(-(imageView?.marginLeft?.toFloat() ?: 0f), -(imageView?.marginTop?.toFloat() ?: 0f))
                imageView?.zoomImage(center, out)
            }
        } ?: run {
            overlayContainer?.cropperStub?.inflate()
        }
    }

    /**
     * Bind teh current cropper overlay to the image.
     * @param cropper the cropper overlay to bind.
     */
    private fun setImageCropperBindings(cropper: CropView) {
        imageView?.onZoomLevelChangeListener = { level ->
            cropper.zoomLevel = level
        }

        imageView?.onZoomedInListener = {
            cropper.onZoomedIn()
        }

        imageView?.onImageUpdate = {
            cropper.updateBounds(getRect())
            cropper.restrictBorder()
        }

        imageView?.onResetListener = {
            cropper.reset(getRect())
        }
    }

    /**
     * Remove the image binding for the overlays.
     */
    private fun clearImageBindings() {
        imageView?.onZoomLevelChangeListener = {}
        imageView?.onZoomedInListener = {}
        imageView?.onImageUpdate = {}
        imageView?.onResetListener = {}
    }

    /**
     * Initializes the draw layer.
     * Resets the imageView listeners (if applied)
     */
    private fun setDrawLayer() {
        drawer?.let { draw ->
            clearImageBindings()
            draw.visible()
            draw.setBounds(getRect().toRect())
            draw.setBitmap(imageView!!.drawable.toBitmap())
        } ?: run {
            overlayContainer?.drawStub?.inflate()
        }
    }

    /**
     * Sets the correct binding and listeners for the edit control buttons.
     */
    private fun initializeControls() {
        controls?.apply {
            buttonSave.setOnClickListener {
                val saveModal = SaveModal()
                saveModal.onCloseListener = {
                    createImage(it == SaveModal.TYPE_COPY)
                }

                saveModal.show((context as FragmentActivity).supportFragmentManager, SaveModal.TAG)
            }

            buttonCancel.setOnClickListener {
                onEditCancel()
            }

            overlayModeSelector.setOnCheckedStateChangeListener { _, checkedId ->
                if (!isUserInput) {
                    isUserInput = true
                } else {
                    when (checkedId[0]) {
                        R.id.overlay_mode_crop -> initializeOverlay(OverlayMode.CROP)
                        R.id.overlay_mode_draw -> initializeOverlay(OverlayMode.DRAW)
                    }
                }
            }
        }
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        // Make sure to put the image view in the correct wrapper
        if (child?.id == R.id.imageView) {
            binding.viewContainer.addView(child, params)
            imageView = child as? InteractiveImageView
            return
        }
        super.addView(child, index, params)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // On detach cancel the handler
        boxTransHandler.removeCallbacksAndMessages(null)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSaving && !isClickable && isEnabled) {
            // get the current state of the image matrix, its values, and the bounds of the drawn bitmap
            imageView?.updateDetectors(event)

            if (imageView?.checkDoubleTap(event, getRect()) == true) {
                resetHandler()
                return true
            }

            imageView?.updatePointerData(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> imageView?.onActionDown()

                MotionEvent.ACTION_MOVE -> onActionMove(event)
                MotionEvent.ACTION_UP -> onActionUp()
            }

            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Handle movement event on the container.
     * Responsible for delegating the touch event to the correct view.
     * @param event the motion event
     */
    private fun onActionMove(event: MotionEvent) {
        // If there is one finger on the screen, we either do a box movement or a outside box translation
        if (event.pointerCount == 1) {
            if (isEdit() && !movingBox && !movingOther) {
                // Check if we can start a move (touching a handler)
                movingBox = cropper?.startMove(PointF(event.x, event.y)) ?: false
            }

            // If we have a handler movement
            if (isEdit() && movingBox) {
                cropper?.move(PointF(event.x, event.y))
            } else {
                // we are dealing with an outside box movement
                imageView?.onSinglePointerMove(getRect())
                movingOther = true
            }
        } else {
            // If we have two fingers, it can only be scaling, cancel all other movement
            if (isEdit() && movingBox) {
                // Reset everything
                cropper?.cancelMove()
                resetHandler()
                movingBox = false
            }

            movingOther = true
            imageView?.onMultiPointerMove(getRect())
        }
    }

    /**
     * Responsible for handling an action up event.
     * Complete the movement and resets the movement state
     */
    private fun onActionUp() {
        // Cancel all box related movement
        if (isEdit() && movingBox) {
            cropper?.endMove()
            resetHandler()
            movingBox = false
        }

        if (movingOther) {
            imageView?.onActionUp()
            movingOther = false
        }
    }

    /**
     * Set the current container mode.
     * Can be either in EDIT mode or in VIEW mode.
     * Will initialize the mode and the associated overlays.
     * Resets the imageView to an initial state.
     * @param newMode the new mode of the container.
     */
    fun setMode(newMode: ContainerMode) {
        if (newMode != containerMode) {
            imageView?.apply {
                containerMode = newMode
                onModeChange?.invoke(newMode)
                onResetListener = {
                    initializeMode()
                }
                reset()
            }
        }
    }

    /**
     * Check if the current container is in edit mode or not
     * @return a boolean indicating if in edit mode or not
     */
    private fun isEdit(): Boolean {
        return containerMode == EDIT
    }

    /**
     * Call when the save operation is complete.
     * Updates the internal state related to saving.
     * @param success: indicates if the save was cancelled or not.
     */
    fun isSaved(success: Boolean) {

        saveOverlay?.gone()
        isSaving = false
        if (success) {
            setMode(VIEW)
        }
    }

    /**
     * Shows a modal notifying the user of any unsaved changes.
     * Invokes the set handler on clicking the buttons.
     * @param onDiscard the function to call when the discard button is clicked
     * @param onCancel the function to call when the cancel button is clicked
     */
    private fun showUnsavedChanges(onDiscard: (() -> Unit)?, onCancel: (() -> Unit)?) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.edit_cancel_warning_title))
            .setMessage(context.getString(R.string.edit_cancel_warning_content))
            .setPositiveButton(context.getString(R.string.edit_cancel_warning_btn_continue)) { _: DialogInterface, _: Int ->
                onDiscard?.invoke()
            }
            .setNegativeButton(context.getString(R.string.edit_cancel_warning_btn_cancel)) { dialog: DialogInterface, _: Int ->
                onCancel?.invoke()
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Creates the resulting bitmap and invoke the onSave listener with the correct saving mode.
     * Updates the internal saving state of the view.
     * @param copy indicates if the image needs to be a copy or replace the original
     */
    private fun createImage(copy: Boolean) {
        // Update saving indicators
        isSaving = true
        saveOverlay = binding.saveOverlayStub.inflate()

        // Get view values
        val bitmap = imageView?.drawable?.toBitmap()

        bitmap?.let {
            val result = when (overlayMode) {
                OverlayMode.CROP -> createCropResult(bitmap)
                OverlayMode.DRAW -> createDrawResult(bitmap)
            }

            result?.let {
                onSave?.invoke(result, copy)
            }
        }

        if (onSave == null) {
            isSaved(true)
        }
    }

    /**
     * Modifies the source (image) bitmap to fit the set translation, rotation, zoom, and crop box.
     * Uses information received from the [CropView] layer.
     * Creates a modified result image based on the new properties.
     * @param src: the image source bitmap
     * @return a new bitmap of the modified source
     */
    private fun createCropResult(src: Bitmap): Bitmap? {
        imageView?.let { imageView ->
            cropper?.let { crop ->

                val startScale = imageView.baseScale
                val cropBox = crop.outline

                // Convert the reference bitmap to a rotated bitmap.
                val baseMatrix = Matrix()
                baseMatrix.postRotate(imageView.rotated, src.width / 2f, src.height / 2f)
                val bitmap = Bitmap.createBitmap(src, 0, 0, src.width, src.height, baseMatrix, true)

                // Get matrix values
                val scale = imageView.currentScale
                val rect = imageView.getRect(imageView.imageMatrix)
                val transX = rect.left
                val transY = rect.top

                // Build new matrix
                val normalizedScaled = scale / startScale
                val matrix = Matrix()
                matrix.postScale(normalizedScaled, normalizedScaled)

                // Combine standard bitmap data.
                val bitmapWidthData = BitmapData(bitmap.width, startScale, normalizedScaled)
                val bitmapHeightData = BitmapData(bitmap.height, startScale, normalizedScaled)

                // Calculate new image data
                val xOffset = getAxisOffset(bitmapWidthData, transX, cropBox.left, imageView.left)
                val yOffset = getAxisOffset(bitmapHeightData, transY, cropBox.top, imageView.top)
                val resultWidth = getAxisSize(bitmapWidthData, imageView.width, cropBox.width(), scale)
                val resultHeight = getAxisSize(bitmapHeightData, imageView.height, cropBox.height(), scale)

                return Bitmap.createBitmap(
                    bitmap,
                    xOffset.toInt(),
                    yOffset.toInt(),
                    resultWidth.toInt(),
                    resultHeight.toInt(),
                    matrix,
                    false
                )
            }
        }
        return null
    }

    /**
     * Create a bitmap based on an image source and the drawing layers of a [DrawView].
     * @param src: the image source bitmap
     * @return a composite bitmap of the source and the drawings.
     */
    private fun createDrawResult(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, src.config)
        drawer?.let {
            val drawnBitmap = it.getBitmap()
            val canvas = Canvas(result)

            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.isFilterBitmap = true
            canvas.drawBitmap(src, Matrix(), p)
            canvas.drawBitmap(drawnBitmap, Matrix(), p)
        }
        return result
    }

    /**
     * On edit handler.
     */
    private fun onEditCancel() {
        // Reset all the data, show the popup etc
        if (isTouched()) {
            showUnsavedChanges({
                setMode(VIEW)
                onCancel?.invoke()
            }, null)
        } else {
            setMode(VIEW)
            onCancel?.invoke()
        }
    }

    /**
     * Check if the image or layers were altered in any way.
     * @return true if any is edited or false if not
     */
    private fun isTouched(): Boolean {
        return imageView?.isTouched() == true || cropper?.isTouched() ?: false || drawer?.isTouched() ?: false
    }

    /**
     * Handler invoked when the bitmap is set on the imageView.
     * Responsible for initializing the cropHandler.
     */
    private fun onBitmapSet() {
        if (containerMode == EDIT) {
            initializeOverlay(overlayMode)
        }
    }

    /**
     * Get the bounding box of the current image.
     * This is translated according to the margin of the imageView.
     * @returns the bounding box of the image relative to the margins.
     */
    private fun getRect(translate: Boolean = true): RectF {
        imageView?.let { imageView ->
            val rect = imageView.getBoundedRect(imageView.imageMatrix)
            if (translate) {
                val m = Matrix()
                m.postTranslate(imageView.marginLeft.toFloat(), imageView.marginTop.toFloat())
                rect.transform(m)
            }
            return rect
        }
        return RectF()
    }

    /**
     * Handler for starting and updating the side translation.
     * This is a translation where the side of the border box touches the boundaries.
     * Which translates the image in that direction (if the image is zoomed in).
     * @param delta the movement in the x and y axis
     * @param types the types of the x and y direction
     */
    private fun handleSideTranslate(delta: PointF, types: Pair<HandlerType, HandlerType>) {
        // Set easier names for the pairs
        val (curX, curY) = direction
        val (newX, newY) = (types)

        // Parse HandlerTypes to Sides
        val directionX = handlerToSide(newX)
        val directionY = handlerToSide(newY)

        // If the direction changed
        if (curX != directionX || curY != directionY) {
            // Set the translation variables and start a new runnable
            trans = delta
            direction = Pair(directionX, directionY)
            boxTransHandler.removeCallbacksAndMessages(null)
            boxTransHandler.postDelayed(boxTransRunnable, 1000L / refreshRate.toLong())
        }
    }

    /**
     * Executed by the boxTransRunnable.
     * Responsible for automatically translating the image in the set directions by the set amounts.
     * Will continue to (auto) translate the image until the edge of the image is at the boundary.
     * This allows the user to hold the box at the boundary and just keep translating.
     */
    private fun handleAutoTranslation() {
        // If no direction is set, there is no movement
        if (direction.first == NONE && direction.second == NONE) return

        if (imageView?.translateImage(direction, trans) == true) {
            // Keep translating each 1 / refresh rate seconds.
            boxTransHandler.postDelayed(boxTransRunnable, 1000L / refreshRate.toLong())
        }
    }

    /**
     * Resets the auto translation handler.
     */
    private fun resetHandler() {
        boxTransHandler.removeCallbacksAndMessages(null)
        direction = Pair(NONE, NONE)
        trans = PointF()
    }

    /**
     * Based on the bitmap, the imageView size, cropBox size and scale,
     * calculates the size of the result bitmap on an axis (width/height).
     * @param data: common bitmap data
     * @param imgSize the width/height of the imageView
     * @param boxSize: the width/height of the crop box
     * @param scale: the current image scale in the view
     * @return the size (width/height) of the cropped image
     */
    private fun getAxisSize(data: BitmapData, imgSize: Int, boxSize: Int, scale: Float): Float {
        // Calculate the maximum size on this scale
        val maxSize = data.startScale * data.bitmapSize * data.normalizedScale
        // Get the ratio that is visible
        val sizeRatio = imgSize / maxSize
        // Get the maximum size the box can be
        val maxBoxSize = min(maxSize, imgSize.toFloat())
        // Get the ratio of the box compared to the maximum box size
        val boxSizeRatio = min(boxSize / maxBoxSize, 1f)
        // The result is the size multiplied by both ratios (box and translation)
        var resultSize = sizeRatio * data.bitmapSize * boxSizeRatio

        // If the image is fully within the image view, only use the crop ratio
        if (scale * data.bitmapSize <= imgSize) {
            resultSize = data.bitmapSize * boxSizeRatio
        }
        return resultSize
    }

    /**
     * Calculates the offset for a specific axis (x/y).
     * The calculation is based on the current translation of the image in the view and the place of the crop box.
     * @param data: the bitmap size and scaling data
     * @param translation: the current image translation on this axis
     * @param cropSide: the current location of the crop box on this axis (left/top)
     * @param imageSide: the current location of the imageView on this axis (left/top)
     * @return the calculated offset of the bitmap (cropped image lef/top)
     */
    private fun getAxisOffset(data: BitmapData, translation: Float, cropSide: Int, imageSide: Int): Float {
        // Calculate the maximum size at this scale in relation to the imageView
        val maxSize = data.startScale * data.bitmapSize * data.normalizedScale
        var offset = 0f

        // If the origin of the image is outside the visible imageView
        if (translation < 0) {
            // Get the % of the image that is no longer visible at this scale
            val relativeOffset = abs(translation) / maxSize
            // Convert this to pixels
            offset = relativeOffset * data.bitmapSize
        }

        // Get a possible centering offset within the imageView
        val cappedTranslation = max(0f, translation)
        // Get the distance between the image side and the crop box side
        val difference = max(0f, cropSide - (imageSide + cappedTranslation))
        // Get the offset as a size percentage and convert this to pixels
        offset += (difference / maxSize) * data.bitmapSize
        return offset
    }

    private fun handlerToSide(type: HandlerType): Side {
        return when (type) {
            HandlerType.LEFT -> Side.LEFT
            HandlerType.TOP -> Side.TOP
            HandlerType.RIGHT -> Side.RIGHT
            HandlerType.BOTTOM -> Side.BOTTOM
            else -> NONE
        }
    }
}
