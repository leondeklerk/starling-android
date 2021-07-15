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
        val folderThumb = binding.folderThumb

        binding.item = item

        // Register the click listener
        binding.folderItem.setOnClickListener {
            onClick(item)
        }

        // If the folder thumbnail should be made from a video, use the coil IamgeLoader
        if (item.type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            val request = ImageRequest.Builder(folderThumb.context)
                .fetcher(VideoFrameUriFetcher(folderThumb.context))
                .transformations(RoundedCornersTransformation(32f))
                .data(item.thumbnailUri).target(folderThumb).build()
            folderThumb.context.imageLoader.enqueue(request)
        } else {
            // Otherwise just load the URI
            folderThumb.load(item.thumbnailUri) {
                transformations(RoundedCornersTransformation(32f))
            }
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
