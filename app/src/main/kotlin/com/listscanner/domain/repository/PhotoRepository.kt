package com.listscanner.domain.repository

import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.domain.Result
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    fun getAllPhotos(): Flow<List<Photo>>
    suspend fun getPhotoById(id: Long): Result<Photo?>
    suspend fun insertPhoto(photo: Photo): Result<Long>
    suspend fun deletePhoto(id: Long): Result<Unit>
    suspend fun updateOcrStatus(id: Long, status: OcrStatus): Result<Unit>
}
