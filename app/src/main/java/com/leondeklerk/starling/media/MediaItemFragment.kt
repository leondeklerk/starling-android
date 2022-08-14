package com.leondeklerk.starling.media

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.leondeklerk.starling.media.data.ImageItem
import com.leondeklerk.starling.media.data.MediaItem
import com.leondeklerk.starling.media.data.MediaItemTypes
import com.leondeklerk.starling.media.data.VideoItem

abstract class MediaItemFragment : Fragment() {
    protected abstract val viewModel: MediaItemViewModel<out MediaItem, out Any>
    protected val activityViewModel: MediaViewModel by activityViewModels()

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.pendingRequestType.observe(viewLifecycleOwner) {
            activityViewModel.request(it, viewModel.pendingRequest)
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
            activityViewModel.goTo(it)
        }
    }

    /**
     * Grant a pending request to this fragment.
     * @param type the type of request that is granted.
     */
    fun grantRequest(type: MediaActionTypes) {
        viewModel.onRequestGranted(type)
    }

    companion object {
        fun createFragment(item: MediaItem): MediaItemFragment {
            return when (item.type) {
                MediaItemTypes.IMAGE -> ImageFragment(item as ImageItem)
                MediaItemTypes.VIDEO -> VideoFragment(item as VideoItem)
                else -> throw NotImplementedError()
            }
        }
    }
}
