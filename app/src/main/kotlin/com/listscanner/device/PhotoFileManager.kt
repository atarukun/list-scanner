package com.listscanner.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoFileManager {

    private const val PHOTOS_DIRECTORY = "photos"
    private const val PHOTO_PREFIX = "photo_"
    private const val PHOTO_EXTENSION = ".jpg"
    private const val TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"

    fun getPhotosDirectory(context: Context): File {
        val photosDir = File(context.filesDir, PHOTOS_DIRECTORY)
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        return photosDir
    }

    fun generatePhotoFile(context: Context): File {
        val photosDir = getPhotosDirectory(context)
        val timestamp = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US).format(Date())
        val filename = "$PHOTO_PREFIX$timestamp$PHOTO_EXTENSION"
        return File(photosDir, filename)
    }

    fun compressToJpeg(sourceFile: File, destFile: File, quality: Int = 85): Boolean {
        return try {
            // Read EXIF orientation before decoding
            val exif = ExifInterface(sourceFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            // Decode bitmap
            val originalBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return false

            // Apply rotation based on EXIF orientation
            val rotatedBitmap = rotateBitmapIfRequired(originalBitmap, orientation)

            // Compress and save
            FileOutputStream(destFile).use { out ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }

            // Clean up
            if (rotatedBitmap !== originalBitmap) {
                originalBitmap.recycle()
            }
            rotatedBitmap.recycle()
            sourceFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap // No rotation needed
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun deletePhotoFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
