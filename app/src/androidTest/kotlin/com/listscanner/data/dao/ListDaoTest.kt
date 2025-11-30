package com.listscanner.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.listscanner.data.ListScannerDatabase
import com.listscanner.data.entity.Photo
import com.listscanner.data.entity.ShoppingList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListDaoTest {

    private lateinit var database: ListScannerDatabase
    private lateinit var listDao: ListDao
    private lateinit var photoDao: PhotoDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ListScannerDatabase::class.java
        ).allowMainThreadQueries().build()
        listDao = database.listDao()
        photoDao = database.photoDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveList() = runTest {
        val list = ShoppingList(photoId = null, name = "Groceries")
        val listId = listDao.insert(list)

        val retrieved = listDao.getById(listId)

        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("Groceries")
    }

    @Test
    fun insertReturnsGeneratedId() = runTest {
        val list = ShoppingList(photoId = null, name = "Groceries")

        val listId = listDao.insert(list)

        assertThat(listId).isGreaterThan(0)
    }

    @Test
    fun getAllReturnsListsOrderedByCreatedDateDesc() = runTest {
        val list1 = ShoppingList(photoId = null, name = "List1", createdDate = 1000L)
        val list2 = ShoppingList(photoId = null, name = "List2", createdDate = 3000L)
        val list3 = ShoppingList(photoId = null, name = "List3", createdDate = 2000L)
        listDao.insert(list1)
        listDao.insert(list2)
        listDao.insert(list3)

        val lists = listDao.getAll().first()

        assertThat(lists).hasSize(3)
        assertThat(lists[0].name).isEqualTo("List2")
        assertThat(lists[1].name).isEqualTo("List3")
        assertThat(lists[2].name).isEqualTo("List1")
    }

    @Test
    fun getByIdReturnsNullForNonExistent() = runTest {
        val retrieved = listDao.getById(999L)

        assertThat(retrieved).isNull()
    }

    @Test
    fun getByPhotoIdReturnsCorrectList() = runTest {
        val photo = Photo(filePath = "/test/photo.jpg")
        val photoId = photoDao.insert(photo)

        val list = ShoppingList(photoId = photoId, name = "From Photo")
        listDao.insert(list)

        val retrieved = listDao.getByPhotoId(photoId)

        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("From Photo")
    }

    @Test
    fun getByPhotoIdReturnsNullForNonExistent() = runTest {
        val retrieved = listDao.getByPhotoId(999L)

        assertThat(retrieved).isNull()
    }

    @Test
    fun deleteRemovesList() = runTest {
        val list = ShoppingList(photoId = null, name = "Groceries")
        val listId = listDao.insert(list)

        listDao.delete(listId)

        val retrieved = listDao.getById(listId)
        assertThat(retrieved).isNull()
    }

    @Test
    fun updateModifiesList() = runTest {
        val list = ShoppingList(photoId = null, name = "Groceries")
        val listId = listDao.insert(list)

        val updatedList = ShoppingList(id = listId, photoId = null, name = "Updated Name", createdDate = list.createdDate)
        listDao.update(updatedList)

        val retrieved = listDao.getById(listId)
        assertThat(retrieved?.name).isEqualTo("Updated Name")
    }

    @Test
    fun flowEmitsOnDataChange() = runTest {
        val list1 = ShoppingList(photoId = null, name = "List1")
        listDao.insert(list1)

        val initialLists = listDao.getAll().first()
        assertThat(initialLists).hasSize(1)

        val list2 = ShoppingList(photoId = null, name = "List2")
        listDao.insert(list2)

        val updatedLists = listDao.getAll().first()
        assertThat(updatedLists).hasSize(2)
    }

    @Test
    fun insertWithNullPhotoIdSucceeds() = runTest {
        val list = ShoppingList(photoId = null, name = "Manual List")
        val listId = listDao.insert(list)

        val retrieved = listDao.getById(listId)
        assertThat(retrieved?.photoId).isNull()
    }

    @Test
    fun photoDeleteSetsPhotoIdToNull() = runTest {
        val photo = Photo(filePath = "/test/photo.jpg")
        val photoId = photoDao.insert(photo)

        val list = ShoppingList(photoId = photoId, name = "From Photo")
        val listId = listDao.insert(list)

        photoDao.delete(photoId)

        val retrieved = listDao.getById(listId)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.photoId).isNull()
    }
}
