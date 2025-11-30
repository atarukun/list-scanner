package com.listscanner.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.listscanner.data.dao.ItemDao
import com.listscanner.data.dao.ListDao
import com.listscanner.data.dao.PhotoDao
import com.listscanner.data.entity.Item
import com.listscanner.data.entity.Photo
import com.listscanner.data.entity.ShoppingList

@Database(
    entities = [Photo::class, ShoppingList::class, Item::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ListScannerDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun listDao(): ListDao
    abstract fun itemDao(): ItemDao

    companion object {
        const val DATABASE_NAME = "list_scanner.db"
    }
}
