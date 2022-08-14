package com.leondeklerk.starling.media

import android.app.Application
import android.app.PendingIntent
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.leondeklerk.starling.media.data.MediaItem
import com.leondeklerk.starling.media.data.MediaItemTypes
import kotlinx.coroutines.launch

/**
 */
class MediaViewModel(application: Application) : AndroidViewModel(application) {
    // General state
    private val _data = MutableLiveData<MutableList<MediaItem>>()
    val data: LiveData<MutableList<MediaItem>> = _data

    private val _goToId = MutableLiveData<Number?>()
    val goToId: LiveData<Number?> = _goToId

    private var contentObserver: ContentObserver? = null
    private var initialId: Long? = null

    val lockView = MutableLiveData(false)

    // Handle request variables (delete/share/update)
    private val _requestType = MutableLiveData<MediaActionTypes?>()
    val requestType: LiveData<MediaActionTypes?> = _requestType

    var request: PendingIntent? = null

    // Inset/overlay state
    private var _showInsets = MutableLiveData(View.VISIBLE)
    val showInsets: LiveData<Int> = _showInsets

    private var _showOverlay = MutableLiveData<Boolean?>()
    val showOverlay: LiveData<Boolean?> = _showOverlay

    private var _enableEdit = MutableLiveData(true)
    val enableEdit: LiveData<Boolean> = _enableEdit

    var trigger = false

    override fun onCleared() {
        contentObserver?.let {
            getApplication<Application>().contentResolver.unregisterContentObserver(it)
        }
    }

    /**
     * Set the initial id which is the first item that is shown after launching.
     * @param id the id of the initial media item.
     */
    fun setInitial(id: Long?) {
        initialId = id
    }

    /**
     * Set the pending item id to navigate to.
     * @param id the id of the media item to navigate to.
     */
    fun goTo(id: Number?) {
        _goToId.postValue(id)
    }

    /**
     * Register a new ongoing request.
     * @param type the type of the new request
     * @param req the associated intent of the request
     */
    fun request(type: MediaActionTypes?, req: PendingIntent?) {
        request = req
        _requestType.postValue(type)
    }

    /**
     * Manually sync the state.
     * Can be set to not trigger the observer
     * @param visibility the visibility of the insets and overlay
     * @param
     */
    fun setInsets(visibility: Int, triggerObserver: Boolean) {
        trigger = triggerObserver
        _showInsets.postValue(visibility)
    }

    /**
     * Set if the overlay should be shown or not.
     * @param show the boolean value indicating the state
     */
    fun showOverlay(show: Boolean) {
        if (show) {
            if (_showInsets.value == View.VISIBLE) {
                _showOverlay.postValue(show)
            }
        } else {
            _showOverlay.postValue(show)
        }
    }

    /**
     * Switch the insets from Visible to Gone or vice versa.
     */
    fun toggleInsets() {
        trigger = true

        if (_showInsets.value == View.GONE) {
            _showInsets.postValue(View.VISIBLE)
        } else {
            _showInsets.postValue(View.GONE)
        }
    }

    /**
     * Disable or enable the edit button.
     * @param enabled if the button should be enabled or not.
     */
    fun setEditEnabled(enabled: Boolean) {
        _enableEdit.postValue(enabled)
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
            val mediaList = retriever.queryMedia(resolver, projection, selection, selectionArgs, sortOrder)
            _data.postValue(mediaList.filter { it.type == MediaItemTypes.IMAGE || it.type == MediaItemTypes.VIDEO }.toMutableList())

            if (initialId != null) {
                _goToId.postValue(initialId)
                initialId = null
            }

            // To observer any additional files created on the system, a observer is registered.
            if (contentObserver == null) {
                contentObserver = getApplication<Application>().contentResolver.registerObserver(
                    MediaStore.Files.getContentUri("external")
                ) {
                    // Upon a detected change it reloads the media.
                    loadMedia()
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
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH
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
