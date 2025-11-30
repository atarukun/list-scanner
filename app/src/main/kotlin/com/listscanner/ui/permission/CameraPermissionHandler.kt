package com.listscanner.ui.permission

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * A composable utility that handles camera permission requests.
 *
 * This composable provides a permission launcher to child content that can be
 * invoked to request camera permission. The result is passed to the callback.
 *
 * @param onPermissionResult Callback invoked with the permission result (true if granted)
 * @param content Composable content that receives a requestPermission lambda
 */
@Composable
fun CameraPermissionHandler(
    onPermissionResult: (Boolean) -> Unit,
    content: @Composable (requestPermission: () -> Unit) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionResult(isGranted)
    }

    content { launcher.launch(Manifest.permission.CAMERA) }
}
