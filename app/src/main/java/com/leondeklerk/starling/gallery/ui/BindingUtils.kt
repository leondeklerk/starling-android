package com.leondeklerk.starling.gallery.ui

import android.icu.util.Calendar
import android.text.format.DateFormat
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.leondeklerk.starling.R
import com.leondeklerk.starling.data.HeaderItem
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.data.VideoItem
import java.util.Date

@BindingAdapter("idFormatted")
fun TextView.setIdFormatted(item: ImageItem) {
    text = item.id.toString()
}

@BindingAdapter("uriFormatted")
fun TextView.setUriFormatted(item: ImageItem) {
    text = item.contentUri.toString()
}

/**
 * [BindingAdapter] used to parse a video duration from milliseconds to a minute:second format.
 * The value is applied to the text attribute of the TextView.
 * @param item: [VideoItem] containing the duration of the video.
 */
@BindingAdapter("video_duration")
fun TextView.setVideoDuration(item: VideoItem) {
    text = DateFormat.format(
        "mm:ss",
        item.duration.toLong()
    )
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
