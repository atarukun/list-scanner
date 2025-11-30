package com.listscanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.listscanner.ui.screens.regionselection.CropRect

/**
 * Handle type for identifying which part of the crop rectangle is being interacted with.
 */
enum class HandleType {
    NONE,
    BODY,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT
}

/**
 * CropOverlay composable that draws a semi-transparent overlay outside the crop area,
 * the crop rectangle border, and interactive handles for resizing.
 *
 * @param cropRect The normalized crop rectangle (0.0-1.0 coordinates)
 * @param imageRect The actual screen rect where the image is displayed
 * @param modifier Modifier for the Canvas
 */
@Composable
fun CropOverlay(
    cropRect: CropRect,
    imageRect: Rect,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val screenCropRect = cropRectToScreenRect(cropRect, imageRect)

        // Draw semi-transparent overlay outside crop area
        drawDimOverlay(screenCropRect, imageRect)

        // Draw crop rectangle border
        drawCropBorder(screenCropRect)

        // Draw corner handles
        drawCornerHandles(screenCropRect)

        // Draw edge handles
        drawEdgeHandles(screenCropRect)
    }
}

/**
 * Converts normalized CropRect to screen pixel coordinates.
 */
fun cropRectToScreenRect(cropRect: CropRect, imageRect: Rect): Rect {
    return Rect(
        left = imageRect.left + cropRect.left * imageRect.width,
        top = imageRect.top + cropRect.top * imageRect.height,
        right = imageRect.left + cropRect.right * imageRect.width,
        bottom = imageRect.top + cropRect.bottom * imageRect.height
    )
}

/**
 * Converts screen pixel coordinates back to normalized CropRect.
 */
fun screenRectToCropRect(screenRect: Rect, imageRect: Rect): CropRect {
    return CropRect(
        left = (screenRect.left - imageRect.left) / imageRect.width,
        top = (screenRect.top - imageRect.top) / imageRect.height,
        right = (screenRect.right - imageRect.left) / imageRect.width,
        bottom = (screenRect.bottom - imageRect.top) / imageRect.height
    )
}

private fun DrawScope.drawDimOverlay(cropRect: Rect, imageRect: Rect) {
    val dimColor = Color.Black.copy(alpha = 0.5f)

    // Create a path that covers the entire image area minus the crop rectangle
    val path = Path().apply {
        // Outer rectangle (full image area)
        addRect(imageRect)
        // Inner rectangle (crop area) - using evenOdd fill will create a hole
        addRect(cropRect)
    }

    drawPath(
        path = path,
        color = dimColor,
        style = androidx.compose.ui.graphics.drawscope.Fill
    )
}

private fun DrawScope.drawCropBorder(cropRect: Rect) {
    val borderWidth = 2.dp.toPx()
    val shadowWidth = 4.dp.toPx()

    // Draw dark shadow/outline first (for contrast)
    drawRect(
        color = Color.Black.copy(alpha = 0.5f),
        topLeft = Offset(cropRect.left - shadowWidth / 2, cropRect.top - shadowWidth / 2),
        size = Size(cropRect.width + shadowWidth, cropRect.height + shadowWidth),
        style = Stroke(width = shadowWidth)
    )

    // Draw white border on top
    drawRect(
        color = Color.White,
        topLeft = cropRect.topLeft,
        size = cropRect.size,
        style = Stroke(width = borderWidth)
    )

    // Draw grid lines (rule of thirds)
    val gridColor = Color.White.copy(alpha = 0.5f)
    val gridLineWidth = 1.dp.toPx()

    // Vertical lines
    val thirdWidth = cropRect.width / 3
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left + thirdWidth, cropRect.top),
        end = Offset(cropRect.left + thirdWidth, cropRect.bottom),
        strokeWidth = gridLineWidth
    )
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left + 2 * thirdWidth, cropRect.top),
        end = Offset(cropRect.left + 2 * thirdWidth, cropRect.bottom),
        strokeWidth = gridLineWidth
    )

    // Horizontal lines
    val thirdHeight = cropRect.height / 3
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left, cropRect.top + thirdHeight),
        end = Offset(cropRect.right, cropRect.top + thirdHeight),
        strokeWidth = gridLineWidth
    )
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left, cropRect.top + 2 * thirdHeight),
        end = Offset(cropRect.right, cropRect.top + 2 * thirdHeight),
        strokeWidth = gridLineWidth
    )
}

private fun DrawScope.drawCornerHandles(cropRect: Rect) {
    val handleSize = 16.dp.toPx()
    val handleColor = Color.White
    val shadowColor = Color.Black.copy(alpha = 0.5f)

    val corners = listOf(
        cropRect.topLeft,
        Offset(cropRect.right, cropRect.top),
        Offset(cropRect.left, cropRect.bottom),
        cropRect.bottomRight
    )

    corners.forEach { corner ->
        // Draw shadow
        drawCircle(
            color = shadowColor,
            radius = handleSize / 2 + 2.dp.toPx(),
            center = corner
        )
        // Draw handle
        drawCircle(
            color = handleColor,
            radius = handleSize / 2,
            center = corner
        )
    }
}

private fun DrawScope.drawEdgeHandles(cropRect: Rect) {
    val handleWidth = 24.dp.toPx()
    val handleHeight = 6.dp.toPx()
    val handleColor = Color.White
    val shadowColor = Color.Black.copy(alpha = 0.5f)

    // Top and bottom edge centers
    val topCenter = Offset(cropRect.center.x, cropRect.top)
    val bottomCenter = Offset(cropRect.center.x, cropRect.bottom)

    // Left and right edge centers
    val leftCenter = Offset(cropRect.left, cropRect.center.y)
    val rightCenter = Offset(cropRect.right, cropRect.center.y)

    // Draw horizontal edge handles (top, bottom)
    listOf(topCenter, bottomCenter).forEach { center ->
        // Shadow
        drawRoundRect(
            color = shadowColor,
            topLeft = Offset(center.x - handleWidth / 2 - 1.dp.toPx(), center.y - handleHeight / 2 - 1.dp.toPx()),
            size = Size(handleWidth + 2.dp.toPx(), handleHeight + 2.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(handleHeight / 2)
        )
        // Handle
        drawRoundRect(
            color = handleColor,
            topLeft = Offset(center.x - handleWidth / 2, center.y - handleHeight / 2),
            size = Size(handleWidth, handleHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(handleHeight / 2)
        )
    }

    // Draw vertical edge handles (left, right)
    listOf(leftCenter, rightCenter).forEach { center ->
        // Shadow
        drawRoundRect(
            color = shadowColor,
            topLeft = Offset(center.x - handleHeight / 2 - 1.dp.toPx(), center.y - handleWidth / 2 - 1.dp.toPx()),
            size = Size(handleHeight + 2.dp.toPx(), handleWidth + 2.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(handleHeight / 2)
        )
        // Handle
        drawRoundRect(
            color = handleColor,
            topLeft = Offset(center.x - handleHeight / 2, center.y - handleWidth / 2),
            size = Size(handleHeight, handleWidth),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(handleHeight / 2)
        )
    }
}

/**
 * Detects which handle or part of the crop rectangle the touch point is interacting with.
 *
 * @param touchPoint The touch position in screen coordinates
 * @param screenCropRect The crop rectangle in screen coordinates
 * @param touchThreshold The threshold distance for handle hit detection (typically 44dp for Material guidelines)
 * @return The type of handle being touched
 */
fun detectHandle(
    touchPoint: Offset,
    screenCropRect: Rect,
    touchThreshold: Float
): HandleType {
    // Check corners first (higher priority)
    if (isNearPoint(touchPoint, screenCropRect.topLeft, touchThreshold)) return HandleType.TOP_LEFT
    if (isNearPoint(touchPoint, Offset(screenCropRect.right, screenCropRect.top), touchThreshold)) return HandleType.TOP_RIGHT
    if (isNearPoint(touchPoint, Offset(screenCropRect.left, screenCropRect.bottom), touchThreshold)) return HandleType.BOTTOM_LEFT
    if (isNearPoint(touchPoint, screenCropRect.bottomRight, touchThreshold)) return HandleType.BOTTOM_RIGHT

    // Check edges
    val edgeTouchThreshold = touchThreshold / 2

    // Top edge
    if (touchPoint.y in (screenCropRect.top - edgeTouchThreshold)..(screenCropRect.top + edgeTouchThreshold) &&
        touchPoint.x in screenCropRect.left..screenCropRect.right
    ) return HandleType.TOP

    // Bottom edge
    if (touchPoint.y in (screenCropRect.bottom - edgeTouchThreshold)..(screenCropRect.bottom + edgeTouchThreshold) &&
        touchPoint.x in screenCropRect.left..screenCropRect.right
    ) return HandleType.BOTTOM

    // Left edge
    if (touchPoint.x in (screenCropRect.left - edgeTouchThreshold)..(screenCropRect.left + edgeTouchThreshold) &&
        touchPoint.y in screenCropRect.top..screenCropRect.bottom
    ) return HandleType.LEFT

    // Right edge
    if (touchPoint.x in (screenCropRect.right - edgeTouchThreshold)..(screenCropRect.right + edgeTouchThreshold) &&
        touchPoint.y in screenCropRect.top..screenCropRect.bottom
    ) return HandleType.RIGHT

    // Check body (inside the rectangle)
    if (screenCropRect.contains(touchPoint)) return HandleType.BODY

    return HandleType.NONE
}

private fun isNearPoint(touchPoint: Offset, targetPoint: Offset, threshold: Float): Boolean {
    return (touchPoint - targetPoint).getDistance() < threshold
}
