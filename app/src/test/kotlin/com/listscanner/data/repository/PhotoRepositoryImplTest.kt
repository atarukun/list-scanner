package com.listscanner.data.repository

import com.google.common.truth.Truth.assertThat
import com.listscanner.data.dao.PhotoDao
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.domain.Result
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PhotoRepositoryImplTest {

    private val photoDao: PhotoDao = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PhotoRepositoryImpl

    @BeforeEach
    fun setup() {
        repository = PhotoRepositoryImpl(photoDao, testDispatcher)
    }

    @Test
    fun `getAllPhotos returns flow from dao`() = runTest(testDispatcher) {
        val photos = listOf(
            Photo(id = 1, filePath = "/test/photo1.jpg"),
            Photo(id = 2, filePath = "/test/photo2.jpg")
        )
        every { photoDao.getAll() } returns flowOf(photos)

        val result = repository.getAllPhotos().first()

        assertThat(result).hasSize(2)
        assertThat(result[0].filePath).isEqualTo("/test/photo1.jpg")
    }

    @Test
    fun `getPhotoById success returns Result Success`() = runTest(testDispatcher) {
        val photo = Photo(id = 1, filePath = "/test/photo.jpg")
        coEvery { photoDao.getById(1L) } returns photo

        val result = repository.getPhotoById(1L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(photo)
        coVerify { photoDao.getById(1L) }
    }

    @Test
    fun `getPhotoById returns null when not found`() = runTest(testDispatcher) {
        coEvery { photoDao.getById(999L) } returns null

        val result = repository.getPhotoById(999L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isNull()
    }

    @Test
    fun `getPhotoById failure returns Result Failure`() = runTest(testDispatcher) {
        coEvery { photoDao.getById(any()) } throws RuntimeException("Database error")

        val result = repository.getPhotoById(1L)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to get photo by id")
    }

    @Test
    fun `insertPhoto success returns Result Success with id`() = runTest(testDispatcher) {
        val photo = Photo(filePath = "/test/photo.jpg")
        coEvery { photoDao.insert(photo) } returns 1L

        val result = repository.insertPhoto(photo)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(1L)
        coVerify { photoDao.insert(photo) }
    }

    @Test
    fun `insertPhoto failure returns Result Failure`() = runTest(testDispatcher) {
        val photo = Photo(filePath = "/test/photo.jpg")
        coEvery { photoDao.insert(any()) } throws RuntimeException("Insert failed")

        val result = repository.insertPhoto(photo)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to insert photo")
    }

    @Test
    fun `deletePhoto removes database record and file from disk`() = runTest(testDispatcher) {
        val testFile = java.io.File.createTempFile("test_photo", ".jpg")
        testFile.writeText("test content")
        val photo = Photo(id = 1, filePath = testFile.absolutePath, timestamp = 1000L)

        coEvery { photoDao.getById(1L) } returns photo
        coEvery { photoDao.delete(1L) } just Runs

        val result = repository.deletePhoto(1L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(testFile.exists()).isFalse()
        coVerify { photoDao.delete(1L) }
    }

    @Test
    fun `deletePhoto succeeds when file already missing`() = runTest(testDispatcher) {
        val photo = Photo(id = 1, filePath = "/nonexistent/path.jpg", timestamp = 1000L)

        coEvery { photoDao.getById(1L) } returns photo
        coEvery { photoDao.delete(1L) } just Runs

        val result = repository.deletePhoto(1L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { photoDao.delete(1L) }
    }

    @Test
    fun `deletePhoto returns Failure when file cannot be deleted`() = runTest(testDispatcher) {
        // Test using a directory path (files can't be deleted when they're actually directories)
        val testDir = java.io.File.createTempFile("test_photo", ".jpg")
        testDir.delete()
        testDir.mkdir()
        // Create a file inside to make directory non-empty and undeletable as a file
        java.io.File(testDir, "inner.txt").writeText("content")

        val photo = Photo(id = 1, filePath = testDir.absolutePath, timestamp = 1000L)

        coEvery { photoDao.getById(1L) } returns photo

        val result = repository.deletePhoto(1L)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).contains("Could not delete photo file")
        coVerify(exactly = 0) { photoDao.delete(any()) }

        // Cleanup
        java.io.File(testDir, "inner.txt").delete()
        testDir.delete()
    }

    @Test
    fun `deletePhoto succeeds when photo not found in database`() = runTest(testDispatcher) {
        coEvery { photoDao.getById(1L) } returns null
        coEvery { photoDao.delete(1L) } just Runs

        val result = repository.deletePhoto(1L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { photoDao.delete(1L) }
    }

    @Test
    fun `deletePhoto failure returns Result Failure`() = runTest(testDispatcher) {
        coEvery { photoDao.getById(any()) } throws RuntimeException("Database error")

        val result = repository.deletePhoto(1L)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to delete photo")
    }

    @Test
    fun `updateOcrStatus success returns Result Success`() = runTest(testDispatcher) {
        coEvery { photoDao.updateOcrStatus(1L, OcrStatus.COMPLETED) } just Runs

        val result = repository.updateOcrStatus(1L, OcrStatus.COMPLETED)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { photoDao.updateOcrStatus(1L, OcrStatus.COMPLETED) }
    }

    @Test
    fun `updateOcrStatus failure returns Result Failure`() = runTest(testDispatcher) {
        coEvery { photoDao.updateOcrStatus(any(), any()) } throws RuntimeException("Update failed")

        val result = repository.updateOcrStatus(1L, OcrStatus.COMPLETED)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to update OCR status")
    }
}
