package com.leondeklerk.starling.media

import android.app.Application
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.leondeklerk.starling.data.ImageItem
import kotlinx.coroutines.launch

/**
 * [MediaViewModel] responsible for handling functionality specific to [ImageItem] media.
 */
class ImageViewModel(application: Application) : MediaViewModel(application) {
    private val _mode = MutableLiveData(Mode.VIEW)
    val mode: LiveData<Mode> = _mode
    private val _savedItem = MutableLiveData<ImageItem?>()
    val savedItem: LiveData<ImageItem?> = _savedItem
    private val _pendingIntent = MutableLiveData<Pair<String, PendingIntent>?>()
    val pendingIntent: LiveData<Pair<String, PendingIntent>?> = _pendingIntent
    private var pendingBitmap: Bitmap? = null
    var pendingSwitch = false

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

    // TODO refactor to general media interface when restructuring activities/fragments
    /**
     * Try to update the image,
     * Newer android version require additional permission handling which is done here.
     * @param bitmap the image to store
     * @param imageItem the data class containing information
     * @parma copy if the new image should overwrite or be a copy
     */
    fun tryUpdate(bitmap: Bitmap, imageItem: ImageItem, copy: Boolean) {
        val resolver = getApplication<Application>().contentResolver
        pendingSwitch = copy

        if (copy) {
            createCopy(bitmap, imageItem)
            return
        }

        pendingBitmap = bitmap

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contentUri = MediaStore.Images.Media.getContentUri("external")
            val uri = ContentUris.withAppendedId(contentUri, imageItem.id)

            val editIntent = MediaStore.createWriteRequest(resolver, listOf(uri))
            _pendingIntent.postValue(Pair(OPERATION_UPDATE, editIntent))
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            try {
                // On first try this will fail with an exception
                update(imageItem)
            } catch (securityException: SecurityException) {
                // Correctly cast the exception
                val recoverableSecurityException =
                    securityException as? RecoverableSecurityException
                        ?: throw securityException

                _pendingIntent.postValue(Pair(OPERATION_UPDATE, recoverableSecurityException.userAction.actionIntent))
            }
        } else {
            update(imageItem)
        }
    }

    /**
     * Update the image in actual storage
     * @param imageItem the data class
     */
    fun update(imageItem: ImageItem) {
        val resolver = getApplication<Application>().contentResolver
        _savedItem.postValue(MediaInterface().update(resolver, imageItem, pendingBitmap!!))
    }

    /**
     * Create a new image as a copy of an existing image.
     * @param data the new image data
     * @param imageItem the data of the original image
     */
    private fun createCopy(data: Bitmap, imageItem: ImageItem) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            _savedItem.postValue(MediaInterface().createCopy(resolver, data, imageItem))
        }
    }

    companion object {
        const val OPERATION_UPDATE = "UPDATE"
        const val OPERATION_DELETE = "DELETE"
    }

    enum class Mode {
        EDIT,
        VIEW
    }
}
