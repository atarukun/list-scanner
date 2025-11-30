package com.listscanner.ui.screens.camera

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.device.PhotoFileManager
import com.listscanner.domain.Result
import com.listscanner.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CameraViewModel(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    sealed interface UiState {
        data object Initializing : UiState
        data object Ready : UiState
        data object Capturing : UiState
        data class Success(val photoId: Long) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Initializing)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun setReady() {
        _uiState.value = UiState.Ready
    }

    fun setError(message: String) {
        _uiState.value = UiState.Error(message)
    }

    fun capturePhoto(context: Context, imageCapture: ImageCapture) {
        if (_uiState.value == UiState.Capturing) return
        _uiState.value = UiState.Capturing

        val photosDir = PhotoFileManager.getPhotosDirectory(context)
        val tempFile = File(photosDir, "temp_capture.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputResults: ImageCapture.OutputFileResults) {
                    viewModelScope.launch {
                        processAndSavePhoto(context, tempFile)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.value = UiState.Error("Photo capture failed. Tap to retry.")
                }
            }
        )
    }

    private suspend fun processAndSavePhoto(context: Context, tempFile: File) {
        val finalFile = PhotoFileManager.generatePhotoFile(context)
        val compressed = PhotoFileManager.compressToJpeg(tempFile, finalFile, 85)

        if (!compressed) {
            tempFile.delete()
            _uiState.value = UiState.Error("Failed to process photo. Tap to retry.")
            return
        }

        savePhotoToDatabase(finalFile.absolutePath)
    }

    suspend fun savePhotoToDatabase(filePath: String) {
        val photo = Photo(
            filePath = filePath,
            timestamp = System.currentTimeMillis(),
            ocrStatus = OcrStatus.PENDING
        )

        when (val result = photoRepository.insertPhoto(photo)) {
            is Result.Success -> {
                _uiState.value = UiState.Success(result.data)
            }
            is Result.Failure -> {
                File(filePath).delete()
                _uiState.value = UiState.Error("Failed to save photo. Tap to retry.")
            }
        }
    }
}
