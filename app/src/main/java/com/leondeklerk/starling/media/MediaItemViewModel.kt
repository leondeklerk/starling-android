package com.leondeklerk.starling.media

import android.app.Application
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.leondeklerk.starling.media.data.MediaItem
import kotlinx.coroutines.launch

/**
 * Media item view model.
 * Contains the generic code for deleting, updating and handing permissions.
 */
abstract class MediaItemViewModel<M : MediaItem, D : Any>(application: Application) : AndroidViewModel(application) {
    private val _finishedRequestType = MutableLiveData<MediaActionTypes?>()
    val finishedRequestType: LiveData<MediaActionTypes?> = _finishedRequestType

    private val _pendingRequestType = MutableLiveData<MediaActionTypes?>()
    val pendingRequestType: LiveData<MediaActionTypes?> = _pendingRequestType

    private val _nextId = MutableLiveData<Number?>()
    val nextId: LiveData<Number?> = _nextId

    lateinit var item: M

    private var pendingData: D? = null
    protected abstract val contentUri: Uri

    var isSuccess = true
    var pendingRequest: PendingIntent? = null

    /**
     * Try to update the media item,
     * Newer android version require additional permission handling which is done here.
     * @param data the media item data to store
     * @param copy if the new item should overwrite or be a copy
     */
    fun tryUpdate(data: D, copy: Boolean) {
        val resolver = getApplication<Application>().contentResolver

        if (copy) {
            createCopy(data)
            return
        }

        pendingData = data

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = ContentUris.withAppendedId(contentUri, item.id)

            pendingRequest = MediaStore.createWriteRequest(resolver, listOf(uri))
            _pendingRequestType.postValue(MediaActionTypes.EDIT)
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            try {
                // On first try this will fail with an exception
                update()
            } catch (securityException: SecurityException) {
                // Correctly cast the exception
                val recoverableSecurityException =
                    securityException as? RecoverableSecurityException
                        ?: throw securityException

                pendingRequest = recoverableSecurityException.userAction.actionIntent
                _pendingRequestType.postValue(MediaActionTypes.EDIT)
            }
        } else {
            update()
        }
    }

    /**
     * Try to delete the media item.
     * Newer android version require additional permission handling which is done here.
     */
    fun tryDelete() {
        val resolver = getApplication<Application>().contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = ContentUris.withAppendedId(contentUri, item.id)

            pendingRequest = MediaStore.createDeleteRequest(resolver, listOf(uri))
            _pendingRequestType.postValue(MediaActionTypes.DELETE)
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            try {
                // On first try this will fail with an exception
                delete()
            } catch (securityException: SecurityException) {
                // Correctly cast the exception
                val recoverableSecurityException =
                    securityException as? RecoverableSecurityException
                        ?: throw securityException

                pendingRequest = recoverableSecurityException.userAction.actionIntent
                _pendingRequestType.postValue(MediaActionTypes.DELETE)
            }
        } else {
            delete()
        }
    }

    /**
     * When a request is granted, execute the action if needed.
     * A granted request for delete already deletes the item therefore only state reset is needed.
     * @param type the type of request that was granted.
     *
     */
    fun onRequestGranted(type: MediaActionTypes) {
        if (hasPending()) {
            when (type) {
                MediaActionTypes.DELETE -> {
                    isSuccess = true
                }
                MediaActionTypes.EDIT -> update()
                else -> {
                    isSuccess = false
                }
            }

            _finishedRequestType.postValue(_pendingRequestType.value)
            _pendingRequestType.postValue(null)
        }
    }

    /**
     * Create a new media item entry as a copy of an existing item.
     * @param data the new item data
     */
    private fun createCopy(data: D) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val result = MediaInterface().createCopy<M>(resolver, data, item)

            isSuccess = result != null
            if (isSuccess) {
                _nextId.postValue(result?.id)
            }
            _finishedRequestType.postValue(MediaActionTypes.EDIT)
        }
    }

    /**
     * Update the media item data in actual storage
     */
    private fun update() {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            pendingData?.let {
                val result = MediaInterface().update<M>(resolver, item, it)
                if (result != null) {
                    item = result
                    isSuccess = true
                } else {
                    isSuccess = false
                }
                _finishedRequestType.postValue(MediaActionTypes.EDIT)
            }
        }
    }

    /**
     * Check if there is a pending request.
     * @return pending or not.
     */
    private fun hasPending(): Boolean {
        return pendingRequestType.value != null
    }

    /**
     * Delete the media item from storage.
     */
    private fun delete() {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val result = MediaInterface().delete(resolver, item)
            isSuccess = result > 0
            _finishedRequestType.postValue(MediaActionTypes.DELETE)
        }
    }
}
