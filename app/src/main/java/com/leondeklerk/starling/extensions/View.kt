package com.leondeklerk.starling.extensions

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams

/**
 * Convert a display density value to a pixel value
 * @param dp: the number of display density units
 * @return the actual size in pixels
 */
fun View.dpToPx(dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}

/**
 * Helper function to add the margin top property to a view.
 */
fun View.setMarginTop(value: Int) = updateLayoutParams<ViewGroup.MarginLayoutParams> {
    topMargin = value
}
