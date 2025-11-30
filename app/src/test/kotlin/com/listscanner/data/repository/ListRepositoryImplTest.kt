package com.listscanner.data.repository

import com.google.common.truth.Truth.assertThat
import com.listscanner.data.ListScannerDatabase
import com.listscanner.data.dao.ListDao
import com.listscanner.data.dao.PhotoDao
import com.listscanner.data.entity.ShoppingList
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

class ListRepositoryImplTest {

    private val listDao: ListDao = mockk()
    private val photoDao: PhotoDao = mockk()
    private val database: ListScannerDatabase = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ListRepositoryImpl

    @BeforeEach
    fun setup() {
        repository = ListRepositoryImpl(listDao, photoDao, database, testDispatcher)
    }

    @Test
    fun `getAllLists returns flow from dao`() = runTest(testDispatcher) {
        val lists = listOf(
            ShoppingList(id = 1, photoId = null, name = "Groceries"),
            ShoppingList(id = 2, photoId = null, name = "Hardware")
        )
        every { listDao.getAll() } returns flowOf(lists)

        val result = repository.getAllLists().first()

        assertThat(result).hasSize(2)
        assertThat(result[0].name).isEqualTo("Groceries")
    }

    @Test
    fun `getListById success returns Result Success`() = runTest(testDispatcher) {
        val list = ShoppingList(id = 1, photoId = null, name = "Groceries")
        coEvery { listDao.getById(1L) } returns list

        val result = repository.getListById(1L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(list)
        coVerify { listDao.getById(1L) }
    }

    @Test
    fun `getListById returns null when not found`() = runTest(testDispatcher) {
        coEvery { listDao.getById(999L) } returns null

        val result = repository.getListById(999L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isNull()
    }

    @Test
    fun `getListById failure returns Result Failure`() = runTest(testDispatcher) {
        coEvery { listDao.getById(any()) } throws RuntimeException("Database error")

        val result = repository.getListById(1L)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to get list by id")
    }

    @Test
    fun `getListByPhotoId success returns Result Success`() = runTest(testDispatcher) {
        val list = ShoppingList(id = 1, photoId = 10L, name = "From Photo")
        coEvery { listDao.getByPhotoId(10L) } returns list

        val result = repository.getListByPhotoId(10L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(list)
        coVerify { listDao.getByPhotoId(10L) }
    }

    @Test
    fun `getListByPhotoId returns null when not found`() = runTest(testDispatcher) {
        coEvery { listDao.getByPhotoId(999L) } returns null

        val result = repository.getListByPhotoId(999L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isNull()
    }

    @Test
    fun `getListByPhotoId failure returns Result Failure`() = runTest(testDispatcher) {
        coEvery { listDao.getByPhotoId(any()) } throws RuntimeException("Database error")

        val result = repository.getListByPhotoId(10L)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to get list by photo id")
    }

    @Test
    fun `insertList success returns Result Success with id`() = runTest(testDispatcher) {
        val list = ShoppingList(photoId = null, name = "Groceries")
        coEvery { listDao.insert(list) } returns 1L

        val result = repository.insertList(list)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(1L)
        coVerify { listDao.insert(list) }
    }

    @Test
    fun `insertList failure returns Result Failure`() = runTest(testDispatcher) {
        val list = ShoppingList(photoId = null, name = "Groceries")
        coEvery { listDao.insert(any()) } throws RuntimeException("Insert failed")

        val result = repository.insertList(list)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to insert list")
    }

    @Test
    fun `updateList success returns Result Success`() = runTest(testDispatcher) {
        val list = ShoppingList(id = 1, photoId = null, name = "Updated")
        coEvery { listDao.update(list) } just Runs

        val result = repository.updateList(list)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { listDao.update(list) }
    }

    @Test
    fun `updateList failure returns Result Failure`() = runTest(testDispatcher) {
        val list = ShoppingList(id = 1, photoId = null, name = "Updated")
        coEvery { listDao.update(any()) } throws RuntimeException("Update failed")

        val result = repository.updateList(list)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to update list")
    }

    @Test
    fun `deleteList success returns Result Success`() = runTest(testDispatcher) {
        coEvery { listDao.delete(1L) } just Runs

        val result = repository.deleteList(1L)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { listDao.delete(1L) }
    }

    @Test
    fun `deleteList failure returns Result Failure`() = runTest(testDispatcher) {
        coEvery { listDao.delete(any()) } throws RuntimeException("Delete failed")

        val result = repository.deleteList(1L)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to delete list")
    }

}
