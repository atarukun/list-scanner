package com.listscanner.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.listscanner.data.entity.Item
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Item>): List<Long>

    @Query("SELECT * FROM items WHERE list_id = :listId ORDER BY position ASC")
    fun getItemsForList(listId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): Item?

    @Update
    suspend fun update(item: Item)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE items SET is_checked = :isChecked WHERE id = :id")
    suspend fun updateIsChecked(id: Long, isChecked: Boolean)

    @Query("UPDATE items SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM items WHERE list_id = :listId")
    suspend fun getMaxPositionForList(listId: Long): Int

    @Query("UPDATE items SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}
