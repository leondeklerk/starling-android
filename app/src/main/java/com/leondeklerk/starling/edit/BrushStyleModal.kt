package com.leondeklerk.starling.edit

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
                val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout
                val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
                bottomSheetBehavior.isDraggable = false
            }
        }

        setup()
    }

    private fun setup() {
        binding.sliderHue.trackGradient = getGradient(GradientType.HUE, 360f, 1f)
        binding.sliderSaturation.trackGradient = getGradient(GradientType.SATURATION, hue, 1f)
        binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, hue, saturation)
        setPreview()

        binding.sliderHue.addOnChangeListener { _, sliderValue, _ ->
            hue = sliderValue
            binding.sliderSaturation.trackGradient = getGradient(GradientType.SATURATION, hue, 1f)
            binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, hue, saturation)
            setPreview()
        }

        binding.sliderSaturation.addOnChangeListener { _, sliderValue, _ ->
            saturation = sliderValue
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

    private fun enableSliders(enabled: Boolean) {
        binding.sliderHue.isEnabled = enabled
        binding.sliderSaturation.isEnabled = enabled
        binding.sliderValue.isEnabled = enabled
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCloseListener?.invoke(getStyle())
    }

    private fun setPreview() {
        val color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        binding.sizeColorPreview.color = color
        binding.sizeColorPreview.radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, resources.displayMetrics)
    }

    fun getStyle(): BrushStyle {
        return BrushStyle(type, hue, saturation, value, size, alpha)
    }

    private fun getGradient(type: GradientType, hue: Float, saturation: Float): GradientDrawable {
        val spaceSize = when (type) {
            GradientType.HUE -> 360
            GradientType.VALUE, GradientType.SATURATION -> 100
        }

        val colors = IntArray(spaceSize)
        for (i in 0 until spaceSize) {
            colors[i] = when (type) {
                GradientType.HUE -> Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
                GradientType.SATURATION -> Color.HSVToColor(floatArrayOf(hue, i / 100f, 1f))
                GradientType.VALUE -> Color.HSVToColor(floatArrayOf(hue, saturation, i / 100f))
            }
        }

        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
    }

    companion object {
        const val TAG = "BrushStyleDialog"
    }
}
