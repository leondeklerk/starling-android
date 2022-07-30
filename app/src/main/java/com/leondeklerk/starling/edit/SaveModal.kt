package com.leondeklerk.starling.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.leondeklerk.starling.databinding.ModalSaveTypeBinding

/**
 * Modal responsible for selecting the correct save type.
 */
class SaveModal : BottomSheetDialogFragment() {
    private lateinit var binding: ModalSaveTypeBinding
    var onCloseListener: ((type: String) -> Unit)? = null

    companion object {
        const val TAG = "SaveModal"
        const val TYPE_SAVE = "SAVE"
        const val TYPE_COPY = "COPY"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ModalSaveTypeBinding.inflate(inflater, container, false)

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

        binding.typeSave.setOnClickListener {
            close(TYPE_SAVE)
        }

        binding.typeCopy.setOnClickListener {
            close(TYPE_COPY)
        }
    }

    /**
     * Close the dialog and invoke the on close listener if exists.
     * @param type: the selected save type.
     */
    private fun close(type: String) {
        onCloseListener?.invoke(type)
        dismiss()
    }
}
