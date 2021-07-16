package com.leondeklerk.starling.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.leondeklerk.starling.data.FolderItem
import com.leondeklerk.starling.databinding.LibraryFolderViewBinding

/**
 * [RecyclerView.ViewHolder] class to display [FolderItem]s in a [LibraryFragment]
 * The bind function to bind a [FolderItem] to the view,
 * and a companion function from that acts as a constructor for the class.
 */
class LibraryFolderViewHolder private constructor(
    private val binding: LibraryFolderViewBinding,
    private val onClick: ((FolderItem) -> Unit)
) : RecyclerView.ViewHolder(binding.root) {

    /**
     * Bind a [FolderItem] to the actual view.
     * Also responsible for registering the on click listener
     * @param item: A folder item containing the folder data
     */
    fun bind(item: FolderItem) {
        binding.item = item

        // Register the click listener
        binding.folderItem.setOnClickListener {
            onClick(item)
        }

        binding.executePendingBindings()
    }

    companion object {
        /**
         * Static constructor to build a viewHolder instance.
         * Inflates the layout and sets up the binding.
         * @param parent: The [ViewGroup] the context can be retrieved from
         * @param onClick: the on click function that should be executed on item click
         * @return A [LibraryFolderViewHolder] instance that can be populated with data.
         */
        fun from(parent: ViewGroup, onClick: ((FolderItem) -> Unit)): LibraryFolderViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = LibraryFolderViewBinding.inflate(layoutInflater, parent, false)
            return LibraryFolderViewHolder(binding, onClick)
        }
    }
}
