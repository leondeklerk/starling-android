package com.leondeklerk.starling.media

import androidx.recyclerview.widget.AsyncListDiffer
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.leondeklerk.starling.media.data.MediaItem

class MediaFragmentAdapter(
    activity: PagerActivity,
//    private val enterListeners: PagerFragment.TransitionListeners?,
//    private val exitListeners: PagerFragment.TransitionListeners?
) : FragmentStateAdapter(activity) {
    private val differ = AsyncListDiffer(this, MediaItem.DiffCallback)

    override fun createFragment(position: Int): MediaItemFragment {
        return MediaItemFragment.createFragment(differ.currentList[position]) // , enterListeners, exitListeners)
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun getItemId(position: Int): Long {
        return differ.currentList[position].id
    }

    override fun containsItem(itemId: Long): Boolean {
        return differ.currentList.any { it.id == itemId }
    }

    override fun getItemViewType(position: Int): Int {
        val item = differ.currentList[position]
        return item.type.ordinal
    }

    /**
     * Get the index of an item based on the item id.
     * @param id the id of the item to retrieve the index of
     * @return the index of the item
     */
    fun getItemIndex(id: Long): Int {
        return differ.currentList.indexOfFirst { it.id == id }
    }

    /**
     * Get an item from the list based on a given position.
     * @param position the position of the item to retrieve
     */
    fun getItem(position: Int): MediaItem {
        return differ.currentList[position]
    }

    /**
     * Add a new or updated list to the adapter
     * @param list the new list to update to.
     */
    fun submitList(list: List<MediaItem>) {
        differ.submitList(list)
    }
}
