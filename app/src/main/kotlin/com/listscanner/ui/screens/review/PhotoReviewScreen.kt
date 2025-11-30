package com.listscanner.ui.screens.review

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoReviewScreen(
    viewModel: PhotoReviewViewModel,
    onBackClick: () -> Unit,
    onRetakeClick: () -> Unit,
    onSelectAreaClick: () -> Unit,
    onDeleteComplete: () -> Unit,
    onListCreated: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    val showConsentDialog by viewModel.showConsentDialog.collectAsState()
    val showCostWarningDialog by viewModel.showCostWarningDialog.collectAsState()
    val currentWeeklyUsage by viewModel.currentWeeklyUsage.collectAsState()
    val ocrErrorState by viewModel.ocrErrorDialogState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is PhotoReviewViewModel.UiState.Deleted) {
            onDeleteComplete()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deletionError.collect { errorMessage ->
            snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToList.collect { listId ->
            onListCreated(listId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (val state = uiState) {
                            is PhotoReviewViewModel.UiState.Success -> formatTimestamp(state.photo.timestamp)
                            is PhotoReviewViewModel.UiState.Processing -> formatTimestamp(state.photo.timestamp)
                            else -> "Photo Review"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState is PhotoReviewViewModel.UiState.Success && !isProcessing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete photo"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is PhotoReviewViewModel.UiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is PhotoReviewViewModel.UiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onReturnToGallery = onBackClick,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is PhotoReviewViewModel.UiState.Success -> {
                    PhotoReviewContent(
                        photo = state.photo,
                        isProcessing = isProcessing,
                        isNetworkAvailable = isNetworkAvailable,
                        onRetakeClick = {
                            viewModel.deletePhoto()
                            onRetakeClick()
                        },
                        onProcessOcrClick = viewModel::processOcr,
                        onSelectAreaClick = onSelectAreaClick,
                        onCancelClick = viewModel::cancelOcrProcessing
                    )
                }

                is PhotoReviewViewModel.UiState.Processing -> {
                    PhotoReviewContent(
                        photo = state.photo,
                        isProcessing = true,
                        isNetworkAvailable = isNetworkAvailable,
                        onRetakeClick = { },
                        onProcessOcrClick = { },
                        onSelectAreaClick = { },
                        onCancelClick = viewModel::cancelOcrProcessing
                    )
                }

                is PhotoReviewViewModel.UiState.Deleted -> {
                    // Navigation handled by LaunchedEffect
                }
            }
        }
    }

    if (showDeleteDialog) {
        val hasAssociatedList = (uiState as? PhotoReviewViewModel.UiState.Success)
            ?.photo?.ocrStatus == OcrStatus.COMPLETED
        DeleteConfirmationDialog(
            hasAssociatedList = hasAssociatedList,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deletePhoto()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showConsentDialog) {
        PrivacyConsentDialog(
            onAccept = viewModel::onConsentAccepted,
            onDecline = viewModel::onConsentDeclined
        )
    }

    if (showCostWarningDialog) {
        CostWarningDialog(
            weeklyUsage = currentWeeklyUsage,
            onDismiss = viewModel::onCostWarningDismissed
        )
    }

    ocrErrorState?.let { errorState ->
        OcrErrorDialog(
            errorMessage = errorState.message,
            onRetry = viewModel::retryFromErrorDialog,
            onRetakePhoto = {
                viewModel.dismissOcrErrorDialog()
                viewModel.deletePhoto()
                onRetakeClick()
            },
            onDismiss = viewModel::dismissOcrErrorDialog
        )
    }
}

@Composable
private fun PhotoReviewContent(
    photo: Photo,
    isProcessing: Boolean,
    isNetworkAvailable: Boolean,
    onRetakeClick: () -> Unit,
    onProcessOcrClick: () -> Unit,
    onSelectAreaClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ZoomableImage(
                filePath = photo.filePath,
                modifier = Modifier.fillMaxSize()
            )

            if (isProcessing) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Processing... This may take a few seconds",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        ActionButtonsRow(
            isProcessing = isProcessing,
            isNetworkAvailable = isNetworkAvailable,
            onRetakeClick = onRetakeClick,
            onProcessOcrClick = onProcessOcrClick,
            onSelectAreaClick = onSelectAreaClick,
            onCancelClick = onCancelClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

@Composable
private fun ZoomableImage(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) {
                        offset + pan
                    } else {
                        Offset.Zero
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(filePath))
                .crossfade(true)
                .build(),
            contentDescription = "Photo for review",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            placeholder = rememberVectorPainter(Icons.Default.Image),
            error = rememberVectorPainter(Icons.Default.BrokenImage)
        )
    }
}

@Composable
private fun ActionButtonsRow(
    isProcessing: Boolean,
    isNetworkAvailable: Boolean,
    onRetakeClick: () -> Unit,
    onProcessOcrClick: () -> Unit,
    onSelectAreaClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isProcessing) {
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            } else {
                OutlinedButton(
                    onClick = onRetakeClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Retake Photo")
                }

                Button(
                    onClick = onProcessOcrClick,
                    modifier = Modifier.weight(1f),
                    enabled = isNetworkAvailable,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Scan Full Image")
                }
            }
        }

        if (!isProcessing) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSelectAreaClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Area")
            }
        }

        if (!isNetworkAvailable && !isProcessing) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "OCR requires internet connection",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onReturnToGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = onReturnToGallery) {
            Text("Return to Gallery")
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    hasAssociatedList: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Photo?") },
        text = {
            Column {
                Text("Are you sure? This cannot be undone.")
                if (hasAssociatedList) {
                    Text(
                        text = "This photo has an associated list that will also be deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PrivacyConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Privacy Notice") },
        text = {
            Text(
                "To recognize text, your photo will be sent to Google Cloud Vision " +
                "for processing. Google does not store your images. Do you consent?"
            )
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Accept")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Decline")
            }
        }
    )
}

@Composable
private fun CostWarningDialog(
    weeklyUsage: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Usage Notice") },
        text = {
            Text(
                "You've processed $weeklyUsage photos this week. " +
                "Continued use may incur costs (~\$1.50 per 1000 photos after free tier)."
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun OcrErrorDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onRetakePhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OCR Error") },
        text = { Text(errorMessage) },
        confirmButton = {
            Button(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                OutlinedButton(onClick = onRetakePhoto) {
                    Text("Retake Photo")
                }
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
