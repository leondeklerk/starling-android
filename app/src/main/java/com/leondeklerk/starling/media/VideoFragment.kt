package com.leondeklerk.starling.media

import android.animation.ValueAnimator
import android.content.DialogInterface
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.viewModels
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SeekParameters
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.FragmentVideoBinding
import com.leondeklerk.starling.media.data.VideoItem

class VideoFragment(private val item: VideoItem) : MediaItemFragment() {
    override val viewModel: VideoViewModel by viewModels()

    private lateinit var binding: FragmentVideoBinding
    private lateinit var player: ExoPlayer

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable {
        updateProgress()
    }
    private val sliderListener = object : Slider.OnSliderTouchListener {
        override fun onStartTrackingTouch(slider: Slider) {
            viewModel.startSeek()
        }

        override fun onStopTrackingTouch(slider: Slider) {
            viewModel.endSeek()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVideoBinding.inflate(inflater, container, false)

        // Set binding basics
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        viewModel.item = item

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activityViewModel.setEditEnabled(false)

        setupSurface()

        setupVideoPlayer()

        setupViewModelBindings()

        setupViewBindings()
    }

    override fun onPause() {
        super.onPause()
        viewModel.statePause()
    }

    override fun onResume() {
        super.onResume()
        activityViewModel.setEditEnabled(false)
        viewModel.stateResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.stop()
        player.release()
    }

    override fun delete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.video_delete_title))
            .setMessage(getString(R.string.video_delete_message))
            .setPositiveButton(getString(R.string.media_delete)) { _: DialogInterface, _: Int ->
                viewModel.tryDelete()
            }
            .setNegativeButton(getString(android.R.string.cancel)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    override fun edit() {
        // Implement video editing in the future
    }

    override fun isDeleted(success: Boolean) {
        // Potential on delete hook
    }

    override fun isSaved(success: Boolean) {
        // Unused as there is no video editing yet
    }

    /**
     * Creates the video player object and binds it to the UI.
     * Also configures the player with the correct settings.
     */
    private fun setupVideoPlayer() {
        player = ExoPlayer.Builder(requireContext()).build()
        player.setVideoSurfaceView(binding.videoView)
        val video = MediaItem.fromUri(viewModel.item.uri)
        player.setMediaItem(video)

        player.setSeekParameters(SeekParameters.EXACT)

        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()

        viewModel.setVolume(player.volume)
        viewModel.setPosition(POSITION_START)

        player.play()

        handler.postDelayed(runnable, 0)
    }

    /**
     * Set up the video surface.
     * Determines the orientation of the video based on the video metadata.
     */
    private fun setupSurface() {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(requireContext(), viewModel.item.uri)
        val r = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toFloat()
        retriever.release()

        if (r == ROTATION_90 || r == ROTATION_270) {
            binding.videoView.setVideoSize(item.height.toFloat(), item.width.toFloat())
        } else {
            binding.videoView.setVideoSize(item.width.toFloat(), item.height.toFloat())
        }
    }

    /**
     * Set up the observers of the view model variables.
     */
    private fun setupViewModelBindings() {
        viewModel.volume.observe(viewLifecycleOwner) {
            player.volume = it
        }

        viewModel.paused.observe(viewLifecycleOwner) {
            onPauseChange(it)
        }

        viewModel.seeking.observe(viewLifecycleOwner) {
            binding.videoPause.animate().alpha(viewModel.seekAlpha)
        }

        viewModel.showOverlay.observe(viewLifecycleOwner) {
            setOverlayAlpha()
            activityViewModel.setInsets(it, true)
        }

        activityViewModel.showInsets.observe(viewLifecycleOwner) {
            viewModel.setOverlay(it)
        }
    }

    /**
     * Set up all the listeners and variables of the view binding.
     */
    private fun setupViewBindings() {
        binding.videoProgress.addOnSliderTouchListener(sliderListener)

        binding.videoProgress.addOnChangeListener { _, value, fromUser ->
            onProgressSliderChange(value, fromUser)
        }

        binding.videoSound.setOnCheckedChangeListener { _, isChecked ->
            viewModel.enableSound(isChecked)
        }

        binding.videoPause.setOnClickListener {
            viewModel.togglePausedState()
        }

        binding.layout.setOnClickListener {
            viewModel.toggleOverlay()
        }

        binding.videoView.setOnClickListener {
            viewModel.toggleOverlay()
        }
    }

    /**
     * Update the state of the UI and player based on the paused state.
     * @param paused if the video is paused or not.
     */
    private fun onPauseChange(paused: Boolean) {
        if (paused) {
            player.pause()
            binding.videoPause.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_outline_play_arrow_24)
            handler.removeCallbacksAndMessages(null)
        } else {
            player.play()
            binding.videoPause.icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_outline_pause_24)
            handler.postDelayed(runnable, 0L)
        }
    }

    /**
     * Updates the visual state of the overlay.
     */
    private fun setOverlayAlpha() {
        val alpha = viewModel.overlayAlpha

        val vis = if (alpha == ALPHA_HIDDEN) {
            View.GONE
        } else {
            View.VISIBLE
        }

        binding.videoPause.apply {
            animate().alpha(alpha)
            visibility = vis
        }
        binding.videoSound.apply {
            animate().alpha(alpha)
            visibility = vis
        }
        binding.videoDuration.apply {
            animate().alpha(alpha)
            visibility = vis
        }
        binding.videoPosition.apply {
            animate().alpha(alpha)
            visibility = vis
        }
        binding.videoProgress.apply {
            animate().alpha(alpha)
            visibility = vis
        }
    }

    /**
     * When using the slider, make sure to seek to the position.
     * Only seeks when teh player is ready to prevent a large number of seeks.
     */
    private fun onProgressSliderChange(value: Float, fromUser: Boolean) {
        if (fromUser) {
            if (player.playbackState == Player.STATE_READY) {
                val newPos = (player.duration * value).toLong()
                player.seekTo(newPos)
                viewModel.setPosition(newPos)
            }
        }
    }

    /**
     * Update the progress/position state in the view model and the slider.
     * Animates the slider changes.
     */
    private fun updateProgress() {
        viewModel.setPosition(player.currentPosition)

        val animator = ValueAnimator.ofFloat(binding.videoProgress.value, viewModel.updateProgress()).setDuration(PROGRESS_REFRESH_RATE)
        animator.addUpdateListener {
            binding.videoProgress.value = it.animatedValue as Float
        }
        animator.start()
        handler.postDelayed(runnable, PROGRESS_REFRESH_RATE)
    }

    companion object {
        private const val PROGRESS_REFRESH_RATE = 50L
        private const val ROTATION_90 = 90f
        private const val ROTATION_270 = 270f
        private const val POSITION_START = 0L
        private const val ALPHA_HIDDEN = 0f
    }
}
