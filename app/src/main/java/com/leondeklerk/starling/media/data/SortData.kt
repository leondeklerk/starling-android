package com.leondeklerk.starling.media.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SortData(val year: String, val month: String, val day: String, val folder: String) : Parcelable
