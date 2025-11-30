package com.listscanner.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Checks if camera permission is currently granted.
 *
 * @param context Application or Activity context
 * @return true if camera permission is granted, false otherwise
 */
fun checkCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Checks if the app should show a rationale for requesting camera permission.
 *
 * This returns true if the user has previously denied the permission but hasn't
 * selected "Don't ask again". Returns false if:
 * 1. Permission not yet requested (first time)
 * 2. Permission permanently denied ("Don't ask again" checked)
 * 3. Permission already granted
 *
 * @param activity The Activity context required for rationale check
 * @return true if rationale should be shown, false otherwise
 */
fun shouldShowCameraRationale(activity: Activity): Boolean {
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.CAMERA
    )
}
