package com.leondeklerk.starling.edit.crop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.ModalAspectRatioBinding

/**
 * Modal responsible for selection the correct [AspectRatio].
 * The selected aspectRatio is accessible via the optional [onCloseListener] or directly via the [aspectRatio] property.
 * Default aspectRatio: [AspectRatio.FREE].
 */
class AspectRatioModal : BottomSheetDialogFragment() {
    private lateinit var binding: ModalAspectRatioBinding
    var aspectRatio = AspectRatio.FREE

    var onCloseListener: ((ratio: AspectRatio) -> Unit)? = null

    companion object {
        const val TAG = "AspectRatioModal"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ModalAspectRatioBinding.inflate(inflater, container, false)

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

        binding.buttonGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && isVisible) {
                when (checkedId) {
                    R.id.ratio_free -> close(AspectRatio.FREE)
                    R.id.ratio_square -> close(AspectRatio.SQUARE)
                    R.id.ratio_original -> close(AspectRatio.ORIGINAL)
                    R.id.ratio_four_to_three -> close(AspectRatio.FOUR_THREE)
                    R.id.ratio_sixteen_to_nine -> close(AspectRatio.SIXTEEN_NINE)
                    R.id.ratio_twenty_one_to_nine -> close(AspectRatio.TWENTYONE_NINE)
                    R.id.ratio_five_to_four -> close(AspectRatio.FIVE_FOUR)
                }
            }
        }
    }

    /**
     * Close the dialog and invoke the on close listener if exists.
     * Takes the new aspect ratio.
     * @param aspectRatio: the new aspect ratio.
     */
    private fun close(aspectRatio: AspectRatio) {
        this.aspectRatio = aspectRatio
        onCloseListener?.invoke(aspectRatio)
        dismiss()
    }
}
