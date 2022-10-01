package com.leondeklerk.starling.media

import android.app.Application
import android.graphics.Rect
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged

/**
 */
class PagerViewModel(application: Application) : AndroidViewModel(application) {
    // General state
    private val _goToId = MutableLiveData<Number?>()
    val goToId: LiveData<Number?> = _goToId

    private val _action = MutableLiveData<Pair<Long, MediaActionTypes>?>()
    val action: LiveData<Pair<Long, MediaActionTypes>?> = _action

    val lockView = MutableLiveData(false)

    // Inset/overlay state
    private var _showOverlay = MutableLiveData<Boolean?>()
    val showOverlay: LiveData<Boolean?> = _showOverlay.distinctUntilChanged()

    private val _insetState = MutableLiveData<Int>()
    val insetState: LiveData<Int> = _insetState.distinctUntilChanged()

    private var _enableEdit = MutableLiveData(true)
    val enableEdit: LiveData<Boolean> = _enableEdit

    var onlyOverlay = false

    // Transition state
    var rect = Rect()

    var initial = true

    var containerSize = Pair(0, 0)

    private var beforeDismissState = false

    private var _touchCaptured = MutableLiveData(false)
    val touchCaptured: LiveData<Boolean> = _touchCaptured

    fun captureTouch() {
        _touchCaptured.postValue(true)
    }

    fun releaseTouch() {
        _touchCaptured.postValue(false)
    }

    /**
     * Set the current state of the insets.
     * @param state either View.VISIBLE or View.GONE
     */
    fun setInsetState(state: Int) {
        _insetState.postValue(state)
    }

    /**
     * Set the pending item id to navigate to.
     * @param id the id of the media item to navigate to.
     */
    fun goTo(id: Number?) {
        _goToId.postValue(id)
    }

    fun resetOverlay() {
        _showOverlay.postValue(null)
        _insetState.postValue(View.VISIBLE)
        onlyOverlay = false
    }

    fun onDismissStart() {
        onlyOverlay = true
        beforeDismissState = _showOverlay.value ?: true
        _showOverlay.postValue(false)
    }

    fun onDismissCancel() {
        if (beforeDismissState) {
            _showOverlay.postValue(true)
        }
    }

    fun setOverlay(state: Int, showOnlyOverlay: Boolean = false) {
        if (!initial) {
            onlyOverlay = showOnlyOverlay
            if (state == View.VISIBLE && _showOverlay.value != true) {
                _showOverlay.postValue(true)
            } else if (state == View.GONE && _showOverlay.value != false) {
                _showOverlay.postValue(false)
            }
        }
    }

    fun toggleOverlay() {
        onlyOverlay = false
        if (_showOverlay.value == true) {
            _showOverlay.postValue(false)
        } else {
            _showOverlay.postValue(true)
        }
    }

    /**
     * Disable or enable the edit button.
     * @param enabled if the button should be enabled or not.
     */
    fun setEditEnabled(enabled: Boolean) {
        _enableEdit.postValue(enabled)
    }

    fun setAction(id: Long, action: MediaActionTypes) {
        _action.postValue(Pair(id, action))
    }

    fun clearAction() {
        _action.postValue(null)
    }
}
