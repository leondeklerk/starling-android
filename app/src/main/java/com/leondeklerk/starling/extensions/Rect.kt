package com.leondeklerk.starling.extensions

import android.graphics.PointF
import android.graphics.Rect
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint
import java.security.InvalidParameterException

fun Rect.shrinkBy(pixels: Int): Rect {
    if (pixels <= width() / 2 && pixels <= height() / 2) {
        return Rect(left + pixels, top + pixels, right - pixels, bottom - pixels)
    } else {
        throw InvalidParameterException()
    }
}

fun Rect.enlargeBy(pixels: Int): Rect {
    return Rect(left - pixels, top - pixels, right + pixels, bottom + pixels)
}

fun Rect.enlargeBy(pixels: Float): Rect {
    val px = pixels.toInt()
    return Rect(left - px, top - px, right + px, bottom + px)
}

fun Rect.contains(point: PointF): Boolean {
    return (contains(point.toPoint()))
}
