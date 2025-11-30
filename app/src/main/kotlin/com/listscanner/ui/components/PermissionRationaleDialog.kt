package com.listscanner.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Dialog explaining why camera permission is needed and providing options to proceed.
 *
 * @param showOpenSettings If true, shows "Open Settings" button for permanently denied state.
 *                         If false, shows "Try Again" button for standard denial.
 * @param onTryAgain Callback when user wants to request permission again
 * @param onOpenSettings Callback when user wants to open app settings
 * @param onDismiss Callback when dialog is dismissed
 * @param modifier Optional modifier for the dialog
 */
@Composable
fun PermissionRationaleDialog(
    showOpenSettings: Boolean = false,
    onTryAgain: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(text = "Camera Permission Required")
        },
        text = {
            Text(
                text = "List Scanner needs camera access to photograph your handwritten lists for digitization."
            )
        },
        confirmButton = {
            if (showOpenSettings) {
                TextButton(onClick = onOpenSettings) {
                    Text("Open Settings")
                }
            } else {
                TextButton(onClick = onTryAgain) {
                    Text("Try Again")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
