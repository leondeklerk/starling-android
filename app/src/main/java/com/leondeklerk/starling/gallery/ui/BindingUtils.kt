package com.leondeklerk.starling.gallery.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.icu.util.Calendar
import android.net.Uri
import android.text.format.DateFormat
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ComponentActivity
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.leondeklerk.starling.R
import com.leondeklerk.starling.data.FolderItem
import com.leondeklerk.starling.data.HeaderItem
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.data.MediaItem
import com.leondeklerk.starling.data.MediaItemTypes
import com.leondeklerk.starling.data.VideoItem
import java.util.Date

@BindingAdapter("media_uri", "folder_uri", requireAll = false)
fun setMediaUri(view: ImageView, media: MediaItem?, folder: FolderItem?) {
    val type: MediaItemTypes
    val uri: Uri
    if (media != null) {
        type = media.type
        uri = media.uri!!
    } else {
        type = folder!!.type
        uri = folder.thumbnailUri
    }

    if (type == MediaItemTypes.VIDEO) {
        val options = RequestOptions()
            .skipMemoryCache(false)
            .priority(Priority.LOW)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

        val builder = Glide.with(view.context)
            .load(uri)
            .transform(CenterCrop())
            .thumbnail(0.2f)
            .apply(options)
            .transition(DrawableTransitionOptions.withCrossFade())

        builder.into(view)
    } else {
        Glide.with(view)
            .load(uri)
            .transform(CenterCrop())
            .placeholder(ColorDrawable(Color.GRAY))
            .thumbnail(0.2f)
            .into(view)
    }

    // TODO: implement coil if loading issues are fixed
    // view.lifecycleOwner?.lifecycleScope?.launch {
    //     withContext(Dispatchers.Default) {
    //         val request: ImageRequest = if (item.type === MediaItemTypes.VIDEO) {
    //             item as VideoItem
    //             ImageRequest.Builder(view.context)
    //                 .placeholder(ColorDrawable(Color.GRAY))
    //                 .fetcher(VideoFrameUriFetcher(view.context))
    //                 .data(item.contentUri).target(view).build()
    //         } else {
    //             item as ImageItem
    //             ImageRequest.Builder(view.context)
    //                 .placeholder(ColorDrawable(Color.GRAY))
    //                 .data(item.contentUri).target(view).build()
    //         }
    //         view.context.imageLoader.enqueue(request)
    //     }
    // }
}

tailrec fun Context?.getActivity(): Activity? = when (this) {
    is Activity -> this

    else -> {
        val contextWrapper = this as? ContextWrapper
        contextWrapper?.baseContext?.getActivity()
    }
}

val View.lifecycleOwner: LifecycleOwner?
    get() = try {
        val fragment = findFragment<Fragment>()
        fragment.viewLifecycleOwner
    } catch (e: IllegalStateException) {
        when (val activity = context.getActivity()) {
            is ComponentActivity -> activity
            else -> null
        }
    }

@BindingAdapter("idFormatted")
fun TextView.setIdFormatted(item: ImageItem) {
    text = item.id.toString()
}

@BindingAdapter("uriFormatted")
fun TextView.setUriFormatted(item: ImageItem) {
    text = item.uri.toString()
}

/**
 * [BindingAdapter] used to parse a video duration from milliseconds to a minute:second format.
 * The value is applied to the text attribute of the TextView.
 * @param item: [VideoItem] containing the duration of the video.
 */
@BindingAdapter("video_duration")
fun TextView.setVideoDuration(item: MediaItem) {
    if (item is VideoItem) {
        text = DateFormat.format(
            "mm:ss",
            item.duration.toLong()
        )
    }
}

/**
 * [BindingAdapter] for parsing the date from a [HeaderItem] and setting the correct date text.
 * It uses the zoomLevel to determine what scope of date should be shown (years/months/days).
 * @param item: A [HeaderItem] that contains the zoomLevel and the date of the group.
 */
@BindingAdapter("header_item")
fun TextView.setHeaderItem(item: HeaderItem) {
    // Get all the values from the item
    val cal = Calendar.getInstance()
    cal.time = item.date

    val year = cal.get(Calendar.YEAR)
    val week = cal.get(Calendar.WEEK_OF_YEAR)
    val day = cal.get(Calendar.DAY_OF_YEAR)

    // Get all the current date values
    val now = Calendar.getInstance()
    now.time = Date(System.currentTimeMillis())

    val curYear = now.get(Calendar.YEAR)
    val curWeek = now.get(Calendar.WEEK_OF_YEAR)
    val curDay = now.get(Calendar.DAY_OF_YEAR)

    // Set the header text depending on the zoom level
    text = when (item.zoomLevel) {
        0 -> {
            // year view always shows the year (2021)
            DateFormat.format("yyyy", item.date).toString()
        }
        2, 3 -> {
            // Dates within this year are handled differently
            if (year == curYear) {
                // Current day shows "Today"
                if (day == curDay) {
                    resources.getString(R.string.date_today)
                } else if (day == (curDay - 1)) {
                    // Yesterday shows "Yesterday
                    resources.getString(R.string.date_yesterday)
                } else {
                    // Dates still in the current week can show just as text (Tuesday)
                    if (week == curWeek) {
                        DateFormat.format("EEEE", item.date).toString()
                    } else {
                        // Otherwise they include the day of the month and the month (Mon, May 17)
                        DateFormat.format("EEE, MMM d ", item.date).toString()
                    }
                }
            } else {
                // We do show previous years (Sun, May 17, 2020)
                DateFormat.format("EEE, MMM d, yyyy ", item.date).toString()
            }
        }
        else -> {
            // We don't show the current year (May)
            if (year == curYear) {
                DateFormat.format("MMMM", item.date).toString()
            } else {
                // But do show for previous years (May 2020)
                DateFormat.format("MMMM, yyyy", item.date).toString()
            }
        }
    }
}
