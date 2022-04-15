package com.leondeklerk.starling.edit

import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRect
import androidx.core.graphics.transform
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leondeklerk.starling.R
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.databinding.ViewEditBinding
import com.leondeklerk.starling.edit.Side.NONE
import java.io.IOException
import java.lang.Long.parseLong
import java.util.Date
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Class that takes in a image and provides edit options.
 * Provides scaling, translating and cropping.
 * Makes use of a [CropView] to handle all selection box rendering and movement.
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

    // Animation
    private var refreshRate = 60f

    private var binding: ViewEditBinding = ViewEditBinding.inflate(LayoutInflater.from(context), this, true)

    var imageView: InteractiveImageView = binding.interactiveImageView
    var onCancel: (() -> Unit)? = null
    var onSave: ((data: ImageItem) -> Unit)? = null
    var isSaving = false

    var drawMode = false

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
            saveToStorage()
        }

        binding.buttonCancel.setOnClickListener {
            onEditCancel()
        }

        binding.cropper.onButtonReset = {
            onEditReset()
        }

        binding.cropper.onButtonRotate = {
            imageView.rotateImage()
        }

        binding.modeSelector.setOnCheckedChangeListener { group, checkedId ->
            imageView.reset()
            when (checkedId) {
                R.id.mode_crop -> {
                    binding.drawOverlay.visibility = View.GONE
                    binding.cropper.visibility = View.VISIBLE
                    binding.drawOverlay.reset()
                    drawMode = false
                }
                R.id.mode_draw -> {
                    binding.drawOverlay.visibility = View.VISIBLE
                    binding.cropper.visibility = View.GONE
                    drawMode = true
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // On detach cancel the handler
        boxTransHandler.removeCallbacksAndMessages(null)
    }

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
     * Saves an edited image to storage.
     * First applies translation and zoom from the imageview.
     * Then applies the crop box to the image.
     * Stores the new bitmap image to local storage.
     */
    private fun saveToStorage() {
        // Update saving indicators
        isSaving = true
        binding.savingIndicator.visibility = VISIBLE
        binding.savingOverlay.visibility = VISIBLE

        // Get view values
        var bitmap = imageView.drawable.toBitmap()
        var result: Bitmap
        if (!drawMode) {
            val startScale = imageView.baseScale
            val cropBox = binding.cropper.getCropperRect()

            // Convert the reference bitmap to a rotated bitmap.
            val baseMatrix = Matrix()
            baseMatrix.postRotate(imageView.rotated, bitmap.width / 2f, bitmap.height / 2f)
            bitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
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

            result = Bitmap.createBitmap(
                bitmap,
                xOffset.toInt(),
                yOffset.toInt(),
                resultWidth.toInt(),
                resultHeight.toInt(),
                matrix,
                false
            )
        } else {
            result = Bitmap.createBitmap(
                bitmap.width,
                bitmap.height,
                bitmap.config
            )
            var bm2 = binding.drawOverlay.getBitmap()
            val canvas = Canvas(result)

            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.isFilterBitmap = true
            canvas.drawBitmap(bitmap, Matrix(), p)
            canvas.drawBitmap(bm2, Matrix(), p)
        }

        var item: ImageItem? = null

        CoroutineScope(Dispatchers.Main).launch {
            try {
                item = saveBitmap(context, result, Bitmap.CompressFormat.JPEG, "image/jpeg", UUID.randomUUID().toString())
            } catch (e: IOException) {
                Toast.makeText(context, "Error $e", Toast.LENGTH_SHORT).show()
            } finally {
                // Update the UI
                binding.savingOverlay.visibility = GONE
                binding.savingIndicator.visibility = GONE
                isSaving = false
                item?.let {
                    onSave?.invoke(it)
                }
            }
        }
    }

    /**
     * Async function that saves the bitmap to the device and creates and entry in the MediaStore.
     * @param context: current context
     * @param bitmap: the bitmap to save
     * @param format: the compression format
     * @param mimeType: the mimeType of the new image
     * @param displayName: the new name of the image.
     */
    private suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        displayName: String
    ): ImageItem {
        val uri: Uri?

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }

        val resolver = context.contentResolver

        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Failed to create new MediaStore record.")
        val dateAdded = Date()
        try {
            withContext(Dispatchers.IO) {
                // Android studio gives an incorrect blocking call warning
                @Suppress("BlockingMethodInNonBlockingContext")
                val inputStream = resolver.openOutputStream(uri)
                inputStream?.use {
                    if (!bitmap.compress(format, 100, it))
                        throw IOException("Failed to save bitmap.")
                }

                delay(800)
            }
        } catch (e: IOException) {
            uri.let { orphanUri ->
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(orphanUri, null, null)
            }
            throw e
        }

        // Create the image data
        val id = parseLong(uri.lastPathSegment!!)
        return ImageItem(id, uri, displayName, dateAdded, bitmap.width, bitmap.height, mimeType)
    }

    /**
     * On edit handler.
     */
    private fun onEditCancel() {
        // Reset all the data, show the popup etc
        if (imageView.isTouched() || binding.cropper.isTouched()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.edit_cancel_warning_title))
                .setMessage(context.getString(R.string.edit_cancel_warning_content))
                .setPositiveButton(context.getString(R.string.edit_cancel_warning_btn_continue)) { _: DialogInterface, _: Int ->
                    onCancel?.invoke()
                }
                .setNegativeButton(context.getString(R.string.edit_cancel_warning_btn_cancel)) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                }
                .show()
        } else {
            onCancel?.invoke()
        }
    }

    /**
     * On clicking reset button reset the image state and the cropper state.
     */
    private fun onEditReset() {
        imageView.reset()

        // Register the image on reset listener
        imageView.onResetListener = {
            binding.cropper.reset()
        }
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
            binding.cropper.setZoomLevel(level)
        }

        imageView.onZoomedInListener = {
            binding.cropper.onZoomedIn()
        }

        imageView.onImageUpdate = {
            binding.cropper.updateBounds(getRect())
            binding.cropper.restrictBorder()
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
        val directionX = newX.parseToSide(newX)
        val directionY = newY.parseToSide(newY)

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
}
