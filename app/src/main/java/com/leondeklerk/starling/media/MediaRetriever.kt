package com.leondeklerk.starling.media

import android.content.ContentResolver
import android.content.ContentUris
import android.icu.util.Calendar
import android.provider.MediaStore
import com.leondeklerk.starling.data.HeaderItem
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.data.MediaItem
import com.leondeklerk.starling.data.VideoItem
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Class responsible for handling the retrieval of media items.
 * TODO: Implement handling of media from starling-backend
 */
class MediaRetriever {

    /**
     * Queries all media on the device.
     * Using the [MediaStore.Files] API, all Images and Videos on the device are loaded into a list.
     * Implemented with a [ContentResolver] using a cursor.
     * This list uses [MediaItem] to represent the data.
     * Also responsible for adding headers into the list based on the zoom level of the application.
     * @param contentResolver: the content resolver object to retrieve the media items from
     * @param projection: the query projection
     * @param selection: the items to select with the query
     * @param selectionArgs: The arguments needed to execute the selection
     * @param sortOrder: the sorting order of the query
     * @return: A list of [MediaItem]s containing all [HeaderItem]s, [VideoItem]s and [ImageItem]s.
     */
    suspend fun queryMedia(
        contentResolver: ContentResolver,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String
    ):
        List<MediaItem> {
        val media = mutableListOf<MediaItem>()

        withContext(Dispatchers.IO) {
            contentResolver.query(
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
                                contentUri,
                                displayName,
                                date,
                                duration,
                            )
                        }
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> {
                            mediaItem = ImageItem(
                                id,
                                contentUri,
                                displayName,
                                date,
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
}
