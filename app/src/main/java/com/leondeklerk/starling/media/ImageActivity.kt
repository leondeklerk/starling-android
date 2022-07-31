package com.leondeklerk.starling.media

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leondeklerk.starling.R
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.databinding.ActivityImageBinding
import com.leondeklerk.starling.media.ImageViewModel.Companion.OPERATION_UPDATE

/**
 * Activity responsible for showing a [ImageItem].
 * This gives the user a fullscreen view of the image,
 * in addition to image details and simple edit options.
 */
class ImageActivity : AppCompatActivity() {

    private lateinit var viewModel: ImageViewModel
    private lateinit var binding: ActivityImageBinding
    private lateinit var imageItem: ImageItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Create the viewModel
        viewModel = ViewModelProvider(this)[ImageViewModel::class.java]

        imageItem = ImageActivityArgs.fromBundle(intent.extras!!).imageItem

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

        // Create an ActivityResult handler for the permission popups on android Q and up.
        val deleteResultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    // We can now delete the pending image
                    viewModel.deletePending()
                } else {
                    // The image is deleted so the fragment can be closed
                    onBackPressed()
                }
            }
        }

        val updateResultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    // We can now delete the pending image
                } else {
                    viewModel.update(imageItem)
                    viewModel.switchMode()
                }
            }
        }

        // On clicking the image the system ui and the toolbar should disappear for a fullscreen experience.
        imageView.onTapListener = {
            binding.toolbar.animate().alpha(0f).setDuration(150).withEndAction {
                // Set the system ui visibility.
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
                }
                supportActionBar?.hide()
            }

            binding.bottomActionBar.animate().alpha(0f).withEndAction { binding.bottomActionBar.visibility = View.INVISIBLE }
        }

        // Observer to handle cases where additional permission are needed to delete an item (Q and up)
        viewModel.requiresPermission.observe(
            this
        ) { intentSender ->
            intentSender?.let {
                deleteResultLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        }

        // Observer to check if the current fragment should be closed or not
        viewModel.shouldClose.observe(
            this
        ) {
            if (it) {
                onBackPressed()
            }
        }

        viewModel.mode.observe(
            this
        ) {
            if (it == ImageViewModel.Mode.VIEW) {
                binding.editView.visibility = View.INVISIBLE
                binding.imageView.visibility = View.VISIBLE

                loadImage(binding.imageView)

                setupInsets()

                // Set the system ui visibility.
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
                    controller.show(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())
                }
            } else {
                imageView.visibility = View.INVISIBLE
                binding.toolbar.animate().alpha(0f).withEndAction {
                    supportActionBar?.hide()
                }

                binding.bottomActionBar.animate().alpha(0f).withEndAction {
                    binding.bottomActionBar.visibility = View.INVISIBLE
                }

                // Set the system ui visibility.
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
                }

                // Override the window insets listener
                ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { _, insets ->
                    insets
                }

                binding.editView.visibility = View.VISIBLE
                loadImage(binding.editView.imageView)
            }
        }

        viewModel.savedItem.observe(this) {
            it?.let {
                imageItem = it
                isSaved(it)
            }
        }

        binding.bottomActionItems.mediaActionDelete.setOnClickListener {
            deleteMedia()
        }

        binding.bottomActionItems.mediaActionShare.setOnClickListener {
            shareMedia()
        }

        binding.bottomActionItems.mediaActionCrop.let { button ->

            button.setOnClickListener {
                viewModel.switchMode()
            }
            button.visibility = View.VISIBLE
        }

        binding.editView.onCancel = {
            viewModel.switchMode()
        }

        binding.editView.onSave = { result, copy ->
            viewModel.tryUpdate(result, imageItem, copy)
        }

        viewModel.pendingIntent.observe(this) {
            it?.let {
                val (code, intent) = it
                when (code) {
                    OPERATION_UPDATE -> updateResultLauncher.launch(IntentSenderRequest.Builder(intent).build())
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        loadImage(binding.imageView)
    }

    override fun onSupportNavigateUp(): Boolean {
        // navigate back to the previous activity
        onBackPressed()
        return true
    }

    private fun isSaved(item: ImageItem) {
        binding.editView.isSaved()
        viewModel.switchMode()
        if (viewModel.pendingSwitch) {
            switchToActivity(item)
        }
    }

    private fun loadImage(imageView: ImageView) {
        // Load image with Glide into the imageView
        Glide.with(imageView.context)
            .asBitmap()
            .signature(ObjectKey(imageItem.dateModified))
            .load(imageItem.uri)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                    imageView.setImageBitmap(bitmap)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
    }

    /**
     * Share the media item to other applications.
     * Creates an Intent that other apps can receive, containing the Uri of the media item.
     */
    private fun shareMedia() {
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, imageItem.uri)
            type = imageItem.mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                startActivity(Intent.createChooser(this, getString(R.string.media_share)))
            } catch (e: Exception) {
                Toast.makeText(applicationContext, R.string.media_share_error, Toast.LENGTH_SHORT).show()
            }
        }
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
                binding.toolbar.animate().alpha(1f)

                binding.bottomActionBar.alpha = 0f
                binding.bottomActionBar.visibility = View.VISIBLE
                binding.bottomActionBar.animate().alpha(1f)
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

    /**
     * Builds a MaterialAlertDialog asking the user to confirm a delete.
     * On ok the viewModel.delete will be called and the delete process starts.
     */
    private fun deleteMedia() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.image_delete_title))
            .setMessage(getString(R.string.image_delete_message))
            .setPositiveButton(getString(R.string.media_delete)) { _: DialogInterface, _: Int ->
                viewModel.delete(imageItem)
            }
            .setNegativeButton(getString(android.R.string.cancel)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Open another activity with the new image.
     * @param item: the new ImageItem containing all image data.
     */
    private fun switchToActivity(item: ImageItem) {
        val intent = Intent(this, ImageActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
            putExtra("imageItem", item)
        }
        startActivity(intent)
        finish()
    }
}
