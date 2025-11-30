package com.listscanner.device

import android.graphics.Bitmap
import android.util.Base64
import com.listscanner.domain.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException

class CloudVisionServiceImpl(
    private val api: CloudVisionApi,
    private val apiKey: String,
    private val fileReader: (String) -> ByteArray = { path -> File(path).readBytes() },
    private val base64Encoder: (ByteArray) -> String = { bytes ->
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
) : CloudVisionService {

    override suspend fun recognizeText(imagePath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val imageBytes = fileReader(imagePath)
                val base64Image = base64Encoder(imageBytes)
                performOcrRequest(base64Image)
            } catch (e: FileNotFoundException) {
                Result.Failure(e, "Photo file not found: $imagePath")
            } catch (e: HttpException) {
                handleHttpException(e)
            } catch (e: Exception) {
                Result.Failure(e, "Network error: Unable to reach OCR service")
            }
        }

    override suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val imageBytes = stream.toByteArray()
                val base64Image = base64Encoder(imageBytes)
                performOcrRequest(base64Image)
            } catch (e: HttpException) {
                handleHttpException(e)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to process cropped image")
            }
        }

    private suspend fun performOcrRequest(base64Image: String): Result<String> {
        val request = CloudVisionRequest(
            requests = listOf(
                AnnotateImageRequest(
                    image = ImageContent(content = base64Image),
                    features = listOf(Feature(type = "TEXT_DETECTION"))
                )
            )
        )
        val response = api.annotateImage(apiKey, request)

        val apiError = response.responses.firstOrNull()?.error
        if (apiError != null) {
            return Result.Failure(
                RuntimeException("API error: ${apiError.message}"),
                "API error: ${apiError.message}"
            )
        }

        val text = response.responses.firstOrNull()?.fullTextAnnotation?.text
            ?: response.responses.firstOrNull()?.textAnnotations?.firstOrNull()?.description
            ?: ""
        return Result.Success(text)
    }

    private fun handleHttpException(e: HttpException): Result<String> {
        val message = when (e.code()) {
            401 -> "Invalid API key"
            403 -> "API quota exceeded"
            400 -> "Invalid image format"
            else -> "API error: ${e.message()}"
        }
        return Result.Failure(e, message)
    }
}
