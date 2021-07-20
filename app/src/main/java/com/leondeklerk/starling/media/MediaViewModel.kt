package com.leondeklerk.starling.media

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.leondeklerk.starling.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Parent class for the different types of [MediaViewModel]s: [ImageViewModel] and [VideoViewModel].
 * Provides shared functionality between both types of media such as deleting and sharing.
 */
abstract class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private var pendingDeleteItem: MediaItem? = null
    private val _permissionNeededForDelete = MutableLiveData<IntentSender?>()
    val requiresPermission: LiveData<IntentSender?> = _permissionNeededForDelete

    private val _shouldClose = MutableLiveData(false)
    val shouldClose: LiveData<Boolean> = _shouldClose

    /**
     * Delete a media item from the device storage.
     * @param media: the media item to delete from the device.
     */
    fun delete(media: MediaItem) {
        viewModelScope.launch {
            performDelete(media)
        }
    }

    /**
     * Deletes a media item that is already pending,
     * after the user granted the required extra permission.
     * Resets the pendingDeleteItem
     */
    fun deletePending() {
        pendingDeleteItem?.let { media ->
            pendingDeleteItem = null
            delete(media)
        }
    }

    /**
     * Performs the delete of a media item from device storage.
     * Chooses the correct type of delete operation based on the API level:
     * Pre Q: The contentResolver can delete without any extra permission
     * Q: The user needs to grant explicit permission, otherwise an exception is thrown, which handles the permission.
     * Q+: The user needs to grant explicit permission, but there is no need to catch an exception to handle it.
     * @param media: the media item that needs to be deleted
     */
    private suspend fun performDelete(media: MediaItem) {
        withContext(Dispatchers.IO) {
            // Set the content resolver
            val contentResolver = getApplication<Application>().contentResolver

            when {
                VERSION.SDK_INT > VERSION_CODES.Q -> {
                    // We can perform the delete and signal to the fragment that additional permissions are needed
                    _permissionNeededForDelete.postValue(MediaInterface().deletePostQ(contentResolver, media))
                }
                VERSION.SDK_INT == VERSION_CODES.Q -> {
                    try {
                        // On first try this will fail with an exception
                        _shouldClose.postValue(MediaInterface().deleteQ(contentResolver, media) > 0)
                    } catch (securityException: SecurityException) {
                        // Correctly cast the exception
                        val recoverableSecurityException =
                            securityException as? RecoverableSecurityException
                                ?: throw securityException

                        // Set the pending delete item and signal the fragment that additional action is needed
                        pendingDeleteItem = media
                        _permissionNeededForDelete.postValue(
                            recoverableSecurityException.userAction.actionIntent.intentSender
                        )
                    }
                }
                else -> {
                    // Simply delete the data and post the result
                    _shouldClose.postValue(MediaInterface().deletePreQ(contentResolver, media) > 0)
                }
            }
        }
    }
}
