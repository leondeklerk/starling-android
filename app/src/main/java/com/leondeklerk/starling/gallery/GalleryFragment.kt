package com.leondeklerk.starling.gallery

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.data.MediaItem
import com.leondeklerk.starling.data.MediaItemTypes
import com.leondeklerk.starling.data.VideoItem
import com.leondeklerk.starling.databinding.FragmentGalleryBinding
import com.leondeklerk.starling.extensions.goToSettings
import com.leondeklerk.starling.extensions.hasPermission
import com.leondeklerk.starling.media.MediaGalleryAdapter

/**
 * [GalleryFragment] is the main fragment of the application.
 * It contains the main recyclerView that contains all images and videos on the device and synced.
 * Uses a [MediaGalleryAdapter] assisted by [GalleryViewModel] to display
 * [com.leondeklerk.starling.data.MediaItem]s.
 * This fragment handles the required permission.
 */
class GalleryFragment : Fragment() {

    private lateinit var galleryViewModel: GalleryViewModel
    private var _binding: FragmentGalleryBinding? = null
    private lateinit var sharedPrefs: SharedPreferences

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    companion object {
        private const val READ_EXTERNAL_STORAGE_PERMISSION_PREF_FLAG =
            "READ_EXTERNAL_STORAGE_PERMISSION_FLAG"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create the viewModel
        galleryViewModel =
            ViewModelProvider(this).get(GalleryViewModel::class.java)

        // Inflate the binding
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Set binding basics
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = galleryViewModel

        sharedPrefs = requireActivity().getPreferences(Context.MODE_PRIVATE)

        handleReadStoragePermission()
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Helper function to check and ask for the [Manifest.permission.MANAGE_EXTERNAL_STORAGE] permission.
     * This will check if the permission is granted, if so we can call [setAdapter].
     * If a permission was not granted we need to request it, and potentially show a rationale to inform the user about the reason.
     */
    private fun handleReadStoragePermission() {
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE

        registerPermissionLauncher()

        if (hasPermission(permission)) {
            // We can bind the adapter to the view
            setAdapter()
        } else {

            // We have no permission so we should either show the rationale or immediately request the permission
            if (shouldShowRequestPermissionRationale(permission)) {
                binding.permissionRationaleView.isGone = false
                // Setup the view and button listener
                binding.permissionRationaleButton.setOnClickListener {
                    requestPermissionLauncher.launch(permission)
                }
            } else {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    /**
     * Helper function to register the [ActivityResultLauncher] permission launcher.
     * Responsible for requesting permission and hanlding the result.
     */
    private fun registerPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts
                .RequestPermission()
        ) {
            // If a permission is requested for the first time it should not show the settings rationale immediately.
            val isFirstRequest =
                sharedPrefs.getBoolean(READ_EXTERNAL_STORAGE_PERMISSION_PREF_FLAG, true)
            if (isFirstRequest) {
                sharedPrefs.edit().putBoolean(READ_EXTERNAL_STORAGE_PERMISSION_PREF_FLAG, false)
                    .apply()
            }

            if (it) {
                setAdapter()
            } else {
                handleRejectedPermission(isFirstRequest)
            }
        }
    }

    /**
     * Makes sure everything is set so the fragment can properly show the recyclerview.
     * Handles the removal of a (potential) permission rationale as well as creating the layout and adapter.
     * Also instantiates an observer for the viewModel data.
     */
    private fun setAdapter() {
        // Start loading the media from the device
        galleryViewModel.loadMedia()

        // Handle the permission rationale
        binding.permissionRationaleView.isGone = true

        // Create item click listener
        val mediaItemClickListener = { item: MediaItem ->
            if (item.type == MediaItemTypes.VIDEO) {
                val directions = GalleryFragmentDirections.actionNavigationGalleryToVideoActivity(item as VideoItem)
                findNavController().navigate(directions)
            } else if (item.type == MediaItemTypes.IMAGE) {
                val directions = GalleryFragmentDirections.actionNavigationGalleryToImageActivity(item as ImageItem)
                findNavController().navigate(directions)
            }
        }

        // Create a GalleryAdapter and add the data to it
        val adapter = MediaGalleryAdapter(mediaItemClickListener)

        galleryViewModel.data.observe(
            viewLifecycleOwner,
            {
                it?.let {
                    adapter.submitList(it)
                }
            }
        )

        // Create a grid layout manager
        // TODO: allow for updates of the layout + allow for more flexibility in size
        val manager = GridLayoutManager(activity, 5, GridLayoutManager.VERTICAL, false)

        // Give headers a full span
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    MediaItemTypes.HEADER.ordinal -> manager.spanCount
                    else -> 1
                }
            }
        }

        // Bind this data to the view to populate the recyclerView
        binding.galleryGrid.let {
            it.adapter = adapter
            it.layoutManager = manager
        }
    }

    /**
     * Helper function to manage rejected permissions.
     * From a rejected permission there are multiple cases:
     * 1. Show the permission rationale, with on button click showing the permission dialog again.
     * 2. We cannot show the dialog again (Either Android 11+ or user clicked don't ask again),
     * so we need to redirect to the settings (Note: Only if this was not the first request)
     * @param isFirstRequest: a boolean indicating if this was the first ever request for this permission or not.
     */
    private fun handleRejectedPermission(isFirstRequest: Boolean) {
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        binding.permissionRationaleView.isGone = false

        if (shouldShowRequestPermissionRationale(permission)) {
            // Case 1: we can show the permission dialog again on click
            binding.permissionRationaleButton.setOnClickListener {
                requestPermissionLauncher.launch(permission)
            }
        } else {
            // If we got a rejection and it was not the first time, we need to show the settings rationale
            if (!isFirstRequest) {
                // Case 2: We cannot show the dialog, but we can (willingly) redirect the user to settings.
                galleryViewModel.updateRationale(true)
                binding.permissionRationaleButton.setOnClickListener {
                    goToSettings()
                }
            } else {
                binding.permissionRationaleButton.setOnClickListener {
                    requestPermissionLauncher.launch(permission)
                }
            }
        }
    }
}
