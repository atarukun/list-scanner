package com.listscanner.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.listscanner.data.ListScannerDatabase
import com.listscanner.data.entity.Item
import com.listscanner.data.entity.ShoppingList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemDaoTest {

    private lateinit var database: ListScannerDatabase
    private lateinit var itemDao: ItemDao
    private lateinit var listDao: ListDao
    private var testListId: Long = 0

    @Before
    fun setup() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ListScannerDatabase::class.java
        ).allowMainThreadQueries().build()
        itemDao = database.itemDao()
        listDao = database.listDao()

        val list = ShoppingList(photoId = null, name = "Test List")
        testListId = listDao.insert(list)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveItem() = runTest {
        val item = Item(listId = testListId, text = "Milk", position = 0)
        val itemId = itemDao.insert(item)

        val retrieved = itemDao.getById(itemId)

        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.text).isEqualTo("Milk")
    }

    @Test
    fun insertReturnsGeneratedId() = runTest {
        val item = Item(listId = testListId, text = "Milk", position = 0)

        val itemId = itemDao.insert(item)

        assertThat(itemId).isGreaterThan(0)
    }

    @Test
    fun insertAllReturnsList ofIds() = runTest {
        val items = listOf(
            Item(listId = testListId, text = "Milk", position = 0),
            Item(listId = testListId, text = "Bread", position = 1),
            Item(listId = testListId, text = "Eggs", position = 2)
        )

        val ids = itemDao.insertAll(items)

        assertThat(ids).hasSize(3)
        ids.forEach { assertThat(it).isGreaterThan(0) }
    }

    @Test
    fun getItemsForListReturnsOrderedByPosition() = runTest {
        val item1 = Item(listId = testListId, text = "Third", position = 2)
        val item2 = Item(listId = testListId, text = "First", position = 0)
        val item3 = Item(listId = testListId, text = "Second", position = 1)
        itemDao.insert(item1)
        itemDao.insert(item2)
        itemDao.insert(item3)

        val items = itemDao.getItemsForList(testListId).first()

        assertThat(items).hasSize(3)
        assertThat(items[0].text).isEqualTo("First")
        assertThat(items[1].text).isEqualTo("Second")
        assertThat(items[2].text).isEqualTo("Third")
    }

    @Test
    fun getItemsForListReturnsEmptyForNonExistent() = runTest {
        val items = itemDao.getItemsForList(999L).first()

        assertThat(items).isEmpty()
    }

    @Test
    fun getByIdReturnsNullForNonExistent() = runTest {
        val retrieved = itemDao.getById(999L)

        assertThat(retrieved).isNull()
    }

    @Test
    fun updateModifiesItem() = runTest {
        val item = Item(listId = testListId, text = "Milk", position = 0)
        val itemId = itemDao.insert(item)

        val updatedItem = Item(id = itemId, listId = testListId, text = "2% Milk", position = 0, isChecked = true)
        itemDao.update(updatedItem)

        val retrieved = itemDao.getById(itemId)
        assertThat(retrieved?.text).isEqualTo("2% Milk")
        assertThat(retrieved?.isChecked).isTrue()
    }

    @Test
    fun deleteRemovesItem() = runTest {
        val item = Item(listId = testListId, text = "Milk", position = 0)
        val itemId = itemDao.insert(item)

        itemDao.delete(itemId)

        val retrieved = itemDao.getById(itemId)
        assertThat(retrieved).isNull()
    }

    @Test
    fun updateIsCheckedChangesOnlyIsChecked() = runTest {
        val item = Item(listId = testListId, text = "Milk", position = 0, isChecked = false)
        val itemId = itemDao.insert(item)

        itemDao.updateIsChecked(itemId, true)

        val retrieved = itemDao.getById(itemId)
        assertThat(retrieved?.isChecked).isTrue()
        assertThat(retrieved?.text).isEqualTo("Milk")
    }

    @Test
    fun flowEmitsOnDataChange() = runTest {
        val item1 = Item(listId = testListId, text = "Milk", position = 0)
        itemDao.insert(item1)

        val initialItems = itemDao.getItemsForList(testListId).first()
        assertThat(initialItems).hasSize(1)

        val item2 = Item(listId = testListId, text = "Bread", position = 1)
        itemDao.insert(item2)

        val updatedItems = itemDao.getItemsForList(testListId).first()
        assertThat(updatedItems).hasSize(2)
    }

    @Test
    fun cascadeDeleteRemovesItemsWhenListDeleted() = runTest {
        val items = listOf(
            Item(listId = testListId, text = "Milk", position = 0),
            Item(listId = testListId, text = "Bread", position = 1)
        )
        itemDao.insertAll(items)

        listDao.delete(testListId)

        val remainingItems = itemDao.getItemsForList(testListId).first()
        assertThat(remainingItems).isEmpty()
    }

    @Test
    fun defaultIsCheckedIsFalse() = runTest {
        val item = Item(listId = testListId, text = "Milk", position = 0)
        val itemId = itemDao.insert(item)

        val retrieved = itemDao.getById(itemId)
        assertThat(retrieved?.isChecked).isFalse()
    }
}
