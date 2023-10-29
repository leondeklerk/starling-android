package com.leondeklerk.starling.media

import android.app.Application
import android.content.ContentResolver
import android.database.ContentObserver
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import coil.memory.MemoryCache
import com.leondeklerk.starling.media.data.DataSource
import com.leondeklerk.starling.media.data.FolderItem
import com.leondeklerk.starling.media.data.HeaderItem
import com.leondeklerk.starling.media.data.ImageItem
import com.leondeklerk.starling.media.data.MediaItem
import com.leondeklerk.starling.media.data.MediaItemTypes
import com.leondeklerk.starling.media.data.VideoItem
import java.util.Date
import kotlinx.coroutines.launch

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private var contentObserver: ContentObserver? = null

    private val _data = MutableLiveData<MutableList<MediaItem>>()
    val data: LiveData<MutableList<MediaItem>> = _data

    private var _activeList: List<MediaItem> = listOf()
    val activeList: LiveData<List<MediaItem>>
        get() {
            return MutableLiveData(_activeList)
        }

    private val _gallery = MutableLiveData<MutableList<MediaItem>>()
    val gallery: LiveData<MutableList<MediaItem>> = _gallery

    private val _folder = MutableLiveData<List<MediaItem>?>()
    val folder: LiveData<List<MediaItem>?> = _folder

    private val _folders = MutableLiveData<List<FolderItem>>()
    val folders: LiveData<List<FolderItem>> = _folders

    private val folderLists = MutableLiveData<Map<String, List<MediaItem>>>()

    private val idIndexMappings = MutableLiveData<Map<Long, Int>>()

    private val folderData = MutableLiveData<List<MediaItem>?>()

    var position = 0
    var galleryPosition = 0

    val scroll = MutableLiveData(false)

    private var folderGallery = false

    private var rect = Rect()

    var onRect: ((rect: Rect) -> Unit)? = null

    var cacheKey: MemoryCache.Key? = null

    override fun onCleared() {
        contentObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
    }

    fun getMappedIndex(id: Long): Int {
        return idIndexMappings.value?.get(id) ?: 0
    }

    fun setRect(rectangle: Rect) {
        rect = rectangle
        onRect?.invoke(rect)
    }

    //
    fun setPositionPager(index: Int) {
//        val index = if (folderGallery) {
//            _folder.value!!.indexOfFirst { it.id == item.id }
//        } else {
//            _gallery.value!!.indexOfFirst { it.id == item.id }
//        }

        position = index // if (index == -1) {
//            0
//        } else {
//            index
//        }
    }

    fun setPositionGallery(index: Int) {
//        galleryPosition = index
//        val result = _activeList.indexOfFirst {
//            it.id == item.id
//        }
        position = index // = if (result == -1) {
//            0
//        } else {
//            result
//        }
    }

    fun setActive(folder: Boolean) {
//        folderGallery = folder
//        if (folder) {
//            _activeList = folderData.value!!
//        } else {
//            _activeList = _data.value!!
//        }
    }

    /**
     * Function used to start loading in the media.
     * This is only called after all proper permissions are granted.
     * Loads in all images and videos from the [MediaStore] using coroutines.
     */
    fun loadMedia() {
        viewModelScope.launch {
            // Create the query parameters
            val projection = createProjection()
            val selection = createSelection()
            val selectionArgs = createSelectionArgs()
            val sortOrder = createSortOrder()

            // Create the image retriever
            val retriever = MediaInterface()
            val resolver = getApplication<Application>().contentResolver

            // Start querying media.
            val list = DataSource.list
            val mediaList = retriever.queryMedia(resolver, projection, selection, selectionArgs, sortOrder)
//            _data.postValue(mediaList.data)
//            _folders.postValue(mediaList.folders.map { (_, value) -> value })

            _gallery.postValue(mediaList.data)
//            folderLists.postValue(createFolders(mediaList.data))
//            createFolde
//            createFolderGallery(mediaList.data, mediaList.folders)
//            _gallery.postValue(mediaList.galleryData)
//            folderLists.postValue(mediaList.folderData)
//            idIndexMappings.postValue(mediaList.idIndexMappings)

//            setFolders(mediaList.folders)

            // To observer any additional files created on the system, a observer is registered.
//            if (contentObserver == null) {
//                contentObserver = getApplication<Application>().contentResolver.registerObserver(
//                    MediaStore.Files.getContentUri("external")
//                ) {
//                    // Upon a detected change it reloads the media.
//                    loadMedia()
//                }
//            }
        }
    }

    private fun createGallery(media: List<MediaItem>): MutableList<MediaItem> {
        val dateMap = media.groupBy(keySelector = { item ->

            when (item.type) {
                MediaItemTypes.VIDEO -> {
                    (item as VideoItem).sortData.day
                }
                MediaItemTypes.IMAGE -> {
                    (item as ImageItem).sortData.day
                }
                else -> {
                    ""
                }
            }
        }, { item ->

            item
        })

        val gallery = mutableListOf<MediaItem>()
        dateMap.keys.forEachIndexed { index, key ->
            val first = dateMap[key]?.get(0)
            first?.let {
                val list = dateMap[key]?.toList()!!
                gallery.add(HeaderItem(-first.id, Date(first.dateAdded), 2))
                gallery.addAll(list)
            }
        }

        return gallery
    }

    private fun createFolders(media: List<MediaItem>): Map<String, List<MediaItem>> {
        return media.groupBy { item ->
            when (item.type) {
                MediaItemTypes.VIDEO -> {
                    (item as VideoItem).sortData.folder
                }
                MediaItemTypes.IMAGE -> {
                    (item as ImageItem).sortData.folder
                }
                else -> {
                    ""
                }
            }
        }
    }

    fun createFolderGallery(name: String) {
        _folder.postValue(null)
        folderLists.value?.get(name)?.let {
            folderData.postValue(it)
            _folder.postValue(createGallery(it))
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
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.BUCKET_ID,
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
