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
import com.leondeklerk.starling.databinding.ModalDrawStyleBinding

class BrushStyleModal : BottomSheetDialogFragment() {

    private lateinit var binding: ModalDrawStyleBinding
    private var brushHue: Float = 0f
    private var brushSaturation: Float = 1f
    private var brushValue: Float = 1f
    private var brushType = BrushType.PENCIL
    private var brushSize = 8f
    private var alpha = 1f
    private var textSize = 24f
    private var textHue: Float = 0f
    private var textSaturation: Float = 0f
    private var textValue = 1f

    var onCloseListener: ((brush: BrushStyle, text: TextStyle) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ModalDrawStyleBinding.inflate(inflater, container, false)
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

        setupBrushViews()
        setupTextViews()
    }

    private fun setupBrushViews() {
        binding.sliderHue.trackGradient = getGradient(GradientType.HUE, 360f, 1f)
        binding.sliderSaturation.trackGradient = getGradient(GradientType.SATURATION, brushHue, 1f)
        binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, brushHue, brushSaturation)
        setBrushPreview()

        binding.sliderHue.addOnChangeListener { _, sliderValue, _ ->
            brushHue = sliderValue
            binding.sliderSaturation.trackGradient = getGradient(GradientType.SATURATION, brushHue, 1f)
            binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, brushHue, brushSaturation)
            setBrushPreview()
        }

        binding.sliderSaturation.addOnChangeListener { _, sliderValue, _ ->
            brushSaturation = sliderValue
            binding.sliderValue.trackGradient = getGradient(GradientType.VALUE, brushHue, brushSaturation)
            setBrushPreview()
        }

        binding.sliderValue.addOnChangeListener { _, sliderValue, _ ->
            brushValue = sliderValue
            setBrushPreview()
        }

        binding.sliderSize.addOnChangeListener { _, value, _ ->
            brushSize = value
            setBrushPreview()
        }

        binding.brushType.addOnButtonCheckedListener { _, checkedId, _ ->
            when (checkedId) {
                R.id.button_pencil -> {
                    brushType = BrushType.PENCIL
                    alpha = 1f
                    enableBrushSliders(true)
                }
                R.id.button_marker -> {
                    brushType = BrushType.MARKER
                    alpha = 0.3f
                    enableBrushSliders(true)
                }
                R.id.button_eraser -> {
                    brushType = BrushType.ERASER
                    alpha = 0f
                    enableBrushSliders(false)
                }
            }
        }
    }

    private fun setupTextViews() {
        binding.textSliderHue.trackGradient = getGradient(GradientType.HUE, 360f, 1f)
        binding.textSliderSaturation.trackGradient = getGradient(GradientType.SATURATION, textHue, 1f)
        binding.textSliderValue.trackGradient = getGradient(GradientType.VALUE, textHue, textSaturation)
        setTextPreview()

        binding.textSliderHue.addOnChangeListener { _, sliderValue, _ ->
            textHue = sliderValue
            binding.textSliderSaturation.trackGradient = getGradient(GradientType.SATURATION, textHue, 1f)
            binding.textSliderValue.trackGradient = getGradient(GradientType.VALUE, textHue, textSaturation)
            setTextPreview()
        }

        binding.textSliderSaturation.addOnChangeListener { _, sliderValue, _ ->
            textSaturation = sliderValue
            binding.textSliderValue.trackGradient = getGradient(GradientType.VALUE, textHue, textSaturation)
            setTextPreview()
        }

        binding.textSliderValue.addOnChangeListener { _, sliderValue, _ ->
            textValue = sliderValue
            setTextPreview()
        }

        binding.textSliderSize.addOnChangeListener { _, value, _ ->
            textSize = value
            setTextPreview()
        }
    }

    private fun enableBrushSliders(enabled: Boolean) {
        binding.sliderHue.isEnabled = enabled
        binding.sliderSaturation.isEnabled = enabled
        binding.sliderValue.isEnabled = enabled
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCloseListener?.invoke(getBrushStyle(), getTextStyle())
    }

    private fun setBrushPreview() {
        val color = Color.HSVToColor(floatArrayOf(brushHue, brushSaturation, brushValue))
        binding.sizeColorPreview.color = color
        binding.sizeColorPreview.radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, brushSize, resources.displayMetrics)
    }

    private fun setTextPreview() {
        val color = Color.HSVToColor(floatArrayOf(textHue, textSaturation, textValue))
        binding.textSizeColorPreview.setTextColor(color)
        binding.textSizeColorPreview.textSize = textSize
    }

    fun getBrushStyle(): BrushStyle {
        return BrushStyle(brushType, brushHue, brushSaturation, brushValue, brushSize, alpha)
    }

    fun getTextStyle(): TextStyle {
        return TextStyle(textHue, textSaturation, textValue, textSize)
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
        const val TAG = "StyleDialog"
    }
}
