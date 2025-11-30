package com.listscanner.ui.screens.camera

import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.listscanner.ui.components.CameraPreview

@Composable
fun CameraCaptureScreen(
    onBackClick: () -> Unit,
    onPhotoSaved: (Long) -> Unit,
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    @Suppress("DEPRECATION")
    val windowManager = context.getSystemService(WindowManager::class.java)
    val displayRotation = windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0

    // Initialize camera provider
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: Exception) {
                viewModel.setError("Camera unavailable. Please try again.")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Bind camera when both provider and preview view are ready
    LaunchedEffect(cameraProvider, previewView) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val preview = previewView ?: return@LaunchedEffect

        try {
            provider.unbindAll()

            val newPreview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }

            val newImageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(displayRotation)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                newPreview,
                newImageCapture
            )

            imageCapture = newImageCapture
            viewModel.setReady()
        } catch (e: Exception) {
            viewModel.setError("Camera unavailable. Please try again.")
        }
    }

    // Handle success state - navigate back
    LaunchedEffect(uiState) {
        if (uiState is CameraViewModel.UiState.Success) {
            val photoId = (uiState as CameraViewModel.UiState.Success).photoId
            onPhotoSaved(photoId)
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState) {
        if (uiState is CameraViewModel.UiState.Error) {
            val errorMessage = (uiState as CameraViewModel.UiState.Error).message
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    // Cleanup camera on dispose
    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Camera preview
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onPreviewViewCreated = { view ->
                previewView = view
            }
        )

        // Loading overlay during capture
        if (uiState is CameraViewModel.UiState.Capturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Capture button
        FloatingActionButton(
            onClick = {
                imageCapture?.let { capture ->
                    viewModel.capturePhoto(context, capture)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            if (uiState is CameraViewModel.UiState.Capturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture photo",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Snackbar for errors
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                actionColor = MaterialTheme.colorScheme.inversePrimary
            )
        }

        // Error state with retry prompt
        if (uiState is CameraViewModel.UiState.Error && cameraProvider == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (uiState as CameraViewModel.UiState.Error).message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
