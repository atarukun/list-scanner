package com.listscanner.ui.screens.listsoverview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listscanner.data.model.ListWithCounts
import com.listscanner.domain.Result
import com.listscanner.domain.repository.ListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber

class ListsOverviewViewModel(
    private val listRepository: ListRepository
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Empty : UiState
        data class Success(val lists: List<ListWithCounts>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _listPendingDeletion = MutableStateFlow<ListWithCounts?>(null)
    val listPendingDeletion: StateFlow<ListWithCounts?> = _listPendingDeletion.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun requestDeleteList(listWithCounts: ListWithCounts) {
        _listPendingDeletion.value = listWithCounts
    }

    fun cancelDeleteList() {
        _listPendingDeletion.value = null
    }

    fun confirmDeleteList() {
        viewModelScope.launch {
            val listWithCounts = _listPendingDeletion.value ?: return@launch

            when (val result = listRepository.deleteListAndResetPhoto(listWithCounts.list.id, listWithCounts.list.photoId)) {
                is Result.Success -> {
                    _listPendingDeletion.value = null
                }
                is Result.Failure -> {
                    Timber.e("Failed to delete list: ${result.message}")
                    _errorMessage.value = "Failed to delete list"
                    _listPendingDeletion.value = null
                }
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    init {
        viewModelScope.launch {
            listRepository.getAllListsWithCounts()
                .catch { e ->
                    _uiState.value = UiState.Error(e.message ?: "Failed to load lists")
                }
                .collect { lists ->
                    _uiState.value = if (lists.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Success(lists)
                    }
                }
        }
    }
}
