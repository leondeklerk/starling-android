package com.leondeklerk.starling.gallery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.TransitionSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.SharedElementCallback
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.leondeklerk.starling.MainActivity
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.FragmentGalleryBinding
import com.leondeklerk.starling.extensions.globalVisibleRect
import com.leondeklerk.starling.extensions.goToSettings
import com.leondeklerk.starling.extensions.hasPermission
import com.leondeklerk.starling.media.MediaViewModel
import com.leondeklerk.starling.media.PagerActivity
import com.leondeklerk.starling.media.data.MediaItem
import com.leondeklerk.starling.media.data.MediaItemTypes
import com.leondeklerk.starling.media.gallery.MediaGalleryAdapter

/**
 * [GalleryFragment] is the main fragment of the application.
 * It contains the main recyclerView that contains all images and videos on the device and synced.
 * Uses a [MediaGalleryAdapter] assisted by [GalleryViewModel] to display
 * [com.leondeklerk.starling.media.data.MediaItem]s.
 * This fragment handles the required permission.
 */
class GalleryFragment : Fragment() {

    private val galleryViewModel: GalleryViewModel by viewModels()
    private lateinit var binding: FragmentGalleryBinding
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private val mediaViewModel: MediaViewModel by activityViewModels()
    private var index = 0

    companion object {
        private const val READ_EXTERNAL_STORAGE_PERMISSION_PREF_FLAG =
            "READ_EXTERNAL_STORAGE_PERMISSION_FLAG"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the binding
        binding = FragmentGalleryBinding.inflate(inflater, container, false)

        // Set binding basics
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = galleryViewModel

        // Set up the actual nav controller
        val navController = findNavController()
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.gallery_graph, R.id.library_graph
            )
        )

        val set = TransitionSet()
//            .addTransition(ChangeClipBounds())
//            .addTransition(ChangeTransform())
            .addTransition(ChangeBounds())
            .addTransition(ChangeImageTransform())
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setOrdering(TransitionSet.ORDERING_TOGETHER)
            .setDuration(200)

//        set.addListener(onStart = { onEnterStart(it) }, onEnd = { onEnterEnd() })

//        val genericTransition =
//            Fade().addTarget(binding.appBar).addTarget(binding.bottomActionBar).addTarget(binding.background).setDuration(200)
//                .setInterpolator(AccelerateDecelerateInterpolator())
//
//        window.enterTransition = genericTransition
//        window.exitTransition = genericTransition
        requireActivity().window.sharedElementEnterTransition = set
//        window.sharedElementExitTransition = set
//        window.sharedElementsUseOverlay = false

        ActivityCompat.setExitSharedElementCallback(requireActivity(), exitElementCallback)

        (requireActivity() as MainActivity).onReenter = ::onReenter

//        mediaViewModel.scroll.observe(viewLifecycleOwner) {
//            if (it) {
//                scrollToPosition(mediaViewModel.position)
//                mediaViewModel.scroll.postValue(false)
//            }
//        }

        NavigationUI.setupWithNavController(binding.toolbar, navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_gallery) {
                binding.toolbar.navigationIcon = resources.getDrawable(R.drawable.ic_gallery_black_24dp)
            }
        }

        sharedPrefs = requireActivity().getPreferences(Context.MODE_PRIVATE)

        handleReadStoragePermission()
        return binding.root
    }

    private var reenterState: Bundle? = null

    private val exitElementCallback = object : SharedElementCallback() {
        override fun onMapSharedElements(
            names: MutableList<String>,
            sharedElements: MutableMap<String, View>
        ) {
            if (reenterState != null) {
                val position = reenterState!!.getInt(PagerActivity.POSITION)
//                if (startingPosition != currentPosition) {
                // Current element has changed, need to override previous exit transitions
                val id = mediaViewModel.gallery.value!![position].id
                val newTransitionName = "$id" // GalleryItem.transitionName(DataSource.ITEMS[currentPosition].id)
                val vh = binding.galleryGrid.findViewHolderForAdapterPosition(position)
                val newSharedElement = vh?.itemView?.findViewById<ImageView>(R.id.imageView)

//                    val newSharedElement = binding.galleryGrid.findViewWithTag<View>(newTransitionName)
                if (newSharedElement != null) {
                    names.clear()
                    names.add(newTransitionName)

                    sharedElements.clear()
                    sharedElements.put(newTransitionName, newSharedElement)
                }
//                }
                reenterState = null
            }
        }
    }

    private fun onReenter(resultCode: Int, data: Intent?) {
        reenterState = Bundle(data?.extras)
        reenterState?.let {
            val position = it.getInt(PagerActivity.POSITION)
//            val currentPosition = it.getInt(EXTRA_CURRENT_ALBUM_POSITION)
            binding.galleryGrid.scrollToPosition(position)
//            if (startingPosition != currentPosition) imagesRv.scrollToPosition(currentPosition)
            ActivityCompat.postponeEnterTransition(requireActivity())

            binding.galleryGrid.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    binding.galleryGrid.viewTreeObserver.removeOnPreDrawListener(this)
                    ActivityCompat.startPostponedEnterTransition(requireActivity())
                    return true
                }
            })
        }
    }

    private fun scrollToPosition(pos: Int) {
        val layoutManager: RecyclerView.LayoutManager = binding.galleryGrid.layoutManager!!
        val viewAtPosition = layoutManager.findViewByPosition(pos)

        if (viewAtPosition == null || layoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
            layoutManager.scrollToPosition(pos)
        }

        mediaViewModel.setRect(viewAtPosition?.globalVisibleRect ?: Rect())
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
     * Responsible for requesting permission and handling the result.
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
        mediaViewModel.loadMedia()

        // Handle the permission rationale
        binding.permissionRationaleView.isGone = true

        // Create item click listener
        val mediaItemClickListener = { item: MediaItem, view: View, index: Int ->
            this.index = index
//            mediaViewModel.setActive(false)
            mediaViewModel.setPositionGallery(index)

            val intent = Intent(context, PagerActivity::class.java)
            intent.putExtra(PagerActivity.ITEM_ID, index)
            intent.putExtra(PagerActivity.ITEM, item)

//            val statusBar: View = requireActivity().findViewById(android.R.id.statusBarBackground)
//            val navigationBar: View = requireActivity().findViewById(android.R.id.navigationBarBackground)
//
            val p1 = androidx.core.util.Pair.create(view, ViewCompat.getTransitionName(view))
//            val p2 = androidx.core.util.Pair.create(statusBar, Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME)
//            val p3 = androidx.core.util.Pair.create(navigationBar, Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME)

            val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), p1).toBundle()

//            requireActivity().window.exitTransition = null
//            exitTransition = null
//            sharedElementEnterTransition = null
//            sharedElementReturnTransition = null
//            requireActivity().window.sharedElementExitTransition = null
            // Open detail activity with shared element transition
            startActivity(intent, bundle)

//            mediaViewModel.setRect(view.globalVisibleRect)

//            val directions = GalleryFragmentDirections.actionNavigationGalleryToPagerFragment(
//                item,
//                view.globalVisibleRect,
//            )

//            findNavController().navigate(directions)
        }

        // Create a GalleryAdapter and add the data to it
        val adapter = MediaGalleryAdapter(mediaItemClickListener)

        mediaViewModel.gallery.observe(viewLifecycleOwner) {
            it?.let {
                adapter.submitList(it)
            }
        }

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
