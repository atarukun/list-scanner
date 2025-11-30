package com.listscanner.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Threshold constants for gesture direction detection.
 * These values can be tuned based on user testing.
 */
private const val MIN_HORIZONTAL_THRESHOLD_DP = 50f
private const val MAX_ANGLE_FROM_HORIZONTAL_DEG = 30f
private const val DIRECTION_LOCK_THRESHOLD_DP = 15f

/**
 * Direction of the detected gesture.
 */
enum class SwipeDirection {
    NONE,
    HORIZONTAL,
    VERTICAL
}

/**
 * A modifier that filters pointer events to prevent accidental horizontal swipes
 * during vertical scrolling.
 *
 * This modifier intercepts touch events at the Initial pass and determines if the
 * gesture is primarily horizontal or vertical. The key insight is:
 * - We NEVER block vertical gestures (scrolling must always work)
 * - We only need to reset the SwipeToDismissBox when vertical scrolling is detected
 *
 * For horizontal swipes to be recognized:
 * 1. The gesture angle must be within [MAX_ANGLE_FROM_HORIZONTAL_DEG] degrees of horizontal
 * 2. The horizontal displacement must exceed [MIN_HORIZONTAL_THRESHOLD_DP]
 *
 * @param density Screen density for dp to px conversion
 * @param enabled Whether the swipe should be enabled (set to false when vertical scroll detected)
 * @param onVerticalScrollDetected Callback when vertical scroll is detected, used to disable swipe
 */
fun Modifier.directionalSwipeDetector(
    density: Density,
    onVerticalScrollDetected: () -> Unit
): Modifier = this.pointerInput(Unit) {
    val directionLockThresholdPx = with(density) { DIRECTION_LOCK_THRESHOLD_DP.dp.toPx() }

    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Initial)
        var lockedDirection = SwipeDirection.NONE
        var totalDelta = Offset.Zero

        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: break

            if (!change.pressed) break

            totalDelta += change.positionChange()
            val deltaX = abs(totalDelta.x)
            val deltaY = abs(totalDelta.y)

            // Determine direction if not yet locked
            if (lockedDirection == SwipeDirection.NONE) {
                val totalMovement = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)

                if (totalMovement >= directionLockThresholdPx) {
                    // Calculate angle from horizontal (0° = right, 90° = up, -90° = down)
                    val angleRadians = atan2(totalDelta.y, abs(totalDelta.x))
                    val angleDegrees = Math.toDegrees(angleRadians.toDouble())

                    // Check if gesture is within horizontal threshold
                    val isNearHorizontal = abs(angleDegrees) <= MAX_ANGLE_FROM_HORIZONTAL_DEG

                    lockedDirection = if (isNearHorizontal) {
                        SwipeDirection.HORIZONTAL
                    } else {
                        SwipeDirection.VERTICAL
                    }

                    // If vertical scroll detected, notify to reset/disable swipe
                    if (lockedDirection == SwipeDirection.VERTICAL) {
                        onVerticalScrollDetected()
                    }
                }
            }

            // IMPORTANT: Never consume events here - let them pass through naturally
            // The SwipeToDismissBox will be reset via state when vertical scroll is detected

        } while (event.changes.any { it.pressed })
    }
}

/**
 * Composable helper that manages swipe-to-dismiss state with directional filtering.
 *
 * This resets the SwipeToDismissBox to Settled state when vertical scrolling is detected,
 * preventing accidental delete reveals during scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberDirectionalSwipeState(
    dismissState: SwipeToDismissBoxState,
    density: Density
): Modifier {
    var shouldResetSwipe by remember { mutableStateOf(false) }

    // Reset swipe state when vertical scroll is detected
    LaunchedEffect(shouldResetSwipe) {
        if (shouldResetSwipe) {
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            shouldResetSwipe = false
        }
    }

    return Modifier.directionalSwipeDetector(
        density = density,
        onVerticalScrollDetected = { shouldResetSwipe = true }
    )
}
