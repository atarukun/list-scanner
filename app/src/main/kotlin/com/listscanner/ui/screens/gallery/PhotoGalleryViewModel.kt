package com.listscanner.ui.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listscanner.data.entity.Photo
import com.listscanner.domain.Result
import com.listscanner.domain.repository.ListRepository
import com.listscanner.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PhotoGalleryViewModel(
    private val photoRepository: PhotoRepository,
    private val listRepository: ListRepository
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Empty : UiState
        data class Success(val photos: List<Photo>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _deletionError = MutableSharedFlow<String>()
    val deletionError: SharedFlow<String> = _deletionError.asSharedFlow()

    init {
        viewModelScope.launch {
            photoRepository.getAllPhotos()
                .map { photos -> photos.sortedByDescending { it.timestamp } }
                .collect { photos ->
                    _uiState.value = if (photos.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Success(photos)
                    }
                }
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch {
            when (val result = photoRepository.deletePhoto(photoId)) {
                is Result.Success -> {
                    // List updates reactively via Flow - no manual refresh needed
                }
                is Result.Failure -> _deletionError.emit(result.message)
            }
        }
    }

    suspend fun getListIdForPhoto(photoId: Long): Long? {
        return when (val result = listRepository.getListByPhotoId(photoId)) {
            is Result.Success -> result.data?.id
            is Result.Failure -> null
        }
    }
}
