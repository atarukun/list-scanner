package com.listscanner.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listscanner.data.entity.Item
import com.listscanner.data.entity.ShoppingList
import com.listscanner.domain.Result
import com.listscanner.domain.repository.ItemRepository
import com.listscanner.domain.repository.ListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

class ListDetailViewModel(
    private val listRepository: ListRepository,
    private val itemRepository: ItemRepository,
    private val listId: Long
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val list: ShoppingList,
            val items: List<Item>,
            val totalItemCount: Int
        ) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _hideChecked = MutableStateFlow(false)
    val hideChecked: StateFlow<Boolean> = _hideChecked.asStateFlow()

    private val _editingItemId = MutableStateFlow<Long?>(null)
    val editingItemId: StateFlow<Long?> = _editingItemId.asStateFlow()

    private val _isAddingItem = MutableStateFlow(false)
    val isAddingItem: StateFlow<Boolean> = _isAddingItem.asStateFlow()

    private val _itemPendingDeletion = MutableStateFlow<Item?>(null)
    val itemPendingDeletion: StateFlow<Item?> = _itemPendingDeletion.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isRenaming = MutableStateFlow(false)
    val isRenaming: StateFlow<Boolean> = _isRenaming.asStateFlow()

    private val _isDeletingList = MutableStateFlow(false)
    val isDeletingList: StateFlow<Boolean> = _isDeletingList.asStateFlow()

    private val _listDeleted = MutableStateFlow(false)
    val listDeleted: StateFlow<Boolean> = _listDeleted.asStateFlow()

    private val _reorderedItems = MutableStateFlow<List<Item>?>(null)

    fun requestDeleteItem(item: Item) {
        _itemPendingDeletion.value = item
    }

    fun cancelDeleteItem() {
        _itemPendingDeletion.value = null
    }

    fun confirmDeleteItem() {
        viewModelScope.launch {
            val item = _itemPendingDeletion.value ?: return@launch

            when (val result = itemRepository.deleteItem(item.id)) {
                is Result.Success -> {
                    _itemPendingDeletion.value = null
                }
                is Result.Failure -> {
                    Timber.e("Failed to delete item: ${result.message}")
                    _errorMessage.value = "Failed to delete item"
                    _itemPendingDeletion.value = null
                }
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun startRenaming() {
        _isRenaming.value = true
    }

    fun cancelRenaming() {
        _isRenaming.value = false
    }

    fun renameList(newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) return

        val finalName = if (trimmedName.length > 50) trimmedName.take(50) else trimmedName

        viewModelScope.launch {
            val currentState = _uiState.value as? UiState.Success ?: return@launch
            val updatedList = currentState.list.copy(name = finalName)

            when (val result = listRepository.updateList(updatedList)) {
                is Result.Success -> {
                    _uiState.value = currentState.copy(list = updatedList)
                    _isRenaming.value = false
                }
                is Result.Failure -> {
                    Timber.e("Failed to rename list: ${result.message}")
                    _errorMessage.value = "Failed to rename list"
                }
            }
        }
    }

    fun requestDeleteList() {
        _isDeletingList.value = true
    }

    fun cancelDeleteList() {
        _isDeletingList.value = false
    }

    fun confirmDeleteList() {
        viewModelScope.launch {
            val currentState = _uiState.value as? UiState.Success ?: return@launch
            val list = currentState.list

            when (val result = listRepository.deleteListAndResetPhoto(list.id, list.photoId)) {
                is Result.Success -> {
                    _listDeleted.value = true
                    _isDeletingList.value = false
                }
                is Result.Failure -> {
                    Timber.e("Failed to delete list: ${result.message}")
                    _errorMessage.value = "Failed to delete list"
                    _isDeletingList.value = false
                }
            }
        }
    }

    fun toggleHideChecked() {
        _hideChecked.value = !_hideChecked.value
    }

    fun startEditing(itemId: Long) {
        _editingItemId.value = itemId
    }

    fun cancelEditing() {
        _editingItemId.value = null
    }

    fun startAddingItem() {
        cancelEditing()
        _isAddingItem.value = true
    }

    fun cancelAddingItem() {
        _isAddingItem.value = false
    }

    fun addItem(text: String) {
        viewModelScope.launch {
            val trimmedText = text.trim()

            if (trimmedText.isEmpty()) {
                return@launch
            }

            val finalText = if (trimmedText.length > 200) {
                trimmedText.take(200)
            } else {
                trimmedText
            }

            val maxPositionResult = itemRepository.getMaxPositionForList(listId)
            val maxPosition = when (maxPositionResult) {
                is Result.Success -> maxPositionResult.data
                is Result.Failure -> {
                    Timber.e("Failed to get max position: ${maxPositionResult.message}")
                    return@launch
                }
            }

            val newItem = Item(
                listId = listId,
                text = finalText,
                isChecked = false,
                position = maxPosition + 1
            )

            when (val result = itemRepository.insertItem(newItem)) {
                is Result.Success -> {
                    cancelAddingItem()
                }
                is Result.Failure -> {
                    Timber.e("Failed to add item: ${result.message}")
                }
            }
        }
    }

    fun updateItemText(itemId: Long, newText: String) {
        viewModelScope.launch {
            val trimmedText = newText.trim()

            if (trimmedText.isEmpty()) {
                return@launch
            }

            val finalText = if (trimmedText.length > 200) {
                trimmedText.take(200)
            } else {
                trimmedText
            }

            when (val result = itemRepository.updateItemText(itemId, finalText)) {
                is Result.Success -> {
                    cancelEditing()
                }
                is Result.Failure -> {
                    Timber.e("Failed to update item text: ${result.message}")
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            val listResult = listRepository.getListById(listId)
            when (listResult) {
                is Result.Success -> {
                    val list = listResult.data
                    if (list == null) {
                        _uiState.value = UiState.Error("List not found")
                        return@launch
                    }
                    combine(
                        itemRepository.getItemsForList(listId),
                        _hideChecked,
                        _reorderedItems
                    ) { dbItems, hideChecked, reordered ->
                        // Use reordered items if available (preserving drag order), otherwise sort db items
                        val itemsToDisplay = if (reordered != null) {
                            // During drag, use reordered items as-is (no sorting)
                            reordered
                        } else {
                            // Normal mode: sort by checked status then position
                            dbItems.sortedWith(
                                compareBy<Item> { it.isChecked }.thenBy { it.position }
                            )
                        }
                        val displayItems = if (hideChecked) {
                            itemsToDisplay.filter { !it.isChecked }
                        } else {
                            itemsToDisplay
                        }
                        Triple(list, displayItems, dbItems.size)
                    }.collect { (list, displayItems, totalCount) ->
                        _uiState.value = UiState.Success(list, displayItems, totalCount)
                    }
                }
                is Result.Failure -> {
                    _uiState.value = UiState.Error(listResult.message)
                }
            }
        }
    }

    fun toggleItemChecked(itemId: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                val item = currentState.items.find { it.id == itemId }
                if (item != null) {
                    val newCheckedState = !item.isChecked
                    when (val result = itemRepository.updateItemChecked(itemId, newCheckedState)) {
                        is Result.Success -> {
                            // Flow will automatically update UI with new state from database
                        }
                        is Result.Failure -> {
                            Timber.e("Failed to toggle item: ${result.message}")
                        }
                    }
                }
            }
        }
    }

    fun onItemMove(from: Int, to: Int) {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            val currentItems = _reorderedItems.value ?: currentState.items
            val mutableList = currentItems.toMutableList()
            val movedItem = mutableList.removeAt(from)
            mutableList.add(to, movedItem)
            _reorderedItems.value = mutableList
        }
    }

    fun onDragEnd() {
        viewModelScope.launch {
            val reorderedItems = _reorderedItems.value ?: return@launch

            // Calculate new positions (index becomes position)
            val positionUpdates = reorderedItems.mapIndexed { index, item ->
                item.id to index
            }

            when (val result = itemRepository.updateItemPositions(positionUpdates)) {
                is Result.Success -> {
                    _reorderedItems.value = null // Clear local state, db flow will update
                }
                is Result.Failure -> {
                    Timber.e("Failed to update positions: ${result.message}")
                    _errorMessage.value = "Failed to reorder items"
                    _reorderedItems.value = null // Revert to db state
                }
            }
        }
    }
}
