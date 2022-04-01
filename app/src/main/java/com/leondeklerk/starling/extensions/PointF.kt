package com.leondeklerk.starling.extensions

import android.graphics.PointF
import android.graphics.RectF

fun PointF.capTo(rect: RectF) {
    if (x < rect.left) {
        x = rect.left
    } else if (x > rect.right) {
        x = rect.right
    }

    if (y < rect.top) {
        y = rect.top
    } else if (y > rect.bottom) {
        y = rect.bottom
    }
}
