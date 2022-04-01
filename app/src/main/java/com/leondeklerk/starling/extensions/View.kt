package com.leondeklerk.starling.extensions

import android.util.TypedValue
import android.view.View

fun View.dpToPixels(dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}
