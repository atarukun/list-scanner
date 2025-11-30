package com.listscanner.device

import android.graphics.Bitmap
import com.listscanner.domain.Result

interface CloudVisionService {
    suspend fun recognizeText(imagePath: String): Result<String>
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String>
}

// Request data classes
data class CloudVisionRequest(
    val requests: List<AnnotateImageRequest>
)

data class AnnotateImageRequest(
    val image: ImageContent,
    val features: List<Feature>
)

data class ImageContent(
    val content: String // Base64 encoded
)

data class Feature(
    val type: String = "TEXT_DETECTION"
)

// Response data classes
data class CloudVisionResponse(
    val responses: List<AnnotateImageResponse>
)

data class AnnotateImageResponse(
    val textAnnotations: List<TextAnnotation>? = null,
    val fullTextAnnotation: FullTextAnnotation? = null,
    val error: ApiError? = null
)

data class TextAnnotation(
    val description: String
)

data class FullTextAnnotation(
    val text: String
)

data class ApiError(
    val code: Int,
    val message: String
)
