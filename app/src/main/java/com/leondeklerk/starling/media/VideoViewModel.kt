package com.leondeklerk.starling.media

import android.app.Application
import android.provider.MediaStore
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.leondeklerk.starling.media.data.VideoItem
import kotlin.math.max
import kotlin.math.min

class VideoViewModel(application: Application) : MediaItemViewModel<VideoItem, Any>(application) {

    private var beforeTransitionPause = false
    var animateVisibility: Boolean? = true
    override val contentUri = MediaStore.Video.Media.getContentUri("external")!!

    private val _position = MutableLiveData(0L)
    val position: LiveData<Long> = _position

    private var _paused = MutableLiveData(false)
    var paused: LiveData<Boolean> = _paused

    private var beforeSeekPaused = false
    private var beforeStateChange = false

    private val _seeking = MutableLiveData(false)
    val seeking: LiveData<Boolean> = _seeking

    private val _volume = MutableLiveData(0f)
    val volume: LiveData<Float> = _volume

    private var baseVolume = 0f
    private var soundEnabled = true
    private var _overlayVisible = MutableLiveData(false)
    val overlayVisible: LiveData<Boolean> = _overlayVisible

    private var inTransition = false

    val overlayAlpha: Float
        get() {
            return if (_overlayVisible.value == true) {
                1f
            } else {
                0f
            }
        }

    val overlayVisibility: Int
        get() {
            return if (_overlayVisible.value == true) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

    // Get the current alpha based on the seeking state.
    val seekAlpha: Float
        get() {
            return if (_seeking.value == true) {
                0f
            } else {
                1f
            }
        }

    /**
     * Toggle between paused and playing state.
     */
    fun togglePausedState() {
        _paused.postValue(!_paused.value!!)
    }

    /**
     * Pause the video playback and store the old state.
     */
    fun statePause() {
        beforeStateChange = _paused.value!!
        _paused.postValue(true)
    }

    /**
     * Resume playback of the player.
     */
    fun stateResume() {
        _paused.postValue(beforeStateChange)
    }

    /**
     * Start a seeking operation,
     * save the current paused state and pause the current playback.
     */
    fun startSeek() {
        if (!_seeking.value!!) {
            _seeking.postValue(true)
            beforeSeekPaused = _paused.value!!
            if (!beforeSeekPaused) {
                _paused.postValue(true)
            }
        }
    }

    /**
     * End a seeking operation and potentially resume playback.
     */
    fun endSeek() {
        _seeking.postValue(false)
        _paused.postValue(beforeSeekPaused)
    }

    fun startTransition() {
        if (!inTransition) {
            inTransition = true
            beforeTransitionPause = _paused.value!!
            if (!beforeTransitionPause) {
                _paused.postValue(true)
            }
        }
    }

    fun endTransition() {
        if (inTransition) {
            inTransition = false
            _paused.postValue(beforeTransitionPause)
        }
    }

    /**
     * Determines the current progress based on the player position and total duration.
     * @return the current progress between 0 and 1
     */
    fun updateProgress(): Float {
        return if (position.value!! == 0L) {
            0f
        } else if (position.value!! == item.duration) {
            1f
        } else {
            max(0f, min(1f, position.value!!.toFloat() / item.duration))
        }
    }

    /**
     * Set the current player position.
     * @param newPos the new position of the player
     */
    fun setPosition(newPos: Long) {
        _position.postValue(newPos)
    }

    /**
     * Enable or disable the sound of the player.
     * Updates the internal sound state and posts the new volume to the _volume observable.
     * @param enable if the sound should be enabled or not.
     */
    fun enableSound(enable: Boolean) {
        soundEnabled = enable

        if (enable) {
            _volume.postValue(baseVolume)
        } else {
            _volume.postValue(0f)
        }
    }

    /**
     * Initialize the base volume.
     * The base volume is stored and if sound is enabled, posted to the volume variable.
     * @param volume the standard volume of the player
     */
    fun setVolume(volume: Float) {
        baseVolume = volume
        if (soundEnabled) {
            _volume.postValue(volume)
        }
    }

    fun setOverlayState(visible: Boolean?, animate: Boolean? = true) {
        animateVisibility = animate
        visible?.let {
            _overlayVisible.postValue(visible)
        }
    }
}
