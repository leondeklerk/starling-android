package com.leondeklerk.starling.extensions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment

/**
 * Helper function that will redirect the user to the settings screen of this application using [Intent]
 */
fun Fragment.goToSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${requireActivity().packageName}")
    ).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.also { intent ->
        startActivity(intent)
    }
}

/**
 * Helper function to check if the user granted a specific permission to the application
 * @param permission: The permission that should be checked, a [String] from [Manifest.permission]
 * @return: A [Boolean] indicating if the permission was granted or not
 */
fun Fragment.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        requireContext(),
        permission
    ) == PermissionChecker.PERMISSION_GRANTED
}

/**
 * Convert a display density value to a pixel value
 * @param dp: the number of display density units
 * @return the actual size in pixels
 */
fun Fragment.dpToPx(dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}
