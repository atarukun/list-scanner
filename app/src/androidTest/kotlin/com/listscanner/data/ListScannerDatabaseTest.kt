package com.listscanner.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.listscanner.data.entity.Item
import com.listscanner.data.entity.OcrStatus
import com.listscanner.data.entity.Photo
import com.listscanner.data.entity.ShoppingList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListScannerDatabaseTest {

    private lateinit var database: ListScannerDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ListScannerDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun databaseCreatesSuccessfully() {
        assertNotNull(database)
    }

    @Test
    fun photoDaoIsAccessible() {
        val dao = database.photoDao()
        assertNotNull(dao)
    }

    @Test
    fun listDaoIsAccessible() {
        val dao = database.listDao()
        assertNotNull(dao)
    }

    @Test
    fun itemDaoIsAccessible() {
        val dao = database.itemDao()
        assertNotNull(dao)
    }

    @Test
    fun databaseVersionIsOne() {
        assertEquals(1, database.openHelper.readableDatabase.version)
    }
}
