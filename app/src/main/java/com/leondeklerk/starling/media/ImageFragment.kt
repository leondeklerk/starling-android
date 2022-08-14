package com.leondeklerk.starling.media

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.FragmentImageBinding
import com.leondeklerk.starling.media.data.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewBindings()

        setupViewModelBindings()
    }

    override fun onResume() {
        super.onResume()
        activityViewModel.setEditEnabled(true)
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
        viewModel.switchMode()
    }

    override fun isDeleted(success: Boolean) {
        // Unused hook
    }

    override fun isSaved(success: Boolean) {
        binding.editView.isSaved(success)

        if (success) {
            viewModel.switchMode()
        }
    }

    /**
     * Initialize all view binding variables and register listeners.
     */
    private fun setupViewBindings() {
        binding.imageView.allowTranslation = false

        binding.imageView.onRequireLock = {
            activityViewModel.lockView.postValue(it)
        }

        binding.editView.onCancel = {
            viewModel.switchMode()
        }

        // On clicking the image the system ui and the toolbar should disappear for a fullscreen experience.
        binding.imageView.onTapListener = {
            activityViewModel.toggleInsets()
        }

        binding.editView.onSave = { result, copy ->
            viewModel.tryUpdate(result, copy)
        }
    }

    /**
     * Initialize all viewModel listeners and state.
     */
    private fun setupViewModelBindings() {
        viewModel.mode.observe(viewLifecycleOwner) {
            onModeChanged(it)
        }

        activityViewModel.setEditEnabled(true)
    }

    /**
     * Change between the edit mode and view mode based on the given state.
     * @param mode the current mode (Edit/View)
     */
    private fun onModeChanged(mode: ImageViewModel.Mode) {
        if (mode == ImageViewModel.Mode.VIEW) {
            binding.editView.visibility = View.GONE
            binding.imageView.visibility = View.VISIBLE

            activityViewModel.lockView.postValue(false)

            loadImage(binding.imageView)
            activityViewModel.showOverlay(true)
        } else {
            binding.imageView.visibility = View.GONE

            activityViewModel.lockView.postValue(true)
            activityViewModel.showOverlay(false)

            binding.editView.visibility = View.VISIBLE
            loadImage(binding.editView.imageView)
        }
    }

    /**
     * Load the image into the provided image view.
     * @param imageView the specific image view to load the image into.
     */
    private fun loadImage(imageView: ImageView) {
        val fragment = this
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                // Load image with Glide into the imageView
                Glide.with(fragment)
                    .asBitmap()
                    .signature(ObjectKey(viewModel.item.dateModified))
                    .load(viewModel.item.uri)
                    .into(imageView)
            }
        }
    }
}
