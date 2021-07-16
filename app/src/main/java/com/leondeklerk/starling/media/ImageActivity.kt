package com.leondeklerk.starling.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.databinding.ActivityImageBinding

/**
 * Activity responsible for showing a [ImageItem].
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

        val imageItem: ImageItem = ImageActivityArgs.fromBundle(intent.extras!!).imageItem

        // Inflate the binding
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register the toolbar
        setSupportActionBar(binding.toolbar)

        // Configure the toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupInsets()

        binding.lifecycleOwner = this

        val imageView = binding.imageView

        // On clicking the image the system ui and the toolbar should disappear for a fullscreen experience.
        imageView.setOnClickListener {
            supportActionBar?.hide()

            // Set the system ui visibility.
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
            }
        }

        // Load image with Coil into the imageView
        Glide.with(imageView.context)
            .load(imageItem.uri)
            .into(imageView)
    }

    override fun onSupportNavigateUp(): Boolean {
        // navigate back to the previous activity
        onBackPressed()
        return true
    }

    /**
     * Handle the insets and inset changes for the toolbar.
     * This is required for having a semi transparent toolbar and system ui,
     * without the toolbar sliding under the status bar.
     */
    private fun setupInsets() {
        // Make the views go under the status and navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set the insets to the height of the status bar, so the toolbar is below the status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { _, insets ->
            // If the status bar and navigation bar reappear, so should the toolbar
            if (insets.isVisible(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())) {
                supportActionBar?.show()
            }

            binding.toolbar.setMarginTop(insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
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
