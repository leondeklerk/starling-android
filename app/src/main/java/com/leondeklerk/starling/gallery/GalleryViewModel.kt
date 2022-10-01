package com.leondeklerk.starling.gallery

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.leondeklerk.starling.PermissionViewModel

/**
 * Basic [ViewModel] that handles the data of a [GalleryFragment].
 * This handles the current value of the rationale text and button text, for the permissions.
 * Its main functionality is to provide the data for the adapter.
 * Uses [MutableLiveData] and [LiveData] to store and provide data that is kept up to date.
 */
class GalleryViewModel(application: Application) : PermissionViewModel(application) {

    fun addHeaders() {}
}
