package com.listscanner.ui.screens.regionselection

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.listscanner.ui.components.CropOverlay
import com.listscanner.ui.components.HandleType
import com.listscanner.ui.components.cropRectToScreenRect
import com.listscanner.ui.components.detectHandle
import com.listscanner.ui.components.screenRectToCropRect
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionSelectionScreen(
    viewModel: RegionSelectionViewModel,
    onBackClick: () -> Unit,
    onScanSelectedArea: (CropRect) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val cropRect by viewModel.cropRect.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Area") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is RegionSelectionViewModel.UiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is RegionSelectionViewModel.UiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }

                is RegionSelectionViewModel.UiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        RegionSelectionContent(
                            filePath = state.filePath,
                            cropRect = state.cropRect,
                            onCropRectChange = viewModel::updateCropRect,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onScanSelectedArea(cropRect) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Scan Selected Area")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionSelectionContent(
    filePath: String,
    cropRect: CropRect,
    onCropRectChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val touchThreshold = with(density) { 44.dp.toPx() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var imageRect by remember { mutableStateOf(Rect.Zero) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Track current handle being dragged
    var activeHandle by remember { mutableStateOf(HandleType.NONE) }
    var dragStartCropRect by remember { mutableStateOf(cropRect) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Select area to scan",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    containerSize = size
                    if (imageSize != IntSize.Zero) {
                        imageRect = calculateImageRect(containerSize, imageSize)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Only apply zoom/pan when not actively dragging a handle
                        if (activeHandle == HandleType.NONE) {
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) {
                                offset + pan
                            } else {
                                Offset.Zero
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(filePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Photo for region selection",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    success = { state ->
                        imageSize = IntSize(
                            state.painter.intrinsicSize.width.toInt(),
                            state.painter.intrinsicSize.height.toInt()
                        )
                        if (containerSize != IntSize.Zero) {
                            imageRect = calculateImageRect(containerSize, imageSize)
                        }
                        SubcomposeAsyncImageContent()
                    }
                )

                // Only show overlay once we have valid image dimensions
                if (imageRect != Rect.Zero) {
                    // Use rememberUpdatedState to get latest values inside gesture callbacks
                    // without restarting the gesture detector
                    val currentCropRect by rememberUpdatedState(cropRect)
                    val currentImageRect by rememberUpdatedState(imageRect)

                    CropOverlay(
                        cropRect = cropRect,
                        imageRect = imageRect,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val screenCropRect = cropRectToScreenRect(currentCropRect, currentImageRect)
                                        activeHandle = detectHandle(startOffset, screenCropRect, touchThreshold)
                                        dragStartCropRect = currentCropRect
                                    },
                                    onDragEnd = {
                                        activeHandle = HandleType.NONE
                                    },
                                    onDragCancel = {
                                        activeHandle = HandleType.NONE
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()

                                        if (activeHandle != HandleType.NONE && currentImageRect.width > 0 && currentImageRect.height > 0) {
                                            val normalizedDelta = Offset(
                                                dragAmount.x / currentImageRect.width,
                                                dragAmount.y / currentImageRect.height
                                            )

                                            val newCropRect = when (activeHandle) {
                                                HandleType.BODY -> handleBodyDrag(currentCropRect, normalizedDelta)
                                                HandleType.TOP_LEFT -> handleCornerDrag(currentCropRect, normalizedDelta, HandleType.TOP_LEFT)
                                                HandleType.TOP_RIGHT -> handleCornerDrag(currentCropRect, normalizedDelta, HandleType.TOP_RIGHT)
                                                HandleType.BOTTOM_LEFT -> handleCornerDrag(currentCropRect, normalizedDelta, HandleType.BOTTOM_LEFT)
                                                HandleType.BOTTOM_RIGHT -> handleCornerDrag(currentCropRect, normalizedDelta, HandleType.BOTTOM_RIGHT)
                                                HandleType.TOP -> handleEdgeDrag(currentCropRect, normalizedDelta, HandleType.TOP)
                                                HandleType.BOTTOM -> handleEdgeDrag(currentCropRect, normalizedDelta, HandleType.BOTTOM)
                                                HandleType.LEFT -> handleEdgeDrag(currentCropRect, normalizedDelta, HandleType.LEFT)
                                                HandleType.RIGHT -> handleEdgeDrag(currentCropRect, normalizedDelta, HandleType.RIGHT)
                                                HandleType.NONE -> currentCropRect
                                            }

                                            onCropRectChange(newCropRect)
                                        }
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}

/**
 * Calculates the actual image display rectangle within the container,
 * accounting for ContentScale.Fit behavior.
 */
private fun calculateImageRect(containerSize: IntSize, imageSize: IntSize): Rect {
    if (containerSize.width == 0 || containerSize.height == 0 ||
        imageSize.width == 0 || imageSize.height == 0
    ) {
        return Rect.Zero
    }

    val containerAspect = containerSize.width.toFloat() / containerSize.height
    val imageAspect = imageSize.width.toFloat() / imageSize.height

    val displayWidth: Float
    val displayHeight: Float

    if (imageAspect > containerAspect) {
        // Image is wider - fit to width
        displayWidth = containerSize.width.toFloat()
        displayHeight = containerSize.width / imageAspect
    } else {
        // Image is taller - fit to height
        displayHeight = containerSize.height.toFloat()
        displayWidth = containerSize.height * imageAspect
    }

    val offsetX = (containerSize.width - displayWidth) / 2
    val offsetY = (containerSize.height - displayHeight) / 2

    return Rect(
        offset = Offset(offsetX, offsetY),
        size = Size(displayWidth, displayHeight)
    )
}

/**
 * Handles dragging the entire crop rectangle body.
 */
private fun handleBodyDrag(cropRect: CropRect, delta: Offset): CropRect {
    var newLeft = cropRect.left + delta.x
    var newTop = cropRect.top + delta.y
    var newRight = cropRect.right + delta.x
    var newBottom = cropRect.bottom + delta.y

    // Constrain to image bounds
    if (newLeft < 0f) {
        newRight -= newLeft
        newLeft = 0f
    }
    if (newTop < 0f) {
        newBottom -= newTop
        newTop = 0f
    }
    if (newRight > 1f) {
        newLeft -= (newRight - 1f)
        newRight = 1f
    }
    if (newBottom > 1f) {
        newTop -= (newBottom - 1f)
        newBottom = 1f
    }

    return CropRect(
        left = newLeft.coerceIn(0f, 1f - cropRect.width),
        top = newTop.coerceIn(0f, 1f - cropRect.height),
        right = newRight.coerceIn(cropRect.width, 1f),
        bottom = newBottom.coerceIn(cropRect.height, 1f)
    )
}

/**
 * Handles dragging a corner to resize the rectangle.
 */
private fun handleCornerDrag(cropRect: CropRect, delta: Offset, corner: HandleType): CropRect {
    val minSize = CropRect.minSizeFraction()

    return when (corner) {
        HandleType.TOP_LEFT -> {
            val newLeft = (cropRect.left + delta.x).coerceIn(0f, cropRect.right - minSize)
            val newTop = (cropRect.top + delta.y).coerceIn(0f, cropRect.bottom - minSize)
            cropRect.copy(left = newLeft, top = newTop)
        }
        HandleType.TOP_RIGHT -> {
            val newRight = (cropRect.right + delta.x).coerceIn(cropRect.left + minSize, 1f)
            val newTop = (cropRect.top + delta.y).coerceIn(0f, cropRect.bottom - minSize)
            cropRect.copy(right = newRight, top = newTop)
        }
        HandleType.BOTTOM_LEFT -> {
            val newLeft = (cropRect.left + delta.x).coerceIn(0f, cropRect.right - minSize)
            val newBottom = (cropRect.bottom + delta.y).coerceIn(cropRect.top + minSize, 1f)
            cropRect.copy(left = newLeft, bottom = newBottom)
        }
        HandleType.BOTTOM_RIGHT -> {
            val newRight = (cropRect.right + delta.x).coerceIn(cropRect.left + minSize, 1f)
            val newBottom = (cropRect.bottom + delta.y).coerceIn(cropRect.top + minSize, 1f)
            cropRect.copy(right = newRight, bottom = newBottom)
        }
        else -> cropRect
    }
}

/**
 * Handles dragging an edge to resize one dimension.
 */
private fun handleEdgeDrag(cropRect: CropRect, delta: Offset, edge: HandleType): CropRect {
    val minSize = CropRect.minSizeFraction()

    return when (edge) {
        HandleType.TOP -> {
            val newTop = (cropRect.top + delta.y).coerceIn(0f, cropRect.bottom - minSize)
            cropRect.copy(top = newTop)
        }
        HandleType.BOTTOM -> {
            val newBottom = (cropRect.bottom + delta.y).coerceIn(cropRect.top + minSize, 1f)
            cropRect.copy(bottom = newBottom)
        }
        HandleType.LEFT -> {
            val newLeft = (cropRect.left + delta.x).coerceIn(0f, cropRect.right - minSize)
            cropRect.copy(left = newLeft)
        }
        HandleType.RIGHT -> {
            val newRight = (cropRect.right + delta.x).coerceIn(cropRect.left + minSize, 1f)
            cropRect.copy(right = newRight)
        }
        else -> cropRect
    }
}
