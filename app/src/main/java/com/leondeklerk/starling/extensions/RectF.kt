package com.leondeklerk.starling.extensions

import android.graphics.RectF

fun RectF.enlargeBy(pixels: Int): RectF {
    return RectF(left - pixels, top - pixels, right + pixels, bottom + pixels)
}
