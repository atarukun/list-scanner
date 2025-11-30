package com.listscanner.data.repository

import com.listscanner.data.dao.PhotoDao
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.domain.Result
import com.listscanner.domain.repository.PhotoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class PhotoRepositoryImpl(
    private val photoDao: PhotoDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PhotoRepository {

    override fun getAllPhotos(): Flow<List<Photo>> =
        photoDao.getAll().flowOn(ioDispatcher)

    override suspend fun getPhotoById(id: Long): Result<Photo?> =
        withContext(ioDispatcher) {
            try {
                Result.Success(photoDao.getById(id))
            } catch (e: Exception) {
                Result.Failure(e, "Failed to get photo by id")
            }
        }

    override suspend fun insertPhoto(photo: Photo): Result<Long> =
        withContext(ioDispatcher) {
            try {
                Result.Success(photoDao.insert(photo))
            } catch (e: Exception) {
                Result.Failure(e, "Failed to insert photo")
            }
        }

    override suspend fun deletePhoto(id: Long): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val photo = photoDao.getById(id)
                if (photo != null) {
                    val file = File(photo.filePath)
                    if (file.exists()) {
                        val deleted = file.delete()
                        if (!deleted && file.exists()) {
                            return@withContext Result.Failure(
                                IOException("Failed to delete photo file"),
                                "Could not delete photo file from storage"
                            )
                        }
                    }
                }
                photoDao.delete(id)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to delete photo")
            }
        }

    override suspend fun updateOcrStatus(id: Long, status: OcrStatus): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                photoDao.updateOcrStatus(id, status)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to update OCR status")
            }
        }
}
