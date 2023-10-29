package com.leondeklerk.starling.extensions

import android.graphics.Rect
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

fun View.dpToPx(dp: Int): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
}

/**
 * Helper function to add the margin top property to a view.
 */
fun View.setMarginTop(value: Int) = updateLayoutParams<ViewGroup.MarginLayoutParams> {
    topMargin = value
}

fun View.gone() {
    visibility = View.GONE
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.visibleNoAlpha() {
    alpha = 0f
    visibility = View.VISIBLE
}

fun View.goneFullAlpha() {
    alpha = 1f
    visibility = View.GONE
}

val View.localVisibleRect: Rect
    get() = Rect().also { getLocalVisibleRect(it) }

val View.globalVisibleRect: Rect
    get() = Rect().also { getGlobalVisibleRect(it) }

fun View.applyMargin(start: Int? = null, top: Int? = null, end: Int? = null, bottom: Int? = null) {
    if (layoutParams is ViewGroup.MarginLayoutParams) {
        layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
            marginStart = start ?: marginStart
            topMargin = top ?: topMargin
            marginEnd = end ?: marginEnd
            bottomMargin = bottom ?: bottomMargin
        }
    }
}

fun View.applyMargin(rect: Rect) {
    applyMargin(rect.left, rect.top, rect.right, rect.bottom)
}

fun View.requestNewSize(width: Int, height: Int) {
    layoutParams.width = width
    layoutParams.height = height
    layoutParams = layoutParams
}
