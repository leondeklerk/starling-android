package com.leondeklerk.starling.gallery.ui

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.leondeklerk.starling.R
import com.leondeklerk.starling.gallery.data.GalleryItem

/**
 * Basic [ViewModel] that handles the data of a [GalleryFragment].
 * This handles the current value of the rationale text and button text, for the permissions.
 * Its main functionality is to provide the data for the adapter.
 * TODO: Implement MediaStore and Glide to display local images (and later cloud synced images)
 * Uses [MutableLiveData] and [LiveData] to store and provide data that is kept up to date.
 */
class GalleryViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is gallery fragment"
    }
    val text: LiveData<String> = _text

    private val _data = MutableLiveData<List<GalleryItem>>().apply {
        value = listOf(
            GalleryItem(1, "name1", Uri.parse("http://test.com")),
            GalleryItem(2, "name2", Uri.parse("http://test.com"))
        )
    }

    val data: LiveData<List<GalleryItem>> = _data

    private val _permissionTextId = MutableLiveData<Int>().apply {
        value = R.string.permission_rationale
    }

    val permissionTextId: LiveData<Int> = _permissionTextId

    private val _permissionButtonTextId = MutableLiveData<Int>().apply {
        value = R.string.permission_button
    }

    val permissionButtonTextId: LiveData<Int> = _permissionButtonTextId

    /**
     * Updates the rationale for the storage permission. If possible this will show the regular rationale.
     * If a user has denied the permission twice (android 11+) or click "don't ask again", a settings rationale will be shown instead.
     * @param showSettings: A [Boolean] that indicates if this is a regular or a settings permission rationale.
     */
    fun updateRationale(showSettings: Boolean) {
        if (showSettings) {
            // Set all resource ids to setting resource ids
            _permissionTextId.value = R.string.permission_rationale_settings
            _permissionButtonTextId.value = R.string.permission_button_settings
        } else {
            // Set all ids to regular rationale ids
            _permissionTextId.value = R.string.permission_rationale
            _permissionButtonTextId.value = R.string.permission_button
        }
    }
}
