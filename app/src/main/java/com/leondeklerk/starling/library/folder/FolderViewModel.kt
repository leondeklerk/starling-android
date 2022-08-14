package com.leondeklerk.starling.library.folder

import android.app.Application
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leondeklerk.starling.media.MediaInterface
import com.leondeklerk.starling.media.data.MediaItem
import kotlinx.coroutines.launch

/**
 * Basic [ViewModel] that handles the data of a [FolderFragment].
 * Its main functionality is to provide the data for the adapter.
 * Uses [MutableLiveData] and [LiveData] to store and provide data that is kept up to date.
 */
class FolderViewModel(application: Application) : AndroidViewModel(application) {

    private val _data = MutableLiveData<List<MediaItem>>()

    val data: LiveData<List<MediaItem>> = _data

    private var contentObserver: ContentObserver? = null

    /**
     * On removal of the [ViewModel], remove the [ContentObserver] to prevent a memory leak.
     */
    override fun onCleared() {
        contentObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
    }

    /**
     * Function used to start loading in the media.
     * Loads in all images and videos of the folder from the [MediaStore] using coroutines.
     * @param bucketId: the id of the bucket this folder represents
     */
    fun loadMedia(bucketId: Long) {
        viewModelScope.launch {
            // Create the query parameters
            val projection = createProjection()
            val selection = createSelection()
            val selectionArgs = createSelectionArgs(bucketId)
            val sortOrder = createSortOrder()

            // Create the image retriever
            val retriever = MediaInterface()
            val resolver = getApplication<Application>().contentResolver

            // Start querying media.
            val mediaList = retriever.queryMedia(resolver, projection, selection, selectionArgs, sortOrder)
            _data.postValue(mediaList)

            // To observer any additional files created on the system, a observer is registered.
            if (contentObserver == null) {
                contentObserver = getApplication<Application>().contentResolver.registerObserver(
                    MediaStore.Files.getContentUri("external")
                ) {
                    // Upon a detected change it reloads the media.
                    loadMedia(bucketId)
                }
            }
        }
    }

    /**
     * Create a projection of media columns to retrieve from the MediaStore.
     * Helper function with potential to enable filter options later
     */
    private fun createProjection(): Array<String> {
        return arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
    }

    /**
     * Create a selection query to retrieve all files in the bucket but only two specific media types from the
     * MediaStore.
     * Helper function with potential to enable filter options later
     */
    private fun createSelection(): String {
        return "${MediaStore.Files.FileColumns.BUCKET_ID} = ? AND (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
    }

    /**
     * Create a list of arguments used in the selection query.
     * Helper function with potential to enable filter options later
     * @param bucketId: The id of the bucket items should be in
     */
    private fun createSelectionArgs(bucketId: Long): Array<String> {
        return arrayOf(
            bucketId.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
    }

    /**
     * Create the order in which all media is sorted.
     * Helper function with potential to enable custom sort orders later.
     */
    private fun createSortOrder(): String {
        return "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    }

    private fun ContentResolver.registerObserver(
        uri: Uri,
        observer: (selfChange: Boolean) -> Unit
    ): ContentObserver {
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                observer(selfChange)
            }
        }
        registerContentObserver(uri, true, contentObserver)
        return contentObserver
    }
}
