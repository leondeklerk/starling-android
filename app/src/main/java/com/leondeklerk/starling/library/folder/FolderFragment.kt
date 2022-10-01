package com.leondeklerk.starling.library.folder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.leondeklerk.starling.databinding.FragmentFolderBinding
import com.leondeklerk.starling.media.MediaViewModel
import com.leondeklerk.starling.media.data.FolderItem
import com.leondeklerk.starling.media.data.MediaItem
import com.leondeklerk.starling.media.data.MediaItemTypes
import com.leondeklerk.starling.media.gallery.MediaGalleryAdapter

/**
 * [FolderFragment] is a gallery containing all items in a device folder.
 * It contains a recyclerView that contains all the images and videos.
 * Uses a [MediaGalleryAdapter] assisted by [FolderViewModel] to display
 * [com.leondeklerk.starling.media.data.MediaItem]s.
 */
class FolderFragment : Fragment() {

    private lateinit var folderViewModel: FolderViewModel
    private var _binding: FragmentFolderBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val mediaViewModel: MediaViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create the viewModel
        folderViewModel =
            ViewModelProvider(this).get(FolderViewModel::class.java)

        val item: FolderItem = FolderFragmentArgs.fromBundle(requireArguments()).folderItem

        // Inflate the binding
        _binding = FragmentFolderBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Set binding basics
        binding.lifecycleOwner = this

        // Set up the actual nav controller
        val navController = findNavController()
        val appBarConfiguration = AppBarConfiguration(navController.graph)

        binding.toolbar.setupWithNavController(navController, appBarConfiguration)

        setAdapter(item.id)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Makes sure everything is set so the fragment can properly show the recyclerview.
     * Handles the creation and setup of the grid.
     * Also instantiates an observer for the viewModel data.
     */
    private fun setAdapter(bucketId: Long) {
        // Start loading the media from the device
//        folderViewModel.loadMedia(bucketId)

        // Create item click listener
        val mediaItemClickListener = { item: MediaItem, view: View, index: Int ->
//            mediaViewModel.setActive(true)
//            mediaViewModel.setGalleryPosition(item, index)
//            val directions = FolderFragmentDirections.actionNavigationFolderToPagerFragment(item, index)
//            findNavController().navigate(directions)
        }

        // Create a GalleryAdapter and add the data to it
        val adapter = MediaGalleryAdapter(mediaItemClickListener)

        mediaViewModel.folder.observe(viewLifecycleOwner) {
            it?.let {
                adapter.submitList(it)
            }
        }
//        folderViewModel.data.observe(
//            viewLifecycleOwner,
//            {
//                it?.let {
//                    adapter.submitList(it)
//                }
//            }
//        )

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
}
