package com.listscanner.ui.screens.gallery

import com.google.common.truth.Truth.assertThat
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.domain.Result
import com.listscanner.domain.repository.ListRepository
import com.listscanner.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoGalleryViewModelTest {

    private lateinit var mockPhotoRepository: PhotoRepository
    private lateinit var mockListRepository: ListRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockPhotoRepository = mockk()
        mockListRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        every { mockPhotoRepository.getAllPhotos() } returns flowOf(emptyList())

        val viewModel = PhotoGalleryViewModel(mockPhotoRepository, mockListRepository)

        assertThat(viewModel.uiState.value)
            .isEqualTo(PhotoGalleryViewModel.UiState.Loading)
    }

    @Test
    fun `empty photos list updates state to Empty`() = runTest {
        every { mockPhotoRepository.getAllPhotos() } returns flowOf(emptyList())

        val viewModel = PhotoGalleryViewModel(mockPhotoRepository, mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value)
            .isEqualTo(PhotoGalleryViewModel.UiState.Empty)
    }

    @Test
    fun `non-empty photos list updates state to Success`() = runTest {
        val photo = Photo(
            id = 1,
            filePath = "/path/photo1.jpg",
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )
        every { mockPhotoRepository.getAllPhotos() } returns flowOf(listOf(photo))

        val viewModel = PhotoGalleryViewModel(mockPhotoRepository, mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(PhotoGalleryViewModel.UiState.Success::class.java)
        assertThat((state as PhotoGalleryViewModel.UiState.Success).photos).hasSize(1)
    }

    @Test
    fun `photos are sorted by timestamp descending`() = runTest {
        val olderPhoto = Photo(
            id = 1,
            filePath = "/path/1.jpg",
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )
        val newerPhoto = Photo(
            id = 2,
            filePath = "/path/2.jpg",
            timestamp = 2000L,
            ocrStatus = OcrStatus.PENDING
        )
        every { mockPhotoRepository.getAllPhotos() } returns flowOf(listOf(olderPhoto, newerPhoto))

        val viewModel = PhotoGalleryViewModel(mockPhotoRepository, mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PhotoGalleryViewModel.UiState.Success
        assertThat(state.photos.first().id).isEqualTo(2) // Newer first
        assertThat(state.photos.last().id).isEqualTo(1)
    }

    @Test
    fun `multiple photos sorted correctly by timestamp descending`() = runTest {
        val photos = listOf(
            Photo(id = 1, filePath = "/path/1.jpg", timestamp = 1000L),
            Photo(id = 2, filePath = "/path/2.jpg", timestamp = 3000L),
            Photo(id = 3, filePath = "/path/3.jpg", timestamp = 2000L)
        )
        every { mockPhotoRepository.getAllPhotos() } returns flowOf(photos)

        val viewModel = PhotoGalleryViewModel(mockPhotoRepository, mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PhotoGalleryViewModel.UiState.Success
        assertThat(state.photos.map { it.id }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `deletePhoto calls repository and handles success`() = runTest {
        every { mockPhotoRepository.getAllPhotos() } returns flowOf(emptyList())
        coEvery { mockPhotoRepository.deletePhoto(1L) } returns Result.Success(Unit)

        val viewModel = PhotoGalleryViewModel(mockPhotoRepository, mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deletePhoto(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockPhotoRepository.deletePhoto(1L) }
    }

    @Test
    fun `deletePhoto emits error on deletion failure`() = runTest {
        every { mockPhotoRepository.getAllPhotos() } returns flowOf(emptyList())
        coEvery { mockPhotoRepository.deletePhoto(1L) } returns Result.Failure(
            RuntimeException("File error"),
            "Could not delete photo file from storage"
        )

        val viewModel = PhotoGalleryViewModel(mockPhotoRepository, mockListRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val errorDeferred = async { viewModel.deletionError.first() }

        viewModel.deletePhoto(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val error = errorDeferred.await()
        assertThat(error).isEqualTo("Could not delete photo file from storage")
    }
}
