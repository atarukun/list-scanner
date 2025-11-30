package com.listscanner.domain.repository

import com.listscanner.data.entity.Item
import com.listscanner.domain.Result
import kotlinx.coroutines.flow.Flow

interface ItemRepository {
    fun getItemsForList(listId: Long): Flow<List<Item>>
    suspend fun getItemById(id: Long): Result<Item?>
    suspend fun insertItem(item: Item): Result<Long>
    suspend fun insertItems(items: List<Item>): Result<List<Long>>
    suspend fun updateItem(item: Item): Result<Unit>
    suspend fun deleteItem(id: Long): Result<Unit>
    suspend fun updateItemChecked(id: Long, isChecked: Boolean): Result<Unit>
    suspend fun updateItemText(id: Long, text: String): Result<Unit>
    suspend fun getMaxPositionForList(listId: Long): Result<Int>
    suspend fun updateItemPositions(itemPositions: List<Pair<Long, Int>>): Result<Unit>
}
