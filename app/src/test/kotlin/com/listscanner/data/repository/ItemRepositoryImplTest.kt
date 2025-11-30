package com.listscanner.data.repository

import com.google.common.truth.Truth.assertThat
import com.listscanner.data.ListScannerDatabase
import com.listscanner.data.dao.ItemDao
import com.listscanner.data.entity.Item
import com.listscanner.domain.Result
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ItemRepositoryImplTest {

    private val itemDao: ItemDao = mockk()
    private val db: ListScannerDatabase = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ItemRepositoryImpl

    @BeforeEach
    fun setup() {
        repository = ItemRepositoryImpl(itemDao, db, testDispatcher)
    }

    @Test
    fun `getItemsForList returns flow from dao`() = runTest(testDispatcher) {
        val items = listOf(
            Item(id = 1, listId = 1, text = "Milk", position = 0),
            Item(id = 2, listId = 1, text = "Bread", position = 1)
        )
        every { itemDao.getItemsForList(1L) } returns flowOf(items)

        val result = repository.getItemsForList(1L).first()

        assertThat(result).hasSize(2)
        assertThat(result[0].text).isEqualTo("Milk")
    }

    @Test
    fun `getItemById success returns Result Success`() = runTest(testDispatcher) {
        val item = Item(id = 1, listId = 1, text = "Milk", position = 0)
        coEvery { itemDao.getById(1L) } returns item

        val result = repository.getItemById(1L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(item)
        coVerify { itemDao.getById(1L) }
    }

    @Test
    fun `getItemById returns null when not found`() = runTest(testDispatcher) {
        coEvery { itemDao.getById(999L) } returns null

        val result = repository.getItemById(999L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isNull()
    }

    @Test
    fun `getItemById failure returns Result Failure`() = runTest(testDispatcher) {
        coEvery { itemDao.getById(any()) } throws RuntimeException("Database error")

        val result = repository.getItemById(1L)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to get item by id")
    }

    @Test
    fun `insertItem success returns Result Success with id`() = runTest(testDispatcher) {
        val item = Item(listId = 1, text = "Milk", position = 0)
        coEvery { itemDao.insert(item) } returns 1L

        val result = repository.insertItem(item)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(1L)
        coVerify { itemDao.insert(item) }
    }

    @Test
    fun `insertItem failure returns Result Failure`() = runTest(testDispatcher) {
        val item = Item(listId = 1, text = "Milk", position = 0)
        coEvery { itemDao.insert(any()) } throws RuntimeException("Insert failed")

        val result = repository.insertItem(item)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to insert item")
    }

    @Test
    fun `insertItems success returns Result Success with ids`() = runTest(testDispatcher) {
        val items = listOf(
            Item(listId = 1, text = "Milk", position = 0),
            Item(listId = 1, text = "Bread", position = 1)
        )
        coEvery { itemDao.insertAll(items) } returns listOf(1L, 2L)

        val result = repository.insertItems(items)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).containsExactly(1L, 2L)
        coVerify { itemDao.insertAll(items) }
    }

    @Test
    fun `insertItems failure returns Result Failure`() = runTest(testDispatcher) {
        val items = listOf(Item(listId = 1, text = "Milk", position = 0))
        coEvery { itemDao.insertAll(any()) } throws RuntimeException("Insert failed")

        val result = repository.insertItems(items)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to insert items")
    }

    @Test
    fun `updateItem success returns Result Success`() = runTest(testDispatcher) {
        val item = Item(id = 1, listId = 1, text = "Updated", position = 0)
        coEvery { itemDao.update(item) } just Runs

        val result = repository.updateItem(item)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { itemDao.update(item) }
    }

    @Test
    fun `updateItem failure returns Result Failure`() = runTest(testDispatcher) {
        val item = Item(id = 1, listId = 1, text = "Updated", position = 0)
        coEvery { itemDao.update(any()) } throws RuntimeException("Update failed")

        val result = repository.updateItem(item)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to update item")
    }

    @Test
    fun `deleteItem success returns Result Success`() = runTest(testDispatcher) {
        coEvery { itemDao.delete(1L) } just Runs

        val result = repository.deleteItem(1L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { itemDao.delete(1L) }
    }

    @Test
    fun `deleteItem failure returns Result Failure`() = runTest(testDispatcher) {
        coEvery { itemDao.delete(any()) } throws RuntimeException("Delete failed")

        val result = repository.deleteItem(1L)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to delete item")
    }

    @Test
    fun `updateItemChecked success returns Result Success`() = runTest(testDispatcher) {
        coEvery { itemDao.updateIsChecked(1L, true) } just Runs

        val result = repository.updateItemChecked(1L, true)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { itemDao.updateIsChecked(1L, true) }
    }

    @Test
    fun `updateItemChecked failure returns Result Failure`() = runTest(testDispatcher) {
        coEvery { itemDao.updateIsChecked(any(), any()) } throws RuntimeException("Update failed")

        val result = repository.updateItemChecked(1L, true)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to update item checked status")
    }
}
