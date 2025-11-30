package com.listscanner.ui.screens.review

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoReviewViewModelTest {

    private lateinit var mockPhotoRepository: PhotoRepository
    private lateinit var mockCloudVisionService: CloudVisionService
    private lateinit var mockImageCropService: ImageCropService
    private lateinit var mockListCreationService: ListCreationService
    private lateinit var mockNetworkConnectivityService: NetworkConnectivityService
    private lateinit var mockPrivacyConsentRepository: PrivacyConsentRepository
    private lateinit var mockUsageTrackingRepository: UsageTrackingRepository
    private val testDispatcher = StandardTestDispatcher()

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockPhotoRepository = mockk()
        mockCloudVisionService = mockk()
        mockImageCropService = mockk()
        mockListCreationService = mockk()
        mockNetworkConnectivityService = mockk {
            every { observeNetworkState() } returns flowOf(true)
            every { isNetworkAvailable() } returns true
        }
        mockPrivacyConsentRepository = mockk {
            coEvery { hasUserConsented() } returns true
            coEvery { setUserConsent(any()) } returns Unit
            every { observeConsentState() } returns flowOf(true)
        }
        mockUsageTrackingRepository = mockk {
            coEvery { incrementUsage() } returns Unit
            coEvery { getWeeklyUsage() } returns 0
            coEvery { shouldShowCostWarning() } returns false
            coEvery { markWarningShown() } returns Unit
            coEvery { resetIfNewWeek() } returns Unit
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(photoId: Long = 1L) = PhotoReviewViewModel(
        mockPhotoRepository,
        mockCloudVisionService,
        mockImageCropService,
        mockListCreationService,
        mockNetworkConnectivityService,
        mockPrivacyConsentRepository,
        mockUsageTrackingRepository,
        photoId
    )

    @Test
    fun `initial state is Loading`() = runTest {
        coEvery { mockPhotoRepository.getPhotoById(any()) } returns Result.Success(null)

        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value)
            .isEqualTo(PhotoReviewViewModel.UiState.Loading)
    }

    @Test
    fun `successful photo load with existing file updates state to Success`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )
        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(PhotoReviewViewModel.UiState.Success::class.java)
        assertThat((state as PhotoReviewViewModel.UiState.Success).photo).isEqualTo(photo)
    }

    @Test
    fun `photo not found updates state to Error`() = runTest {
        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(null)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value)
            .isEqualTo(PhotoReviewViewModel.UiState.Error("Photo not found"))
    }

    @Test
    fun `photo file missing updates state to Error`() = runTest {
        val photo = Photo(
            id = 1,
            filePath = "/nonexistent/path/photo.jpg",
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )
        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value)
            .isEqualTo(PhotoReviewViewModel.UiState.Error("Photo file missing"))
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
            .isEqualTo(PhotoReviewViewModel.UiState.Error("Failed to load photo"))
    }

    @Test
    fun `deletePhoto success updates state to Deleted`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )
        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.deletePhoto(1L) } returns Result.Success(Unit)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deletePhoto()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value)
            .isEqualTo(PhotoReviewViewModel.UiState.Deleted)

        coVerify { mockPhotoRepository.deletePhoto(1L) }
    }

    @Test
    fun `deletePhoto failure keeps current state`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )
        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.deletePhoto(1L) } returns Result.Failure(
            exception = Exception("Delete failed"),
            message = "Failed to delete"
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val stateBeforeDelete = viewModel.uiState.value

        viewModel.deletePhoto()
        testDispatcher.scheduler.advanceUntilIdle()

        // State should remain Success, not change to Deleted
        assertThat(viewModel.uiState.value).isEqualTo(stateBeforeDelete)
    }

    // OCR Processing Tests

    @Test
    fun `processOcr updates status to PROCESSING then COMPLETED on success`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Milk\nEggs")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PROCESSING) }
        coVerify(exactly = 1) { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.COMPLETED) }
    }

    @Test
    fun `processOcr reverts status to PENDING on failure`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Failure(
            Exception("OCR failed"),
            "Text recognition failed"
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PROCESSING) }
        coVerify(exactly = 1) { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PENDING) }
    }

    @Test
    fun `processOcr ignores duplicate calls while processing (debouncing)`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } coAnswers {
            delay(1000)
            Result.Success("Text")
        }
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // First call - starts processing
        viewModel.processOcr()
        // Advance slightly to let the coroutine set _isProcessing.value = true
        testDispatcher.scheduler.advanceTimeBy(1)

        // These should be ignored because isProcessing is true
        viewModel.processOcr()
        viewModel.processOcr()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockCloudVisionService.recognizeText(any()) }
    }

    @Test
    fun `processOcr shows error dialog on failure`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Failure(
            Exception("OCR failed"),
            "Network error: Unable to reach OCR service"
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        val errorState = viewModel.ocrErrorDialogState.value
        assertThat(errorState).isNotNull()
        assertThat(errorState?.message).isEqualTo("Network unavailable. Please check your connection and try again.")
        assertThat(errorState?.errorType).isEqualTo(OcrErrorType.NetworkError)
    }

    // Network Connectivity Tests

    @Test
    fun `processOcr does not execute when offline`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

        // Setup offline network state using MutableStateFlow for immediate emission
        val networkStateFlow = MutableStateFlow(false)
        val offlineNetworkService: NetworkConnectivityService = mockk {
            every { observeNetworkState() } returns networkStateFlow
        }

        val viewModel = PhotoReviewViewModel(
            mockPhotoRepository,
            mockCloudVisionService,
            mockImageCropService,
            mockListCreationService,
            offlineNetworkService,
            mockPrivacyConsentRepository,
            mockUsageTrackingRepository,
            1L
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify network state is false before calling processOcr
        assertThat(viewModel.isNetworkAvailable.value).isFalse()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify no OCR processing started - updateOcrStatus should never be called
        coVerify(exactly = 0) { mockPhotoRepository.updateOcrStatus(any(), OcrStatus.PROCESSING) }
    }

    @Test
    fun `isNetworkAvailable state updates from network service`() = runTest {
        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(null)

        // Setup offline network state using MutableStateFlow for immediate emission
        val networkStateFlow = MutableStateFlow(false)
        val offlineNetworkService: NetworkConnectivityService = mockk {
            every { observeNetworkState() } returns networkStateFlow
        }

        val viewModel = PhotoReviewViewModel(
            mockPhotoRepository,
            mockCloudVisionService,
            mockImageCropService,
            mockListCreationService,
            offlineNetworkService,
            mockPrivacyConsentRepository,
            mockUsageTrackingRepository,
            1L
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // The isNetworkAvailable state should reflect the network service state
        assertThat(viewModel.isNetworkAvailable.value).isFalse()
    }

    @Test
    fun `processOcr executes when online`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Test text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        // Setup online network state (default mock)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify OCR processing started
        coVerify(exactly = 1) { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PROCESSING) }
    }

    // Privacy Consent Tests

    @Test
    fun `processOcr shows consent dialog when not consented`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPrivacyConsentRepository.hasUserConsented() } returns false

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showConsentDialog.value).isTrue()
        coVerify(exactly = 0) { mockPhotoRepository.updateOcrStatus(any(), OcrStatus.PROCESSING) }
    }

    @Test
    fun `processOcr proceeds directly when already consented`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Test text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)
        coEvery { mockPrivacyConsentRepository.hasUserConsented() } returns true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showConsentDialog.value).isFalse()
        coVerify { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PROCESSING) }
    }

    @Test
    fun `onConsentAccepted stores consent and triggers OCR`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Test text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onConsentAccepted()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockPrivacyConsentRepository.setUserConsent(true) }
        coVerify { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PROCESSING) }
        assertThat(viewModel.showConsentDialog.value).isFalse()
    }

    @Test
    fun `onConsentDeclined dismisses dialog without OCR`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPrivacyConsentRepository.hasUserConsented() } returns false

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // First trigger the dialog
        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showConsentDialog.value).isTrue()

        // Then decline
        viewModel.onConsentDeclined()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showConsentDialog.value).isFalse()
        coVerify(exactly = 0) { mockPhotoRepository.updateOcrStatus(any(), OcrStatus.PROCESSING) }
    }

    @Test
    fun `subsequent processOcr calls skip dialog after consent given`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Test text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        // First call: not consented
        coEvery { mockPrivacyConsentRepository.hasUserConsented() } returns false

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showConsentDialog.value).isTrue()

        // Accept consent
        coEvery { mockPrivacyConsentRepository.hasUserConsented() } returns true
        viewModel.onConsentAccepted()
        testDispatcher.scheduler.advanceUntilIdle()

        // Reset processing state for second call
        assertThat(viewModel.showConsentDialog.value).isFalse()
        coVerify { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PROCESSING) }
    }

    // Usage Tracking Tests

    @Test
    fun `successful OCR increments usage counter`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Test text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockUsageTrackingRepository.incrementUsage() }
    }

    @Test
    fun `cost warning dialog shown when threshold crossed`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Test text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)
        coEvery { mockUsageTrackingRepository.shouldShowCostWarning() } returns true
        coEvery { mockUsageTrackingRepository.getWeeklyUsage() } returns 667

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showCostWarningDialog.value).isTrue()
        assertThat(viewModel.currentWeeklyUsage.value).isEqualTo(667)
    }

    @Test
    fun `cost warning not shown when already dismissed for threshold`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Test text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)
        coEvery { mockUsageTrackingRepository.shouldShowCostWarning() } returns false

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showCostWarningDialog.value).isFalse()
    }

    @Test
    fun `onCostWarningDismissed hides dialog and marks warning shown`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Test text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)
        coEvery { mockUsageTrackingRepository.shouldShowCostWarning() } returns true
        coEvery { mockUsageTrackingRepository.getWeeklyUsage() } returns 667

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showCostWarningDialog.value).isTrue()

        viewModel.onCostWarningDismissed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showCostWarningDialog.value).isFalse()
        coVerify { mockUsageTrackingRepository.markWarningShown() }
    }

    // OCR Error Dialog Tests

    @Test
    fun `processOcr with empty text shows empty text error dialog`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        val errorState = viewModel.ocrErrorDialogState.value
        assertThat(errorState).isNotNull()
        assertThat(errorState?.message).isEqualTo("No text recognized. Try retaking the photo with better lighting.")
        assertThat(errorState?.errorType).isEqualTo(OcrErrorType.EmptyText)
    }

    @Test
    fun `processOcr with blank text shows empty text error dialog`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("   \n\t  ")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        val errorState = viewModel.ocrErrorDialogState.value
        assertThat(errorState).isNotNull()
        assertThat(errorState?.errorType).isEqualTo(OcrErrorType.EmptyText)
    }

    @Test
    fun `processOcr with zero parsed items shows no items error dialog`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("some text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Failure(
            IllegalStateException("No items parsed"),
            "No list items detected. Ensure your list is clearly written."
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        val errorState = viewModel.ocrErrorDialogState.value
        assertThat(errorState).isNotNull()
        assertThat(errorState?.message).isEqualTo("No list items detected. Ensure your list is clearly written.")
        assertThat(errorState?.errorType).isEqualTo(OcrErrorType.NoItems)
    }

    @Test
    fun `processOcr with file not found shows file error dialog`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Failure(
            java.io.FileNotFoundException("file"),
            "Photo file not found: /path/photo.jpg"
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        val errorState = viewModel.ocrErrorDialogState.value
        assertThat(errorState).isNotNull()
        assertThat(errorState?.message).isEqualTo("Photo file not found or corrupted.")
        assertThat(errorState?.errorType).isEqualTo(OcrErrorType.FileError)
    }

    @Test
    fun `processOcr with generic API error shows api error dialog`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Failure(
            Exception("API error"),
            "API error: Bad Request"
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        val errorState = viewModel.ocrErrorDialogState.value
        assertThat(errorState).isNotNull()
        assertThat(errorState?.message).isEqualTo("OCR processing failed. Please try again.")
        assertThat(errorState?.errorType).isEqualTo(OcrErrorType.ApiError)
    }

    @Test
    fun `retryFromErrorDialog clears error and triggers processOcr`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Failure(
            Exception("First failure"),
            "Network error: Unable to reach OCR service"
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // First call fails
        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.ocrErrorDialogState.value).isNotNull()

        // Now make it succeed on retry
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("Milk")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        viewModel.retryFromErrorDialog()
        testDispatcher.scheduler.advanceUntilIdle()

        // Error should be cleared
        assertThat(viewModel.ocrErrorDialogState.value).isNull()
        // OCR should have been retried
        coVerify(exactly = 2) { mockCloudVisionService.recognizeText(any()) }
    }

    @Test
    fun `dismissOcrErrorDialog clears error state`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.ocrErrorDialogState.value).isNotNull()

        viewModel.dismissOcrErrorDialog()

        assertThat(viewModel.ocrErrorDialogState.value).isNull()
    }

    @Test
    fun `photo status remains PENDING after error dialog shown`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockCloudVisionService.recognizeText(any()) } returns Result.Success("")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processOcr()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify status was set back to PENDING after the error
        coVerify { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PROCESSING) }
        coVerify { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PENDING) }

        // UI state should show PENDING status
        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(PhotoReviewViewModel.UiState.Success::class.java)
        assertThat((state as PhotoReviewViewModel.UiState.Success).photo.ocrStatus).isEqualTo(OcrStatus.PENDING)
    }

    // Cropped OCR Tests (Story 5.3)

    @Test
    fun `processCroppedOcr calls imageCropService with correct parameters`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        val mockBitmap = mockk<Bitmap>(relaxed = true)

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockImageCropService.cropImage(any(), any()) } returns Result.Success(mockBitmap)
        coEvery { mockCloudVisionService.recognizeTextFromBitmap(any()) } returns Result.Success("Cropped text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        viewModel.processCroppedOcr(cropRect)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify crop service was called with correct params
        coVerify { mockImageCropService.cropImage(tempFile.absolutePath, cropRect) }
        coVerify { mockCloudVisionService.recognizeTextFromBitmap(mockBitmap) }
    }

    @Test
    fun `processCroppedOcr uses recognizeTextFromBitmap not recognizeText`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        val mockBitmap = mockk<Bitmap>(relaxed = true)

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockImageCropService.cropImage(any(), any()) } returns Result.Success(mockBitmap)
        coEvery { mockCloudVisionService.recognizeTextFromBitmap(any()) } returns Result.Success("Milk\nEggs")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val cropRect = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        viewModel.processCroppedOcr(cropRect)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should use recognizeTextFromBitmap, NOT recognizeText
        coVerify(exactly = 1) { mockCloudVisionService.recognizeTextFromBitmap(mockBitmap) }
        coVerify(exactly = 0) { mockCloudVisionService.recognizeText(any()) }

        // Full OCR flow should complete
        coVerify(exactly = 1) { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.PROCESSING) }
        coVerify(exactly = 1) { mockPhotoRepository.updateOcrStatus(1L, OcrStatus.COMPLETED) }
    }

    @Test
    fun `processCroppedOcr shows CropError on crop failure`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockImageCropService.cropImage(any(), any()) } returns Result.Failure(
            OutOfMemoryError("Heap space"),
            "Image too large to crop"
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        viewModel.processCroppedOcr(cropRect)
        testDispatcher.scheduler.advanceUntilIdle()

        val errorState = viewModel.ocrErrorDialogState.value
        assertThat(errorState).isNotNull()
        assertThat(errorState?.message).isEqualTo("Image too large to crop. Try scanning the full image instead.")
        assertThat(errorState?.errorType).isEqualTo(OcrErrorType.CropError)
    }

    @Test
    fun `processCroppedOcr recycles bitmap after OCR`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        val mockBitmap = mockk<Bitmap>(relaxed = true)

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockImageCropService.cropImage(any(), any()) } returns Result.Success(mockBitmap)
        coEvery { mockCloudVisionService.recognizeTextFromBitmap(any()) } returns Result.Success("Text")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(1L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        viewModel.processCroppedOcr(cropRect)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify bitmap was recycled
        verify { mockBitmap.recycle() }
    }

    @Test
    fun `processCroppedOcr navigates to list on success`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        val mockBitmap = mockk<Bitmap>(relaxed = true)
        val createdListId = 42L

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPhotoRepository.updateOcrStatus(1L, any()) } returns Result.Success(Unit)
        coEvery { mockImageCropService.cropImage(any(), any()) } returns Result.Success(mockBitmap)
        coEvery { mockCloudVisionService.recognizeTextFromBitmap(any()) } returns Result.Success("Milk\nEggs")
        coEvery { mockListCreationService.createListFromOcrResults(any(), any()) } returns Result.Success(createdListId)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val emittedListIds = mutableListOf<Long>()
        val job = launch {
            viewModel.navigateToList.collect { emittedListIds.add(it) }
        }

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        viewModel.processCroppedOcr(cropRect)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(emittedListIds).contains(createdListId)
        job.cancel()
    }

    @Test
    fun `processCroppedOcr does not execute when offline`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)

        // Setup offline network state
        val networkStateFlow = MutableStateFlow(false)
        val offlineNetworkService: NetworkConnectivityService = mockk {
            every { observeNetworkState() } returns networkStateFlow
        }

        val viewModel = PhotoReviewViewModel(
            mockPhotoRepository,
            mockCloudVisionService,
            mockImageCropService,
            mockListCreationService,
            offlineNetworkService,
            mockPrivacyConsentRepository,
            mockUsageTrackingRepository,
            1L
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        viewModel.processCroppedOcr(cropRect)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify no OCR processing started
        coVerify(exactly = 0) { mockPhotoRepository.updateOcrStatus(any(), OcrStatus.PROCESSING) }
    }

    @Test
    fun `processCroppedOcr shows consent dialog when not consented`() = runTest {
        val tempFile = File(tempDir.toFile(), "test_photo.jpg")
        tempFile.writeText("test")

        val photo = Photo(
            id = 1,
            filePath = tempFile.absolutePath,
            timestamp = 1000L,
            ocrStatus = OcrStatus.PENDING
        )

        coEvery { mockPhotoRepository.getPhotoById(1L) } returns Result.Success(photo)
        coEvery { mockPrivacyConsentRepository.hasUserConsented() } returns false

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        viewModel.processCroppedOcr(cropRect)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.showConsentDialog.value).isTrue()
        coVerify(exactly = 0) { mockPhotoRepository.updateOcrStatus(any(), OcrStatus.PROCESSING) }
    }
}
