package com.listscanner.ui.screens.regionselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.listscanner.domain.Result
import com.listscanner.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Normalized crop rectangle with coordinates in 0.0-1.0 range.
 * This allows resolution-independent storage and calculations.
 */
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        private const val DEFAULT_SIZE_FRACTION = 0.8f
        private const val MIN_SIZE_FRACTION = 0.1f

        fun centered(widthFraction: Float = DEFAULT_SIZE_FRACTION, heightFraction: Float = DEFAULT_SIZE_FRACTION): CropRect {
            val marginH = (1f - widthFraction) / 2
            val marginV = (1f - heightFraction) / 2
            return CropRect(marginH, marginV, 1f - marginH, 1f - marginV)
        }

        fun minSizeFraction(): Float = MIN_SIZE_FRACTION
    }

    /**
     * Ensures the crop rectangle stays within image bounds (0.0-1.0).
     */
    fun coerceInBounds(): CropRect {
        val clampedLeft = left.coerceIn(0f, 1f - MIN_SIZE_FRACTION)
        val clampedTop = top.coerceIn(0f, 1f - MIN_SIZE_FRACTION)
        val clampedRight = right.coerceIn(clampedLeft + MIN_SIZE_FRACTION, 1f)
        val clampedBottom = bottom.coerceIn(clampedTop + MIN_SIZE_FRACTION, 1f)
        return CropRect(clampedLeft, clampedTop, clampedRight, clampedBottom)
    }

    /**
     * Ensures the crop rectangle meets minimum size requirements.
     */
    fun enforceMinSize(): CropRect {
        var newRight = right
        var newBottom = bottom

        if (width < MIN_SIZE_FRACTION) {
            newRight = left + MIN_SIZE_FRACTION
            if (newRight > 1f) {
                newRight = 1f
            }
        }

        if (height < MIN_SIZE_FRACTION) {
            newBottom = top + MIN_SIZE_FRACTION
            if (newBottom > 1f) {
                newBottom = 1f
            }
        }

        return CropRect(left, top, newRight, newBottom)
    }

    /**
     * Converts normalized coordinates to pixel coordinates.
     */
    fun toPixelRect(imageWidth: Int, imageHeight: Int): PixelRect {
        return PixelRect(
            left = (left * imageWidth).toInt(),
            top = (top * imageHeight).toInt(),
            right = (right * imageWidth).toInt(),
            bottom = (bottom * imageHeight).toInt()
        )
    }
}

/**
 * Pixel-based rectangle for actual image cropping operations.
 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Serializes CropRect to JSON string for navigation.
 */
fun CropRect.toJson(): String = Gson().toJson(this)

/**
 * Deserializes JSON string to CropRect.
 * Returns null if parsing fails.
 */
fun String.toCropRect(): CropRect? = try {
    Gson().fromJson(this, CropRect::class.java)
} catch (e: Exception) {
    Timber.w(e, "Failed to parse CropRect from JSON: $this")
    null
}

class RegionSelectionViewModel(
    private val photoRepository: PhotoRepository,
    private val photoId: Long
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val filePath: String, val cropRect: CropRect) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _cropRect = MutableStateFlow(CropRect.centered())
    val cropRect: StateFlow<CropRect> = _cropRect.asStateFlow()

    private var filePath: String = ""

    init {
        loadPhoto()
    }

    private fun loadPhoto() {
        viewModelScope.launch {
            when (val result = photoRepository.getPhotoById(photoId)) {
                is Result.Success -> {
                    val photo = result.data
                    if (photo != null) {
                        filePath = photo.filePath
                        _uiState.value = UiState.Success(filePath, _cropRect.value)
                    } else {
                        _uiState.value = UiState.Error("Photo not found")
                    }
                }
                is Result.Failure -> {
                    _uiState.value = UiState.Error("Failed to load photo: ${result.message}")
                }
            }
        }
    }

    fun updateCropRect(newRect: CropRect) {
        val constrainedRect = newRect.coerceInBounds().enforceMinSize()
        _cropRect.value = constrainedRect

        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            _uiState.value = currentState.copy(cropRect = constrainedRect)
        }
    }

    /**
     * Returns the current crop rectangle in pixel coordinates for the given image dimensions.
     */
    fun getCropPixelRect(imageWidth: Int, imageHeight: Int): PixelRect {
        return _cropRect.value.toPixelRect(imageWidth, imageHeight)
    }
}
