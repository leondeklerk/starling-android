package com.leondeklerk.starling.library.folder

import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.icu.util.Calendar
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leondeklerk.starling.data.HeaderItem
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.data.MediaItem
import com.leondeklerk.starling.data.VideoItem
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
     * Loads in all images and videos from the [MediaStore] using coroutines.
     */
    fun loadMedia(buckedId: Long) {
        viewModelScope.launch {
            // Start querying media.
            val mediaList = queryMedia(buckedId)
            _data.postValue(mediaList)

            // To observer any additional files created on the system, a observer is registered.
            if (contentObserver == null) {
                contentObserver = getApplication<Application>().contentResolver.registerObserver(
                    MediaStore.Files.getContentUri("external")
                ) {
                    // Upon a detected change it reloads the media.
                    loadMedia(buckedId)
                }
            }
        }
    }

    /**
     * Queries all media on the device.
     * Using the [MediaStore.Files] API, all Images and Videos on the device are loaded into a list.
     * Implemented with a [ContentResolver] using a cursor.
     * This list uses [MediaItem] to represent the data.
     * Also responsible for adding headers into the list based on the zoom level of the application.
     * @return: A list of [MediaItem]s containing all [HeaderItem]s, [VideoItem]s and [ImageItem]s.
     */
    private suspend fun queryMedia(bucketId: Long): List<MediaItem> {
        val media = mutableListOf<MediaItem>()

        withContext(Dispatchers.IO) {

            // Create the contentResolver arguments
            val projection = createProjection()
            val selection = createSelection()
            val selectionArgs = createSelectionArgs(bucketId)
            val sortOrder = createSortOrder()

            getApplication<Application>().contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dateColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                val baseDate = Calendar.getInstance()
                baseDate.time = Date(Long.MAX_VALUE)

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idColumn)
                    val date =
                        Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateColumn)))
                    val displayName = cursor.getString(displayNameColumn)

                    val width = cursor.getLong(widthColumn)
                    val height = cursor.getLong(heightColumn)

                    val duration = cursor.getInt(durationColumn)

                    val mediaType = cursor.getInt(mediaTypeColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        id
                    )

                    updateHeaders(media, date, baseDate)

                    val mediaItem: MediaItem

                    when (mediaType) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> {
                            mediaItem = VideoItem(
                                id,
                                displayName,
                                date,
                                duration,
                                contentUri,
                            )
                        }
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> {
                            mediaItem = ImageItem(
                                id,
                                displayName,
                                date,
                                contentUri,
                                width,
                                height
                            )
                        }
                        else -> continue
                    }
                    media += mediaItem
                }
            }
        }
        return media
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
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
    }

    /**
     * Create a selection query to retrieve only two specific media types from the MediaStore.
     * Helper function with potential to enable filter options later
     */
    private fun createSelection(): String {
        return "${MediaStore.Files.FileColumns.BUCKET_ID} = ? AND (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
    }

    /**
     * Create a list of arguments used in the selection query.
     * Helper function with potential to enable filter options later
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

    /**
     * Based on the current zoom level, insert headers into the media gallery.
     * Headers can be created for years, months or days.
     * @param media: the media list to insert the header items into
     * @param date: the data of the current media item (Image or Video)
     * @param base: the previous date.
     */
    private fun updateHeaders(media: MutableList<MediaItem>, date: Date, base: Calendar) {
        val cal = Calendar.getInstance()
        cal.time = date

        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)

        val baseDay = base.get(Calendar.DAY_OF_MONTH)
        val baseMonth = base.get(Calendar.MONTH)
        val baseYear = base.get(Calendar.YEAR)

        // TODO: add adaptive zoom level
        // 0: year view
        // 1: month view
        // 2: day view
        // 3: zoomed in day view
        val zoomLevel = 2

        // Determine if a header should be added or not.
        val addHeader = when (zoomLevel) {
            0 -> year < baseYear
            2, 3 -> day < baseDay || month < baseMonth || year < baseYear
            else -> month < baseMonth || year < baseYear
        }

        if (addHeader) {
            // Insert a header item
            media += HeaderItem(-date.time, date, zoomLevel)
            base.time = cal.time
        }
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
