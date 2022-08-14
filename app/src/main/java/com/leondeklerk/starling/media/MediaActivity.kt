package com.leondeklerk.starling.media

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.ActivityMediaBinding
import com.leondeklerk.starling.extensions.dpToPx
import com.leondeklerk.starling.extensions.setMarginTop
import com.leondeklerk.starling.media.data.ImageItem
import com.leondeklerk.starling.media.data.MediaItem
import com.leondeklerk.starling.media.data.MediaItemTypes
import com.leondeklerk.starling.media.data.VideoItem

class MediaActivity : FragmentActivity() {
    private lateinit var binding: ActivityMediaBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var deleteLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var updateLauncher: ActivityResultLauncher<IntentSenderRequest>

    private val viewModel: MediaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMediaBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setupToolbar()

        showInsets()

        setupViewPager()

        registerObservers()

        setupBindings()

        registerLaunchers()

        viewModel.setInitial(MediaActivityArgs.fromBundle(intent.extras!!).mediaItem?.id)
        viewModel.loadMedia()
    }

    override fun onNavigateUp(): Boolean {
        // navigate back to the previous activity
        onBackPressed()
        return true
    }

    /**
     * Bind the toolbar view to the activity and configure it (back button, menu items)
     */
    private fun setupToolbar() {
        setActionBar(binding.toolbar)

        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowHomeEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(false)
    }

    /**
     * Set up the inset listener with the callback to show the rest of the overlay (toolbar/bottom bar).
     * Calls the window to show the insets and sets the behavior to [WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE]
     */
    private fun showInsets() {
        setupInsets {
            showOverlay()
        }

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            controller.show(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())
        }
    }

    /**
     * Hides the overlay with a callback to also hide all the insets.
     * Sets the behavior to [WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE]
     */
    private fun hideInsets() {
        hideOverlay {
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            }
        }
    }

    /**
     * Handle the insets and inset changes for the toolbar.
     * This is required for having a semi transparent toolbar and system ui,
     * without the toolbar sliding under the status bar.
     */
    private fun setupInsets(callback: (() -> Unit)?) {
        // Make the views go under the status and navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set the insets to the height of the status bar, so the toolbar is below the status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())) {
                viewModel.setInsets(View.VISIBLE, false)
                callback?.let {
                    it.invoke()
                    binding.toolbar.setMarginTop(insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
                }
            } else {
                viewModel.setInsets(View.GONE, false)
            }
            insets
        }
    }

    /**
     * Show both the overlays with an animation: toolbar and bottom bar.
     */
    private fun showOverlay() {
        binding.toolbar.visibility = View.VISIBLE
        binding.toolbar.animate().alpha(1f)

        binding.bottomActionBar.visibility = View.VISIBLE
        binding.bottomActionBar.animate().alpha(1f)
    }

    /**
     * Hide both the toolbar and the bottom bar with an animation.
     * After the alpha is set to 0, their visibility is set to View.GONE.
     * After the toolbar is hidden the callback is invoked if given.
     * @param callback callback that is executed after the toolbar is hidden if not null
     */
    private fun hideOverlay(callback: (() -> Unit)?) {
        binding.toolbar.animate().alpha(0f).setDuration(TOOLBAR_ANIMATION_DURATION).withEndAction {
            binding.toolbar.visibility = View.GONE
            callback?.invoke()
        }
        binding.bottomActionBar.animate().alpha(0f).setDuration(BOTTOM_BAR_ANIMATION_DURATION).withEndAction {
            binding.bottomActionBar.visibility = View.GONE
        }
    }

    /**
     * Initialize the viewPager variable, create the page transformer and adapter,
     * and register these to the pager.
     * Uses a PageTransformer with no translation and a MarginTransFormer with 32DP margins.
     * Uses a [MediaFragmentAdapter] as the adapter.
     */
    private fun setupViewPager() {
        viewPager = binding.viewPager

        val cancelTranslationsTransformer = ViewPager2.PageTransformer { page, _ ->
            page.translationX = 0f
            page.translationY = 0f
        }

        viewPager.setPageTransformer(
            CompositePageTransformer().also {
                it.addTransformer(cancelTranslationsTransformer)
                it.addTransformer(MarginPageTransformer(dpToPx(32f).toInt()))
            }
        )

        viewPager.adapter = MediaFragmentAdapter(this)
    }

    /**
     * Create and register the activity result launchers for the delete and update permission.
     */
    private fun registerLaunchers() {
        // Create an ActivityResult handler for the permission popups on android Q and up.
        deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val fragment = getFragment()
            if (it.resultCode == Activity.RESULT_OK) {
                fragment?.grantRequest(MediaActionTypes.DELETE)
            } else {
                fragment?.grantRequest(MediaActionTypes.CANCEL)
            }
        }

        updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val fragment = getFragment()
            if (it.resultCode == Activity.RESULT_OK) {
                fragment?.grantRequest(MediaActionTypes.EDIT)
            } else {
                fragment?.grantRequest(MediaActionTypes.CANCEL)
            }
        }
    }

    /**
     * Set up all listeners on the binding.
     */
    private fun setupBindings() {
        binding.bottomActionItems.mediaActionCrop.setOnClickListener { getFragment()?.edit() }

        binding.bottomActionItems.mediaActionDelete.setOnClickListener { getFragment()?.delete() }

        binding.bottomActionItems.mediaActionShare.setOnClickListener { shareMedia(getAdapter().getItem(viewPager.currentItem)) }
    }

    /**
     * Share the media item to other applications.
     * Creates an Intent that other apps can receive, containing the Uri of the media item.
     */
    private fun shareMedia(shareItem: MediaItem?) {
        shareItem ?: return

        val mimeType = when (shareItem.type) {
            MediaItemTypes.VIDEO -> (shareItem as VideoItem).mimeType
            MediaItemTypes.IMAGE -> (shareItem as ImageItem).mimeType
            else -> null
        } ?: return

        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, shareItem.uri)
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val fragment = getFragment()

            try {
                startActivity(Intent.createChooser(this, getString(R.string.media_share)))
                fragment?.grantRequest(MediaActionTypes.SHARE)
            } catch (e: Exception) {
                Toast.makeText(applicationContext, R.string.media_share_error, Toast.LENGTH_SHORT).show()
                fragment?.grantRequest(MediaActionTypes.CANCEL)
            }
        }
    }

    /**
     * Register the observers for all the different view model data that needs reactive actions.
     */
    private fun registerObservers() {
        viewModel.data.observe(this) { getAdapter().submitList(it) }

        viewModel.lockView.observe(this) { viewPager.isUserInputEnabled = !it }

        viewModel.requestType.observe(this) { onRequestType(it) }

        viewModel.goToId.observe(this) { onGoToId(it) }

        viewModel.showInsets.observe(this) { onShowInsets(it) }

        viewModel.enableEdit.observe(this) { onEnableEdit(it) }

        viewModel.showOverlay.observe(this) { onShowOverlay(it) }
    }

    /**
     * Upon receiving a specific type of pending request, call the correct activity handler.
     * Mainly takes care of permission via activity on  result.
     * @param type the type of request
     */
    private fun onRequestType(type: MediaActionTypes?) {
        type?.let {
            when (it) {
                MediaActionTypes.DELETE -> {
                    viewModel.request?.let { request ->
                        deleteLauncher.launch(IntentSenderRequest.Builder(request).build())
                    }
                }
                MediaActionTypes.EDIT -> {
                    viewModel.request?.let { request ->
                        updateLauncher.launch(IntentSenderRequest.Builder(request).build())
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * Navigate to a specific item within the viewpager based on the ID.
     * Does not use smooth scrolling as this can skip a large number of items.
     * @param id the id of the item to navigate to, if null no navigation will happen
     */
    private fun onGoToId(id: Number?) {
        id?.let {
            val pos = getAdapter().getItemIndex(it.toLong())
            viewPager.setCurrentItem(pos, false)
        }
    }

    /**
     * Either hides the insets (View.GONE) or shows them (View.VISIBLE).
     * Sometimes the state needs to be synced (insets are shown via a swipe instead of within the application),
     * therefore on updates this is only executed when [MediaViewModel.trigger] is true.
     * @param visibility the inset state: View.VISIBLE or View.GONE
     */
    private fun onShowInsets(visibility: Int) {
        if (viewModel.trigger) {
            if (visibility == View.VISIBLE) {
                showInsets()
            } else if (visibility == View.GONE) {
                hideInsets()
            }
        }
    }

    /**
     * Enables or disables the edit button in the bottom action bar.
     * Sets the visibility of the button view.
     * @param enabled indicates if the button should be enabled (true) or not (false)
     */
    private fun onEnableEdit(enabled: Boolean) {
        if (enabled) {
            binding.bottomActionItems.mediaActionCrop.visibility = View.VISIBLE
        } else {
            binding.bottomActionItems.mediaActionCrop.visibility = View.GONE
        }
    }

    /**
     * Either shows or hides the overlay itself (toolbar and bottom action bar),
     * without updating the state of the insets.
     * @param show show the overlay (true) or hide it (false), if null there is no change
     */
    private fun onShowOverlay(show: Boolean?) {
        show?.let {
            if (it) {
                showOverlay()
            } else {
                hideOverlay(null)
            }
        }
    }

    /**
     * Helper fragment to get the current [MediaItemFragment] based on the position of the viewpager.
     * @return the current [MediaFragmentAdapter] or null if it could not be found.
     */
    private fun getFragment(): MediaItemFragment? {
        val id = getAdapter().getItemId(viewPager.currentItem)
        val fragment = supportFragmentManager.findFragmentByTag("f$id")

        return if (fragment != null) {
            fragment as MediaItemFragment
        } else {
            null
        }
    }

    /**
     * Gets the adapter of the pager and casts it to the correct class.
     * @return the pager [MediaFragmentAdapter]
     */
    private fun getAdapter(): MediaFragmentAdapter {
        return viewPager.adapter as MediaFragmentAdapter
    }

    companion object {
        private const val TOOLBAR_ANIMATION_DURATION = 150L
        private const val BOTTOM_BAR_ANIMATION_DURATION = 140L
    }
}
