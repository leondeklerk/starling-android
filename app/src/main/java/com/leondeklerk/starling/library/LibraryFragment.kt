package com.leondeklerk.starling.library

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.leondeklerk.starling.databinding.FragmentLibraryBinding

/**
 * A simple [Fragment] responsible for showing all media on the device (device only) in a folder structure.
 * This will also responsible for setting sync settings on all folders. TODO
 */
class LibraryFragment : Fragment() {

    private lateinit var libraryViewModel: LibraryViewModel
    private var _binding: FragmentLibraryBinding? = null
    private lateinit var sharedPrefs: SharedPreferences

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
        // Instantiate the viewModel
        libraryViewModel =
            ViewModelProvider(this).get(LibraryViewModel::class.java)

        // Inflate the bindings
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Set binding basics
        binding.lifecycleOwner = this
        binding.viewModel = libraryViewModel

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
        // Start loading all media folders
        libraryViewModel.loadFolders()

        // Handle the permission rationale
        binding.permissionRationaleView.isGone = true

        // Create a LibraryAdapter and add the data to it
        val adapter = LibraryAdapter()

        // Register on item click
        adapter.onFolderClick = { item ->
            val directions = LibraryFragmentDirections.actionNavigationLibraryToFolderFragment(item)
            findNavController().navigate(directions)
        }

        libraryViewModel.data.observe(
            viewLifecycleOwner,
            {
                it?.let {
                    adapter.submitList(it)
                }
            }
        )

        // Create a grid layout manager
        // TODO: allow for updates of the layout + allow for more flexibility in size
        val manager = GridLayoutManager(activity, 2, GridLayoutManager.VERTICAL, false)

        // Bind this data to the view to populate the recyclerView
        binding.libraryGrid.let {
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
                libraryViewModel.updateRationale(true)
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
