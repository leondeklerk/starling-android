package com.leondeklerk.starling.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.leondeklerk.starling.databinding.ViewDrawBinding
import com.leondeklerk.starling.extensions.dpToPixels

class DrawView(context: Context, attributeSet: AttributeSet?) : RelativeLayout(
    context,
    attributeSet
) {
    private var binding: ViewDrawBinding = ViewDrawBinding.inflate(LayoutInflater.from(context), this, true)
    private val styleModal = BrushStyleModal()
    private val textStyleModal = TextStyleModal()

    private var touchOffset = 0f

    init {
        binding.canvas.setBrushStyle(styleModal.getBrushStyle())
        binding.canvas.setTextStyle(styleModal.getTextStyle())

        styleModal.onCloseListener = { brush, text ->
            binding.canvas.setBrushStyle(brush)
            binding.canvas.setTextStyle(text)
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
            binding.canvas.addText()
        }

        binding.buttonStyle.setOnClickListener {
            styleModal.show((context as AppCompatActivity).supportFragmentManager, BrushStyleModal.TAG)
        }

        binding.buttonText.setOnLongClickListener {
            true
        }

        touchOffset = dpToPixels(16f)
    }

    fun setBitmap(src: Bitmap) {
        binding.canvas.setBitmap(src)
    }

    private fun undo() {
        binding.canvas.undo()
    }

    private fun redo() {
        binding.canvas.redo()
    }

    fun setBounds(newBounds: Rect) {
        newBounds.let { rect ->
            val params = binding.canvas.layoutParams as MarginLayoutParams

            params.apply {
                marginStart = rect.left
                marginEnd = rect.left
                topMargin = rect.top
                bottomMargin = binding.buttonClear.top - rect.bottom
            }

            binding.canvas.layoutParams = params
        }
    }

    fun getBitmap(): Bitmap {
        return binding.canvas.getBitmap()
    }

    fun reset() {
        binding.canvas.reset()
    }
}
