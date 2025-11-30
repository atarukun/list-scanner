package com.listscanner.ui.screens.regionselection

import com.google.common.truth.Truth.assertThat
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.domain.Result
import com.listscanner.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegionSelectionViewModelTest {

    private lateinit var mockPhotoRepository: PhotoRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockPhotoRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(photoId: Long = 1L) = RegionSelectionViewModel(
        mockPhotoRepository,
        photoId
    )

    @Nested
    inner class Initialization {

        @Test
        fun `initial state is Loading`() = runTest {
            coEvery { mockPhotoRepository.getPhotoById(any()) } returns Result.Success(null)

            val viewModel = createViewModel()

            assertThat(viewModel.uiState.value)
                .isEqualTo(RegionSelectionViewModel.UiState.Loading)
        }

        @Test
        fun `successful photo load updates state to Success`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state).isInstanceOf(RegionSelectionViewModel.UiState.Success::class.java)
            assertThat((state as RegionSelectionViewModel.UiState.Success).filePath)
                .isEqualTo("/path/to/photo.jpg")
        }

        @Test
        fun `photo not found updates state to Error`() = runTest {
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(null)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.uiState.value)
                .isEqualTo(RegionSelectionViewModel.UiState.Error("Photo not found"))
        }

        @Test
        fun `repository failure updates state to Error with message`() = runTest {
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Failure(
                exception = Exception("Database error"),
                message = "Failed to load photo"
            )

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.uiState.value)
                .isEqualTo(RegionSelectionViewModel.UiState.Error("Failed to load photo: Failed to load photo"))
        }
    }

    @Nested
    inner class DefaultCropRectInitialization {

        @Test
        fun `default crop rect is 80 percent centered`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value as RegionSelectionViewModel.UiState.Success
            val cropRect = state.cropRect

            // 80% centered means 10% margin on each side
            assertThat(cropRect.left).isWithin(0.001f).of(0.1f)
            assertThat(cropRect.top).isWithin(0.001f).of(0.1f)
            assertThat(cropRect.right).isWithin(0.001f).of(0.9f)
            assertThat(cropRect.bottom).isWithin(0.001f).of(0.9f)
        }

        @Test
        fun `default crop rect width is 80 percent`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value as RegionSelectionViewModel.UiState.Success
            assertThat(state.cropRect.width).isWithin(0.001f).of(0.8f)
            assertThat(state.cropRect.height).isWithin(0.001f).of(0.8f)
        }
    }

    @Nested
    inner class MinimumSizeEnforcement {

        @Test
        fun `minimum size fraction is 10 percent`() {
            assertThat(CropRect.minSizeFraction()).isWithin(0.001f).of(0.1f)
        }

        @Test
        fun `crop rect smaller than minimum is enlarged`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Try to set a rect smaller than 10%
            val tooSmallRect = CropRect(0.5f, 0.5f, 0.55f, 0.55f) // 5% x 5%
            viewModel.updateCropRect(tooSmallRect)

            val state = viewModel.uiState.value as RegionSelectionViewModel.UiState.Success
            val resultRect = state.cropRect

            // Should be enforced to minimum 10% in both dimensions
            assertThat(resultRect.width).isAtLeast(0.1f)
            assertThat(resultRect.height).isAtLeast(0.1f)
        }

        @Test
        fun `enforceMinSize correctly enlarges width`() {
            val smallWidth = CropRect(0.4f, 0.4f, 0.45f, 0.6f) // 5% width, 20% height
            val enforced = smallWidth.enforceMinSize()

            assertThat(enforced.width).isWithin(0.001f).of(0.1f)
            assertThat(enforced.height).isWithin(0.001f).of(0.2f) // unchanged
        }

        @Test
        fun `enforceMinSize correctly enlarges height`() {
            val smallHeight = CropRect(0.4f, 0.4f, 0.6f, 0.45f) // 20% width, 5% height
            val enforced = smallHeight.enforceMinSize()

            assertThat(enforced.width).isWithin(0.001f).of(0.2f) // unchanged
            assertThat(enforced.height).isWithin(0.001f).of(0.1f)
        }
    }

    @Nested
    inner class MaximumSizeEnforcement {

        @Test
        fun `crop rect cannot extend past left boundary`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val outOfBounds = CropRect(-0.1f, 0.2f, 0.5f, 0.8f)
            viewModel.updateCropRect(outOfBounds)

            val state = viewModel.uiState.value as RegionSelectionViewModel.UiState.Success
            assertThat(state.cropRect.left).isAtLeast(0f)
        }

        @Test
        fun `crop rect cannot extend past right boundary`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val outOfBounds = CropRect(0.5f, 0.2f, 1.2f, 0.8f)
            viewModel.updateCropRect(outOfBounds)

            val state = viewModel.uiState.value as RegionSelectionViewModel.UiState.Success
            assertThat(state.cropRect.right).isAtMost(1f)
        }

        @Test
        fun `crop rect cannot extend past top boundary`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val outOfBounds = CropRect(0.2f, -0.1f, 0.8f, 0.5f)
            viewModel.updateCropRect(outOfBounds)

            val state = viewModel.uiState.value as RegionSelectionViewModel.UiState.Success
            assertThat(state.cropRect.top).isAtLeast(0f)
        }

        @Test
        fun `crop rect cannot extend past bottom boundary`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val outOfBounds = CropRect(0.2f, 0.5f, 0.8f, 1.2f)
            viewModel.updateCropRect(outOfBounds)

            val state = viewModel.uiState.value as RegionSelectionViewModel.UiState.Success
            assertThat(state.cropRect.bottom).isAtMost(1f)
        }

        @Test
        fun `coerceInBounds keeps rect within 0-1 range`() {
            val outOfBounds = CropRect(-0.1f, -0.2f, 1.3f, 1.4f)
            val clamped = outOfBounds.coerceInBounds()

            assertThat(clamped.left).isAtLeast(0f)
            assertThat(clamped.top).isAtLeast(0f)
            assertThat(clamped.right).isAtMost(1f)
            assertThat(clamped.bottom).isAtMost(1f)
        }
    }

    @Nested
    inner class CoordinateNormalization {

        @Test
        fun `toPixelRect correctly converts to pixel coordinates for square image`() {
            val normalized = CropRect(0.1f, 0.2f, 0.9f, 0.8f)
            val pixelRect = normalized.toPixelRect(1000, 1000)

            assertThat(pixelRect.left).isEqualTo(100)
            assertThat(pixelRect.top).isEqualTo(200)
            assertThat(pixelRect.right).isEqualTo(900)
            assertThat(pixelRect.bottom).isEqualTo(800)
        }

        @Test
        fun `toPixelRect correctly converts for rectangular image`() {
            val normalized = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
            val pixelRect = normalized.toPixelRect(4000, 3000)

            assertThat(pixelRect.left).isEqualTo(1000)
            assertThat(pixelRect.top).isEqualTo(750)
            assertThat(pixelRect.right).isEqualTo(3000)
            assertThat(pixelRect.bottom).isEqualTo(2250)
        }

        @Test
        fun `pixelRect width and height are calculated correctly`() {
            val normalized = CropRect(0.1f, 0.2f, 0.9f, 0.8f)
            val pixelRect = normalized.toPixelRect(1000, 1000)

            assertThat(pixelRect.width).isEqualTo(800)
            assertThat(pixelRect.height).isEqualTo(600)
        }

        @Test
        fun `getCropPixelRect returns pixel coordinates`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Default is 80% centered (0.1 to 0.9)
            val pixelRect = viewModel.getCropPixelRect(1000, 1000)

            // Allow for floating-point precision issues (within 1 pixel)
            assertThat(pixelRect.left).isIn(99..101)
            assertThat(pixelRect.top).isIn(99..101)
            assertThat(pixelRect.right).isIn(899..901)
            assertThat(pixelRect.bottom).isIn(899..901)
        }
    }

    @Nested
    inner class UpdateCropRect {

        @Test
        fun `updateCropRect updates both cropRect and uiState`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val newRect = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
            viewModel.updateCropRect(newRect)

            val state = viewModel.uiState.value as RegionSelectionViewModel.UiState.Success
            assertThat(state.cropRect).isEqualTo(newRect)
            assertThat(viewModel.cropRect.value).isEqualTo(newRect)
        }

        @Test
        fun `updateCropRect applies constraints before updating`() = runTest {
            val photo = Photo(
                id = 1,
                filePath = "/path/to/photo.jpg",
                timestamp = 1000L,
                ocrStatus = OcrStatus.PENDING
            )
            coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Try invalid rect
            val invalidRect = CropRect(-0.5f, -0.5f, 0.02f, 0.02f)
            viewModel.updateCropRect(invalidRect)

            val state = viewModel.uiState.value as RegionSelectionViewModel.UiState.Success
            val resultRect = state.cropRect

            // Should be constrained
            assertThat(resultRect.left).isAtLeast(0f)
            assertThat(resultRect.top).isAtLeast(0f)
            assertThat(resultRect.width).isAtLeast(0.1f)
            assertThat(resultRect.height).isAtLeast(0.1f)
        }
    }

    @Nested
    inner class CropRectHelper {

        @Test
        fun `centered creates properly centered rectangle`() {
            val centered = CropRect.centered(0.5f, 0.5f)

            assertThat(centered.left).isWithin(0.001f).of(0.25f)
            assertThat(centered.top).isWithin(0.001f).of(0.25f)
            assertThat(centered.right).isWithin(0.001f).of(0.75f)
            assertThat(centered.bottom).isWithin(0.001f).of(0.75f)
        }

        @Test
        fun `centered with default params creates 80 percent rectangle`() {
            val centered = CropRect.centered()

            assertThat(centered.width).isWithin(0.001f).of(0.8f)
            assertThat(centered.height).isWithin(0.001f).of(0.8f)
        }

        @Test
        fun `width and height properties are correct`() {
            val rect = CropRect(0.1f, 0.2f, 0.6f, 0.9f)

            assertThat(rect.width).isWithin(0.001f).of(0.5f)
            assertThat(rect.height).isWithin(0.001f).of(0.7f)
        }
    }

    @Nested
    inner class JsonSerialization {

        @Test
        fun `toJson serializes CropRect correctly`() {
            val cropRect = CropRect(0.1f, 0.2f, 0.9f, 0.8f)
            val json = cropRect.toJson()

            assertThat(json).contains("0.1")
            assertThat(json).contains("0.2")
            assertThat(json).contains("0.9")
            assertThat(json).contains("0.8")
        }

        @Test
        fun `toCropRect deserializes JSON correctly`() {
            val cropRect = CropRect(0.1f, 0.2f, 0.9f, 0.8f)
            val json = cropRect.toJson()
            val deserialized = json.toCropRect()

            assertThat(deserialized).isNotNull()
            assertThat(deserialized?.left).isWithin(0.001f).of(0.1f)
            assertThat(deserialized?.top).isWithin(0.001f).of(0.2f)
            assertThat(deserialized?.right).isWithin(0.001f).of(0.9f)
            assertThat(deserialized?.bottom).isWithin(0.001f).of(0.8f)
        }

        @Test
        fun `roundtrip serialization preserves values`() {
            val original = CropRect(0.15f, 0.25f, 0.85f, 0.75f)
            val json = original.toJson()
            val restored = json.toCropRect()

            assertThat(restored).isNotNull()
            assertThat(restored?.left).isWithin(0.001f).of(original.left)
            assertThat(restored?.top).isWithin(0.001f).of(original.top)
            assertThat(restored?.right).isWithin(0.001f).of(original.right)
            assertThat(restored?.bottom).isWithin(0.001f).of(original.bottom)
        }

        @Test
        fun `toCropRect returns null for invalid JSON`() {
            val invalidJson = "not valid json"
            val result = invalidJson.toCropRect()

            assertThat(result).isNull()
        }

        @Test
        fun `toCropRect returns null for empty string`() {
            val result = "".toCropRect()

            assertThat(result).isNull()
        }

        @Test
        fun `toCropRect returns null for malformed JSON`() {
            val malformedJson = "{\"left\": \"not a number\"}"
            val result = malformedJson.toCropRect()

            assertThat(result).isNull()
        }
    }
}
