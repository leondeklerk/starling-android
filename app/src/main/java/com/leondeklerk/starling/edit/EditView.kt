package com.leondeklerk.starling.edit

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRect
import androidx.core.graphics.values
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.databinding.ImageEditLayoutBinding
import com.leondeklerk.starling.edit.Side.NONE
import java.io.IOException
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Class that takes in a image and provides edit options.
 * Provides scaling, translating and cropping.
 * Makes use of a [CropView] to handle all selection box rendering and movement..
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

    // Matrix variables
    private var matrixValues = FloatArray(9)

    // Touch variables
    private var movingBox = false
    private var movingOther = false

    // Animation
    private var refreshRate = 60f

    companion object {
        private const val BOX_DURATION = 100L
    }

    private var binding: ImageEditLayoutBinding = ImageEditLayoutBinding.inflate(LayoutInflater.from(context), this, true)
    private var cropHandler: CropView = binding.cropHandler

    var imageView: InteractiveImageView = binding.interactiveImageView
    var onCancel: (() -> Unit)? = null
    var onSave: ((data: ImageItem) -> Unit)? = null

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
        binding.buttonSave.setOnClickListener {
            saveToStorage()
        }

        binding.buttonCancel.setOnClickListener {
            onEditCancel()
        }

        binding.buttonReset.setOnClickListener {
            onEditReset()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // On detach cancel the handler
        boxTransHandler.removeCallbacksAndMessages(null)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            if (ev.y >= binding.rotationSlider.top) {
                return false
            }
        }

        // The edit component should always handle a touch event -- false
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable && isEnabled) {
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
                            movingBox = cropHandler.startMove(PointF(event.x, event.y))
                        }

                        // If we have a handler movement
                        if (movingBox) {
                            cropHandler.onMove(PointF(event.x, event.y))
                        } else {
                            // we are dealing with an outside box movement
                            imageView.onSinglePointerMove(getRect())
                            movingOther = true
                        }
                    } else {
                        // If we have two fingers, it can only be scaling, cancel all other movement
                        if (movingBox) {
                            // Reset everything
                            cropHandler.cancelMove()
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
                        cropHandler.endMove()
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
        // Get view values
        val bitmap = imageView.drawable.toBitmap()
        val values = imageView.imageMatrix.values()
        val startScale = imageView.startValues!![Matrix.MSCALE_X]
        val cropBox = cropHandler.cropBox.toRect()

        // Get matrix values
        val scale = values[Matrix.MSCALE_X]
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

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

        val result = Bitmap.createBitmap(
            bitmap,
            xOffset.toInt(),
            yOffset.toInt(),
            resultWidth.toInt(),
            resultHeight.toInt(),
            matrix,
            true
        )

        CoroutineScope(Dispatchers.Main).launch {
            try {
                saveBitmap(context, result, Bitmap.CompressFormat.JPEG, "image/jpeg", UUID.randomUUID().toString())
            } catch (e: IOException) {
                Toast.makeText(context, "Error $e", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        displayName: String
    ) {
        val uri: Uri?

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }

        val resolver = context.contentResolver

        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Failed to create new MediaStore record.")
        try {
            withContext(Dispatchers.IO) {
                // Android studio gives an incorrect blocking call warning
                @Suppress("BlockingMethodInNonBlockingContext")
                val inputStream = resolver.openOutputStream(uri)
                inputStream?.use {
                    if (!bitmap.compress(format, 100, it))
                        throw IOException("Failed to save bitmap.")
                }
            }
        } catch (e: IOException) {
            uri.let { orphanUri ->
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(orphanUri, null, null)
            }
            throw e
        }
    }

    private fun onEditCancel() {
        // Reset all the data, show the popup etc
        onCancel?.invoke()
    }

    /**
     * On clicking reset button reset the image state and the cropper state.
     */
    private fun onEditReset() {
        imageView.reset()

        // Register the image on reset listener
        imageView.onResetListener = {
            cropHandler.reset(BOX_DURATION)
        }
    }

    /**
     * Sets up all the listeners for the image view.
     * And set the properties of the imageView.
     */
    private fun setupImageView() {
        imageView.onBitmapSetListener = {
            onBitmapSet()
        }

        imageView.onMMatrixUpdateListener = { values ->
            onMMatrixUpdate(values)
        }

        imageView.onMatrixAppliedListener = {
            cropHandler.onRestrictBorder(BOX_DURATION)
        }

        imageView.onAxisCenteredListener = {
            cropHandler.onAxisCentered(BOX_DURATION)
        }

        imageView.onZoomLevelChangeListener = { level ->
            cropHandler.setZoomLevel(level)
        }
        imageView.onZoomedInListener = {
            cropHandler.onZoomedIn(BOX_DURATION * 2L)
        }
        imageView.allowTranslation = true
    }

    /**
     * Handler invoked when the bitmap is set on the imageView.
     * Responsible for initializing the cropHandler.
     */
    private fun onBitmapSet() {
        cropHandler.setInitialValues(getRect())

        cropHandler.boundsHitHandler = { delta, types ->
            handleSideTranslate(delta, types)
        }

        cropHandler.zoomHandler = { center, out ->
            imageView.zoomImage(center, out)
        }
    }

    /**
     * Get the rectangle based on the values of the image matrix value array.
     * Takes the translation into account.
     * @returns a rectangle representing the matrix borders
     */
    private fun getRect(): RectF {
        val left = imageView.marginLeft + matrixValues[Matrix.MTRANS_X]
        val top = imageView.marginTop + matrixValues[Matrix.MTRANS_Y]
        val right = imgWidth() + left
        val bottom = imgHeight() + top
        return RectF(left, top, right, bottom)
    }

    /**
     * Getter for the current width of the image
     * @return the width
     */
    private fun imgWidth(): Float {
        return imageView.imgWidth()
    }

    /**
     * Getter for the current height of the image
     * @return the height
     */
    private fun imgHeight(): Float {
        return imageView.imgHeight()
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
     * Handler invoked when the manipulation matrix was updated.
     * Updates the bounds of the cropHandler and sets the current matrix values.
     * @param values a float array containing the current image matrix values.
     */
    private fun onMMatrixUpdate(values: FloatArray) {
        matrixValues = values
        val rect = getRect()

        // Cap at the bounds.
        if (imgWidth() > imageView.width) {
            rect.left = imageView.marginLeft.toFloat()
            rect.right = rect.left + imageView.width.toFloat()
        }

        if (imgHeight() > imageView.height) {
            rect.top = imageView.marginTop.toFloat()
            rect.bottom = rect.top + imageView.height.toFloat()
        }

        cropHandler.updateBounds(rect)
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
