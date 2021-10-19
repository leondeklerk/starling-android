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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.values
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.leondeklerk.starling.databinding.ImageEditLayoutBinding
import com.leondeklerk.starling.edit.Side.NONE
import java.io.IOException

/**
 * Class that takes in a image and provides edit options.
 * Provides scaling, translating and cropping.
 * Makes use of a [CropHandlerView] to handle all selection box rendering and movement..
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

    var binding: ImageEditLayoutBinding = ImageEditLayoutBinding.inflate(LayoutInflater.from(context), this, true)
    var imageView: InteractiveImageView = binding.interactiveImageView
    private var cropHandler: CropHandlerView = binding.cropHandler

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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // On detach cancel the handler
        boxTransHandler.removeCallbacksAndMessages(null)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            if (ev.y >= binding.buttonSave.top) {
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

    private fun saveToStorage() {
        // TODO
        // var bitmap = imageView.drawable.toBitmap()
        //
        // // bitmap = Bitmap.createBitmap(bitmap, 0, 0, origWidth, origHeight, imageView.imageMatrix, false)
        // val scale = imageView.imageMatrix.values()[Matrix.MSCALE_X]
        // val cropBox = cropHandler.cropBox
        //
        // val bmWidth = bitmap.width
        // val bmHeight = bitmap.height
        //
        // val origWidth = floor(cropBox.width())
        // val origHeight = floor(cropBox.height())
        //
        // val ratio = bmWidth / origWidth
        //
        // val baseWidth = origWidth * (bmWidth / origWidth)
        // val baseHeight = origHeight * (bmHeight / origHeight)
        //
        // Timber.d("Values: $bmWidth, $bmHeight, $origWidth, $origHeight")
        // Timber.d("Ratios: ${bmWidth / origWidth}, ${bmHeight / origHeight}")
        // Timber.d( "Results: $baseWidth, $baseHeight")
        //
        // val cX = imageView.imageMatrix.values()[Matrix.MTRANS_X].toInt()
        // val cY = imageView.imageMatrix.values()[Matrix.MTRANS_Y].toInt()
        // val oX = (imageView.startValues!![Matrix.MTRANS_X]).toInt()
        // val oY = (imageView.startValues!![Matrix.MTRANS_Y]).toInt()
        //
        // val dX = (oX - cX)// / scale).toInt() //+ (cropBox.left.toInt() * scale).toInt()
        // val dY = (oY - cY)// / scale).toInt()
        // Timber.d("Offsets: $dX, $dY")
        //
        // val newWidth = floor(baseWidth).toInt()
        // val newHeight = floor(baseHeight).toInt()
        //
        // val resizedBitmap = Bitmap.createBitmap(
        //     bitmap, dX, dY,
        //     bmWidth, bmHeight, imageView.imageMatrix, true
        // )
        //
        //
        // saveBitmap(context, resizedBitmap, Bitmap.CompressFormat.PNG, "image/png", "test43")
    }

    @Throws(IOException::class)
    fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        displayName: String
    ): Uri {

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }

        val resolver = context.contentResolver
        var uri: Uri? = null

        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create new MediaStore record.")

            resolver.openOutputStream(uri)?.use {
                if (!bitmap.compress(format, 95, it))
                    throw IOException("Failed to save bitmap.")
            } ?: throw IOException("Failed to open output stream.")

            return uri
        } catch (e: IOException) {

            uri?.let { orphanUri ->
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(orphanUri, null, null)
            }

            throw e
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
}
