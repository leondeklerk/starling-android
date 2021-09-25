package com.leondeklerk.starling.media

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.leondeklerk.starling.data.ImageItem

/**
 * [MediaViewModel] responsible for handling functionality specific to [ImageItem] media.
 */
class ImageViewModel(application: Application) : MediaViewModel(application) {
    private val _mode = MutableLiveData(Mode.VIEW)
    val mode: LiveData<Mode> = _mode

    fun switchMode() {
        if (_mode.value == Mode.VIEW) {
            _mode.postValue(Mode.EDIT)
        } else {
            _mode.postValue(Mode.VIEW)
        }
    }

    enum class Mode {
        EDIT,
        VIEW
    }
}
