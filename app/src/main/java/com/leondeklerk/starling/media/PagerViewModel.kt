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
    private val _overlayInsetState = MutableLiveData(View.VISIBLE)
    val overlayInsetState: LiveData<Int> = _overlayInsetState.distinctUntilChanged()

    private val _overlayBarState = MutableLiveData(View.VISIBLE)
    val overlayBarState: LiveData<Int> = _overlayBarState.distinctUntilChanged()

    private var showOverlayBar = true

    val overlayBarAlpha: Float
        get() {
            return if (_overlayBarState.value == View.VISIBLE) {
                1f
            } else {
                0f
            }
        }

    private var _enableEdit = MutableLiveData(true)
    val enableEdit: LiveData<Boolean> = _enableEdit

    // Transition state
    var rect = Rect()

    var initial = true

    var containerSize = Pair(0, 0)

    // Dismiss state
    private var beforeDismissState = false
    private var beforeDismissOverlayState = View.GONE
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

    fun setBarState(state: Int) {
        if (showOverlayBar) {
            _overlayBarState.postValue(state)
        } else {
            _overlayBarState.postValue(View.GONE)
        }
    }

    /**
     * Set the current state of the insets.
     * @param state either View.VISIBLE or View.GONE
     */
    fun setInsetState(state: Int) {
        _overlayInsetState.postValue(state)
        setBarState(state)
    }

    /**
     * Set the pending item id to navigate to.
     * @param id the id of the media item to navigate to.
     */
    fun goTo(id: Number?) {
        _goToId.postValue(id)
    }

    fun setOverlayMode(showBars: Boolean) {
        showOverlayBar = showBars
        if (!showBars) {
            setBarState(View.GONE)
        }
    }

    fun showOverlay() {
        setInsetState(View.VISIBLE)
        setBarState(View.VISIBLE)
    }

    fun hideOverlay() {
        setInsetState(View.GONE)
        setBarState(View.GONE)
    }

    /**
     * Start a dismiss, hides the overlay and stores the current state.
     */
    fun onDismissStart() {
        dismissing = true
        showOverlay()
    }

    /**
     * Cancels a started dismiss, restores the before dismiss state.
     */
    fun onDismissCancel() {
        dismissing = false
    }

    /**
     * Toggle between overlay states.
     */
    fun toggleOverlay() {
        if (!dismissing) {
            when (_overlayInsetState.value) {
                View.GONE -> showOverlay()
                View.VISIBLE -> hideOverlay()
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
