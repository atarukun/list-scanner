package com.listscanner.ui.screens.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.ui.components.PhotoCard
import com.listscanner.ui.components.SwipeToDeleteContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGalleryScreen(
    viewModel: PhotoGalleryViewModel,
    onCaptureClick: () -> Unit,
    onPhotoClick: (Long) -> Unit,
    onListClick: (Long) -> Unit,
    onListsOverviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var photoToDelete by remember { mutableStateOf<Photo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.deletionError.collect { errorMessage ->
            snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("List Scanner") },
                actions = {
                    IconButton(onClick = onListsOverviewClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "My Lists"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCaptureClick) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture Photo"
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is PhotoGalleryViewModel.UiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is PhotoGalleryViewModel.UiState.Empty -> {
                    EmptyGalleryContent(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is PhotoGalleryViewModel.UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = state.photos,
                            key = { photo -> photo.id }
                        ) { photo ->
                            SwipeToDeleteContainer(
                                onDelete = { photoToDelete = photo }
                            ) {
                                PhotoCard(
                                    photo = photo,
                                    onClick = {
                                        if (photo.ocrStatus == OcrStatus.COMPLETED) {
                                            scope.launch {
                                                val listId = viewModel.getListIdForPhoto(photo.id)
                                                if (listId != null) {
                                                    onListClick(listId)
                                                } else {
                                                    onPhotoClick(photo.id)
                                                }
                                            }
                                        } else {
                                            onPhotoClick(photo.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                is PhotoGalleryViewModel.UiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    photoToDelete?.let { photo ->
        DeleteConfirmationDialog(
            hasAssociatedList = photo.ocrStatus == OcrStatus.COMPLETED,
            onConfirm = {
                viewModel.deletePhoto(photo.id)
                photoToDelete = null
            },
            onDismiss = { photoToDelete = null }
        )
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
private fun EmptyGalleryContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Capture your first list!",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
