package com.listscanner.ui.screens.listsoverview

import com.google.common.truth.Truth.assertThat
import com.listscanner.data.entity.ShoppingList
import com.listscanner.data.model.ListWithCounts
import com.listscanner.domain.Result
import com.listscanner.domain.repository.ListRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListsOverviewViewModelTest {

    private lateinit var mockListRepository: ListRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockListRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        val listsFlow = flow<List<ListWithCounts>> {
            delay(100)
            emit(emptyList())
        }
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow

        val viewModel = ListsOverviewViewModel(mockListRepository)

        assertThat(viewModel.uiState.value).isEqualTo(ListsOverviewViewModel.UiState.Loading)
    }

    @Test
    fun `empty list from repository emits Empty state`() = runTest {
        every { mockListRepository.getAllListsWithCounts() } returns MutableStateFlow(emptyList())

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(ListsOverviewViewModel.UiState.Empty)
    }

    @Test
    fun `lists from repository emits Success state with correct data`() = runTest {
        val testLists = listOf(
            ListWithCounts(
                list = ShoppingList(id = 1L, photoId = 1L, name = "Grocery List", createdDate = 1000L),
                itemCount = 5,
                checkedCount = 2,
                photoFilePath = "/path/to/photo.jpg"
            ),
            ListWithCounts(
                list = ShoppingList(id = 2L, photoId = null, name = "Hardware Store", createdDate = 2000L),
                itemCount = 3,
                checkedCount = 0,
                photoFilePath = null
            )
        )
        every { mockListRepository.getAllListsWithCounts() } returns MutableStateFlow(testLists)

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ListsOverviewViewModel.UiState.Success::class.java)
        val successState = state as ListsOverviewViewModel.UiState.Success
        assertThat(successState.lists).hasSize(2)
        assertThat(successState.lists[0].list.name).isEqualTo("Grocery List")
        assertThat(successState.lists[0].itemCount).isEqualTo(5)
        assertThat(successState.lists[0].checkedCount).isEqualTo(2)
        assertThat(successState.lists[0].uncheckedCount).isEqualTo(3)
    }

    @Test
    fun `lists are sorted by creation date descending`() = runTest {
        val now = System.currentTimeMillis()
        val testLists = listOf(
            ListWithCounts(
                list = ShoppingList(id = 1L, photoId = null, name = "Oldest", createdDate = now - 2000),
                itemCount = 1,
                checkedCount = 0,
                photoFilePath = null
            ),
            ListWithCounts(
                list = ShoppingList(id = 2L, photoId = null, name = "Newest", createdDate = now),
                itemCount = 2,
                checkedCount = 1,
                photoFilePath = null
            ),
            ListWithCounts(
                list = ShoppingList(id = 3L, photoId = null, name = "Middle", createdDate = now - 1000),
                itemCount = 3,
                checkedCount = 2,
                photoFilePath = null
            )
        )
        // DAO query orders by created_date DESC
        val sortedLists = testLists.sortedByDescending { it.list.createdDate }
        every { mockListRepository.getAllListsWithCounts() } returns MutableStateFlow(sortedLists)

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListsOverviewViewModel.UiState.Success
        assertThat(state.lists[0].list.name).isEqualTo("Newest")
        assertThat(state.lists[1].list.name).isEqualTo("Middle")
        assertThat(state.lists[2].list.name).isEqualTo("Oldest")
    }

    @Test
    fun `flow updates trigger state updates`() = runTest {
        val listsFlow = MutableStateFlow<List<ListWithCounts>>(emptyList())
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially empty
        assertThat(viewModel.uiState.value).isEqualTo(ListsOverviewViewModel.UiState.Empty)

        // Add a list
        listsFlow.value = listOf(
            ListWithCounts(
                list = ShoppingList(id = 1L, photoId = null, name = "New List"),
                itemCount = 2,
                checkedCount = 0,
                photoFilePath = null
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ListsOverviewViewModel.UiState.Success
        assertThat(state.lists).hasSize(1)
        assertThat(state.lists[0].list.name).isEqualTo("New List")
    }

    @Test
    fun `flow error emits Error state`() = runTest {
        val errorFlow = flow<List<ListWithCounts>> {
            throw RuntimeException("Database error")
        }
        every { mockListRepository.getAllListsWithCounts() } returns errorFlow

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ListsOverviewViewModel.UiState.Error::class.java)
        assertThat((state as ListsOverviewViewModel.UiState.Error).message).isEqualTo("Database error")
    }

    // Delete List Tests

    @Test
    fun `listPendingDeletion defaults to null`() = runTest {
        val listsFlow = MutableStateFlow<List<ListWithCounts>>(emptyList())
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.listPendingDeletion.value).isNull()
    }

    @Test
    fun `requestDeleteList sets listPendingDeletion to the list`() = runTest {
        val testList = ListWithCounts(
            list = ShoppingList(id = 1L, photoId = 5L, name = "Test List"),
            itemCount = 10,
            checkedCount = 3,
            photoFilePath = "/path/to/photo.jpg"
        )
        val listsFlow = MutableStateFlow(listOf(testList))
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList(testList)

        assertThat(viewModel.listPendingDeletion.value).isEqualTo(testList)
    }

    @Test
    fun `cancelDeleteList clears listPendingDeletion`() = runTest {
        val testList = ListWithCounts(
            list = ShoppingList(id = 1L, photoId = 5L, name = "Test List"),
            itemCount = 10,
            checkedCount = 3,
            photoFilePath = "/path/to/photo.jpg"
        )
        val listsFlow = MutableStateFlow(listOf(testList))
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList(testList)
        assertThat(viewModel.listPendingDeletion.value).isEqualTo(testList)

        viewModel.cancelDeleteList()

        assertThat(viewModel.listPendingDeletion.value).isNull()
    }

    @Test
    fun `confirmDeleteList calls listRepository deleteListAndResetPhoto with correct params`() = runTest {
        val testList = ListWithCounts(
            list = ShoppingList(id = 1L, photoId = 5L, name = "Test List"),
            itemCount = 10,
            checkedCount = 3,
            photoFilePath = "/path/to/photo.jpg"
        )
        val listsFlow = MutableStateFlow(listOf(testList))
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow
        coEvery { mockListRepository.deleteListAndResetPhoto(1L, 5L) } returns Result.Success(Unit)

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList(testList)
        viewModel.confirmDeleteList()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockListRepository.deleteListAndResetPhoto(1L, 5L) }
    }

    @Test
    fun `confirmDeleteList clears listPendingDeletion on success`() = runTest {
        val testList = ListWithCounts(
            list = ShoppingList(id = 1L, photoId = 5L, name = "Test List"),
            itemCount = 10,
            checkedCount = 3,
            photoFilePath = "/path/to/photo.jpg"
        )
        val listsFlow = MutableStateFlow(listOf(testList))
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow
        coEvery { mockListRepository.deleteListAndResetPhoto(1L, 5L) } returns Result.Success(Unit)

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList(testList)
        assertThat(viewModel.listPendingDeletion.value).isNotNull()

        viewModel.confirmDeleteList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.listPendingDeletion.value).isNull()
    }

    @Test
    fun `confirmDeleteList sets errorMessage on repository failure`() = runTest {
        val testList = ListWithCounts(
            list = ShoppingList(id = 1L, photoId = 5L, name = "Test List"),
            itemCount = 10,
            checkedCount = 3,
            photoFilePath = "/path/to/photo.jpg"
        )
        val listsFlow = MutableStateFlow(listOf(testList))
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow
        coEvery { mockListRepository.deleteListAndResetPhoto(1L, 5L) } returns Result.Failure(
            Exception("DB error"),
            "Failed to delete"
        )

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList(testList)
        viewModel.confirmDeleteList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("Failed to delete list")
        assertThat(viewModel.listPendingDeletion.value).isNull()
    }

    @Test
    fun `errorMessage defaults to null`() = runTest {
        val listsFlow = MutableStateFlow<List<ListWithCounts>>(emptyList())
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isNull()
    }

    @Test
    fun `clearErrorMessage sets errorMessage to null`() = runTest {
        val testList = ListWithCounts(
            list = ShoppingList(id = 1L, photoId = 5L, name = "Test List"),
            itemCount = 10,
            checkedCount = 3,
            photoFilePath = "/path/to/photo.jpg"
        )
        val listsFlow = MutableStateFlow(listOf(testList))
        every { mockListRepository.getAllListsWithCounts() } returns listsFlow
        coEvery { mockListRepository.deleteListAndResetPhoto(1L, 5L) } returns Result.Failure(
            Exception("DB error"),
            "Failed to delete"
        )

        val viewModel = ListsOverviewViewModel(mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteList(testList)
        viewModel.confirmDeleteList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.errorMessage.value).isEqualTo("Failed to delete list")

        viewModel.clearErrorMessage()

        assertThat(viewModel.errorMessage.value).isNull()
    }
}
