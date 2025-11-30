package com.listscanner.device

import android.graphics.Bitmap
import com.listscanner.domain.Result
import com.listscanner.ui.screens.regionselection.CropRect

interface ImageCropService {
    suspend fun cropImage(imagePath: String, cropRect: CropRect): Result<Bitmap>
}
