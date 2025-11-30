package com.listscanner.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.listscanner.data.entity.ShoppingList
import com.listscanner.data.model.ListWithCounts
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: ShoppingList): Long

    @Query("SELECT * FROM lists ORDER BY created_date DESC")
    fun getAll(): Flow<List<ShoppingList>>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun getById(id: Long): ShoppingList?

    @Query("SELECT * FROM lists WHERE photo_id = :photoId")
    suspend fun getByPhotoId(photoId: Long): ShoppingList?

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun delete(id: Long)

    @Update
    suspend fun update(list: ShoppingList)

    @Query("""
        SELECT
            l.*,
            COUNT(i.id) as itemCount,
            SUM(CASE WHEN i.is_checked = 1 THEN 1 ELSE 0 END) as checkedCount,
            p.file_path as photoFilePath
        FROM lists l
        LEFT JOIN items i ON l.id = i.list_id
        LEFT JOIN photos p ON l.photo_id = p.id
        GROUP BY l.id
        ORDER BY l.created_date DESC
    """)
    fun getAllWithCounts(): Flow<List<ListWithCounts>>
}
