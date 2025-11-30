package com.listscanner.ui.screens.list

import com.google.common.truth.Truth.assertThat
import com.listscanner.data.entity.Item
import com.listscanner.data.entity.ShoppingList
import com.listscanner.domain.Result
import com.listscanner.domain.repository.ItemRepository
import com.listscanner.domain.repository.ListRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListDetailViewModelTest {

    private lateinit var mockListRepository: ListRepository
    private lateinit var mockItemRepository: ItemRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockListRepository = mockk()
        mockItemRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())
        coEvery { mockListRepository.getListById(1L) } coAnswers {
            delay(100)
            Result.Success(ShoppingList(id = 1L, photoId = null, name = "Test List"))
        }
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)

        assertThat(viewModel.uiState.value).isEqualTo(ListDetailViewModel.UiState.Loading)
    }

    @Test
    fun `loads list and items successfully`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test List")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Milk", position = 0),
            Item(id = 2L, listId = 1L, text = "Bread", position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ListDetailViewModel.UiState.Success::class.java)
        val successState = state as ListDetailViewModel.UiState.Success
        assertThat(successState.list.name).isEqualTo("Test List")
        assertThat(successState.items).hasSize(2)
    }

    @Test
    fun `items sorted by checked status then position`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val unsortedItems = listOf(
            Item(id = 1L, listId = 1L, text = "Third", isChecked = true, position = 2),
            Item(id = 2L, listId = 1L, text = "First", isChecked = false, position = 0),
            Item(id = 3L, listId = 1L, text = "Second", isChecked = false, position = 1)
        )
        val itemsFlow = MutableStateFlow(unsortedItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        // Unchecked items first, sorted by position
        assertThat(state.items[0].text).isEqualTo("First")
        assertThat(state.items[1].text).isEqualTo("Second")
        // Checked items last
        assertThat(state.items[2].text).isEqualTo("Third")
    }

    @Test
    fun `empty items list emits Success with empty list`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Empty List")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items).isEmpty()
    }

    @Test
    fun `list not found emits Error state`() = runTest {
        coEvery { mockListRepository.getListById(1L) } returns Result.Success(null)
        every { mockItemRepository.getItemsForList(1L) } returns flowOf(emptyList())

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ListDetailViewModel.UiState.Error::class.java)
        assertThat((state as ListDetailViewModel.UiState.Error).message).isEqualTo("List not found")
    }

    @Test
    fun `item updates flow through reactively`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow(listOf(
            Item(id = 1L, listId = 1L, text = "Original", position = 0)
        ))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Update items
        itemsFlow.value = listOf(
            Item(id = 1L, listId = 1L, text = "Original", position = 0),
            Item(id = 2L, listId = 1L, text = "New Item", position = 1)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items).hasSize(2)
    }

    @Test
    fun `toggleItemChecked calls repository with toggled state`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test List")
        val testItem = Item(id = 1L, listId = 1L, text = "Test", isChecked = false, position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemChecked(1L, true) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleItemChecked(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockItemRepository.updateItemChecked(1L, true) }
    }

    @Test
    fun `toggleItemChecked handles repository failure gracefully`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test List")
        val testItem = Item(id = 1L, listId = 1L, text = "Test", isChecked = false, position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemChecked(1L, true) } returns Result.Failure(
            Exception("DB error"),
            "Failed to update"
        )

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleItemChecked(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not crash, state remains Success
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ListDetailViewModel.UiState.Success::class.java)
    }

    @Test
    fun `multiple toggleItemChecked calls execute independently`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test List")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item 1", isChecked = false, position = 0),
            Item(id = 2L, listId = 1L, text = "Item 2", isChecked = true, position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemChecked(1L, true) } returns Result.Success(Unit)
        coEvery { mockItemRepository.updateItemChecked(2L, false) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Rapid toggling of multiple items
        viewModel.toggleItemChecked(1L)
        viewModel.toggleItemChecked(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockItemRepository.updateItemChecked(1L, true) }
        coVerify { mockItemRepository.updateItemChecked(2L, false) }
    }

    @Test
    fun `hideChecked defaults to false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.hideChecked.value).isFalse()
    }

    @Test
    fun `toggleHideChecked flips state from false to true`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleHideChecked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.hideChecked.value).isTrue()
    }

    @Test
    fun `toggleHideChecked flips state from true to false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleHideChecked()
        viewModel.toggleHideChecked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.hideChecked.value).isFalse()
    }

    @Test
    fun `displayItems filters checked items when hideChecked is true`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Unchecked", isChecked = false, position = 0),
            Item(id = 2L, listId = 1L, text = "Checked", isChecked = true, position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleHideChecked()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().text).isEqualTo("Unchecked")
        assertThat(state.totalItemCount).isEqualTo(2)
    }

    @Test
    fun `displayItems shows all items sorted unchecked-first when hideChecked is false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Checked1", isChecked = true, position = 0),
            Item(id = 2L, listId = 1L, text = "Unchecked1", isChecked = false, position = 1),
            Item(id = 3L, listId = 1L, text = "Checked2", isChecked = true, position = 2),
            Item(id = 4L, listId = 1L, text = "Unchecked2", isChecked = false, position = 3)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        // Unchecked items first (sorted by position within their group)
        assertThat(state.items[0].text).isEqualTo("Unchecked1")
        assertThat(state.items[1].text).isEqualTo("Unchecked2")
        // Checked items last (sorted by position within their group)
        assertThat(state.items[2].text).isEqualTo("Checked1")
        assertThat(state.items[3].text).isEqualTo("Checked2")
        assertThat(state.totalItemCount).isEqualTo(4)
    }

    @Test
    fun `displayItems updates reactively when item checked state changes`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item1", isChecked = false, position = 0),
            Item(id = 2L, listId = 1L, text = "Item2", isChecked = false, position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial state: both unchecked
        var state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Item1")
        assertThat(state.items[1].text).isEqualTo("Item2")

        // Simulate Item1 becoming checked (database update flows through)
        itemsFlow.value = listOf(
            Item(id = 1L, listId = 1L, text = "Item1", isChecked = true, position = 0),
            Item(id = 2L, listId = 1L, text = "Item2", isChecked = false, position = 1)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Item1 should now be at bottom (checked items go last)
        state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Item2")
        assertThat(state.items[1].text).isEqualTo("Item1")
    }

    @Test
    fun `totalItemCount reflects total items regardless of filter`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Unchecked", isChecked = false, position = 0),
            Item(id = 2L, listId = 1L, text = "Checked1", isChecked = true, position = 1),
            Item(id = 3L, listId = 1L, text = "Checked2", isChecked = true, position = 2)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Show all mode
        var state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items).hasSize(3)
        assertThat(state.totalItemCount).isEqualTo(3)

        // Hide checked mode
        viewModel.toggleHideChecked()
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items).hasSize(1)
        assertThat(state.totalItemCount).isEqualTo(3)
    }

    @Test
    fun `editingItemId defaults to null`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.editingItemId.value).isNull()
    }

    @Test
    fun `startEditing sets editingItemId`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Test", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditing(1L)

        assertThat(viewModel.editingItemId.value).isEqualTo(1L)
    }

    @Test
    fun `cancelEditing clears editingItemId`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Test", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditing(1L)
        assertThat(viewModel.editingItemId.value).isEqualTo(1L)

        viewModel.cancelEditing()

        assertThat(viewModel.editingItemId.value).isNull()
    }

    @Test
    fun `startEditing on new item clears previous edit`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item 1", position = 0),
            Item(id = 2L, listId = 1L, text = "Item 2", position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditing(1L)
        assertThat(viewModel.editingItemId.value).isEqualTo(1L)

        viewModel.startEditing(2L)
        assertThat(viewModel.editingItemId.value).isEqualTo(2L)
    }

    @Test
    fun `updateItemText calls repository and clears editing state on success`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Original", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemText(1L, "Updated") } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditing(1L)
        assertThat(viewModel.editingItemId.value).isEqualTo(1L)

        viewModel.updateItemText(1L, "Updated")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockItemRepository.updateItemText(1L, "Updated") }
        assertThat(viewModel.editingItemId.value).isNull()
    }

    @Test
    fun `updateItemText with empty text does not call repository`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Original", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditing(1L)
        viewModel.updateItemText(1L, "   ")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockItemRepository.updateItemText(any(), any()) }
    }

    @Test
    fun `updateItemText with more than 200 chars truncates before saving`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Original", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemText(eq(1L), any()) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val longText = "A".repeat(250)
        viewModel.updateItemText(1L, longText)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockItemRepository.updateItemText(1L, "A".repeat(200)) }
    }

    @Test
    fun `updateItemText handles repository failure gracefully`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Original", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemText(1L, "Updated") } returns Result.Failure(
            Exception("DB error"),
            "Failed to update"
        )

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditing(1L)
        viewModel.updateItemText(1L, "Updated")
        testDispatcher.scheduler.advanceUntilIdle()

        // Edit mode should remain open on failure
        assertThat(viewModel.editingItemId.value).isEqualTo(1L)
        // UI state should remain Success
        assertThat(viewModel.uiState.value).isInstanceOf(ListDetailViewModel.UiState.Success::class.java)
    }

    // Add Item Tests

    @Test
    fun `isAddingItem defaults to false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.isAddingItem.value).isFalse()
    }

    @Test
    fun `startAddingItem sets isAddingItem to true`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startAddingItem()

        assertThat(viewModel.isAddingItem.value).isTrue()
    }

    @Test
    fun `startAddingItem cancels active editing`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Test", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startEditing(1L)
        assertThat(viewModel.editingItemId.value).isEqualTo(1L)

        viewModel.startAddingItem()

        assertThat(viewModel.editingItemId.value).isNull()
        assertThat(viewModel.isAddingItem.value).isTrue()
    }

    @Test
    fun `cancelAddingItem sets isAddingItem to false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startAddingItem()
        assertThat(viewModel.isAddingItem.value).isTrue()

        viewModel.cancelAddingItem()

        assertThat(viewModel.isAddingItem.value).isFalse()
    }

    @Test
    fun `addItem calls repository with correct position`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val existingItem = Item(id = 1L, listId = 1L, text = "Existing", position = 5)
        val itemsFlow = MutableStateFlow(listOf(existingItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.getMaxPositionForList(1L) } returns Result.Success(5)
        coEvery { mockItemRepository.insertItem(any()) } returns Result.Success(2L)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startAddingItem()
        viewModel.addItem("New Item")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockItemRepository.insertItem(match { item ->
                item.text == "New Item" &&
                item.position == 6 &&
                item.isChecked == false &&
                item.listId == 1L
            })
        }
        assertThat(viewModel.isAddingItem.value).isFalse()
    }

    @Test
    fun `addItem with empty text does not call repository`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startAddingItem()
        viewModel.addItem("   ")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockItemRepository.getMaxPositionForList(any()) }
        coVerify(exactly = 0) { mockItemRepository.insertItem(any()) }
    }

    @Test
    fun `addItem with more than 200 chars truncates before saving`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.getMaxPositionForList(1L) } returns Result.Success(-1)
        coEvery { mockItemRepository.insertItem(any()) } returns Result.Success(1L)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val longText = "A".repeat(250)
        viewModel.addItem(longText)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockItemRepository.insertItem(match { item ->
                item.text == "A".repeat(200)
            })
        }
    }

    @Test
    fun `addItem clears isAddingItem on success`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.getMaxPositionForList(1L) } returns Result.Success(-1)
        coEvery { mockItemRepository.insertItem(any()) } returns Result.Success(1L)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startAddingItem()
        assertThat(viewModel.isAddingItem.value).isTrue()

        viewModel.addItem("New Item")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.isAddingItem.value).isFalse()
    }

    @Test
    fun `addItem handles repository failure gracefully - keeps add mode open`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.getMaxPositionForList(1L) } returns Result.Success(-1)
        coEvery { mockItemRepository.insertItem(any()) } returns Result.Failure(
            Exception("DB error"),
            "Failed to insert"
        )

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startAddingItem()
        viewModel.addItem("New Item")
        testDispatcher.scheduler.advanceUntilIdle()

        // Add mode should remain open on failure
        assertThat(viewModel.isAddingItem.value).isTrue()
        // UI state should remain Success
        assertThat(viewModel.uiState.value).isInstanceOf(ListDetailViewModel.UiState.Success::class.java)
    }

    @Test
    fun `addItem on empty list uses position 0`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.getMaxPositionForList(1L) } returns Result.Success(-1)
        coEvery { mockItemRepository.insertItem(any()) } returns Result.Success(1L)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addItem("First Item")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockItemRepository.insertItem(match { item ->
                item.position == 0
            })
        }
    }

    // Delete Item Tests

    @Test
    fun `itemPendingDeletion defaults to null`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.itemPendingDeletion.value).isNull()
    }

    @Test
    fun `requestDeleteItem sets itemPendingDeletion to the item`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Delete me", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteItem(testItem)

        assertThat(viewModel.itemPendingDeletion.value).isEqualTo(testItem)
    }

    @Test
    fun `cancelDeleteItem clears itemPendingDeletion`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Delete me", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteItem(testItem)
        assertThat(viewModel.itemPendingDeletion.value).isEqualTo(testItem)

        viewModel.cancelDeleteItem()

        assertThat(viewModel.itemPendingDeletion.value).isNull()
    }

    @Test
    fun `confirmDeleteItem calls repository deleteItem with correct id`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Delete me", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.deleteItem(1L) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteItem(testItem)
        viewModel.confirmDeleteItem()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockItemRepository.deleteItem(1L) }
    }

    @Test
    fun `confirmDeleteItem clears itemPendingDeletion on success`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Delete me", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.deleteItem(1L) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteItem(testItem)
        assertThat(viewModel.itemPendingDeletion.value).isNotNull()

        viewModel.confirmDeleteItem()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.itemPendingDeletion.value).isNull()
        assertThat(viewModel.errorMessage.value).isNull()
    }

    @Test
    fun `confirmDeleteItem sets errorMessage on repository failure`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Delete me", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.deleteItem(1L) } returns Result.Failure(
            Exception("DB error"),
            "Failed to delete"
        )

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteItem(testItem)
        viewModel.confirmDeleteItem()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("Failed to delete item")
        assertThat(viewModel.itemPendingDeletion.value).isNull()
    }

    @Test
    fun `confirmDeleteItem with null pending item does nothing`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmDeleteItem()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockItemRepository.deleteItem(any()) }
    }

    @Test
    fun `clearErrorMessage resets errorMessage to null`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItem = Item(id = 1L, listId = 1L, text = "Delete me", position = 0)
        val itemsFlow = MutableStateFlow(listOf(testItem))

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.deleteItem(1L) } returns Result.Failure(
            Exception("DB error"),
            "Failed to delete"
        )

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteItem(testItem)
        viewModel.confirmDeleteItem()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("Failed to delete item")

        viewModel.clearErrorMessage()

        assertThat(viewModel.errorMessage.value).isNull()
    }

    @Test
    fun `errorMessage defaults to null`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isNull()
    }

    // Reorder Item Tests

    @Test
    fun `onItemMove updates local reordered items list`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item 1", position = 0),
            Item(id = 2L, listId = 1L, text = "Item 2", position = 1),
            Item(id = 3L, listId = 1L, text = "Item 3", position = 2)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Move item from position 0 to position 2
        viewModel.onItemMove(0, 2)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        // Item 1 should now be at the end
        assertThat(state.items[0].text).isEqualTo("Item 2")
        assertThat(state.items[1].text).isEqualTo("Item 3")
        assertThat(state.items[2].text).isEqualTo("Item 1")
    }

    @Test
    fun `onDragEnd calls repository updateItemPositions with correct positions`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item 1", position = 0),
            Item(id = 2L, listId = 1L, text = "Item 2", position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemPositions(any()) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onItemMove(0, 1) // Swap items
        viewModel.onDragEnd()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockItemRepository.updateItemPositions(match { updates ->
                updates.size == 2 &&
                updates.any { it.first == 2L && it.second == 0 } &&
                updates.any { it.first == 1L && it.second == 1 }
            })
        }
    }

    @Test
    fun `onDragEnd clears reordered items on success`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item 1", position = 0),
            Item(id = 2L, listId = 1L, text = "Item 2", position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemPositions(any()) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onItemMove(0, 1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify reordered state is being used
        var state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Item 2")

        viewModel.onDragEnd()
        testDispatcher.scheduler.advanceUntilIdle()

        // After successful save, db flow resumes (which returns original order in this test)
        state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Item 1")
    }

    @Test
    fun `onDragEnd sets errorMessage on repository failure`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item 1", position = 0),
            Item(id = 2L, listId = 1L, text = "Item 2", position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemPositions(any()) } returns Result.Failure(
            Exception("DB error"),
            "Failed to update"
        )

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onItemMove(0, 1)
        viewModel.onDragEnd()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("Failed to reorder items")
    }

    @Test
    fun `reorder works for checked items`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Checked 1", isChecked = true, position = 0),
            Item(id = 2L, listId = 1L, text = "Checked 2", isChecked = true, position = 1),
            Item(id = 3L, listId = 1L, text = "Checked 3", isChecked = true, position = 2)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onItemMove(0, 2)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Checked 2")
        assertThat(state.items[1].text).isEqualTo("Checked 3")
        assertThat(state.items[2].text).isEqualTo("Checked 1")
    }

    @Test
    fun `reorder works for unchecked items`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Unchecked 1", isChecked = false, position = 0),
            Item(id = 2L, listId = 1L, text = "Unchecked 2", isChecked = false, position = 1),
            Item(id = 3L, listId = 1L, text = "Unchecked 3", isChecked = false, position = 2)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onItemMove(2, 0)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Unchecked 3")
        assertThat(state.items[1].text).isEqualTo("Unchecked 1")
        assertThat(state.items[2].text).isEqualTo("Unchecked 2")
    }

    @Test
    fun `display uses reordered items during drag`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item 1", position = 0),
            Item(id = 2L, listId = 1L, text = "Item 2", position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial state
        var state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Item 1")

        // During drag, reordered items should be displayed
        viewModel.onItemMove(0, 1)
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Item 2")
        assertThat(state.items[1].text).isEqualTo("Item 1")
    }

    @Test
    fun `display reverts to db items after successful onDragEnd`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item 1", position = 0),
            Item(id = 2L, listId = 1L, text = "Item 2", position = 1)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockItemRepository.updateItemPositions(any()) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onItemMove(0, 1)
        testDispatcher.scheduler.advanceUntilIdle()

        // During drag
        var state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Item 2")

        viewModel.onDragEnd()
        testDispatcher.scheduler.advanceUntilIdle()

        // After drag end, db items are used again
        // In this test, db still has original order since we don't simulate db update
        state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.items[0].text).isEqualTo("Item 1")
    }

    @Test
    fun `onDragEnd without prior move does nothing`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val testItems = listOf(
            Item(id = 1L, listId = 1L, text = "Item 1", position = 0)
        )
        val itemsFlow = MutableStateFlow(testItems)

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDragEnd()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockItemRepository.updateItemPositions(any()) }
    }

    // Rename List Tests

    @Test
    fun `isRenaming defaults to false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.isRenaming.value).isFalse()
    }

    @Test
    fun `startRenaming sets isRenaming to true`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startRenaming()

        assertThat(viewModel.isRenaming.value).isTrue()
    }

    @Test
    fun `cancelRenaming sets isRenaming to false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startRenaming()
        assertThat(viewModel.isRenaming.value).isTrue()

        viewModel.cancelRenaming()

        assertThat(viewModel.isRenaming.value).isFalse()
    }

    @Test
    fun `renameList calls repository updateList with correct name`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Original Name")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.updateList(any()) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameList("New Name")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockListRepository.updateList(match { list ->
                list.name == "New Name" && list.id == 1L
            })
        }
    }

    @Test
    fun `renameList updates uiState with new list name on success`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Original Name")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.updateList(any()) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameList("New Name")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListDetailViewModel.UiState.Success
        assertThat(state.list.name).isEqualTo("New Name")
    }

    @Test
    fun `renameList sets isRenaming to false on success`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Original Name")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.updateList(any()) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startRenaming()
        assertThat(viewModel.isRenaming.value).isTrue()

        viewModel.renameList("New Name")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.isRenaming.value).isFalse()
    }

    @Test
    fun `renameList sets errorMessage on repository failure`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Original Name")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.updateList(any()) } returns Result.Failure(
            Exception("DB error"),
            "Failed to update"
        )

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameList("New Name")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("Failed to rename list")
    }

    @Test
    fun `renameList with empty string does not call repository`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameList("")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockListRepository.updateList(any()) }
    }

    @Test
    fun `renameList with blank whitespace only does not call repository`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameList("   ")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockListRepository.updateList(any()) }
    }

    @Test
    fun `renameList truncates name longer than 50 characters`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())
        val longName = "A".repeat(60) // 60 characters

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.updateList(any()) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameList(longName)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockListRepository.updateList(match { list ->
                list.name.length == 50 && list.name == "A".repeat(50)
            })
        }
    }

    @Test
    fun `renameList trims whitespace from name`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.updateList(any()) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameList("  New Name  ")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockListRepository.updateList(match { list ->
                list.name == "New Name"
            })
        }
    }

    // Delete List Tests

    @Test
    fun `isDeletingList defaults to false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = 5L, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.isDeletingList.value).isFalse()
    }

    @Test
    fun `requestDeleteList sets isDeletingList to true`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = 5L, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList()

        assertThat(viewModel.isDeletingList.value).isTrue()
    }

    @Test
    fun `cancelDeleteList sets isDeletingList to false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = 5L, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList()
        assertThat(viewModel.isDeletingList.value).isTrue()

        viewModel.cancelDeleteList()

        assertThat(viewModel.isDeletingList.value).isFalse()
    }

    @Test
    fun `listDeleted defaults to false`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = 5L, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.listDeleted.value).isFalse()
    }

    @Test
    fun `confirmDeleteList calls listRepository deleteListAndResetPhoto with correct params`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = 5L, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.deleteListAndResetPhoto(1L, 5L) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList()
        viewModel.confirmDeleteList()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockListRepository.deleteListAndResetPhoto(1L, 5L) }
    }

    @Test
    fun `confirmDeleteList passes null photoId when list has no photo`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.deleteListAndResetPhoto(1L, null) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList()
        viewModel.confirmDeleteList()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockListRepository.deleteListAndResetPhoto(1L, null) }
    }

    @Test
    fun `confirmDeleteList sets listDeleted to true on success`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.deleteListAndResetPhoto(1L, null) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList()
        viewModel.confirmDeleteList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.listDeleted.value).isTrue()
        assertThat(viewModel.isDeletingList.value).isFalse()
    }

    @Test
    fun `confirmDeleteList sets errorMessage on repository failure`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = null, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.deleteListAndResetPhoto(1L, null) } returns Result.Failure(
            Exception("DB error"),
            "Failed to delete"
        )

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList()
        viewModel.confirmDeleteList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("Failed to delete list")
        assertThat(viewModel.listDeleted.value).isFalse()
        assertThat(viewModel.isDeletingList.value).isFalse()
    }

    @Test
    fun `confirmDeleteList sets isDeletingList to false on success`() = runTest {
        val testList = ShoppingList(id = 1L, photoId = 5L, name = "Test")
        val itemsFlow = MutableStateFlow<List<Item>>(emptyList())

        coEvery { mockListRepository.getListById(1L) } returns Result.Success(testList)
        every { mockItemRepository.getItemsForList(1L) } returns itemsFlow
        coEvery { mockListRepository.deleteListAndResetPhoto(1L, 5L) } returns Result.Success(Unit)

        val viewModel = ListDetailViewModel(mockListRepository, mockItemRepository, 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList()
        assertThat(viewModel.isDeletingList.value).isTrue()

        viewModel.confirmDeleteList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.isDeletingList.value).isFalse()
    }
}
