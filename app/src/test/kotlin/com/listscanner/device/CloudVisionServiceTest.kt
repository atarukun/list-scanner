package com.listscanner.device

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.listscanner.domain.Result
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

class CloudVisionServiceTest {

    @MockK
    private lateinit var api: CloudVisionApi

    private lateinit var service: CloudVisionServiceImpl

    private val testImageBytes = "fake-image-data".toByteArray()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = CloudVisionServiceImpl(
            api = api,
            apiKey = "test-api-key",
            fileReader = { testImageBytes },
            base64Encoder = { bytes -> java.util.Base64.getEncoder().encodeToString(bytes) }
        )
    }

    @Test
    fun `recognizeText returns Success with text from fullTextAnnotation`() = runTest {
        val response = CloudVisionResponse(
            responses = listOf(
                AnnotateImageResponse(
                    fullTextAnnotation = FullTextAnnotation(text = "Milk\nBread\nCheese")
                )
            )
        )
        coEvery { api.annotateImage(any(), any()) } returns response

        val result = service.recognizeText("/path/to/image.jpg")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo("Milk\nBread\nCheese")
    }

    @Test
    fun `recognizeText returns Success with text from textAnnotations when fullTextAnnotation is null`() = runTest {
        val response = CloudVisionResponse(
            responses = listOf(
                AnnotateImageResponse(
                    textAnnotations = listOf(TextAnnotation(description = "Eggs\nButter"))
                )
            )
        )
        coEvery { api.annotateImage(any(), any()) } returns response

        val result = service.recognizeText("/path/to/image.jpg")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo("Eggs\nButter")
    }

    @Test
    fun `recognizeText returns Success with empty string when no text detected`() = runTest {
        val response = CloudVisionResponse(
            responses = listOf(AnnotateImageResponse())
        )
        coEvery { api.annotateImage(any(), any()) } returns response

        val result = service.recognizeText("/path/to/image.jpg")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEmpty()
    }

    @Test
    fun `recognizeText returns Failure when file not found`() = runTest {
        val serviceWithFileError = CloudVisionServiceImpl(
            api = api,
            apiKey = "test-api-key",
            fileReader = { path -> throw FileNotFoundException("File not found: $path") }
        )

        val result = serviceWithFileError.recognizeText("/nonexistent/path.jpg")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).contains("not found")
    }

    @Test
    fun `recognizeText returns Failure with invalid key message when API returns 401`() = runTest {
        coEvery { api.annotateImage(any(), any()) } throws HttpException(
            Response.error<CloudVisionResponse>(
                401,
                "Unauthorized".toResponseBody()
            )
        )

        val result = service.recognizeText("/path/to/image.jpg")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Invalid API key")
    }

    @Test
    fun `recognizeText returns Failure with quota exceeded message when API returns 403`() = runTest {
        coEvery { api.annotateImage(any(), any()) } throws HttpException(
            Response.error<CloudVisionResponse>(
                403,
                "Forbidden".toResponseBody()
            )
        )

        val result = service.recognizeText("/path/to/image.jpg")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("API quota exceeded")
    }

    @Test
    fun `recognizeText returns Failure with invalid format message when API returns 400`() = runTest {
        coEvery { api.annotateImage(any(), any()) } throws HttpException(
            Response.error<CloudVisionResponse>(
                400,
                "Bad Request".toResponseBody()
            )
        )

        val result = service.recognizeText("/path/to/image.jpg")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Invalid image format")
    }

    @Test
    fun `recognizeText returns Failure with network error message on IOException`() = runTest {
        coEvery { api.annotateImage(any(), any()) } throws IOException("Connection failed")

        val result = service.recognizeText("/path/to/image.jpg")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).contains("Network error")
    }

    // recognizeTextFromBitmap tests

    @Test
    fun `recognizeTextFromBitmap returns Success with text from fullTextAnnotation`() = runTest {
        val response = CloudVisionResponse(
            responses = listOf(
                AnnotateImageResponse(
                    fullTextAnnotation = FullTextAnnotation(text = "Cropped text")
                )
            )
        )
        coEvery { api.annotateImage(any(), any()) } returns response

        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.compress(any(), any(), any()) } answers {
            val stream = thirdArg<ByteArrayOutputStream>()
            stream.write("test-bitmap-data".toByteArray())
            true
        }

        val result = service.recognizeTextFromBitmap(mockBitmap)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo("Cropped text")
    }

    @Test
    fun `recognizeTextFromBitmap returns Success with empty string when no text detected`() = runTest {
        val response = CloudVisionResponse(
            responses = listOf(AnnotateImageResponse())
        )
        coEvery { api.annotateImage(any(), any()) } returns response

        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.compress(any(), any(), any()) } answers {
            val stream = thirdArg<ByteArrayOutputStream>()
            stream.write("test-bitmap-data".toByteArray())
            true
        }

        val result = service.recognizeTextFromBitmap(mockBitmap)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEmpty()
    }

    @Test
    fun `recognizeTextFromBitmap returns Failure on HttpException`() = runTest {
        coEvery { api.annotateImage(any(), any()) } throws HttpException(
            Response.error<CloudVisionResponse>(
                401,
                "Unauthorized".toResponseBody()
            )
        )

        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.compress(any(), any(), any()) } answers {
            val stream = thirdArg<ByteArrayOutputStream>()
            stream.write("test-bitmap-data".toByteArray())
            true
        }

        val result = service.recognizeTextFromBitmap(mockBitmap)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Invalid API key")
    }

    @Test
    fun `recognizeTextFromBitmap returns Failure on general exception`() = runTest {
        coEvery { api.annotateImage(any(), any()) } throws RuntimeException("Unexpected error")

        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.compress(any(), any(), any()) } answers {
            val stream = thirdArg<ByteArrayOutputStream>()
            stream.write("test-bitmap-data".toByteArray())
            true
        }

        val result = service.recognizeTextFromBitmap(mockBitmap)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to process cropped image")
    }
}
