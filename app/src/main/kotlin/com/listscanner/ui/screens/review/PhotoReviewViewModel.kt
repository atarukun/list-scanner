package com.listscanner.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.data.repository.PrivacyConsentRepository
import com.listscanner.data.repository.UsageTrackingRepository
import com.listscanner.device.ImageCropService
import com.listscanner.device.NetworkConnectivityService
import com.listscanner.device.CloudVisionService
import com.listscanner.domain.Result
import com.listscanner.domain.repository.PhotoRepository
import com.listscanner.domain.service.ListCreationService
import com.listscanner.ui.screens.regionselection.CropRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

sealed class OcrErrorType {
    data object EmptyText : OcrErrorType()
    data object NoItems : OcrErrorType()
    data object ApiError : OcrErrorType()
    data object NetworkError : OcrErrorType()
    data object FileError : OcrErrorType()
    data object CropError : OcrErrorType()
}

data class OcrErrorState(
    val message: String,
    val errorType: OcrErrorType
)

class PhotoReviewViewModel(
    private val photoRepository: PhotoRepository,
    private val cloudVisionService: CloudVisionService,
    private val imageCropService: ImageCropService,
    private val listCreationService: ListCreationService,
    private val networkConnectivityService: NetworkConnectivityService,
    private val privacyConsentRepository: PrivacyConsentRepository,
    private val usageTrackingRepository: UsageTrackingRepository,
    private val photoId: Long
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val photo: Photo) : UiState
        data class Processing(val photo: Photo) : UiState
        data class Error(val message: String) : UiState
        data object Deleted : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _deletionError = MutableSharedFlow<String>()
    val deletionError: SharedFlow<String> = _deletionError.asSharedFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _ocrErrorDialogState = MutableStateFlow<OcrErrorState?>(null)
    val ocrErrorDialogState: StateFlow<OcrErrorState?> = _ocrErrorDialogState.asStateFlow()

    private val _navigateToList = MutableSharedFlow<Long>()
    val navigateToList: SharedFlow<Long> = _navigateToList.asSharedFlow()

    val isNetworkAvailable: StateFlow<Boolean> = networkConnectivityService
        .observeNetworkState()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _showConsentDialog = MutableStateFlow(false)
    val showConsentDialog: StateFlow<Boolean> = _showConsentDialog.asStateFlow()

    private val _showCostWarningDialog = MutableStateFlow(false)
    val showCostWarningDialog: StateFlow<Boolean> = _showCostWarningDialog.asStateFlow()

    private val _currentWeeklyUsage = MutableStateFlow(0)
    val currentWeeklyUsage: StateFlow<Int> = _currentWeeklyUsage.asStateFlow()

    private var ocrJob: Job? = null
    private var pendingCropRect: CropRect? = null

    init {
        loadPhoto()
    }

    private fun loadPhoto() {
        viewModelScope.launch {
            when (val result = photoRepository.getPhotoById(photoId)) {
                is Result.Success -> {
                    val photo = result.data
                    if (photo == null) {
                        _uiState.value = UiState.Error("Photo not found")
                    } else if (!File(photo.filePath).exists()) {
                        _uiState.value = UiState.Error("Photo file missing")
                    } else {
                        _uiState.value = UiState.Success(photo)
                    }
                }
                is Result.Failure -> {
                    _uiState.value = UiState.Error(result.message)
                }
            }
        }
    }

    fun deletePhoto() {
        viewModelScope.launch {
            when (val result = photoRepository.deletePhoto(photoId)) {
                is Result.Success -> _uiState.value = UiState.Deleted
                is Result.Failure -> _deletionError.emit(result.message)
            }
        }
    }

    fun processOcr() {
        if (_isProcessing.value) return
        if (!isNetworkAvailable.value) return

        viewModelScope.launch {
            if (!privacyConsentRepository.hasUserConsented()) {
                _showConsentDialog.value = true
                return@launch
            }
            performOcrProcessing()
        }
    }

    /**
     * Process OCR with crop coordinates.
     * Crops the image to the selected region before sending to OCR.
     */
    fun processCroppedOcr(cropRect: CropRect) {
        pendingCropRect = cropRect

        if (_isProcessing.value) return
        if (!isNetworkAvailable.value) return

        viewModelScope.launch {
            if (!privacyConsentRepository.hasUserConsented()) {
                _showConsentDialog.value = true
                return@launch
            }
            performCroppedOcrProcessing(cropRect)
        }
    }

    private suspend fun performCroppedOcrProcessing(cropRect: CropRect) {
        _isProcessing.value = true
        try {
            val photo = (uiState.value as? UiState.Success)?.photo ?: run {
                when (val photoResult = photoRepository.getPhotoById(photoId)) {
                    is Result.Success -> photoResult.data
                    is Result.Failure -> null
                }
            }

            if (photo == null) {
                showOcrError("Photo file not found or corrupted.", OcrErrorType.FileError)
                return
            }

            _uiState.value = UiState.Processing(photo)
            photoRepository.updateOcrStatus(photoId, OcrStatus.PROCESSING)

            when (val cropResult = imageCropService.cropImage(photo.filePath, cropRect)) {
                is Result.Success -> {
                    val croppedBitmap = cropResult.data
                    try {
                        when (val ocrResult = cloudVisionService.recognizeTextFromBitmap(croppedBitmap)) {
                            is Result.Success -> handleOcrSuccess(ocrResult.data, photo)
                            is Result.Failure -> handleOcrFailure(ocrResult, photo)
                        }
                    } finally {
                        croppedBitmap.recycle()
                    }
                }
                is Result.Failure -> {
                    photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
                    val revertedPhoto = photo.copy(ocrStatus = OcrStatus.PENDING)
                    _uiState.value = UiState.Success(revertedPhoto)
                    val message = if (cropResult.message.contains("too large", ignoreCase = true)) {
                        "Image too large to crop. Try scanning the full image instead."
                    } else {
                        "Failed to crop image. Please try again."
                    }
                    showOcrError(message, OcrErrorType.CropError, cropResult.exception)
                }
            }
        } catch (e: CancellationException) {
            photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
            throw e
        } finally {
            _isProcessing.value = false
            pendingCropRect = null
        }
    }

    private suspend fun handleOcrSuccess(ocrText: String, photo: Photo) {
        if (ocrText.isBlank()) {
            photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
            val revertedPhoto = photo.copy(ocrStatus = OcrStatus.PENDING)
            _uiState.value = UiState.Success(revertedPhoto)
            showOcrError(
                "No text recognized. Try retaking the photo with better lighting.",
                OcrErrorType.EmptyText
            )
            return
        }

        when (val listResult = listCreationService.createListFromOcrResults(photoId, ocrText)) {
            is Result.Success -> {
                usageTrackingRepository.incrementUsage()
                checkCostWarning()

                photoRepository.updateOcrStatus(photoId, OcrStatus.COMPLETED)
                val updatedPhoto = photo.copy(ocrStatus = OcrStatus.COMPLETED)
                _uiState.value = UiState.Success(updatedPhoto)
                _navigateToList.emit(listResult.data)
            }
            is Result.Failure -> {
                photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
                val revertedPhoto = photo.copy(ocrStatus = OcrStatus.PENDING)
                _uiState.value = UiState.Success(revertedPhoto)
                val (message, errorType) = classifyOcrError(listResult.message)
                showOcrError(message, errorType, listResult.exception)
            }
        }
    }

    private suspend fun handleOcrFailure(ocrResult: Result.Failure, photo: Photo) {
        photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
        val revertedPhoto = photo.copy(ocrStatus = OcrStatus.PENDING)
        _uiState.value = UiState.Success(revertedPhoto)
        val (message, errorType) = classifyOcrError(ocrResult.message)
        showOcrError(message, errorType, ocrResult.exception)
    }

    fun onConsentAccepted() {
        viewModelScope.launch {
            privacyConsentRepository.setUserConsent(true)
            _showConsentDialog.value = false
            val cropRect = pendingCropRect
            if (cropRect != null) {
                performCroppedOcrProcessing(cropRect)
            } else {
                performOcrProcessing()
            }
        }
    }

    fun onConsentDeclined() {
        _showConsentDialog.value = false
    }

    private suspend fun checkCostWarning() {
        if (usageTrackingRepository.shouldShowCostWarning()) {
            _currentWeeklyUsage.value = usageTrackingRepository.getWeeklyUsage()
            _showCostWarningDialog.value = true
        }
    }

    fun onCostWarningDismissed() {
        viewModelScope.launch {
            usageTrackingRepository.markWarningShown()
            _showCostWarningDialog.value = false
        }
    }

    fun dismissOcrErrorDialog() {
        Timber.d("OCR error dialog dismissed by user")
        _ocrErrorDialogState.value = null
    }

    fun retryFromErrorDialog() {
        _ocrErrorDialogState.value = null
        processOcr()
    }

    private fun showOcrError(message: String, errorType: OcrErrorType, exception: Throwable? = null) {
        Timber.e(exception, "OCR failed: %s", message)
        _ocrErrorDialogState.value = OcrErrorState(message, errorType)
    }

    private fun classifyOcrError(errorMessage: String): Pair<String, OcrErrorType> {
        return when {
            errorMessage.contains("file not found", ignoreCase = true) ||
            errorMessage.contains("corrupted", ignoreCase = true) ->
                "Photo file not found or corrupted." to OcrErrorType.FileError

            errorMessage.contains("network", ignoreCase = true) ||
            errorMessage.contains("unable to reach", ignoreCase = true) ->
                "Network unavailable. Please check your connection and try again." to OcrErrorType.NetworkError

            errorMessage.contains("No list items", ignoreCase = true) ->
                errorMessage to OcrErrorType.NoItems

            else ->
                "OCR processing failed. Please try again." to OcrErrorType.ApiError
        }
    }

    private fun performOcrProcessing() {
        ocrJob = viewModelScope.launch {
            _isProcessing.value = true
            try {
                photoRepository.updateOcrStatus(photoId, OcrStatus.PROCESSING)

                when (val photoResult = photoRepository.getPhotoById(photoId)) {
                    is Result.Success -> {
                        val photo = photoResult.data
                        if (photo == null) {
                            photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
                            showOcrError(
                                "Photo file not found or corrupted.",
                                OcrErrorType.FileError
                            )
                            return@launch
                        }

                        _uiState.value = UiState.Processing(photo)

                        when (val ocrResult = cloudVisionService.recognizeText(photo.filePath)) {
                            is Result.Success -> {
                                // AC 1: Check for empty OCR result
                                if (ocrResult.data.isBlank()) {
                                    photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
                                    val revertedPhoto = photo.copy(ocrStatus = OcrStatus.PENDING)
                                    _uiState.value = UiState.Success(revertedPhoto)
                                    showOcrError(
                                        "No text recognized. Try retaking the photo with better lighting.",
                                        OcrErrorType.EmptyText
                                    )
                                    return@launch
                                }

                                when (val listResult = listCreationService.createListFromOcrResults(photoId, ocrResult.data)) {
                                    is Result.Success -> {
                                        usageTrackingRepository.incrementUsage()
                                        checkCostWarning()

                                        photoRepository.updateOcrStatus(photoId, OcrStatus.COMPLETED)
                                        val updatedPhoto = photo.copy(ocrStatus = OcrStatus.COMPLETED)
                                        _uiState.value = UiState.Success(updatedPhoto)
                                        _navigateToList.emit(listResult.data)
                                    }
                                    is Result.Failure -> {
                                        photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
                                        val revertedPhoto = photo.copy(ocrStatus = OcrStatus.PENDING)
                                        _uiState.value = UiState.Success(revertedPhoto)
                                        val (message, errorType) = classifyOcrError(listResult.message)
                                        showOcrError(message, errorType, listResult.exception)
                                    }
                                }
                            }
                            is Result.Failure -> {
                                photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
                                val revertedPhoto = photo.copy(ocrStatus = OcrStatus.PENDING)
                                _uiState.value = UiState.Success(revertedPhoto)
                                val (message, errorType) = classifyOcrError(ocrResult.message)
                                showOcrError(message, errorType, ocrResult.exception)
                            }
                        }
                    }
                    is Result.Failure -> {
                        photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
                        val (message, errorType) = classifyOcrError(photoResult.message)
                        showOcrError(message, errorType, photoResult.exception)
                    }
                }
            } catch (e: CancellationException) {
                photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
                throw e
            } finally {
                _isProcessing.value = false
                ocrJob = null
            }
        }
    }

    fun cancelOcrProcessing() {
        ocrJob?.cancel()
        viewModelScope.launch {
            photoRepository.updateOcrStatus(photoId, OcrStatus.PENDING)
            when (val result = photoRepository.getPhotoById(photoId)) {
                is Result.Success -> {
                    result.data?.let { photo ->
                        _uiState.value = UiState.Success(photo.copy(ocrStatus = OcrStatus.PENDING))
                    }
                }
                is Result.Failure -> { /* Ignore, photo still exists */ }
            }
        }
    }
}
