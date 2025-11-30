package com.listscanner.data.repository

import androidx.room.withTransaction
import com.listscanner.data.ListScannerDatabase
import com.listscanner.data.dao.ListDao
import com.listscanner.data.dao.PhotoDao
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.ShoppingList
import com.listscanner.data.model.ListWithCounts
import com.listscanner.domain.Result
import com.listscanner.domain.repository.ListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ListRepositoryImpl(
    private val listDao: ListDao,
    private val photoDao: PhotoDao,
    private val database: ListScannerDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ListRepository {

    override fun getAllLists(): Flow<List<ShoppingList>> =
        listDao.getAll().flowOn(ioDispatcher)

    override fun getAllListsWithCounts(): Flow<List<ListWithCounts>> =
        listDao.getAllWithCounts().flowOn(ioDispatcher)

    override suspend fun getListById(id: Long): Result<ShoppingList?> =
        withContext(ioDispatcher) {
            try {
                Result.Success(listDao.getById(id))
            } catch (e: Exception) {
                Result.Failure(e, "Failed to get list by id")
            }
        }

    override suspend fun getListByPhotoId(photoId: Long): Result<ShoppingList?> =
        withContext(ioDispatcher) {
            try {
                Result.Success(listDao.getByPhotoId(photoId))
            } catch (e: Exception) {
                Result.Failure(e, "Failed to get list by photo id")
            }
        }

    override suspend fun insertList(list: ShoppingList): Result<Long> =
        withContext(ioDispatcher) {
            try {
                Result.Success(listDao.insert(list))
            } catch (e: Exception) {
                Result.Failure(e, "Failed to insert list")
            }
        }

    override suspend fun updateList(list: ShoppingList): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                listDao.update(list)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to update list")
            }
        }

    override suspend fun deleteList(id: Long): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                listDao.delete(id)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to delete list")
            }
        }

    override suspend fun deleteListAndResetPhoto(listId: Long, photoId: Long?): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                database.withTransaction {
                    listDao.delete(listId)
                    photoId?.let { photoDao.updateOcrStatus(it, OcrStatus.PENDING) }
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to delete list")
            }
        }
}
