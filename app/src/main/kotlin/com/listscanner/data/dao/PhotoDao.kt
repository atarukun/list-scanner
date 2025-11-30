package com.listscanner.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: Photo): Long

    @Query("SELECT * FROM photos ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): Photo?

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE photos SET ocr_status = :status WHERE id = :id")
    suspend fun updateOcrStatus(id: Long, status: OcrStatus)
}
