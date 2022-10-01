package com.leondeklerk.starling.edit.draw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.leondeklerk.starling.databinding.ViewDrawBinding

/**
 * An edit overlay view containing buttons for selecting different drawing options,
 * and a [PaintView] canvas for drawing.
 */
class DrawView(context: Context, attributeSet: AttributeSet?) : RelativeLayout(
    context,
    attributeSet
) {
    private var binding: ViewDrawBinding = ViewDrawBinding.inflate(LayoutInflater.from(context), this, true)
    private val brushStyleModal = BrushStyleModal()
    private val textStyleModal = TextStyleModal()

    init {
        // Set the starting brush and text styles
        binding.canvas.setBrushStyle(brushStyleModal.getStyle())
        binding.canvas.setTextStyle(textStyleModal.getStyle())

        // Register the modal close listeners
        brushStyleModal.onCloseListener = { style ->
            binding.canvas.setBrushStyle(style)
        }

        textStyleModal.onCloseListener = { style ->
            binding.canvas.setTextStyle(style)
        }

        // Register the button listeners
        binding.buttonUndo.setOnClickListener {
            binding.canvas.undo()
        }

        binding.buttonClear.setOnClickListener {
            reset()
        }

        binding.buttonRedo.setOnClickListener {
            binding.canvas.redo()
        }

        binding.buttonText.setOnClickListener {
            binding.canvas.showTextModal()
        }

        binding.buttonStyle.setOnClickListener {
            brushStyleModal.show((context as AppCompatActivity).supportFragmentManager, BrushStyleModal.TAG)
        }

        // Register the long click listener for text style
        binding.buttonText.setOnLongClickListener {
            textStyleModal.show((context as AppCompatActivity).supportFragmentManager, TextStyleModal.TAG)
            true
        }
    }

    /**
     * Receives the bitmap source and propagates it to the canvas.
     * @param src: the bitmap to draw on
     */
    fun setBitmap(src: Bitmap) {
        binding.canvas.setBitmap(src)
    }

    /**
     * Updates the bounds of the canvas based on the received image bounds.
     * @param rect the bounds of the underlying imageView.
     */
    fun setBounds(rect: Rect) {
        // Create new margin layout parameters
        val params = binding.canvas.layoutParams as MarginLayoutParams

        params.apply {
            marginStart = rect.left
            marginEnd = rect.left
            topMargin = rect.top
            bottomMargin = binding.overlayControls.top - rect.bottom
        }

        binding.canvas.layoutParams = params
    }

    /**
     * Retrieve the bitmap result from the canvas
     * @return the drawing result
     */
    fun getBitmap(): Bitmap {
        return binding.canvas.getBitmap()
    }

    /**
     * Reset this overlay.
     * Propagates the call to the internal canvas.
     */
    fun reset() {
        binding.canvas.reset()
    }

    fun isTouched(): Boolean {
        return binding.canvas.isTouched()
    }
}
