package com.listscanner.ui.screens.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for managing camera permission state.
 *
 * Tracks the current permission status and provides methods to check
 * and update permission state based on user interactions.
 */
class CameraPermissionViewModel : ViewModel() {

    /**
     * Sealed interface representing the possible permission states.
     */
    sealed interface UiState {
        /** Permission has not been requested yet */
        data object NotRequested : UiState

        /** Permission has been granted */
        data object Granted : UiState

        /** Permission was denied but can be requested again */
        data object Denied : UiState

        /** Permission was permanently denied ("Don't ask again" checked) */
        data object PermanentlyDenied : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.NotRequested)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Checks the current permission status from the system.
     *
     * If permission is granted, updates the state to [UiState.Granted].
     * Does not modify state if permission is not granted (preserves denial states).
     *
     * @param context Application or Activity context
     */
    fun checkPermissionStatus(context: Context) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            _uiState.value = UiState.Granted
        }
    }

    /**
     * Handles the result of a permission request.
     *
     * @param granted True if permission was granted
     * @param shouldShowRationale True if the system recommends showing a rationale.
     *                            When false after denial, indicates permanent denial.
     */
    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        _uiState.value = when {
            granted -> UiState.Granted
            shouldShowRationale -> UiState.Denied
            else -> UiState.PermanentlyDenied
        }
    }

    /**
     * Resets state to allow re-requesting permission.
     * Used when user taps "Try Again" after denial.
     */
    fun resetToNotRequested() {
        _uiState.value = UiState.NotRequested
    }
}
