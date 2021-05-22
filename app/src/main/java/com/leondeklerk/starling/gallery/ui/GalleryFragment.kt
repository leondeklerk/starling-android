package com.leondeklerk.starling.gallery.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.leondeklerk.starling.data.MediaItemTypes
import com.leondeklerk.starling.databinding.FragmentGalleryBinding
import com.leondeklerk.starling.gallery.GalleryAdapter

/**
 * [GalleryFragment] is the main fragment of the application.
 * It contains the main recyclerView that contains all images and videos on the device and synced.
 * Uses a [GalleryAdapter] assisted by [GalleryViewModel] to display
 * [com.leondeklerk.starling.gallery.data.GalleryItem]s.
 * This fragment handles the required permission.
 */
class GalleryFragment : Fragment() {

    private lateinit var galleryViewModel: GalleryViewModel
    private var _binding: FragmentGalleryBinding? = null
    private lateinit var sharedPrefs: SharedPreferences

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    companion object {
        private const val READ_EXTERNAL_STORAGE_PERMISSION_FLAG = 1
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
        binding.lifecycleOwner = this
        binding.viewModel = galleryViewModel

        sharedPrefs = requireActivity().getPreferences(Context.MODE_PRIVATE)

        handleReadStoragePermission()
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            READ_EXTERNAL_STORAGE_PERMISSION_FLAG -> {
                // If a permission is requested for the first time it should not show the settings rationale immediately.
                val isFirstRequest =
                    sharedPrefs.getBoolean(READ_EXTERNAL_STORAGE_PERMISSION_PREF_FLAG, true)
                if (isFirstRequest) {
                    sharedPrefs.edit().putBoolean(READ_EXTERNAL_STORAGE_PERMISSION_PREF_FLAG, false)
                        .apply()
                }
                // If the permission was granted we can set the adapter
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setAdapter()
                } else {
                    // We were not granted the permission so we can do two things:
                    // 1. Show the permission rationale, with on button click showing the permission dialog again.
                    // 2. We cannot show the dialog again (Either Android 11+ or user clicked don't ask again),
                    // so we need to redirect to the settings (Note: Only if this was not the first request)
                    val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                    binding.permissionRationaleView.isGone = false

                    if (shouldShowRequestPermissionRationale(permission)) {
                        // Case 1: we can show the permission dialog again on click
                        binding.permissionRationaleButton.setOnClickListener {
                            requestPermission(permission, READ_EXTERNAL_STORAGE_PERMISSION_FLAG)
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
                                requestPermission(permission, READ_EXTERNAL_STORAGE_PERMISSION_FLAG)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper function to check and ask for the [Manifest.permission.MANAGE_EXTERNAL_STORAGE] permission.
     * This will check if the permission is granted, if so we can call [setAdapter].
     * If a permission was not granted we need to request it, and potentially show a rationale to inform the user about the reason.
     */
    private fun handleReadStoragePermission() {
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE

        if (hasPermission(permission)) {
            // We can bind the adapter to the view
            setAdapter()
        } else {

            // We have no permission so we should either show the rationale or immediately request the permission
            if (shouldShowRequestPermissionRationale(permission)) {
                binding.permissionRationaleView.isGone = false
                // Setup the view and button listener
                binding.permissionRationaleButton.setOnClickListener {
                    requestPermission(permission, READ_EXTERNAL_STORAGE_PERMISSION_FLAG)
                }
            } else {
                requestPermission(permission, READ_EXTERNAL_STORAGE_PERMISSION_FLAG)
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

        // Create a GalleryAdapter and add the data to it
        val adapter = GalleryAdapter()

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
     * Helper function to check if the user granted a specific permission to the application
     * @param permission: The permission that should be checked, a [String] from [Manifest.permission]
     * @return: A [Boolean] indicating if the permission was granted or not
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PermissionChecker.PERMISSION_GRANTED
    }

    /**
     * Helper function to request a specific permission and associate the [onRequestPermissionsResult] flag.
     * @param permission: A [String] containing the permission we want to request, from [Manifest.permission]
     * @param flag: An [Int] containing the flag we associate with this request, needed in [onRequestPermissionsResult]
     */
    private fun requestPermission(permission: String, flag: Int) {
        requestPermission(permission, flag)
    }

    /**
     * Helper function that will redirect the user to the settings screen of this application using [Intent]
     */
    private fun goToSettings() {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${requireActivity().packageName}")
        ).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.also { intent ->
            startActivity(intent)
        }
    }
}
