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
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the viewModel
        viewModel = ViewModelProvider(this).get(VideoViewModel::class.java)

        val videoItem: VideoItem = VideoActivityArgs.fromBundle(intent.extras!!).videoItem

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

        binding.lifecycleOwner = this

        supportActionBar?.hide()

        // Hide the status bar and navigation bars
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
        }

        // Set up video view
        val videoView = binding.videoView
        videoView.useController = false
        videoView.setShowNextButton(false)
        videoView.setShowPreviousButton(false)
        videoView.setShowRewindButton(true)
        videoView.setShowRewindButton(false)
        videoView.setShowFastForwardButton(false)

        // Build and bind the player
        player = SimpleExoPlayer.Builder(this).build()
        videoView.player = player

        // Set up and start the player
        val item = MediaItem.fromUri(videoItem.uri)
        player.setMediaItem(item)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.play()

        // Handle system ui changes
        // Using a setOnApplyWindowInsetsListener does not work properly, therefore deprecated methods are used.
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                videoView.useController = true
                // If the system ui is visible the toolbar should reappear
                supportActionBar?.show()
                videoView.showController()
            }
        }

        // On clicking the video the system ui and the toolbar should disappear for a fullscreen experience.
        videoView.setOnClickListener {
            supportActionBar?.hide()

            // Hide the navigation and status bar on click
            WindowInsetsControllerCompat(window, window.decorView).hide(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause the video
        if (player.isPlaying) {
            player.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        player.playWhenReady = true
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
}
