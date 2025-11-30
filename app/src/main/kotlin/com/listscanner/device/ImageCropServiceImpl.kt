package com.listscanner.device

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.listscanner.domain.Result
import com.listscanner.ui.screens.regionselection.CropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

class ImageCropServiceImpl : ImageCropService {

    override suspend fun cropImage(imagePath: String, cropRect: CropRect): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            try {
                // Get original dimensions without loading full bitmap
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(imagePath, options)
                val originalWidth = options.outWidth
                val originalHeight = options.outHeight

                if (originalWidth <= 0 || originalHeight <= 0) {
                    return@withContext Result.Failure(
                        FileNotFoundException("Image not found or invalid: $imagePath"),
                        "Photo file not found"
                    )
                }

                // Calculate sample size for memory efficiency
                val sampleSize = calculateSampleSize(originalWidth, originalHeight, MAX_DIMENSION)

                // Load bitmap (potentially downsampled)
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                val sourceBitmap = BitmapFactory.decodeFile(imagePath, options)
                    ?: return@withContext Result.Failure(
                        FileNotFoundException("Image not found: $imagePath"),
                        "Photo file not found"
                    )

                // Get actual loaded dimensions (may differ due to sampling)
                val scaledWidth = sourceBitmap.width
                val scaledHeight = sourceBitmap.height

                // Convert normalized CropRect to pixel coordinates
                val pixelRect = cropRect.toPixelRect(scaledWidth, scaledHeight)

                // Clamp to bitmap bounds (defensive)
                val x = pixelRect.left.coerceIn(0, scaledWidth - 1)
                val y = pixelRect.top.coerceIn(0, scaledHeight - 1)
                val w = pixelRect.width.coerceIn(1, scaledWidth - x)
                val h = pixelRect.height.coerceIn(1, scaledHeight - y)

                // Create cropped bitmap
                val croppedBitmap = Bitmap.createBitmap(sourceBitmap, x, y, w, h)

                // Recycle source if different from cropped (createBitmap may return same reference)
                if (croppedBitmap !== sourceBitmap) {
                    sourceBitmap.recycle()
                }

                Result.Success(croppedBitmap)
            } catch (e: OutOfMemoryError) {
                Result.Failure(e, "Image too large to crop")
            } catch (e: Exception) {
                Result.Failure(e, "Failed to crop image: ${e.message}")
            }
        }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        private const val MAX_DIMENSION = 4096
    }
}
