package com.leondeklerk.starling.edit.draw

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.leondeklerk.starling.databinding.ModalTextStyleBinding
import com.leondeklerk.starling.views.ColorDotView
import com.leondeklerk.starling.views.GradientSlider

/**
 * Modal responsible for selecting the different properties of a text style.
 * Contains sliders for the HSV color attributes and size.
 * Additionally includes a [ColorDotView] preview
 */
class TextStyleModal : BottomSheetDialogFragment() {

    private lateinit var binding: ModalTextStyleBinding
    private var size = SIZE_DEFAULT
    private var hue = HUE_DEFAULT
    private var saturation = SATURATION_DEFAULT
    private var value = VALUE_DEFAULT

    var onCloseListener: ((text: TextStyle) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ModalTextStyleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setOnShowListener { dialog ->
            (dialog as BottomSheetDialog).let {
                // Make sure the dialog cannot be dragged
                val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout
                val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
                bottomSheetBehavior.isDraggable = false
            }
        }

        setup()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCloseListener?.invoke(getStyle())
    }

    /**
     * Create a [TextStyle] from the current values
     * @return a [TextStyle] based on the current values
     */
    fun getStyle(): TextStyle {
        return TextStyle(hue, saturation, value, size)
    }

    /**
     * Set up all listeners and the gradients for all the sliders.
     * Responsible for creating the trackGradients of the [GradientSlider]s
     */
    private fun setup() {
        // Create all the starting gradients and preview
        binding.sliderHue.trackGradient = getGradient(GradientType.HUE, 360f, 1f)
        binding.sliderSaturation.trackGradient = getGradient(GradientType.SATURATION, hue, 1f)
        binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, hue, saturation)
        setPreview()

        // Set up the slider listeners
        binding.sliderHue.addOnChangeListener { _, sliderValue, _ ->
            hue = sliderValue
            // On a change of hue, update the other slider gradients
            binding.sliderSaturation.trackGradient = getGradient(GradientType.SATURATION, hue, 1f)
            binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, hue, saturation)
            setPreview()
        }

        binding.sliderSaturation.addOnChangeListener { _, sliderValue, _ ->
            saturation = sliderValue
            // Create the new value gradient based on the saturation value
            binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, hue, saturation)
            setPreview()
        }

        binding.sliderValue.addOnChangeListener { _, sliderValue, _ ->
            value = sliderValue
            setPreview()
        }

        binding.sliderSize.addOnChangeListener { _, value, _ ->
            size = value
            setPreview()
        }
    }

    /**
     * Based on the current color and size, apply the values to the [ColorDotView] preview.
     */
    private fun setPreview() {
        val color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        binding.sizeColorPreview.setTextColor(color)
        binding.sizeColorPreview.textSize = size
    }

    /**
     * Create HSV gradients based on the type of HSV attribute and the current values.
     * Based on the type, a range of different values is created (360 for hue, 100 for saturation/value)
     * @param type: the type of HSV attribute this gradient represents
     * @param hue the current hue value used in the saturation/value sliders
     * @param saturation the current saturation used in the value slider
     * @return a [GradientDrawable] representing the range of possible values for this type
     */
    private fun getGradient(type: GradientType, hue: Float, saturation: Float): GradientDrawable {
        // Determine the correct number of steps available
        val spaceSize = when (type) {
            GradientType.HUE -> 360
            GradientType.VALUE, GradientType.SATURATION -> 100
        }

        val colors = IntArray(spaceSize)
        // Create the specific color for each step of the attribute space.
        for (i in 0 until spaceSize) {
            colors[i] = when (type) {
                GradientType.HUE -> Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
                GradientType.SATURATION -> Color.HSVToColor(floatArrayOf(hue, i / 100f, 1f))
                GradientType.VALUE -> Color.HSVToColor(floatArrayOf(hue, saturation, i / 100f))
            }
        }

        // Create the actual gradient drawable based on the color list
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
    }

    companion object {
        const val TAG = "TextStyleModal"
        private const val SIZE_DEFAULT = 24f
        private const val HUE_DEFAULT = 0f
        private const val SATURATION_DEFAULT = 0f
        private const val VALUE_DEFAULT = 1f
    }
}
