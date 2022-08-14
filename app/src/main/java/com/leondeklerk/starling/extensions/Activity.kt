package com.leondeklerk.starling.extensions

import android.app.Activity
import android.util.TypedValue

/**
 * Convert a display density value to a pixel value
 * @param dp: the number of display density units
 * @return the actual size in pixels
 */
fun Activity.dpToPx(dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}
