package com.leondeklerk.starling.gallery.ui

import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.leondeklerk.starling.gallery.data.GalleryItem

@BindingAdapter("idFormatted")
fun TextView.setIdFormatted(item: GalleryItem) {
    text = item.id.toString()
}

@BindingAdapter("uriFormatted")
fun TextView.setUriFormatted(item: GalleryItem) {
    text = item.contentUri.toString()
}