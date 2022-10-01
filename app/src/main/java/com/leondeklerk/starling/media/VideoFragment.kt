package com.leondeklerk.starling.media

import android.animation.ValueAnimator
import android.content.DialogInterface
import android.graphics.Rect
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
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.leondeklerk.starling.R
import com.leondeklerk.starling.databinding.FragmentVideoBinding
import com.leondeklerk.starling.edit.crop.AspectRatio
import com.leondeklerk.starling.extensions.applyMargin
import com.leondeklerk.starling.extensions.requestNewSize
import com.leondeklerk.starling.media.data.VideoItem

class VideoFragment(
    private val item: VideoItem,
    private val enterListeners: PagerFragment.TransitionListeners,
    private val exitListeners: PagerFragment.TransitionListeners
) :
    MediaItemFragment() {
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

        pagerViewModel.setEditEnabled(false)

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
        pagerViewModel.setEditEnabled(false)
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

    override fun scale(scalarX: Float, scalarY: Float) {
        viewModel.startTransition()
        binding.videoView.scaleX = scalarX
        binding.videoView.scaleY = scalarY
    }

    override fun translate(dX: Float, dY: Float) {
        viewModel.startTransition()
        binding.videoView.translationX += dX
        binding.videoView.translationY += dY
    }

    override fun reset() {
        binding.videoView.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).withEndAction {
            viewModel.endTransition()
        }.setDuration(100L).start()
    }

    override fun close(target: Rect, duration: Long) {
        viewModel.startTransition()
        binding.videoView.apply {
            requestLayout()
            post {
                transition(
                    duration, binding.videoContainer,
                    {
                        exitListeners.startListener.invoke(it)
                    },
                    {
                        exitListeners.endListener.invoke(it)
                    }
                )

                resizeMode = RESIZE_MODE_ZOOM
                translationX = 0f
                translationY = 0f

                scaleX = 1f
                scaleY = 1f
                requestNewSize(target.width(), target.height())
                applyMargin(target.left, target.top)
            }
        }
    }

    fun initialize() {
        player.play()
        handler.postDelayed(runnable, 0)
    }

    private fun loadTransition() {
        if (pagerViewModel.initial) {
            binding.videoView.apply {

                visibility = View.VISIBLE
                resizeMode = RESIZE_MODE_ZOOM
                val rect = pagerViewModel.rect
                requestNewSize(rect.width(), rect.height())
                applyMargin(rect.left, rect.top)

                post {
                    val maxWidth = pagerViewModel.containerSize.first
                    val maxHeight = pagerViewModel.containerSize.second

                    val ratio = AspectRatio.ORIGINAL
                    if (rotated()) {
                        ratio.xRatio = item.height
                        ratio.yRatio = item.width
                    } else {
                        ratio.xRatio = item.width
                        ratio.yRatio = item.height
                    }

                    val (width, height) = ratio.getSizeWithinFrom(maxWidth, maxHeight)

                    transition(
                        250L, binding.videoContainer,
                        {
                            enterListeners.startListener.invoke(it)
                        },
                        {
                            enterListeners.endListener.invoke(it)
                            pagerViewModel.initial = false
                            initialize()
                        }
                    )

                    requestNewSize(width, height)
                    applyMargin((maxWidth - width) / 2, (maxHeight - height) / 2)
                    resizeMode = RESIZE_MODE_FIT
                }
            }
        }
    }

    private fun rotated(): Boolean {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(requireContext(), viewModel.item.uri)
        val r = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toFloat()
        retriever.release()

        return r == ROTATION_90 || r == ROTATION_270
    }

    /**
     * Creates the video player object and binds it to the UI.
     * Also configures the player with the correct settings.
     */
    private fun setupVideoPlayer() {

        player = ExoPlayer.Builder(requireContext()).build()

        if (pagerViewModel.initial) {
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    if (playbackState == Player.STATE_READY) {
                        loadTransition()
                    }
                }
            })
        }

        binding.videoView.player = player

        val video = MediaItem.fromUri(viewModel.item.uri)
        player.setMediaItem(video)
        player.setSeekParameters(SeekParameters.EXACT)

        player.repeatMode = Player.REPEAT_MODE_ALL

        player.prepare()

        viewModel.setVolume(player.volume)
        viewModel.setPosition(POSITION_START)

        if (pagerViewModel.initial) {
            binding.videoView.visibility = View.GONE
        } else {
            initialize()
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

        viewModel.overlayVisible.observe(viewLifecycleOwner) {
            setOverlay()
        }

        pagerViewModel.showOverlay.observe(viewLifecycleOwner) {
            viewModel.setOverlayState(it)
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
            pagerViewModel.toggleOverlay()
        }

        binding.videoView.setOnClickListener {
            pagerViewModel.toggleOverlay()
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
    private fun setOverlay() {
        val alpha = viewModel.overlayAlpha
        val visibility = viewModel.overlayVisibility

        binding.layout.visibility = visibility
        if (viewModel.animateVisibility == true) {
            binding.layout.animate().alpha(alpha)
        } else {
            binding.layout.alpha = alpha
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
