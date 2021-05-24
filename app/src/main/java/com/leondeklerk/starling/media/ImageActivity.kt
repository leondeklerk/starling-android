package com.leondeklerk.starling.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.databinding.ActivityImageBinding

/**
 * Activity responsbile for showing a [ImageItem].
 * This gives the user a fullscreen view of the image,
 * in addition to image details and simple edit options.
 */
class ImageActivity : AppCompatActivity() {

    private lateinit var viewModel: ImageViewModel
    private lateinit var binding: ActivityImageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Create the viewModel
        viewModel = ViewModelProvider(this).get(ImageViewModel::class.java)

        val imageItem = ImageActivityArgs.fromBundle(intent.extras!!).imageItem as ImageItem

        // Inflate the binding
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register the toolbar
        setSupportActionBar(binding.imageToolbar)

        // Configure the toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupInsets()

        binding.lifecycleOwner = this

        val imageView = binding.imageView

        // Handle system ui changes
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                // If the system ui is visible the toolbar should reappear
                supportActionBar?.show()
            }
        }

        // On clicking the iamge the system ui and the toolbar should disappear for a fullscreen experience.
        imageView.setOnClickListener {
            supportActionBar?.hide()

            // Set the system ui visibility.
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        // Load image with Glide into the imageView
        Glide.with(imageView)
            .load(imageItem.contentUri)
            .thumbnail(0.8f)
            .into(imageView)
    }

    override fun onSupportNavigateUp(): Boolean {
        // navigate back to the previous activity
        onBackPressed()
        return true
    }

    /**
     * Handle the insets for the toolbar.
     * This is required for having a semi transparent toolbar and system ui,
     * without the toolbar sliding under the status bar.
     */
    private fun setupInsets() {
        binding.imageLayout.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        // Set the insets to the height of the status bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.imageToolbar) { _, insets ->
            binding.imageToolbar.setMarginTop(insets.systemWindowInsetTop)
            insets
        }
    }

    /**
     * Helper function to add the margin top property to a view.
     */
    private fun View.setMarginTop(value: Int) = updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = value
    }
}
