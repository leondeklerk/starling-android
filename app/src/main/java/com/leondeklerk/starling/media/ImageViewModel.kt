package com.leondeklerk.starling.media

import android.app.Application
import android.graphics.Bitmap
import android.provider.MediaStore
import com.leondeklerk.starling.media.data.ImageItem

class ImageViewModel(application: Application) : MediaItemViewModel<ImageItem, Bitmap>(application) {
    override val contentUri = MediaStore.Images.Media.getContentUri("external")!!
}
