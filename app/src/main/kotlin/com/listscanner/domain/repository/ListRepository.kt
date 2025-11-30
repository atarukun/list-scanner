package com.listscanner.domain.repository

import com.listscanner.data.entity.ShoppingList
import com.listscanner.data.model.ListWithCounts
import com.listscanner.domain.Result
import kotlinx.coroutines.flow.Flow

interface ListRepository {
    fun getAllLists(): Flow<List<ShoppingList>>
    fun getAllListsWithCounts(): Flow<List<ListWithCounts>>
    suspend fun getListById(id: Long): Result<ShoppingList?>
    suspend fun getListByPhotoId(photoId: Long): Result<ShoppingList?>
    suspend fun insertList(list: ShoppingList): Result<Long>
    suspend fun updateList(list: ShoppingList): Result<Unit>
    suspend fun deleteList(id: Long): Result<Unit>
    suspend fun deleteListAndResetPhoto(listId: Long, photoId: Long?): Result<Unit>
}
