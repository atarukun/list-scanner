package com.listscanner.device

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import com.listscanner.domain.Result
import com.listscanner.ui.screens.regionselection.CropRect
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImageCropServiceTest {

    private lateinit var service: ImageCropServiceImpl

    @BeforeEach
    fun setup() {
        mockkStatic(BitmapFactory::class)
        mockkStatic(Bitmap::class)
        service = ImageCropServiceImpl()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `cropImage returns Success with correctly sized bitmap`() = runTest {
        val mockSourceBitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 1000
            every { height } returns 800
        }
        val mockCroppedBitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 800
            every { height } returns 600
        }

        // First call with inJustDecodeBounds = true returns dimensions
        every { BitmapFactory.decodeFile(any(), any()) } answers {
            val options = secondArg<BitmapFactory.Options>()
            if (options.inJustDecodeBounds) {
                options.outWidth = 1000
                options.outHeight = 800
                null
            } else {
                mockSourceBitmap
            }
        }

        every { Bitmap.createBitmap(mockSourceBitmap, any(), any(), any(), any()) } returns mockCroppedBitmap

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.85f)
        val result = service.cropImage("/path/to/image.jpg", cropRect)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(mockCroppedBitmap)
    }

    @Test
    fun `cropImage returns Failure when file not found`() = runTest {
        every { BitmapFactory.decodeFile(any(), any()) } answers {
            val options = secondArg<BitmapFactory.Options>()
            if (options.inJustDecodeBounds) {
                options.outWidth = 0
                options.outHeight = 0
            }
            null
        }

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        val result = service.cropImage("/nonexistent/path.jpg", cropRect)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Photo file not found")
    }

    @Test
    fun `cropImage clamps crop rectangle to image bounds`() = runTest {
        val mockSourceBitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 100
            every { height } returns 100
        }
        val mockCroppedBitmap = mockk<Bitmap>(relaxed = true)

        every { BitmapFactory.decodeFile(any(), any()) } answers {
            val options = secondArg<BitmapFactory.Options>()
            if (options.inJustDecodeBounds) {
                options.outWidth = 100
                options.outHeight = 100
                null
            } else {
                mockSourceBitmap
            }
        }

        every { Bitmap.createBitmap(mockSourceBitmap, any(), any(), any(), any()) } returns mockCroppedBitmap

        // Crop rect that would exceed bounds
        val cropRect = CropRect(0.0f, 0.0f, 1.5f, 1.5f)
        val result = service.cropImage("/path/to/image.jpg", cropRect)

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `cropImage returns Failure on OutOfMemoryError`() = runTest {
        every { BitmapFactory.decodeFile(any(), any()) } answers {
            val options = secondArg<BitmapFactory.Options>()
            if (options.inJustDecodeBounds) {
                options.outWidth = 10000
                options.outHeight = 10000
                null
            } else {
                throw OutOfMemoryError("Java heap space")
            }
        }

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        val result = service.cropImage("/path/to/large_image.jpg", cropRect)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Image too large to crop")
    }

    @Test
    fun `cropImage returns Failure on general exception`() = runTest {
        every { BitmapFactory.decodeFile(any(), any()) } answers {
            val options = secondArg<BitmapFactory.Options>()
            if (options.inJustDecodeBounds) {
                options.outWidth = 1000
                options.outHeight = 1000
                null
            } else {
                throw RuntimeException("Bitmap decode error")
            }
        }

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        val result = service.cropImage("/path/to/image.jpg", cropRect)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).contains("Failed to crop image")
    }

    @Test
    fun `cropImage recycles source bitmap when different from cropped`() = runTest {
        val mockSourceBitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 1000
            every { height } returns 800
        }
        val mockCroppedBitmap = mockk<Bitmap>(relaxed = true)

        every { BitmapFactory.decodeFile(any(), any()) } answers {
            val options = secondArg<BitmapFactory.Options>()
            if (options.inJustDecodeBounds) {
                options.outWidth = 1000
                options.outHeight = 800
                null
            } else {
                mockSourceBitmap
            }
        }

        every { Bitmap.createBitmap(mockSourceBitmap, any(), any(), any(), any()) } returns mockCroppedBitmap

        val cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        service.cropImage("/path/to/image.jpg", cropRect)

        // Verify source bitmap was recycled (different from cropped)
        io.mockk.verify { mockSourceBitmap.recycle() }
    }

    @Test
    fun `cropImage does not recycle bitmap when same reference returned`() = runTest {
        val mockBitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 1000
            every { height } returns 800
        }

        every { BitmapFactory.decodeFile(any(), any()) } answers {
            val options = secondArg<BitmapFactory.Options>()
            if (options.inJustDecodeBounds) {
                options.outWidth = 1000
                options.outHeight = 800
                null
            } else {
                mockBitmap
            }
        }

        // Return the same bitmap (full image crop)
        every { Bitmap.createBitmap(mockBitmap, any(), any(), any(), any()) } returns mockBitmap

        val cropRect = CropRect(0.0f, 0.0f, 1.0f, 1.0f)
        service.cropImage("/path/to/image.jpg", cropRect)

        // Verify bitmap was NOT recycled (same reference)
        io.mockk.verify(exactly = 0) { mockBitmap.recycle() }
    }
}
