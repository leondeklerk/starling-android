package com.leondeklerk.starling.media

import android.app.Application
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.leondeklerk.starling.media.data.ImageItem

class ImageViewModel(application: Application) : MediaItemViewModel<ImageItem, Bitmap>(application) {
    override val contentUri = MediaStore.Images.Media.getContentUri("external")!!

    private val _mode = MutableLiveData(Mode.VIEW)
    val mode: LiveData<Mode> = _mode

    /**
     * Switch between the different image modes
     */
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
