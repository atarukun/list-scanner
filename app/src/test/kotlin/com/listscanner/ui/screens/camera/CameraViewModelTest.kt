package com.listscanner.ui.screens.camera

import com.google.common.truth.Truth.assertThat
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.domain.Result
import com.listscanner.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    private lateinit var viewModel: CameraViewModel
    private lateinit var mockPhotoRepository: PhotoRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockPhotoRepository = mockk()
        viewModel = CameraViewModel(mockPhotoRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Initializing`() {
        assertThat(viewModel.uiState.value)
            .isEqualTo(CameraViewModel.UiState.Initializing)
    }

    @Test
    fun `setReady updates state to Ready`() {
        viewModel.setReady()

        assertThat(viewModel.uiState.value)
            .isEqualTo(CameraViewModel.UiState.Ready)
    }

    @Test
    fun `setError updates state to Error with message`() {
        val errorMessage = "Camera unavailable. Please try again."

        viewModel.setError(errorMessage)

        assertThat(viewModel.uiState.value)
            .isEqualTo(CameraViewModel.UiState.Error(errorMessage))
    }

    @Test
    fun `savePhotoToDatabase with success updates state to Success`() = runTest(testDispatcher) {
        val photoSlot = slot<Photo>()
        coEvery { mockPhotoRepository.insertPhoto(capture(photoSlot)) } returns Result.Success(1L)

        viewModel.savePhotoToDatabase("/path/to/photo.jpg")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value)
            .isEqualTo(CameraViewModel.UiState.Success(1L))
        assertThat(photoSlot.captured.filePath).isEqualTo("/path/to/photo.jpg")
        assertThat(photoSlot.captured.ocrStatus).isEqualTo(OcrStatus.PENDING)
    }

    @Test
    fun `savePhotoToDatabase with failure updates state to Error`() = runTest(testDispatcher) {
        coEvery { mockPhotoRepository.insertPhoto(any()) } returns Result.Failure(
            Exception("Database error"),
            "Failed to insert photo"
        )

        viewModel.savePhotoToDatabase("/path/to/photo.jpg")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value)
            .isInstanceOf(CameraViewModel.UiState.Error::class.java)
        assertThat((viewModel.uiState.value as CameraViewModel.UiState.Error).message)
            .isEqualTo("Failed to save photo. Tap to retry.")
    }

    @Test
    fun `savePhotoToDatabase calls repository with correct Photo entity`() = runTest(testDispatcher) {
        val photoSlot = slot<Photo>()
        coEvery { mockPhotoRepository.insertPhoto(capture(photoSlot)) } returns Result.Success(42L)

        viewModel.savePhotoToDatabase("/storage/photos/photo_20251125_120000.jpg")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockPhotoRepository.insertPhoto(any()) }
        assertThat(photoSlot.captured.filePath).isEqualTo("/storage/photos/photo_20251125_120000.jpg")
        assertThat(photoSlot.captured.ocrStatus).isEqualTo(OcrStatus.PENDING)
        assertThat(photoSlot.captured.id).isEqualTo(0L)
    }
}
