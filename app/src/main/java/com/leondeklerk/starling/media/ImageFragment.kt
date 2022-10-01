package com.leondeklerk.starling.media

import android.content.DialogInterface
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ImageView
import androidx.fragment.app.viewModels
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.FragmentImageBinding
import com.leondeklerk.starling.edit.ContainerMode
import com.leondeklerk.starling.extensions.applyMargin
import com.leondeklerk.starling.extensions.requestNewSize
import com.leondeklerk.starling.media.data.ImageItem

class ImageFragment(
    private val item: ImageItem,
    private val enterListeners: PagerFragment.TransitionListeners,
    private val exitListeners: PagerFragment.TransitionListeners
) :
    MediaItemFragment() {
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

        enterTransition = null
        exitTransition = null

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
        pagerViewModel.setOverlay(View.GONE, true)
    }

    override fun isDeleted(success: Boolean) {
        // Unused hook
    }

    override fun isSaved(success: Boolean) {
        binding.editView.isSaved(success)
        loadImage()
    }

    override fun scale(scalarX: Float, scalarY: Float) {
        binding.imageView.scaleX = scalarX
        binding.imageView.scaleY = scalarY
    }

    override fun translate(dX: Float, dY: Float) {
        binding.imageView.translationX += dX
        binding.imageView.translationY += dY
    }

    override fun reset() {
        binding.imageView.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
            .setDuration(100L).start()
    }

    override fun close(target: Rect, duration: Long) {
        binding.imageView.apply {
            requestLayout()
            post {
                transition(
                    duration, binding.layout,
                    exitListeners.startListener,
                    exitListeners.endListener
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                translationX = 0f
                translationY = 0f
                scaleX = 1f
                scaleY = 1f
                requestNewSize(target.width(), target.height())
                applyMargin(target.left, target.top)
            }
        }
    }

    /**
     * Initialize all view binding variables and register listeners.
     */
    private fun setupViewBindings() {
        binding.editView.onCancel = {}

        binding.editView.onModeChange = { mode ->
            if (mode == ContainerMode.VIEW) {
                pagerViewModel.setOverlay(View.VISIBLE)
                pagerViewModel.releaseTouch()
            } else {
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

        if (pagerViewModel.initial) {
            val rect = pagerViewModel.rect
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.requestNewSize(rect.width(), rect.height())
            imageView.applyMargin(rect)
            imageView.post {
                imageView.load(viewModel.item.uri) {
                    placeholderMemoryCacheKey(viewModel.item.cacheKey)
                    size(binding.layout.width, binding.layout.height)
                    listener(onSuccess = { _, _ ->
                        initializeFragment()
                    })
                }
            }
        } else {
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.load(viewModel.item.uri)
        }
    }

    /**
     * Resize the imageView to the start position.
     * Then with a transition scale it to match the current screen size.
     */
    private fun initializeFragment() {
        pagerViewModel.initial = false

        binding.imageView.apply {
            transition(250L, binding.layout, enterListeners.startListener) {
                reinitialize()
                enterListeners.endListener.invoke(it)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
                marginStart = NO_MARGIN
                marginEnd = NO_MARGIN
                topMargin = NO_MARGIN
                bottomMargin = NO_MARGIN
                width = MATCH_PARENT
                height = MATCH_PARENT
            }
        }
    }

    companion object {
        private const val NO_MARGIN = 0
    }
}
