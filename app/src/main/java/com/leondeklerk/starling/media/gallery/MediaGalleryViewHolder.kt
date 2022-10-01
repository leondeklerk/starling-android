package com.leondeklerk.starling.media.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import coil.result
import com.leondeklerk.starling.databinding.GalleryHeaderViewBinding
import com.leondeklerk.starling.databinding.GalleryImageViewBinding
import com.leondeklerk.starling.databinding.GalleryVideoViewBinding
import com.leondeklerk.starling.databinding.LibraryFolderViewBinding
import com.leondeklerk.starling.library.LibraryFragment
import com.leondeklerk.starling.media.data.FolderItem
import com.leondeklerk.starling.media.data.HeaderItem
import com.leondeklerk.starling.media.data.ImageItem
import com.leondeklerk.starling.media.data.MediaItem
import com.leondeklerk.starling.media.data.VideoItem

/**
 * Generic [RecyclerView.ViewHolder] class to display [MediaItem]s
 * Implementations need to create two separate functions:
 * The bind function to bind a [MediaItem] to the view,
 * and a companion function from that acts as a constructor for the class.
 */
abstract class MediaItemViewHolder(binding: ViewDataBinding) :
    RecyclerView.ViewHolder(binding.root) {

    /**
     * Bind the [MediaItem] to the [android.view] instance.
     */
    abstract fun bind(item: MediaItem)
}

/**
 * An [MediaItemViewHolder] implementation that binds a [VideoItem] and inflates the associated layout.
 * This is used to display media items of the type video.
 */
class HeaderItemViewHolder private constructor(private val binding: GalleryHeaderViewBinding) :
    MediaItemViewHolder(binding) {

    /**
     * Bind a [MediaItem] to the layout using data binding
     * @param item: the gallery item that should be displayed in this view.
     */
    override fun bind(item: MediaItem) {
        binding.item = (item as HeaderItem)
        binding.executePendingBindings()
    }

    companion object {
        /**
         * Static function to create a new [HeaderItemViewHolder] and make sure its layout is inflated.
         * @param parent: The [ViewGroup] the context can be retrieved from
         * @return A [HeaderItemViewHolder] instance that can be populated with data.
         */
        fun from(parent: ViewGroup): HeaderItemViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = GalleryHeaderViewBinding.inflate(layoutInflater, parent, false)
            return HeaderItemViewHolder(binding)
        }
    }
}

/**
 * An [MediaItemViewHolder] implementation that binds a [VideoItem] and inflates the associated layout.
 * This is used to display media items of the type video.
 */
class VideoItemViewHolder private constructor(
    private val binding: GalleryVideoViewBinding,
    private val onClick: ((MediaItem, View, Int) -> Unit)
) : MediaItemViewHolder(binding) {

    /**
     * Bind a [VideoItem] to the layout using data binding
     * @param item: the video item that should be displayed in this view.
     */
    override fun bind(item: MediaItem) {
        val imageView = binding.imageView

        // Bind the variable
        binding.item = item as VideoItem

        imageView.setOnClickListener {
            onClick.invoke(item, imageView, absoluteAdapterPosition)
//            imageView.visibility = View.GONE
        }

        binding.executePendingBindings()
    }

    companion object {
        /**
         * Static function to create a new [VideoItemViewHolder] and make sure its layout is inflated.
         * @param parent: The [ViewGroup] the context can be retrieved from
         * @return A [VideoItemViewHolder] instance that can be populated with data.
         */
        fun from(parent: ViewGroup, onClick: ((MediaItem, View, Int) -> Unit)): VideoItemViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = GalleryVideoViewBinding.inflate(layoutInflater, parent, false)
            return VideoItemViewHolder(binding, onClick)
        }
    }
}

/**
 * An [MediaItemViewHolder] implementation that binds a [ImageItem] and inflates the associated layout.
 * This is used to display media items of the type image.
 */
class ImageItemViewHolder private constructor(
    private val binding: GalleryImageViewBinding,
    private val onClick: ((MediaItem, ImageView, Int) -> Unit)
) : MediaItemViewHolder(binding) {

    /**
     * Bind a [ImageItem] to the layout using data binding
     * @param item: the image item that should be displayed in this view.
     */
    override fun bind(item: MediaItem) {
        val imageView = binding.imageView

        // Inject variable
        binding.item = item
        binding.imageView.transitionName = "${item.id}"

        imageView.setOnClickListener {
            val test = imageView.result?.request?.memoryCacheKey
            onClick.invoke(item, imageView, absoluteAdapterPosition)
//            it.visibility = View.GONE
        }

        binding.executePendingBindings()
    }

    companion object {
        /**
         * Static function to create a new [ImageItemViewHolder] and make sure its layout is inflated.
         * @param parent: The [ViewGroup] the context can be retrieved from
         * @return A [ImageItemViewHolder] instance that can be populated with data.
         */
        fun from(parent: ViewGroup, onClick: ((MediaItem, ImageView, Int) -> Unit)): ImageItemViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = GalleryImageViewBinding.inflate(layoutInflater, parent, false)
            return ImageItemViewHolder(binding, onClick)
        }
    }
}

/**
 * [RecyclerView.ViewHolder] class to display [FolderItem]s in a [LibraryFragment]
 * The bind function to bind a [FolderItem] to the view,
 * and a companion function from that acts as a constructor for the class.
 * @param binding: the LibraryFolderViewBinding containing the layout
 * @param onClick: the on click listener set in the [LibraryFragment], executed on item click
 */
class FolderItemViewHolder private constructor(
    private val binding: LibraryFolderViewBinding,
    private val onClick: ((MediaItem, View, Int) -> Unit)
) : MediaItemViewHolder(binding) {

    /**
     * Bind a [FolderItem] to the actual view.
     * Also responsible for registering the on click listener
     * @param item: A folder item containing the folder data
     */
    override fun bind(item: MediaItem) {
        binding.item = item as FolderItem

        // Register the click listener
        binding.folderItem.setOnClickListener {
            onClick(item, it, absoluteAdapterPosition)
        }

        binding.executePendingBindings()
    }

    companion object {
        /**
         * Static constructor to build a viewHolder instance.
         * Inflates the layout and sets up the binding.
         * @param parent: The [ViewGroup] the context can be retrieved from
         * @param onClick: the on click function that should be executed on item click
         * @return A [FolderItemViewHolder] instance that can be populated with data.
         */
        fun from(parent: ViewGroup, onClick: ((MediaItem, View, Int) -> Unit)): FolderItemViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = LibraryFolderViewBinding.inflate(layoutInflater, parent, false)
            return FolderItemViewHolder(binding, onClick)
        }
    }
}