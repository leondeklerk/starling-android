package com.leondeklerk.starling.gallery.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.leondeklerk.starling.data.HeaderItem
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.data.MediaItem
import com.leondeklerk.starling.data.VideoItem
import com.leondeklerk.starling.databinding.GalleryHeaderViewBinding
import com.leondeklerk.starling.databinding.GalleryImageViewBinding
import com.leondeklerk.starling.databinding.GalleryVideoViewBinding

/**
 * Generic [RecyclerView.ViewHolder] class to display [MediaItem]s in a [GalleryFragment]
 * Implementations need to create two separate functions:
 * The bind function to bind a [MediaItem] to the view,
 * and a companion function from that acts as a constructor for the class.
 */
abstract class GalleryItemViewHolder(binding: ViewDataBinding) :
    RecyclerView.ViewHolder(binding.root) {

    /**
     * Bind the [MediaItem] to the [android.view] instance.
     */
    abstract fun bind(item: MediaItem)
}

/**
 * An [GalleryItemViewHolder] implementation that binds a [VideoItem] and inflates the associated layout.
 * This is used to display media items of the type video.
 */
class GalleryHeaderViewHolder private constructor(private val binding: GalleryHeaderViewBinding) :
    GalleryItemViewHolder(binding) {

    /**
     * Bind a [MediaItem] to the layout using databinding
     * @param item: the gallery item that should be displayed in this view.
     */
    override fun bind(item: MediaItem) {
        binding.item = (item as HeaderItem)
        binding.executePendingBindings()
    }

    companion object {
        /**
         * Static function to create a new [GalleryHeaderViewHolder] and make sure its layout is inflated.
         * @param parent: The [ViewGroup] the context can be retrieved from
         * @return A [GalleryHeaderViewHolder] instance that can be populated with data.
         */
        fun from(parent: ViewGroup): GalleryHeaderViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = GalleryHeaderViewBinding.inflate(layoutInflater, parent, false)
            return GalleryHeaderViewHolder(binding)
        }
    }
}

/**
 * An [GalleryItemViewHolder] implementation that binds a [VideoItem] and inflates the associated layout.
 * This is used to display media items of the type video.
 */
class GalleryVideoViewHolder private constructor(private val binding: GalleryVideoViewBinding) :
    GalleryItemViewHolder(binding) {

    /**
     * Bind a [VideoItem] to the layout using databinding
     * @param item: the video item that should be displayed in this view.
     */
    override fun bind(item: MediaItem) {
        val imageView = binding.imageView
        item as VideoItem

        // Bind the variable
        binding.item = item

        // Load the video thumbnail with glide
        // TODO: Look into live preview playback (Exoplayer?)
        Glide.with(imageView)
            .load(item.contentUri)
            .placeholder(ColorDrawable(Color.GRAY))
            .thumbnail(0.2f)
            .centerCrop()
            .into(imageView)
        binding.executePendingBindings()
    }

    companion object {
        /**
         * Static function to create a new [GalleryVideoViewHolder] and make sure its layout is inflated.
         * @param parent: The [ViewGroup] the context can be retrieved from
         * @return A [GalleryVideoViewHolder] instance that can be populated with data.
         */
        fun from(parent: ViewGroup): GalleryVideoViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = GalleryVideoViewBinding.inflate(layoutInflater, parent, false)
            return GalleryVideoViewHolder(binding)
        }
    }
}

/**
 * An [GalleryItemViewHolder] implementation that binds a [ImageItem] and inflates the associated layout.
 * This is used to display media items of the type image.
 */
class GalleryImageViewHolder private constructor(private val binding: GalleryImageViewBinding) :
    GalleryItemViewHolder(binding) {

    /**
     * Bind a [ImageItem] to the layout using databinding
     * @param item: the image item that should be displayed in this view.
     */
    override fun bind(item: MediaItem) {
        val imageView = binding.imageView

        // Inject variable
        binding.item = item as ImageItem

        // Load image with Glide into the imageView
        Glide.with(imageView)
            .load(item.contentUri)
            .placeholder(ColorDrawable(Color.GRAY))
            .thumbnail(0.2f)
            .centerCrop()
            .into(imageView)
        binding.executePendingBindings()
    }

    companion object {
        /**
         * Static function to create a new [GalleryImageViewHolder] and make sure its layout is inflated.
         * @param parent: The [ViewGroup] the context can be retrieved from
         * @return A [GalleryImageViewHolder] instance that can be populated with data.
         */
        fun from(parent: ViewGroup): GalleryImageViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = GalleryImageViewBinding.inflate(layoutInflater, parent, false)
            return GalleryImageViewHolder(binding)
        }
    }
}
