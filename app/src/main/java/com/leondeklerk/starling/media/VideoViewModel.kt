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

    private val _seconds = MutableLiveData<Int>()
    val seconds = _seconds

    private val _currentPosition = MutableLiveData<Float>()
    val currentPosition: LiveData<Float> = _currentPosition

    /**
     * Update the curren sound state
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

    fun setSeconds(value: Int) {
        _seconds.postValue(value)
    }

    fun setPosition(value: Long) {
        val position = value.toFloat() / 1000
        when {
            value <= seconds.value!! * 1000 -> {
                _currentPosition.postValue(position)
            }
            value < 0 -> {
                _currentPosition.postValue(0.0f)
            }
            else -> {
                _currentPosition.postValue(2.0f)
            }
        }
    }
}
