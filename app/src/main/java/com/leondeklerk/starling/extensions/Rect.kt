package com.leondeklerk.starling.extensions

import android.graphics.Rect
import java.security.InvalidParameterException

fun Rect.shrinkBy(pixels: Int): Rect {
    if (pixels <= width() / 2 && pixels <= height() / 2) {
        return Rect(left + pixels, top + pixels, right - pixels, bottom - pixels)
    } else {
        throw InvalidParameterException()
    }
}
