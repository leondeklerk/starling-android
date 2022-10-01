package com.leondeklerk.starling.media

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.icu.util.Calendar
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.leondeklerk.starling.media.data.FolderItem
import com.leondeklerk.starling.media.data.HeaderItem
import com.leondeklerk.starling.media.data.ImageItem
import com.leondeklerk.starling.media.data.MediaItem
import com.leondeklerk.starling.media.data.SortData
import com.leondeklerk.starling.media.data.VideoItem
import java.io.IOException
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Class responsible for handling the retrieval of media items.
 * TODO: Implement handling of media from starling-backend
 */
class MediaInterface {

    data class MediaQueryData(
        val data: MutableList<MediaItem>,
        val folders: Map<String, FolderItem>,
    )

    private val IMAGE_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val VIDEO_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    val list = mutableListOf<MediaItem>(
        ImageItem(
            1000025626,
            Uri.parse("content://media/external/images/media/1000025626"),
            "Screenshot_20220928-153849.png",
            1664372329000,
            1440,
            3120,
            "image/png",
            1664372330000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025625,
            Uri.parse("content://media/external/images/media/1000025625"),
            "tyqdftuhciq91.jpg",
            1664370461000,
            1920,
            1920,
            "image/jpeg",
            1664370461000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025624,
            Uri.parse("content://media/external/images/media/1000025624"),
            "Screenshot_20220928-142552.png",
            1664367952000,
            1440,
            3120,
            "image/png",
            1664367953000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025615,
            Uri.parse("content://media/external/images/media/1000025615"),
            "escj1e8bnbq91.jpg",
            1664316217000,
            1080,
            1040,
            "image/jpeg",
            1664316217000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025613,
            Uri.parse("content://media/external/images/media/1000025613"),
            "Screenshot_20220927-195951.png",
            1664301591000,
            1440,
            3120,
            "image/png",
            1664301591000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025587,
            Uri.parse("content://media/external/images/media/1000025587"),
            "zjibr9kns8561.jpg",
            1664231867000,
            800,
            1003,
            "image/jpeg",
            1608034700000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025588,
            Uri.parse("content://media/external/images/media/1000025588"),
            "zkrdalmpn0d81.jpg",
            1664231867000,
            734,
            500,
            "image/jpeg",
            1642790673000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025583,
            Uri.parse("content://media/external/images/media/1000025583"),
            "yp1nlvl5mtr81.jpg",
            1664231866000,
            1170,
            994,
            "image/jpeg",
            1661339379000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025584,
            Uri.parse("content://media/external/images/media/1000025584"),
            "yqnell3z1dj91.jpg",
            1664231866000,
            3024,
            4032,
            "image/jpeg",
            1661339379000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025585,
            Uri.parse("content://media/external/images/media/1000025585"),
            "yx6h5jgr3lu81.jpg",
            1664231866000,
            1903,
            1242,
            "image/jpeg",
            1650451770000,
            SortData("2022", "6", "6", "")
        ),
        ImageItem(
            1000025586,
            Uri.parse("content://media/external/images/media/1000025586"),
            "z5sjjm4d40b71.jpg",
            1664231866000,
            995,
            1079,
            "image/jpeg",
            1626210990000,
            SortData("2022", "6", "6", "")
        )
    )

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
        MediaQueryData {
//        val media = mutableListOf<MediaItem>()
//        val folders = mutableMapOf<String, FolderItem>()
//        val foldersData = mutableMapOf<String, MutableMap<String, MutableMap<String, MutableList<MediaItem>>>>()
//        val years = mutableMapOf<String, MutableList<MediaItem>>()
//        val months = mutableMapOf<String, MutableList<MediaItem>>()
//        val days = mutableMapOf<String, MutableList<MediaItem>>()
//
//
// //        val galleryData = mutableListOf<MediaItem>()
// //
// //       val folderData = mutableMapOf<String, MutableList<MediaItem>>()
// //        val idIndexMappings = mutableMapOf<Long, Int>()
//
//        withContext(Dispatchers.IO) {
//            contentResolver.query(
//                MediaStore.Files.getContentUri("external"),
//                projection,
//                selection,
//                selectionArgs,
//                sortOrder
//            )?.use { cursor ->
//                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
//                val dateColumn =
//                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
//                val displayNameColumn =
//                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
//                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
//                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
//                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
//                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
//                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
//                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
//                val folderNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
//                val folderIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
//
//                val baseDate = Calendar.getInstance()
//                baseDate.time = Date(Long.MAX_VALUE)
//
//                while (cursor.moveToNext()) {
//
//                    val id = cursor.getLong(idColumn)
//                    val date = TimeUnit.SECONDS.toMillis(cursor.getLong(dateColumn))
//                    val displayName = cursor.getString(displayNameColumn)
//
//                    val width = cursor.getInt(widthColumn)
//                    val height = cursor.getInt(heightColumn)
//
//                    val duration = cursor.getInt(durationColumn)
//
//                    val mediaType = cursor.getInt(mediaTypeColumn)
//
//                    val mimeType = cursor.getString(mimeTypeColumn)
//
//                    val modified = TimeUnit.SECONDS.toMillis(cursor.getLong(modifiedColumn))
//
//                    var folderName = cursor.getString(folderNameColumn)
//
//                    // Map DCIM items to camera
//                    if (folderName == "DCIM") {
//                        folderName = "Camera"
//                    }
//
//                    val calendar = Calendar.getInstance()
//                    calendar.time = Date(date)
//                    val year = "${calendar.get(Calendar.YEAR)}"
//                    val month = "${calendar.get(Calendar.MONTH)}-$year"
//                    val day = "${calendar.get(Calendar.DAY_OF_MONTH)}-$month-$year"
//
//                    val mediaItem: MediaItem
//
//                    when (mediaType) {
//                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> {
//                            mediaItem = VideoItem(
//                                id,
//                                ContentUris.withAppendedId(VIDEO_URI, id),
//                                displayName,
//                                date,
//                                duration.toLong(),
//                                mimeType,
//                                width,
//                                height,
//                                modified,
//                                SortData(year, month, day, folderName)
//                            )
//                        }
//                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> {
//                            mediaItem = ImageItem(
//                                id,
//                                ContentUris.withAppendedId(IMAGE_URI, id),
//                                displayName,
//                                date,
//                                width,
//                                height,
//                                mimeType,
//                                modified,
//                                SortData(year, month, day, folderName)
//                            )
//                        }
//                        else -> continue
//                    }
//
//
//
//                    media += mediaItem
//
//                    folders[folderName] ?: run {
//                        val folderId = cursor.getLong(folderIdColumn)
//                        folders[folderName] = FolderItem(folderId, mediaItem.uri!!, folderName, mediaItem.type, mediaItem.dateAdded, modified)
//                    }
//
// //                    folders[folderName]?.let {
// //                        foldersData[folderName]?.get("year")?.let {
// //
// //                        } ?: run {
// //                            foldersData[folderName]?.get("year")?.
// //                        }
// //                    } ?: run {
// //                        val folderId = cursor.getLong(folderIdColumn)
// //                    }
// //
// //                    val calendar = Calendar.getInstance()
// //                    calendar.time = Date(TimeUnit.SECONDS.toMillis(date))
// //                    val year = "${calendar.get(Calendar.YEAR)}"
// //                    val month = "${calendar.get(Calendar.MONTH)}-$year"
// //                    val day = "${calendar.get(Calendar.DAY_OF_MONTH)}-$month-$year"
// //
// //
// //                    if (years[year] == null) {
// //                        years[year] = mutableListOf(mediaItem)
// //                    } else {
// //                        years[year]?.add(mediaItem)
// //                    }
// //
// //                    if (months[month] == null) {
// //                        months[month] = mutableListOf(mediaItem)
// //                    } else {
// //                        months[month]?.add(mediaItem)
// //                    }
// //
// //                    if (days[day] == null) {
// //                        days[day] = mutableListOf(mediaItem)
// //                    } else {
// //                        days[day]?.add(mediaItem)
// //                    }
// //
// //
// //
// //
// //                    val items = if (header != null) {
// //                        mutableListOf(header, mediaItem)
// //                    } else {
// //                        mutableListOf(mediaItem)
// //                    }
// //
// //                    idIndexMappings[id] = media.size
// //                    galleryData.addAll(items)
// //
// //
// //
// //                    if (folders[folderName] == null) {
// //                        val folderId = cursor.getLong(folderIdColumn)
// //                        folders[folderName] = FolderItem(folderId, mediaItem.uri!!, folderName, mediaItem.type, modified)
// //                        folderData[folderName] = items
// //                    } else {
// //                        folderData[folderName]?.addAll(items)
// //                    }
// //
// //                    media += mediaItem
//                }
//            }
//        }
        return MediaQueryData(list, mapOf())
    }

    /**
     * Function to delete a media item from the device.
     * @param contentResolver: the contentResolver associated with the MediaStore
     * @param media: the actual media item representing the file to be deleted
     * @return: The number of rows this operation has deleted
     */
    suspend fun delete(contentResolver: ContentResolver, media: MediaItem): Int {
        return withContext(Dispatchers.IO) {
            contentResolver.delete(media.uri!!, null, null)
        }
    }

    suspend fun <M : MediaItem> update(resolver: ContentResolver, item: MediaItem, src: Any): M? {
        val imageItem = item as ImageItem
        val data = src as Bitmap

        return try {
            withContext(Dispatchers.IO) {
//                val contentUri = MediaStore.Images.Media.getContentUri("external")
//                val uri = ContentUris.withAppendedId(contentUri, imageItem.id)

                val stream = resolver.openOutputStream(item.uri, "rwt")
                stream?.let {
                    if (!data.compress(Bitmap.CompressFormat.JPEG, 100, it)) {
                        throw IOException("Failed to save bitmap.")
                    }
                }

                @Suppress("UNCHECKED_CAST")
                ImageItem(
                    imageItem.id,
                    imageItem.uri,
                    imageItem.displayName,
                    imageItem.dateAdded,
                    data.width,
                    data.height,
                    "image/jpeg",
                    System.currentTimeMillis(),
                    imageItem.sortData
                ) as M
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create a new image as a copy of an existing image.
     * @param data the new image data
     * @param imageItem the data of the original image
     */
    suspend fun <M : MediaItem> createCopy(
        contentResolver: ContentResolver,
        src: Any,
        item: MediaItem
    ): M? {
        val imageItem = item as ImageItem
        val data = src as Bitmap
        return try {
            val name = Regex("\\.[^.]*\$").replace(imageItem.displayName, "")
            @Suppress("UNCHECKED_CAST")
            saveBitmap(contentResolver, data, Bitmap.CompressFormat.JPEG, "image/jpeg", "$name-edit") as M
        } catch (e: IOException) {
            null
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
        resolver: ContentResolver,
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

        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Failed to create new MediaStore record.")
        val dateAdded = System.currentTimeMillis()
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

        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.time = now

        val year = "${calendar.get(Calendar.YEAR)}"
        val month = "${calendar.get(Calendar.MONTH)}-$year"
        val day = "${calendar.get(Calendar.DAY_OF_MONTH)}-$month-$year"

        // Create the image data
        val id = java.lang.Long.parseLong(uri.lastPathSegment!!)
        return ImageItem(
            id,
            uri,
            displayName,
            dateAdded,
            bitmap.width,
            bitmap.height,
            mimeType,
            System.currentTimeMillis(),
            SortData(year, month, day, "Camera")
        )
    }

    /**
     * Based on the current zoom level, insert headers into the media gallery.
     * Headers can be created for years, months or days.
     * @param media: the media list to insert the header items into
     * @param date: the data of the current media item (Image or Video)
     * @param base: the previous date.
     */
    private fun getHeader(date: Date, base: Calendar): HeaderItem? {
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
            val item = HeaderItem(-date.time, date, zoomLevel)
            base.time = cal.time
            return item
        }
        return null
    }
}
