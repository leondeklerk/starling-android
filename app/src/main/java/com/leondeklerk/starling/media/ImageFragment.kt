package com.leondeklerk.starling.media

import android.content.DialogInterface
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.FragmentImageBinding
import com.leondeklerk.starling.edit.ContainerMode
import com.leondeklerk.starling.media.data.ImageItem

class ImageFragment(private val item: ImageItem) : MediaItemFragment() {
    override val viewModel: ImageViewModel by viewModels()

    private lateinit var binding: FragmentImageBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentImageBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel.item = item

        binding.imageView.transitionName = "${item.id}"

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadImage()

        setupViewBindings()

        setupViewModelBindings()
    }

    override fun onResume() {
        super.onResume()
        pagerViewModel.setEditEnabled(true)
    }

    override fun delete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.image_delete_title))
            .setMessage(getString(R.string.image_delete_message))
            .setPositiveButton(getString(R.string.media_delete)) { _: DialogInterface, _: Int ->
                viewModel.tryDelete()
            }
            .setNegativeButton(getString(android.R.string.cancel)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    override fun edit() {
        binding.editView.setMode(ContainerMode.EDIT)
    }

    override fun isDeleted(success: Boolean) {
        // Unused hook
    }

    override fun isSaved(success: Boolean) {
        binding.editView.isSaved(success)
        loadImage()
    }

    override fun scale(scalarX: Float, scalarY: Float) {
    }

    override fun translate(dX: Float, dY: Float) {
    }

    override fun reset() {
    }

    override fun close(target: Rect, duration: Long) {
    }

    override fun sharedView(): View {
        return binding.imageView
    }

    /**
     * Initialize all view binding variables and register listeners.
     */
    private fun setupViewBindings() {
        binding.editView.onCancel = {}

        binding.editView.onModeChange = { mode ->
            if (mode == ContainerMode.VIEW) {
                pagerViewModel.setOverlayMode(true)
                pagerViewModel.showOverlay()
                pagerViewModel.releaseTouch()
            } else {
                pagerViewModel.setOverlayMode(false)
                pagerViewModel.captureTouch()
            }
        }

        binding.editView.onScaledStateListener = { scaled ->
            if (scaled) {
                pagerViewModel.captureTouch()
            } else {
                pagerViewModel.releaseTouch()
            }
        }

        // On clicking the image the system ui and the toolbar should disappear for a fullscreen experience.
        binding.editView.onTapListener = { mode ->
            if (mode == ContainerMode.VIEW) {
                pagerViewModel.toggleOverlay()
            }
        }

        binding.editView.onSave = { result, copy ->
            viewModel.tryUpdate(result, copy)
        }
    }

    /**
     * Initialize all viewModel listeners and state.
     */
    private fun setupViewModelBindings() {
        pagerViewModel.setEditEnabled(true)
    }

    /**
     * Load an image into the imageView using Coil.
     * If this is the initialization of the pager, use a transition.
     */
    private fun loadImage() {

        val imageView = binding.imageView
        imageView.load(viewModel.item.uri) {
            allowHardware(false)
            listener(onSuccess = { _, _ ->
                requireActivity().startPostponedEnterTransition()
            })
        }
    }
}
