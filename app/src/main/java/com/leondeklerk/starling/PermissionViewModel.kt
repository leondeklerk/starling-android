package com.leondeklerk.starling

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 */
open class PermissionViewModel(application: Application) : AndroidViewModel(application) {
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
