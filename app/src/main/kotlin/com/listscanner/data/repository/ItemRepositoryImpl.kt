package com.listscanner.data.repository

import androidx.room.withTransaction
import com.listscanner.data.ListScannerDatabase
import com.listscanner.data.dao.ItemDao
import com.listscanner.data.entity.Item
import com.listscanner.domain.Result
import com.listscanner.domain.repository.ItemRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ItemRepositoryImpl(
    private val itemDao: ItemDao,
    private val db: ListScannerDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ItemRepository {

    override fun getItemsForList(listId: Long): Flow<List<Item>> =
        itemDao.getItemsForList(listId).flowOn(ioDispatcher)

    override suspend fun getItemById(id: Long): Result<Item?> =
        withContext(ioDispatcher) {
            try {
                Result.Success(itemDao.getById(id))
            } catch (e: Exception) {
                Result.Failure(e, "Failed to get item by id")
            }
        }

    override suspend fun insertItem(item: Item): Result<Long> =
        withContext(ioDispatcher) {
            try {
                Result.Success(itemDao.insert(item))
            } catch (e: Exception) {
                Result.Failure(e, "Failed to insert item")
            }
        }

    override suspend fun insertItems(items: List<Item>): Result<List<Long>> =
        withContext(ioDispatcher) {
            try {
                Result.Success(itemDao.insertAll(items))
            } catch (e: Exception) {
                Result.Failure(e, "Failed to insert items")
            }
        }

    override suspend fun updateItem(item: Item): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                itemDao.update(item)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to update item")
            }
        }

    override suspend fun deleteItem(id: Long): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                itemDao.delete(id)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to delete item")
            }
        }

    override suspend fun updateItemChecked(id: Long, isChecked: Boolean): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                itemDao.updateIsChecked(id, isChecked)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to update item checked status")
            }
        }

    override suspend fun updateItemText(id: Long, text: String): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                itemDao.updateText(id, text)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to update item text")
            }
        }

    override suspend fun getMaxPositionForList(listId: Long): Result<Int> =
        withContext(ioDispatcher) {
            try {
                Result.Success(itemDao.getMaxPositionForList(listId))
            } catch (e: Exception) {
                Result.Failure(e, "Failed to get max position for list")
            }
        }

    override suspend fun updateItemPositions(itemPositions: List<Pair<Long, Int>>): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                db.withTransaction {
                    itemPositions.forEach { (id, position) ->
                        itemDao.updatePosition(id, position)
                    }
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e, "Failed to update item positions")
            }
        }
}
