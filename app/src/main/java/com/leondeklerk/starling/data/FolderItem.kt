package com.leondeklerk.starling.data

import android.net.Uri
import android.os.Parcelable
import androidx.recyclerview.widget.DiffUtil
import java.util.Objects
import kotlinx.parcelize.Parcelize

@Parcelize
data class FolderItem(val id: Long, val name: String, val thumbnailUri: Uri, val type: Int) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as FolderItem
        return name == that.name && id == that.id
    }

    override fun hashCode(): Int {
        return Objects.hash(name, id)
    }

    override fun toString(): String {
        return name
    }

    companion object {
        // Handles the DiffUtil callback for folder items.
        val DiffCallback = object : DiffUtil.ItemCallback<FolderItem>() {
            override fun areItemsTheSame(oldItem: FolderItem, newItem: FolderItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: FolderItem, newItem: FolderItem) =
                oldItem == newItem
        }
    }
}
