package com.leondeklerk.starling.media

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.transition.Transition
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.FragmentPagerBinding
import com.leondeklerk.starling.extensions.dpToPx

class PagerFragment : DialogFragment() {

    private val args: PagerFragmentArgs by navArgs()
    private lateinit var binding: FragmentPagerBinding

    private val mediaViewModel: MediaViewModel by activityViewModels()
    private val viewModel: PagerViewModel by activityViewModels()

    data class TransitionListeners(
        val startListener: (transition: Transition) -> Unit,
        val endListener: (transition: Transition) -> Unit
    )

    private val enterListeners = TransitionListeners(::onEnterStart) { onEnterEnd() }
    private val exitListeners = TransitionListeners(::onExitStart) { onExitEnd() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.initial = true

        mediaViewModel.onRect = {
            viewModel.rect = it
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.resetOverlay()
    }

    /** The system calls this to get the DialogFragment's layout, regardless
     of whether it's being displayed as a dialog or an embedded fragment. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentPagerBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel.rect = args.globalRect!!

        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }

        viewModel.setOverlay(View.VISIBLE)

        setupInsets()

        registerObservers()

        setupBindings()

        setupViewPager()

        return binding.root
    }

    /** The system calls this only when creating the layout in a dialog. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // The only reason you might override this method when using onCreateView() is
        // to modify any dialog characteristics. For example, the dialog includes a
        // title by default, but your custom layout might not need it. So here you can
        // remove the dialog title, but you must call the superclass to get the Dialog.
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun getTheme(): Int {
        return R.style.DialogTheme
    }

    private fun setupViewPager() {
        val viewPager = binding.viewPager

        viewPager.isUserInputEnabled = false

//        viewPager.offscreenPageLimit = 1 TODO

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

        viewPager.adapter = MediaFragmentAdapter(this, enterListeners, exitListeners)

        getAdapter().submitList(mediaViewModel.gallery.value!!)
        viewPager.setCurrentItem(mediaViewModel.position, false)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
// //                mediaViewModel.setPositionPager(position)
// //                mediaViewModel.scroll.postValue(true)
                viewModel.releaseTouch()
            }
        })
    }

    /**
     * Retrieve the current active fragment from the view pager.
     * @return the fragment if found
     */
    private fun activeFragment(): MediaItemFragment? {
        return binding.viewPager.let {
            childFragmentManager.findFragmentByTag("f${getAdapter().getItem(it.currentItem).id}") as? MediaItemFragment
        }
    }

    /**
     * Pager enter transition start callback.
     * Starts the background animation.
     * @param transition the enter transition.
     */
    private fun onEnterStart(transition: Transition) {
        binding.background.animate().alpha(1f).setDuration(transition.duration).start()
    }

    /**
     * Pager enter end transition callback
     * Enables the overlay and enables all view in the pager.
     */
    private fun onEnterEnd() {
        binding.viewPager.isUserInputEnabled = true
        binding.dismissContainer.ready = true
        setOverlayEnable(true)
        viewModel.setOverlay(View.VISIBLE, true)
    }

    /**
     * Pager exit transition start callback.
     * Hides the background and disables the overlay
     * @param transition the exit transition that started.
     */
    private fun onExitStart(transition: Transition) {
        binding.dismissContainer.ready = false
        binding.background.animate().alpha(0f).setDuration(transition.duration).start()
        setOverlayEnable(false)
    }

    /**
     * Pager exit transition end callback.
     * Returns to the activity
     */
    private fun onExitEnd() {
        // TODO fix stuff with the video being too large
        requireActivity().onBackPressed()
    }

    /**
     * Gets the adapter of the pager and casts it to the correct class.
     * @return the pager [MediaFragmentAdapter]
     */
    private fun getAdapter(): MediaFragmentAdapter {
        return binding.viewPager.adapter as MediaFragmentAdapter
    }

    /**
     * Set up all listeners on the binding.
     */
    private fun setupBindings() {
        binding.bottomActionItems.mediaActionCrop.setOnClickListener {
            viewModel.setAction(
                getAdapter().getItemId(binding.viewPager.currentItem),
                MediaActionTypes.EDIT
            )
        }

        binding.bottomActionItems.mediaActionDelete.setOnClickListener {
            viewModel.setAction(
                getAdapter().getItemId(binding.viewPager.currentItem),
                MediaActionTypes.DELETE
            )
        }

        binding.bottomActionItems.mediaActionShare.setOnClickListener {
            viewModel.setAction(
                getAdapter().getItemId(binding.viewPager.currentItem),
                MediaActionTypes.SHARE
            )
        }

        setupDismissBindings()
    }

    /**
     * Set up all the bindings for the dismiss container.
     */
    private fun setupDismissBindings() {
        binding.dismissContainer.apply {
            onDismissStart = {
                binding.viewPager.isUserInputEnabled = false
                viewModel.onDismissStart()
            }

            onEnableScroll = { enabled ->
                binding.viewPager.isUserInputEnabled = enabled
            }

            onReset = {
                activeFragment()?.reset()
            }

            onScale = { scaleX, scaleY ->
                activeFragment()?.scale(scaleX, scaleY)
            }

            onTranslate = { dX, dY ->
                activeFragment()?.translate(dX, dY)
            }

            onDismissState = {
                binding.background.alpha = 1 - it
            }

            onContainerSizeListener = { w, h ->
                viewModel.containerSize = Pair(w, h)
            }

            onDismissEnd = { completed ->
                if (completed) {
                    activeFragment()?.close(viewModel.rect, 250L)
                } else {
                    viewModel.onDismissCancel()
                    binding.background.animate().alpha(1f).setDuration(100L).start()
                }
            }
        }
    }

    /**
     * Enable or disable the buttons of the overlay.
     * @param enabled the new state of the buttons
     */
    private fun setOverlayEnable(enabled: Boolean) {
        binding.bottomActionItems.apply {
            mediaActionCrop.isEnabled = enabled
            mediaActionDelete.isEnabled = enabled
            mediaActionShare.isEnabled = enabled
        }
    }

    /**
     * Register the observers for all the different view model data that needs reactive actions.
     */
    private fun registerObservers() {
        viewModel.touchCaptured.observe(this) {
            binding.dismissContainer.touchCaptured = it
            binding.viewPager.isUserInputEnabled = !it
        }

        viewModel.insetState.observe(this) {
            viewModel.setOverlay(it)
        }

        mediaViewModel.gallery.observe(viewLifecycleOwner) {
            getAdapter().submitList(it)
        }

        viewModel.goToId.observe(viewLifecycleOwner) { onGoToId(it) }

        viewModel.enableEdit.observe(viewLifecycleOwner) { onEnableEdit(it) }

        viewModel.showOverlay.observe(viewLifecycleOwner) { onShowOverlay(it) }
    }

    /**
     * Navigate to a specific item within the viewpager based on the ID.
     * Does not use smooth scrolling as this can skip a large number of items.
     * @param id the id of the item to navigate to, if null no navigation will happen
     */
    private fun onGoToId(id: Number?) {
        id?.let {
            val pos = getAdapter().getItemIndex(it.toLong())
            binding.viewPager.setCurrentItem(pos, false)
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
                showOverlay(viewModel.onlyOverlay)
            } else {
                hideOverlay(viewModel.onlyOverlay)
            }
        }
    }

    /**
     * Show both the overlays with an animation: toolbar and bottom bar.
     * @param onlyOverlay show only the overlay or also the insets
     */
    private fun showOverlay(onlyOverlay: Boolean = false) {
        if (!onlyOverlay) {
            showInsets()
        }

        binding.toolbar.visibility = View.VISIBLE
        binding.toolbar.animate().alpha(1f).setDuration(50)

        binding.bottomActionBar.visibility = View.VISIBLE
        binding.bottomActionBar.animate().alpha(1f).setDuration(50)
    }

    /**
     * Hide both the toolbar and the bottom bar with an animation.
     * After the alpha is set to 0, their visibility is set to View.GONE.
     * After the toolbar is hidden the insets are hidden
     * @param onlyOverlay hide only the overlay or also the insets
     */
    private fun hideOverlay(onlyOverlay: Boolean = false) {
        binding.toolbar.animate().alpha(0f).setDuration(TOOLBAR_ANIMATION_DURATION).withEndAction {
            binding.toolbar.visibility = View.GONE
            if (!onlyOverlay) {
                hideInsets()
            }
        }
        binding.bottomActionBar.animate().alpha(0f).setDuration(BOTTOM_BAR_ANIMATION_DURATION).withEndAction {
            binding.bottomActionBar.visibility = View.GONE
        }
    }

    /**
     * Hides the overlay with a callback to also hide all the insets.
     * Sets the behavior to [WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE]
     */
    private fun hideInsets() {
        dialog?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            }
        }
    }

    /**
     * Set up the inset listener with the callback to show the rest of the overlay (toolbar/bottom bar).
     * Calls the window to show the insets and sets the behavior to [WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE]
     */
    private fun showInsets() {
        dialog?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
                controller.show(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    /**
     * Handle the insets and propagate the margin.
     * This is required for having a semi transparent toolbar and system ui,
     * without the toolbar sliding under the status bar.
     */
    private fun setupInsets() {
        dialog?.window?.let { window ->

            // Make the views go under the status and navigation bars
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Set the insets to the height of the status bar, so the toolbar is below the status bar
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                if (insets.isVisible(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())) {
                    viewModel.setInsetState(View.VISIBLE)
                } else {
                    viewModel.setInsetState(View.GONE)
                }
                insets
            }

            viewModel.setInsetState(View.VISIBLE)
        }
    }

    companion object {
        private const val TOOLBAR_ANIMATION_DURATION = 150L
        private const val BOTTOM_BAR_ANIMATION_DURATION = 140L
    }
}
