package com.leondeklerk.starling.library.folder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.leondeklerk.starling.data.FolderItem
import com.leondeklerk.starling.data.MediaItemTypes
import com.leondeklerk.starling.databinding.FragmentFolderBinding
import com.leondeklerk.starling.gallery.GalleryAdapter

/**
 * [FolderFragment] is a gallery containing all items in a device folder.
 * It contains a recyclerView that contains all the images and videos.
 * Uses a [GalleryAdapter] assisted by [FolderViewModel] to display
 * [com.leondeklerk.starling.data.MediaItem]s.
 */
class FolderFragment : Fragment() {

    private lateinit var folderViewModel: FolderViewModel
    private var _binding: FragmentFolderBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

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

        setAdapter(item.id)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Makes sure everything is set so the fragment can properly show the recyclerview.
     * Handles the removal of a (potential) permission rationale as well as creating the layout and adapter.
     * Also instantiates an observer for the viewModel data.
     */
    private fun setAdapter(bucketId: Long) {
        // Start loading the media from the device
        folderViewModel.loadMedia(bucketId)

        // Create a GalleryAdapter and add the data to it
        val adapter = GalleryAdapter()

        // Register on item click
        adapter.onImageClick = { item ->
            val directions = FolderFragmentDirections.actionFolderFragmentToImageActivity(item)
            findNavController().navigate(directions)
        }

        adapter.onVideoClick = { item ->
            val directions = FolderFragmentDirections.actionFolderFragmentToVideoActivity(item)
            findNavController().navigate(directions)
        }

        folderViewModel.data.observe(
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
}
