package com.leondeklerk.starling.media

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.leondeklerk.starling.data.VideoItem

/**
 * [MediaViewModel] responsible for handling functionality specific to [VideoItem] media.
 */
class VideoViewModel(application: Application) : MediaViewModel(application) {
    private val _soundEnabled = MutableLiveData(true)
    val soundEnabled: LiveData<Boolean> = _soundEnabled

    private val _paused = MutableLiveData(false)
    val paused: LiveData<Boolean> = _paused

    var beforeScrubState: Boolean = false

    /**
     * Update the current sound state
     * @param value: a [Boolean] indicating if the sound is on (true) or of (false)
     */
    fun setSound(value: Boolean) {
        _soundEnabled.postValue(value)
    }

    /**
     * Change the current pause state on call,
     * will negate the current value (pause -> playing -> pause)
     */
    fun setPausedState() {
        _paused.value?.let {
            _paused.postValue(!it)
        }
    }

    /**
     * Saves the current pause state before scrubbing trough the video
     */
    fun setPausedScrubState() {
        beforeScrubState = _paused.value!!
    }
}
