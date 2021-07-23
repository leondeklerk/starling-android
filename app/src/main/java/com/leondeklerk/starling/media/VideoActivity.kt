package com.leondeklerk.starling.media

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.TimeBar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leondeklerk.starling.R
import com.leondeklerk.starling.data.VideoItem
import com.leondeklerk.starling.databinding.ActivityVideoBinding

/**
 * Activity responsible for showing a [VideoItem].
 * This gives the user a fullscreen view of the video,
 * in addition to video details and simple edit options (TODO).
 */
class VideoActivity : AppCompatActivity() {

    private lateinit var viewModel: VideoViewModel
    private lateinit var binding: ActivityVideoBinding
    private lateinit var player: SimpleExoPlayer
    private lateinit var videoItem: VideoItem
    private var playerVolume: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the viewModel
        viewModel = ViewModelProvider(this).get(VideoViewModel::class.java)

        // Get the data model for this screen
        videoItem = VideoActivityArgs.fromBundle(intent.extras!!).videoItem

        // Inflate the binding
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register the toolbar
        setSupportActionBar(binding.toolbar)

        // Configure the toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        setupInsets()

        // Set up the binding
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.item = videoItem

        // Set up all listeners, handler and objects
        setupVideoPlayer()
        setupUiListeners()
        setupDeleteHandlers()
        setupUiHandlers()

        // Set initial state
        hideUiElements()
        viewModel.setPausedState()
    }

    override fun onPause() {
        super.onPause()
        // Pause the video
        if (player.isPlaying) {
            player.pause()
            viewModel.setPausedState()
        }
    }

    override fun onResume() {
        super.onResume()
        player.playWhenReady = true
        viewModel.setPausedState()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.stop()
        player.release()
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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set the insets to the height of the status bar, so the toolbar is below the status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { _, insets ->
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
     * Sets up all the listeners that handle system changes and user interaction.
     */
    private fun setupUiListeners() {
        // Listener for System UI changes
        // Using a setOnApplyWindowInsetsListener does not work properly, therefore deprecated methods are used.
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                showUiElements()
            }
        }

        // Click listener for the screen layout
        binding.layout.setOnClickListener {
            hideUiElements()
        }

        // Click listener for the video view
        binding.videoView.setOnClickListener {
            hideUiElements()
        }

        // Click listener for the delete button
        binding.bottomActionItems.mediaActionDelete.setOnClickListener {
            deleteMedia()
        }

        // Click listener for the pause/play button
        binding.videoPause.setOnClickListener {
            viewModel.setPausedState()
        }

        // Click listener for the share button
        binding.bottomActionItems.mediaActionShare.setOnClickListener {
            shareMedia()
        }

        // Check listener for the sound button
        findViewById<MaterialCheckBox>(R.id.video_sound).setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSound(isChecked)
        }

        // Progress bar listeners
        findViewById<DefaultTimeBar>(R.id.exo_progress).addListener(object : TimeBar.OnScrubListener {
            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                // Only seek in steps of 0.25 seconds
                val currentPosition = player.currentPosition / 250
                if (position / 250 != currentPosition) {
                    player.seekTo((position / 250) * 250)
                }
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                // If before on pause, stay on pause
                // If was playing, start again
                if (viewModel.beforeScrubState) {
                    player.play()
                    binding.videoPause.icon =
                        AppCompatResources.getDrawable(applicationContext, R.drawable.ic_outline_pause_24)
                }
            }

            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                // If on pause, stay paused
                // If playing, pause
                player.pause()
                viewModel.setPausedScrubState()
                binding.videoPause.icon =
                    AppCompatResources.getDrawable(applicationContext, R.drawable.ic_outline_play_arrow_24)
            }
        })
    }

    /**
     * Makes the UI elements visible again, after the system ui reappeared.
     * Relates to: the toolbar, play/pause button, bottom bar controls
     */
    private fun showUiElements() {
        supportActionBar?.show()
        binding.videoPause.animate().alpha(0.67f)
        binding.videoView.useController = true
        binding.videoView.showController()
        binding.bottomActionBar.animate().alpha(1f)
    }

    /**
     * Hides the screen and system UI elements.
     * Screen elements: Toolbar, play/pause button, bottom action bar
     * UI Elements: status bar, navigation bar
     */
    private fun hideUiElements() {
        supportActionBar?.hide()
        binding.videoPause.animate().alpha(0f)
        binding.videoView.useController = false

        binding.bottomActionBar.animate().alpha(0f)

        // Hide the navigation and status bar on click
        WindowInsetsControllerCompat(window, window.decorView).hide(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        )
    }

    /**
     * Builds a MaterialAlertDialog asking the user to confirm a delete.
     * On ok the viewModel.delete will be called and the delete process starts.
     */
    private fun deleteMedia() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.video_delete_title))
            .setMessage(getString(R.string.video_delete_message))
            .setPositiveButton(getString(R.string.media_delete)) { _: DialogInterface, _: Int ->
                viewModel.delete(videoItem)
            }
            .setNegativeButton(getString(android.R.string.cancel)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Share the media item to other applications.
     * Creates an Intent that other apps can receive, containing the Uri of the media item.
     */
    private fun shareMedia() {
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, videoItem.uri)
            type = videoItem.mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                startActivity(Intent.createChooser(this, getString(R.string.media_share)))
            } catch (e: Exception) {
                Toast.makeText(applicationContext, R.string.media_share_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Registers the observers for changes in the delete related variables.
     * Responsible for creating the ActivityLauncher that asks a user for explicit delete permission.
     */
    private fun setupDeleteHandlers() {
        // Create an ActivityResult handler for the permission popups on android Q and up.
        val permissionResult = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    // We can now delete the pending image
                    viewModel.deletePending()
                } else {
                    // The image is deleted so this screen can be closed
                    onBackPressed()
                }
            }
        }

        // Observer to handle cases where additional permission are needed to delete an item (Q and up)
        viewModel.requiresPermission.observe(
            this,
            { intentSender ->
                intentSender?.let {
                    permissionResult.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            }
        )

        // Observer to check if the current fragment should be closed or not after delete operations
        viewModel.shouldClose.observe(
            this,
            {
                if (it) {
                    onBackPressed()
                }
            }
        )
    }

    /**
     * Creates the videoplayer object and binds it to the UI.
     * Also configures the player with the correct settings.
     */
    private fun setupVideoPlayer() {
        // Set up video view
        val videoView = binding.videoView

        // Build and bind the player
        player = SimpleExoPlayer.Builder(this).build()
        videoView.player = player

        // Set up and start the player
        val item = MediaItem.fromUri(videoItem.uri)
        player.setMediaItem(item)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()

        playerVolume = player.volume
    }

    /**
     * Registers the observer for pause operations.
     * Responsible for changing the play/pause icon and pausing the video player.
     */
    private fun setupUiHandlers() {
        // Pause handler
        viewModel.paused.observe(
            this,
            {
                if (it) {
                    player.play()
                    binding.videoPause.icon = AppCompatResources.getDrawable(this, R.drawable.ic_outline_pause_24)

                    // Call the hide after a 2s timeout
                } else {
                    player.pause()
                    binding.videoPause.icon = AppCompatResources.getDrawable(this, R.drawable.ic_outline_play_arrow_24)
                }
            }
        )

        // Sound handler
        viewModel.soundEnabled.observe(
            this,
            {
                if (it) {
                    player.volume = playerVolume
                } else {
                    player.volume = 0f
                }
            }
        )
    }
}
