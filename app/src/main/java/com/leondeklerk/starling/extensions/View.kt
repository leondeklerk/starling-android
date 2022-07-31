package com.leondeklerk.starling.extensions

import android.util.TypedValue
import android.view.View

/**
 * Convert a display density value to a pixel value
 * @param dp: the number of display density units
 * @return the actual size in pixels
 */
fun View.dpToPixels(dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}
