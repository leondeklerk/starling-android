package com.leondeklerk.starling.media

import android.app.Application
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.leondeklerk.starling.data.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.Long.parseLong
import java.util.Date

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
        val contentUri = MediaStore.Images.Media.getContentUri("external")
        val uri = ContentUris.withAppendedId(contentUri, imageItem.id)

        val stream = resolver.openOutputStream(uri, "rwt")
        stream?.let {
            if (!pendingBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, it)) {
                throw IOException("Failed to save bitmap.")
            }
            it.close()
        }

        val resultItem = ImageItem(
            imageItem.id,
            imageItem.uri,
            imageItem.displayName,
            imageItem.dateAdded,
            pendingBitmap!!.width,
            pendingBitmap!!.height,
            "image/jpeg",
            Date(System.currentTimeMillis())
        )

        _savedItem.postValue(resultItem)
    }

    /**
     * Create a new image as a copy of an existing image.
     * @param data the new image data
     * @param imageItem the data of the original image
     */
    private fun createCopy(data: Bitmap, imageItem: ImageItem) {
        viewModelScope.launch {
            var result: ImageItem? = null
            try {
                val name = Regex("\\.[^.]*\$").replace(imageItem.displayName, "")
                result = saveBitmap(data, Bitmap.CompressFormat.JPEG, "image/jpeg", "${name}-edit")
            } catch (e: IOException) {
                Toast.makeText(getApplication(), "Error $e", Toast.LENGTH_SHORT).show()
            } finally {
                _savedItem.postValue(result)
            }
        }
    }

    /**
     * Async function that saves the bitmap to the device and creates and entry in the MediaStore.
     * @param bitmap: the bitmap to save
     * @param format: the compression format
     * @param mimeType: the mimeType of the new image
     * @param displayName: the new name of the image.
     */
    private suspend fun saveBitmap(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        displayName: String
    ): ImageItem {
        val uri: Uri?

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }

        val resolver = getApplication<Application>().contentResolver

        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Failed to create new MediaStore record.")
        val dateAdded = Date()
        try {
            withContext(Dispatchers.IO) {
                // Android studio gives an incorrect blocking call warning
                @Suppress("BlockingMethodInNonBlockingContext")
                val inputStream = resolver.openOutputStream(uri)
                inputStream?.use {
                    if (!bitmap.compress(format, 100, it))
                        throw IOException("Failed to save bitmap.")
                }

                delay(800)
            }
        } catch (e: IOException) {
            uri.let { orphanUri ->
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(orphanUri, null, null)
            }
            throw e
        }

        // Create the image data
        val id = parseLong(uri.lastPathSegment!!)
        return ImageItem(id, uri, displayName, dateAdded, bitmap.width, bitmap.height, mimeType, Date(System.currentTimeMillis()))
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
