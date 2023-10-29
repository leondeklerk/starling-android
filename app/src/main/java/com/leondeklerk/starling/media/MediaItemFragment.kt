package com.leondeklerk.starling.media

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.transition.ChangeBounds
import androidx.transition.ChangeClipBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.ChangeTransform
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.leondeklerk.starling.R
import com.leondeklerk.starling.media.data.ImageItem
import com.leondeklerk.starling.media.data.MediaItem
import com.leondeklerk.starling.media.data.MediaItemTypes
import com.leondeklerk.starling.media.data.VideoItem
import timber.log.Timber

abstract class MediaItemFragment : Fragment() {
    protected abstract val viewModel: MediaItemViewModel<out MediaItem, out Any>
    protected val pagerViewModel: PagerViewModel by activityViewModels()

    private lateinit var deleteLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var updateLauncher: ActivityResultLauncher<IntentSenderRequest>

    /**
     * Delete the respective media item.
     */
    abstract fun delete()

    /**
     * Start an edit of the respective media item
     */
    abstract fun edit()

    /**
     * Callback when a delete is successfully executed
     */
    abstract fun isDeleted(success: Boolean)

    /**
     * Callback when an edit is successfully saved
     */
    abstract fun isSaved(success: Boolean)

    abstract fun translate(dX: Float, dY: Float)

    abstract fun scale(scalarX: Float, scalarY: Float)

    abstract fun reset()

    abstract fun close(target: Rect, duration: Long)

    abstract fun sharedView(): View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        registerLaunchers()

        // Register the listener for the overlay actions
        pagerViewModel.action.observe(viewLifecycleOwner) {
            viewModel.getAction(it)?.let { action ->
                when (action) {
                    MediaActionTypes.DELETE -> delete()
                    MediaActionTypes.EDIT -> edit()
                    MediaActionTypes.SHARE -> share()
                    else -> {}
                }
                pagerViewModel.clearAction()
            }
        }

        viewModel.pendingRequestType.observe(viewLifecycleOwner) {
            request(it, viewModel.pendingRequest)
        }

        viewModel.finishedRequestType.observe(viewLifecycleOwner) {
            it?.let {
                when (it) {
                    MediaActionTypes.EDIT -> isSaved(viewModel.isSuccess)
                    MediaActionTypes.DELETE -> isDeleted(viewModel.isSuccess)
                    else -> {}
                }
            }
        }

        viewModel.nextId.observe(viewLifecycleOwner) {
            pagerViewModel.goTo(it)
        }
    }

    protected fun transition(
        duration: Long,
        container: ViewGroup,
//        onStart: (transition: Transition) -> Unit,
//        onEnd: (transition: Transition) -> Unit
    ) {
        val transition = TransitionSet()
        transition.addTransition(ChangeClipBounds())
            .setInterpolator(DecelerateInterpolator())
            .addTransition(ChangeTransform())
            .addTransition(ChangeBounds())
            .addTransition(ChangeImageTransform())
            .setOrdering(TransitionSet.ORDERING_TOGETHER)
//            .addListener(onTransitionStart = onStart, onTransitionEnd = onEnd)
            .setDuration(duration)
        TransitionManager.beginDelayedTransition(container, transition)
    }

    /**
     * Create and register the activity result launchers for the delete and update permission.
     */
    private fun registerLaunchers() {
        // Create an ActivityResult handler for the permission popups on android Q and up.
        deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                viewModel.grantRequest(MediaActionTypes.DELETE)
            } else {
                viewModel.grantRequest(MediaActionTypes.CANCEL)
            }
        }

        updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                viewModel.grantRequest(MediaActionTypes.EDIT)
            } else {
                viewModel.grantRequest(MediaActionTypes.CANCEL)
            }
        }
    }

    /**
     * An media action that requires additional permission was invoked,
     * therefore the user needs to be explicitly asked for permission.
     * @param type the action to request permissions for
     * @param request the pending intent to use for the result launcher.
     */
    private fun request(type: MediaActionTypes?, request: PendingIntent?) {
        type?.let { action ->
            request?.let { req ->
                when (action) {
                    MediaActionTypes.DELETE -> deleteLauncher.launch(IntentSenderRequest.Builder(req).build())
                    MediaActionTypes.EDIT -> updateLauncher.launch(IntentSenderRequest.Builder(req).build())
                    else -> {}
                }
            }
        }
    }

    private fun share() {
        viewModel.share()?.let {
            try {
                startActivity(Intent.createChooser(it, getString(R.string.media_share)))
                return
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        Toast.makeText(context, R.string.media_share_error, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun createFragment(
            item: MediaItem,
//            enterListeners: PagerFragment.TransitionListeners?,
//            exitListeners: PagerFragment.TransitionListener?
        ): MediaItemFragment {
            return when (item.type) {
                MediaItemTypes.IMAGE -> ImageFragment(item as ImageItem) // , enterListeners, exitListeners)
                MediaItemTypes.VIDEO -> VideoFragment(item as VideoItem) // , enterListeners, exitListeners)
                else -> throw NotImplementedError()
            }
        }
    }
}
