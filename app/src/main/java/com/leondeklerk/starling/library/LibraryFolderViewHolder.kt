package com.leondeklerk.starling.library

import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.fetch.VideoFrameUriFetcher
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import coil.transform.RoundedCornersTransformation
import com.leondeklerk.starling.data.FolderItem
import com.leondeklerk.starling.databinding.LibraryFolderViewBinding

class LibraryFolderViewHolder private constructor(
    private val binding: LibraryFolderViewBinding,
    private val onClick: ((FolderItem) -> Unit)
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: FolderItem) {
        val folderThumb = binding.folderThumb

        binding.item = item

        binding.folderItem.setOnClickListener {
            onClick(item)
        }

        if (item.type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            val request = ImageRequest.Builder(folderThumb.context)
                .fetcher(VideoFrameUriFetcher(folderThumb.context))
                .transformations(RoundedCornersTransformation(32f))
                .data(item.thumbnailUri).target(folderThumb).build()
            folderThumb.context.imageLoader.enqueue(request)
        } else {
            folderThumb.load(item.thumbnailUri) {
                transformations(RoundedCornersTransformation(32f))
            }
        }

        binding.executePendingBindings()
    }

    companion object {
        fun from(parent: ViewGroup, onClick: ((FolderItem) -> Unit)): LibraryFolderViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = LibraryFolderViewBinding.inflate(layoutInflater, parent, false)
            return LibraryFolderViewHolder(binding, onClick)
        }
    }
}
