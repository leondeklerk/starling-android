package com.leondeklerk.starling.library

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.leondeklerk.starling.data.FolderItem

class LibraryAdapter : ListAdapter<FolderItem, LibraryFolderViewHolder>(FolderItem.DiffCallback) {
    lateinit var onFolderClick: ((FolderItem) -> Unit)

    override fun onBindViewHolder(holder: LibraryFolderViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryFolderViewHolder {
        return LibraryFolderViewHolder.from(parent, onFolderClick)
    }
}
