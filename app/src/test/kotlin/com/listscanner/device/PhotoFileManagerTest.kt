package com.listscanner.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PhotoFileManagerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var mockContext: Context
    private lateinit var filesDir: File

    @BeforeEach
    fun setup() {
        filesDir = File(tempDir, "files")
        filesDir.mkdirs()

        mockContext = mockk {
            every { filesDir } returns this@PhotoFileManagerTest.filesDir
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getPhotosDirectory creates directory if not exists`() {
        val photosDir = PhotoFileManager.getPhotosDirectory(mockContext)

        assertThat(photosDir.exists()).isTrue()
        assertThat(photosDir.isDirectory).isTrue()
        assertThat(photosDir.name).isEqualTo("photos")
    }

    @Test
    fun `getPhotosDirectory returns existing directory`() {
        val existingPhotosDir = File(filesDir, "photos")
        existingPhotosDir.mkdirs()
        val testFile = File(existingPhotosDir, "existing.jpg")
        testFile.createNewFile()

        val photosDir = PhotoFileManager.getPhotosDirectory(mockContext)

        assertThat(photosDir.exists()).isTrue()
        assertThat(File(photosDir, "existing.jpg").exists()).isTrue()
    }

    @Test
    fun `generatePhotoFile returns file with correct naming format`() {
        val photoFile = PhotoFileManager.generatePhotoFile(mockContext)

        assertThat(photoFile.name).startsWith("photo_")
        assertThat(photoFile.name).endsWith(".jpg")
        assertThat(photoFile.name).matches("photo_\\d{8}_\\d{6}\\.jpg")
        assertThat(photoFile.parentFile?.name).isEqualTo("photos")
    }

    @Test
    fun `generatePhotoFile creates photos directory if not exists`() {
        val photoFile = PhotoFileManager.generatePhotoFile(mockContext)

        assertThat(photoFile.parentFile?.exists()).isTrue()
    }

    @Test
    fun `deletePhotoFile removes file successfully`() {
        val photosDir = PhotoFileManager.getPhotosDirectory(mockContext)
        val testFile = File(photosDir, "test_photo.jpg")
        testFile.createNewFile()

        val result = PhotoFileManager.deletePhotoFile(testFile.absolutePath)

        assertThat(result).isTrue()
        assertThat(testFile.exists()).isFalse()
    }

    @Test
    fun `deletePhotoFile returns false for non-existent file`() {
        val result = PhotoFileManager.deletePhotoFile("/non/existent/path.jpg")

        assertThat(result).isFalse()
    }

    @Test
    fun `compressToJpeg creates compressed output file`() {
        mockkStatic(BitmapFactory::class)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { BitmapFactory.decodeFile(any()) } returns mockBitmap
        every { mockBitmap.compress(any(), any(), any()) } returns true

        val photosDir = PhotoFileManager.getPhotosDirectory(mockContext)
        val sourceFile = File(photosDir, "source.jpg")
        sourceFile.writeText("fake image data")
        val destFile = File(photosDir, "dest.jpg")

        val result = PhotoFileManager.compressToJpeg(sourceFile, destFile, 85)

        assertThat(result).isTrue()
        assertThat(sourceFile.exists()).isFalse()
    }

    @Test
    fun `compressToJpeg returns false when bitmap decode fails`() {
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeFile(any()) } returns null

        val photosDir = PhotoFileManager.getPhotosDirectory(mockContext)
        val sourceFile = File(photosDir, "source.jpg")
        sourceFile.writeText("invalid image data")
        val destFile = File(photosDir, "dest.jpg")

        val result = PhotoFileManager.compressToJpeg(sourceFile, destFile, 85)

        assertThat(result).isFalse()
    }
}
