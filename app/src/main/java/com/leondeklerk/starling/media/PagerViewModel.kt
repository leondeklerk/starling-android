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

    // Dismiss state
    private var beforeDismissState = false
    private var dismissing = false

    // Container state
    private var _touchCaptured = MutableLiveData(false)
    val touchCaptured: LiveData<Boolean> = _touchCaptured

    /**
     * The current fragment is capturing touch
     */
    fun captureTouch() {
        _touchCaptured.postValue(true)
    }

    /**
     * The current fragment releases its touch capture
     */
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

    /**
     * Reset the overlay variables to an initial state.
     */
    fun resetOverlay() {
        _showOverlay.postValue(null)
        _insetState.postValue(View.VISIBLE)
        onlyOverlay = false
        dismissing = false
    }

    /**
     * Start a dismiss, hides the overlay and stores the current state.
     */
    fun onDismissStart() {
        dismissing = true
        onlyOverlay = true
        beforeDismissState = _showOverlay.value ?: true
        _showOverlay.postValue(false)
    }

    /**
     * Cancels a started dismiss, restores the before dismiss state.
     */
    fun onDismissCancel() {
        dismissing = false
        if (beforeDismissState) {
            _showOverlay.postValue(true)
        }
    }

    /**
     * Set the current overlay state (both overlay and insets) based on a visibility value.
     * @param state the desired inset state (View.VISIBLE or View.GONE)
     * @param showOnlyOverlay variable that determines if the insets should also be updated or just the overlay itself.
     */
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

    /**
     * Toggle between overlay states.
     */
    fun toggleOverlay() {
        if (!dismissing) {
            onlyOverlay = false
            if (_showOverlay.value == true) {
                _showOverlay.postValue(false)
            } else {
                _showOverlay.postValue(true)
            }
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
     * Set the current active action and id that invoked it.
     * @param id id of the fragment that invoked the action.
     * @param action the current actionType
     */
    fun setAction(id: Long, action: MediaActionTypes) {
        _action.postValue(Pair(id, action))
    }

    /**
     * Mark an action as done (clear it)
     */
    fun clearAction() {
        _action.postValue(null)
    }
}
