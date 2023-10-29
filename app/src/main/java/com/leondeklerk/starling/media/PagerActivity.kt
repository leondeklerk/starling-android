package com.leondeklerk.starling.media

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.Fade
import android.transition.TransitionSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.transition.addListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.transition.Transition
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.leondeklerk.starling.databinding.ActivityPagerBinding
import com.leondeklerk.starling.extensions.dpToPx

class PagerActivity : AppCompatActivity() {
    private var isReturning: Boolean = false
    private var startingPosition: Int = 0
    private var currentPosition: Int = 0
    private var dismissibleView: View? = null

    private val enterElementCallback: androidx.core.app.SharedElementCallback = object : androidx.core.app.SharedElementCallback() {
        override fun onMapSharedElements(
            names: MutableList<String>,
            sharedElements: MutableMap<String, View>
        ) {
            if (isReturning) {
                val sharedElement = activeFragment()?.sharedView() ?: return

//                if (startingPosition != currentPosition) {
                names.clear()
                names.add(ViewCompat.getTransitionName(sharedElement)!!)

                sharedElements.clear()
                sharedElements[ViewCompat.getTransitionName(sharedElement)!!] = sharedElement
//                }
            }
        }
    }

    private lateinit var binding: ActivityPagerBinding

    private val mediaViewModel: MediaViewModel by viewModels()
    private val viewModel: PagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaViewModel.gallery.observe(this) {
            if (it != null) {
                setupViewPager()
            }
        }

        mediaViewModel.loadMedia()

        setBarsEnable(false)

        val set = TransitionSet()
//            .addTransition(ChangeClipBounds())
//            .addTransition(ChangeTransform())
            .addTransition(ChangeBounds())
            .addTransition(ChangeImageTransform())
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setOrdering(TransitionSet.ORDERING_TOGETHER)
            .setDuration(200)

        set.addListener(onStart = { onEnterStart(it) }, onEnd = { onEnterEnd() })

        val genericTransition =
            Fade().addTarget(binding.appBar).addTarget(binding.bottomActionBar).addTarget(binding.background).setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())

        window.enterTransition = genericTransition
        window.exitTransition = genericTransition
        window.sharedElementEnterTransition = set
        window.sharedElementExitTransition = set
        window.sharedElementsUseOverlay = false

        ActivityCompat.postponeEnterTransition(this)
        ActivityCompat.setEnterSharedElementCallback(this, enterElementCallback)

        binding.toolbar.setNavigationOnClickListener { isReturning = true; finishAfterTransition() }

        setupInsets()

        registerObservers()

        setupBindings()
    }

    override fun onBackPressed() {
        isReturning = true
        super.onBackPressed()
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

        viewPager.adapter = MediaFragmentAdapter(this) // , enterListeners, exitListeners)

//        getAdapter().submitList(mediaViewModel.gallery.value!!)
        val index = intent.getIntExtra(ITEM_ID, 0)
//        val item: MediaItem = intent.getParcelableExtra(ITEM)!!
//        val list = MutableList(11) { item }
        getAdapter().submitList(mediaViewModel.gallery.value!!)
        viewPager.setCurrentItem(index, false)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dismissibleView = activeFragment()?.sharedView()
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
            supportFragmentManager.findFragmentByTag("f${getAdapter().getItem(it.currentItem).id}") as? MediaItemFragment
        }
    }

    /**
     * Pager enter transition start callback.
     * Starts the background animation.
     * @param transition the enter transition.
     */
    private fun onEnterStart(transition: android.transition.Transition) {
    }

    /**
     * Pager enter end transition callback
     * Enables the overlay and enables all view in the pager.
     */
    private fun onEnterEnd() {
        binding.viewPager.isUserInputEnabled = true
        binding.dismissContainer.ready = true
        setBarsEnable(true)
    }

    /**
     * Pager exit transition start callback.
     * Hides the background and disables the overlay
     * @param transition the exit transition that started.
     */
    private fun onExitStart(transition: Transition) {
//        binding.dismissContainer.ready = false
    }

    /**
     * Pager exit transition end callback.
     * Returns to the activity
     */
    private fun onExitEnd() {
        // TODO fix stuff with the video being too large
        onBackPressed()
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

    fun reset() {
        activeFragment()?.sharedView()?.animate()?.scaleX(1f)?.scaleY(1f)?.translationX(0f)?.translationY(0f)?.setDuration(100L)?.start()
    }

    fun scale(scalarX: Float, scalarY: Float) {
        activeFragment()?.sharedView()?.scaleX = scalarX
        activeFragment()?.sharedView()?.scaleY = scalarY
    }

    fun translate(dX: Float, dY: Float) {
        activeFragment()?.sharedView()?.translationX = activeFragment()?.sharedView()?.translationX?.plus(dX)!!
        activeFragment()?.sharedView()?.translationY = activeFragment()?.sharedView()?.translationY?.plus(dY)!!
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

            onDismissState = {
                binding.background.alpha = 1 - it
                binding.toolbar.alpha = 1 - it
                binding.bottomActionBar.alpha = 1 - it
            }

            onScale = ::scale
            onTranslate = ::translate
            onReset = ::reset

            onDismissEnd = { completed ->
                if (completed) {
                    isReturning = true
                    finishAfterTransition()
                } else {
                    binding.viewPager.isUserInputEnabled = true
                    viewModel.onDismissCancel()
                    binding.background.animate().alpha(1f).setDuration(100L).start()
                    binding.toolbar.animate().alpha(1f).setDuration(100L).start()
                    binding.bottomActionBar.animate().alpha(1f).setDuration(100L).start()
                }
            }
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

        viewModel.overlayInsetState.observe(this) {
            when (it) {
                View.VISIBLE -> showInsets()
                View.GONE -> hideInsets()
            }
        }

        viewModel.overlayBarState.observe(this) {
            when (it) {
                View.VISIBLE -> showBars()
                View.GONE -> hideBars()
            }
        }

        mediaViewModel.gallery.observe(this) {
            getAdapter().submitList(it)
        }
//
//        viewModel.goToId.observe(viewLifecycleOwner) { onGoToId(it) }
//
        viewModel.enableEdit.observe(this) { onEnableEdit(it) }
//
    }

    private fun showBars() {
        binding.toolbar.visibility = View.VISIBLE
        binding.toolbar.animate().alpha(1f)

        binding.bottomActionBar.visibility = View.VISIBLE
        binding.bottomActionBar.animate().alpha(1f).withEndAction {
            setBarsEnable(true)
        }
    }

    private fun hideBars() {
        setBarsEnable(false)

        binding.toolbar.animate().alpha(0f).withEndAction {
            binding.toolbar.visibility = View.GONE
        }

        binding.bottomActionBar.animate().alpha(0f).withEndAction {
            binding.bottomActionBar.visibility = View.GONE
        }
    }

    /**
     * Enable or disable the buttons of the overlay.
     * @param enabled the new state of the buttons
     */
    private fun setBarsEnable(enabled: Boolean) {
        binding.bottomActionItems.apply {
            mediaActionCrop.isEnabled = enabled
            mediaActionDelete.isEnabled = enabled
            mediaActionShare.isEnabled = enabled
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
     * Hides the overlay with a callback to also hide all the insets.
     * Sets the behavior to [WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE]
     */
    private fun hideInsets() {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
        }
    }

    /**
     * Set up the inset listener with the callback to show the rest of the overlay (toolbar/bottom bar).
     * Calls the window to show the insets and sets the behavior to [WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE]
     */
    private fun showInsets() {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            controller.show(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun finishAfterTransition() {
        val data = Intent()
        data.putExtra(POSITION, binding.viewPager.currentItem)
        setResult(Activity.RESULT_OK, data)
        super.finishAfterTransition()
    }

    /**
     * Handle the insets and propagate the margin.
     * This is required for having a semi transparent toolbar and system ui,
     * without the toolbar sliding under the status bar.
     */
    private fun setupInsets() {
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
    }

    companion object {
        private const val TOOLBAR_ANIMATION_DURATION = 150L
        private const val BOTTOM_BAR_ANIMATION_DURATION = 140L
        const val ITEM_ID = "itemId"
        const val ITEM = "item"
        const val POSITION = "item_POSITION"
    }
}
