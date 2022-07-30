package com.leondeklerk.starling.edit

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRect
import androidx.core.graphics.transform
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.ViewEditBinding
import com.leondeklerk.starling.edit.crop.CropView
import com.leondeklerk.starling.edit.crop.HandlerType
import com.leondeklerk.starling.edit.draw.DrawView
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
class EditView(context: Context, attributeSet: AttributeSet?) : ConstraintLayout(
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
    private var mode = EditModes.CROP

    // Animation
    private var refreshRate = 60f

    private var binding: ViewEditBinding = ViewEditBinding.inflate(LayoutInflater.from(context), this, true)
    private var saveModal = SaveModal()

    var imageView: InteractiveImageView = binding.interactiveImageView
    var onCancel: (() -> Unit)? = null
    var onSave: ((bitmap: Bitmap, copy: Boolean) -> Unit)? = null

    /**
     * Simple data class combining bitmap data.
     * @param bitmapSize: the bitmap width or height value
     * @param startScale: the starting scale of the bitmap in an imageView
     * @param normalizedScale: the current bitmap scale normalized from 1 to max zoom size.
     */
    data class BitmapData(val bitmapSize: Int, val startScale: Float, val normalizedScale: Float)

    init {
        setupImageView()

        // Calculate refresh rate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            refreshRate = context.display?.refreshRate ?: 60f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        binding.cropper.onTouchHandler = { event ->
            onTouchEvent(event)
        }

        binding.interactiveImageView.onTouchHandler = { event ->
            onTouchEvent(event)
        }

        binding.buttonSave.setOnClickListener {
            saveModal.show((context as AppCompatActivity).supportFragmentManager, SaveModal.TAG)
        }

        binding.buttonCancel.setOnClickListener {
            onEditCancel()
        }

        binding.cropper.onButtonReset = {
            imageView.reset()
        }

        binding.cropper.onButtonRotate = {
            imageView.rotateImage()
        }

        binding.modeSelector.setOnCheckedStateChangeListener { _, checkedId ->
            when (checkedId[0]) {
                R.id.mode_crop -> {
                    mode = EditModes.CROP
                }
                R.id.mode_draw -> {
                    mode = EditModes.DRAW
                }
            }
            updateEditLayers()
        }

        saveModal.onCloseListener = {
            createImage(it == SaveModal.TYPE_COPY)
        }
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
            imageView.updateDetectors(event)

            if (imageView.checkDoubleTap(event, getRect())) {
                resetHandler()
                return true
            }

            imageView.updatePointerData(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    imageView.onActionDown()
                }
                MotionEvent.ACTION_MOVE -> {
                    // If there is one finger on the screen, we either do a box movement or a outside box translation
                    if (event.pointerCount == 1) {
                        if (!movingBox && !movingOther) {
                            // Check if we can start a move (touching a handler)
                            movingBox = binding.cropper.startMove(PointF(event.x, event.y))
                        }

                        // If we have a handler movement
                        if (movingBox) {
                            binding.cropper.move(PointF(event.x, event.y))
                        } else {
                            // we are dealing with an outside box movement
                            imageView.onSinglePointerMove(getRect())
                            movingOther = true
                        }
                    } else {
                        // If we have two fingers, it can only be scaling, cancel all other movement
                        if (movingBox) {
                            // Reset everything
                            binding.cropper.cancelMove()
                            resetHandler()
                            movingBox = false
                        }

                        movingOther = true
                        imageView.onMultiPointerMove(getRect())
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // Cancel all box related movement
                    if (movingBox) {
                        binding.cropper.endMove()
                        resetHandler()
                        movingBox = false
                    }

                    if (movingOther) {
                        imageView.onActionUp()
                        movingOther = false
                    }
                }
            }

            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Call when the save operation is complete.
     * Updates the internal state related to saving.
     */
    fun isSaved() {
        binding.savingOverlay.visibility = GONE
        binding.savingIndicator.visibility = GONE
        isSaving = false
        imageView.reset()
    }

    /**
     * Update the state of the view to show the correct editing layer.
     */
    private fun updateEditLayers() {
        when (mode) {
            EditModes.CROP -> {
                if (binding.drawOverlay.isTouched()) {
                    showUnsavedChanges(this::switchToCrop) { binding.modeSelector.check(R.id.mode_draw) }
                } else {
                    switchToCrop()
                }
            }
            EditModes.DRAW -> {
                if (imageView.isTouched() || binding.cropper.isTouched()) {
                    showUnsavedChanges(this::switchToDraw) { binding.modeSelector.check(R.id.mode_crop) }
                } else {
                    switchToDraw()
                }
            }
        }
    }

    /**
     * Switch to a crop overlay, reset the state and disable other overlays.
     */
    private fun switchToCrop() {
        binding.drawOverlay.reset()
        binding.drawOverlay.visibility = View.GONE
        binding.cropper.visibility = View.VISIBLE
    }

    /**
     * Switch to a draw overlay, reset the state and hide other overlays.
     */
    private fun switchToDraw() {
        imageView.reset()
        binding.drawOverlay.visibility = View.VISIBLE
        binding.cropper.visibility = View.GONE
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
        binding.savingIndicator.visibility = VISIBLE
        binding.savingOverlay.visibility = VISIBLE

        // Get view values
        val bitmap = imageView.drawable.toBitmap()

        val result = when (mode) {
            EditModes.CROP -> createCropResult(bitmap)
            EditModes.DRAW -> createDrawResult(bitmap)
        }

        onSave?.invoke(result, copy)

        if (onSave == null) {
            isSaved()
        }
    }

    /**
     * Modifies the source (image) bitmap to fit the set translation, rotation, zoom, and crop box.
     * Uses information received from the [CropView] layer.
     * Creates a modified result image based on the new properties.
     * @param src: the image source bitmap
     * @return a new bitmap of the modified source
     */
    private fun createCropResult(src: Bitmap): Bitmap {
        val startScale = imageView.baseScale
        val cropBox = binding.cropper.outline

        // Convert the reference bitmap to a rotated bitmap.
        val baseMatrix = Matrix()
        baseMatrix.postRotate(imageView.rotated, src.width / 2f, src.height / 2f)
        val bitmap = Bitmap.createBitmap(
            src,
            0,
            0,
            src.width,
            src.height,
            baseMatrix,
            true
        )

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

    /**
     * Create a bitmap based on an image source and the drawing layers of a [DrawView].
     * @param src: the image source bitmap
     * @return a composite bitmap of the source and the drawings.
     */
    private fun createDrawResult(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(
            src.width,
            src.height,
            src.config
        )
        val drawnBitmap = binding.drawOverlay.getBitmap()
        val canvas = Canvas(result)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.isFilterBitmap = true
        canvas.drawBitmap(src, Matrix(), p)
        canvas.drawBitmap(drawnBitmap, Matrix(), p)
        return result
    }

    /**
     * On edit handler.
     */
    private fun onEditCancel() {
        // Reset all the data, show the popup etc
        if (isTouched()) {
            showUnsavedChanges(onCancel, null)
        } else {
            onCancel?.invoke()
        }
    }

    /**
     * Check if the image or layers were altered in any way.
     * @return true if any is edited or false if not
     */
    private fun isTouched(): Boolean {
        return imageView.isTouched() || binding.cropper.isTouched() || binding.drawOverlay.isTouched()
    }

    /**
     * Sets up all the listeners for the image view.
     * And set the properties of the imageView.
     */
    private fun setupImageView() {
        imageView.onBitmapSetListener = { bitmap ->
            onBitmapSet(bitmap)
        }

        imageView.onZoomLevelChangeListener = { level ->
            binding.cropper.zoomLevel = level
        }

        imageView.onZoomedInListener = {
            binding.cropper.onZoomedIn()
        }

        imageView.onImageUpdate = {
            binding.cropper.updateBounds(getRect())
            binding.cropper.restrictBorder()
        }

        imageView.onResetListener = {
            binding.cropper.reset(getRect())
        }

        imageView.allowTranslation = true
    }

    /**
     * Handler invoked when the bitmap is set on the imageView.
     * Responsible for initializing the cropHandler.
     */
    private fun onBitmapSet(bitmap: Bitmap) {
        binding.drawOverlay.setBounds(getRect().toRect())

        binding.drawOverlay.setBitmap(bitmap)

        binding.cropper.initialize(getRect())

        binding.cropper.onBoundsHitHandler = { delta, types ->
            handleSideTranslate(delta, types)
        }

        binding.cropper.onZoomHandler = { center, out ->
            // Revert the offset application.
            center.offset(-imageView.marginLeft.toFloat(), -imageView.marginTop.toFloat())
            imageView.zoomImage(center, out)
        }
    }

    /**
     * Get the bounding box of the current image.
     * This is translated according to the margin of the imageView.
     * @returns the bounding box of the image relative to the margins.
     */
    private fun getRect(translate: Boolean = true): RectF {
        val rect = imageView.getBoundedRect(imageView.imageMatrix)
        if (translate) {
            val m = Matrix()
            m.postTranslate(imageView.marginLeft.toFloat(), imageView.marginTop.toFloat())
            rect.transform(m)
        }
        return rect
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

        if (imageView.translateImage(direction, trans)) {
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
