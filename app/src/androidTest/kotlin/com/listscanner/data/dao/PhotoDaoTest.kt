package com.listscanner.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.listscanner.data.ListScannerDatabase
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoDaoTest {

    private lateinit var database: ListScannerDatabase
    private lateinit var photoDao: PhotoDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ListScannerDatabase::class.java
        ).allowMainThreadQueries().build()
        photoDao = database.photoDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrievePhoto() = runTest {
        val photo = Photo(filePath = "/test/photo.jpg")
        val photoId = photoDao.insert(photo)

        val retrieved = photoDao.getById(photoId)

        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.filePath).isEqualTo("/test/photo.jpg")
    }

    @Test
    fun insertReturnsGeneratedId() = runTest {
        val photo = Photo(filePath = "/test/photo.jpg")

        val photoId = photoDao.insert(photo)

        assertThat(photoId).isGreaterThan(0)
    }

    @Test
    fun getAllReturnsPhotosOrderedByTimestampDesc() = runTest {
        val photo1 = Photo(filePath = "/test/photo1.jpg", timestamp = 1000L)
        val photo2 = Photo(filePath = "/test/photo2.jpg", timestamp = 3000L)
        val photo3 = Photo(filePath = "/test/photo3.jpg", timestamp = 2000L)
        photoDao.insert(photo1)
        photoDao.insert(photo2)
        photoDao.insert(photo3)

        val photos = photoDao.getAll().first()

        assertThat(photos).hasSize(3)
        assertThat(photos[0].filePath).isEqualTo("/test/photo2.jpg")
        assertThat(photos[1].filePath).isEqualTo("/test/photo3.jpg")
        assertThat(photos[2].filePath).isEqualTo("/test/photo1.jpg")
    }

    @Test
    fun getByIdReturnsNullForNonExistent() = runTest {
        val retrieved = photoDao.getById(999L)

        assertThat(retrieved).isNull()
    }

    @Test
    fun deleteRemovesPhoto() = runTest {
        val photo = Photo(filePath = "/test/photo.jpg")
        val photoId = photoDao.insert(photo)

        photoDao.delete(photoId)

        val retrieved = photoDao.getById(photoId)
        assertThat(retrieved).isNull()
    }

    @Test
    fun updateOcrStatusChangesStatus() = runTest {
        val photo = Photo(filePath = "/test/photo.jpg", ocrStatus = OcrStatus.PENDING)
        val photoId = photoDao.insert(photo)

        photoDao.updateOcrStatus(photoId, OcrStatus.COMPLETED)

        val retrieved = photoDao.getById(photoId)
        assertThat(retrieved?.ocrStatus).isEqualTo(OcrStatus.COMPLETED)
    }

    @Test
    fun flowEmitsOnDataChange() = runTest {
        val photo1 = Photo(filePath = "/test/photo1.jpg")
        photoDao.insert(photo1)

        val initialPhotos = photoDao.getAll().first()
        assertThat(initialPhotos).hasSize(1)

        val photo2 = Photo(filePath = "/test/photo2.jpg")
        photoDao.insert(photo2)

        val updatedPhotos = photoDao.getAll().first()
        assertThat(updatedPhotos).hasSize(2)
    }

    @Test
    fun insertWithReplaceUpdatesExisting() = runTest {
        val photo = Photo(filePath = "/test/photo.jpg", ocrStatus = OcrStatus.PENDING)
        val photoId = photoDao.insert(photo)

        val updatedPhoto = Photo(id = photoId, filePath = "/test/updated.jpg", ocrStatus = OcrStatus.COMPLETED)
        photoDao.insert(updatedPhoto)

        val retrieved = photoDao.getById(photoId)
        assertThat(retrieved?.filePath).isEqualTo("/test/updated.jpg")
        assertThat(retrieved?.ocrStatus).isEqualTo(OcrStatus.COMPLETED)
    }
}
