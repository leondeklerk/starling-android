package com.leondeklerk.starling.edit.draw

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.ModalBrushStyleBinding
import com.leondeklerk.starling.views.ColorDotView
import com.leondeklerk.starling.views.GradientSlider

/**
 * Modal responsible for selecting the different properties of the current brush.
 * Contains a button group for the brush type,
 * sliders for the HSV color attributes and size.
 * Additionally includes a [ColorDotView] preview
 */
class BrushStyleModal : BottomSheetDialogFragment() {

    private lateinit var binding: ModalBrushStyleBinding
    private var hue: Float = 0f
    private var saturation: Float = 1f
    private var value: Float = 1f
    private var type = BrushType.PENCIL
    private var size = 8f
    private var alpha = 1f

    var onCloseListener: ((brush: BrushStyle) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ModalBrushStyleBinding.inflate(inflater, container, false)
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
     * Create a [BrushStyle] from the current values
     * @return a [BrushStyle] based on the current values
     */
    fun getStyle(): BrushStyle {
        return BrushStyle(type, hue, saturation, value, size, alpha)
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

        // Listener for the brush type buttons, sets type alpha and enables/disables sliders.
        binding.brushType.addOnButtonCheckedListener { _, checkedId, _ ->
            when (checkedId) {
                R.id.button_pencil -> {
                    type = BrushType.PENCIL
                    alpha = 1f
                    enableSliders(true)
                }
                R.id.button_marker -> {
                    type = BrushType.MARKER
                    alpha = 0.3f
                    enableSliders(true)
                }
                R.id.button_eraser -> {
                    type = BrushType.ERASER
                    alpha = 0f
                    enableSliders(false)
                }
            }
        }
    }

    /**
     * Enable or disable the HSV attribute sliders.
     * @param enabled indicates if the sliders should be enabled or not
     */
    private fun enableSliders(enabled: Boolean) {
        binding.sliderHue.isEnabled = enabled
        binding.sliderSaturation.isEnabled = enabled
        binding.sliderValue.isEnabled = enabled
    }

    /**
     * Based on the current color and size, apply the values to the [ColorDotView] preview.
     */
    private fun setPreview() {
        // Create an actual color from the HSV values
        val color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        binding.sizeColorPreview.color = color
        binding.sizeColorPreview.radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, resources.displayMetrics)
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
        const val TAG = "BrushStyleModal"
    }
}
