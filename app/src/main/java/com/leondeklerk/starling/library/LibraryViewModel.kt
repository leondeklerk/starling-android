package com.leondeklerk.starling.library

import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leondeklerk.starling.PermissionViewModel
import com.leondeklerk.starling.media.data.FolderItem
import com.leondeklerk.starling.media.data.MediaItemTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Basic [ViewModel] that handles the data of a [LibraryViewModel].
 * This handles the current value of the rationale text and button text, for the permissions.
 * Its main functionality is to provide the data for the adapter.
 * Uses [MutableLiveData] and [LiveData] to store and provide data that is kept up to date.
 */
class LibraryViewModel(application: Application) : PermissionViewModel(application) {

    private val _data = MutableLiveData<List<FolderItem>>()

    val data: LiveData<List<FolderItem>> = _data

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
     * Starts loading in all folders on the device containing video or image items.
     * Fills the _data variable with all unique folder names.
     */
    fun loadFolders() {
        viewModelScope.launch {
            // Start querying folders.
            val folderList = queryFolders()
            _data.postValue(folderList.toList())

            // To observer any additional files created on the system, a observer is registered.
            if (contentObserver == null) {
                contentObserver = getApplication<Application>().contentResolver.registerObserver(
                    MediaStore.Files.getContentUri("external")
                ) {
                    // Upon a detected change it reloads the media.
                    loadFolders()
                }
            }
        }
    }

    /**
     * Queries the device for all media folders..
     * Using the [MediaStore.Files] API, all folders with images and videos on the device are loaded into a set.
     * Implemented with a [ContentResolver] using a cursor.
     * This set uses [FolderItem] to represent the data and only uses distinct folder names.
     * @return: A set of unique [FolderItem]s containing all folders on the device with media items.
     */
    private suspend fun queryFolders(): HashSet<FolderItem> {
        val folders = HashSet<FolderItem>()

        withContext(Dispatchers.IO) {
            // Create the contentResolver arguments
            val projection = createProjection()
            val selection = createSelection()
            val selectionArgs = createSelectionArgs()
            val sortOrder = createSortOrder()

            getApplication<Application>().contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
                val mediaIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {

                    val name = cursor.getString(nameColumn)
                    val id = cursor.getLong(idColumn)
                    val mediaId = cursor.getLong(mediaIdColumn)
                    val type = cursor.getInt(mediaTypeColumn)
                    val modified = cursor.getLong(modifiedColumn)

                    val mediaUri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        mediaId,
                    )

                    val folderItem = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                        FolderItem(id, mediaUri, name, MediaItemTypes.IMAGE, modified, modified)
                    } else {
                        FolderItem(id, mediaUri, name, MediaItemTypes.VIDEO, modified, modified)
                    }

                    folders += folderItem
                }
            }
        }
        return folders
    }

    /**
     * Create a projection of media folder columns to retrieve from the MediaStore.
     * Helper function with potential to enable filter options later
     */
    private fun createProjection(): Array<String> {
        return arrayOf(
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
    }

    /**
     * Create a selection query to retrieve only two specific media types from the MediaStore.
     * Helper function with potential to enable filter options later
     */
    private fun createSelection(): String {
        return "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
    }

    /**
     * Create a list of arguments used in the selection query.
     * Helper function with potential to enable filter options later
     */
    private fun createSelectionArgs(): Array<String> {
        return arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
    }

    /**
     * Create the order in which all media is sorted.
     * Helper function with potential to enable custom sort orders later.
     */
    private fun createSortOrder(): String {
        return "${MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME} DESC, ${MediaStore.Files.FileColumns.DATE_ADDED} " +
            "DESC"
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
