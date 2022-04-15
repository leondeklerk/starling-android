package com.leondeklerk.starling.extensions

import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.contains

fun RectF.enlargeBy(pixels: Int): RectF {
    return RectF(left - pixels, top - pixels, right + pixels, bottom + pixels)
}

fun RectF.enlargeBy(pixels: Float): RectF {
    return RectF(left - pixels, top - pixels, right + pixels, bottom + pixels)
}

fun RectF.contains(point: PointF): Boolean {
    return contains(point)
}
