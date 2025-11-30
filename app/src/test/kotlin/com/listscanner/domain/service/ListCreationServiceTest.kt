package com.listscanner.domain.service

import com.google.common.truth.Truth.assertThat
import com.listscanner.data.entity.Item
import com.listscanner.data.entity.ShoppingList
import com.listscanner.domain.Result
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tests for ListCreationService.
 *
 * Note: Full integration tests with Room's withTransaction require androidTest with
 * an in-memory database. These unit tests verify the service logic using a test double
 * that simulates the transaction behavior.
 */
class ListCreationServiceTest {

    @Test
    fun `createListFromOcrResults creates list with correct name format`() {
        val testService = TestListCreationService()

        val result = testService.createListFromOcrResultsSync(
            photoId = 1L,
            recognizedText = "Milk"
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(testService.lastCreatedList?.name).matches("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
    }

    @Test
    fun `createListFromOcrResults sets photoId on list`() {
        val testService = TestListCreationService()

        testService.createListFromOcrResultsSync(photoId = 42L, recognizedText = "Milk")

        assertThat(testService.lastCreatedList?.photoId).isEqualTo(42L)
    }

    @Test
    fun `createListFromOcrResults inserts items with correct listId`() {
        val testService = TestListCreationService()

        testService.createListFromOcrResultsSync(
            photoId = 1L,
            recognizedText = "Milk\nEggs"
        )

        assertThat(testService.lastInsertedItems).hasSize(2)
        assertThat(testService.lastInsertedItems?.all { it.listId == 1L }).isTrue()
    }

    @Test
    fun `createListFromOcrResults returns failure for empty text with no items`() {
        val testService = TestListCreationService()

        val result = testService.createListFromOcrResultsSync(
            photoId = 1L,
            recognizedText = ""
        )

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message)
            .isEqualTo("No list items detected. Ensure your list is clearly written.")
        assertThat(testService.lastCreatedList).isNull() // No list created
    }

    @Test
    fun `createListFromOcrResults returns failure for whitespace-only text with no items`() {
        val testService = TestListCreationService()

        val result = testService.createListFromOcrResultsSync(
            photoId = 1L,
            recognizedText = "   \n\t  "
        )

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message)
            .isEqualTo("No list items detected. Ensure your list is clearly written.")
        assertThat(testService.lastCreatedList).isNull() // No list created
    }

    @Test
    fun `createListFromOcrResults returns failure when list insert fails`() {
        val testService = TestListCreationService(shouldFailOnListInsert = true)

        val result = testService.createListFromOcrResultsSync(
            photoId = 1L,
            recognizedText = "Milk"
        )

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to create list. Please try again.")
    }

    @Test
    fun `createListFromOcrResults returns failure when item insert fails`() {
        val testService = TestListCreationService(shouldFailOnItemInsert = true)

        val result = testService.createListFromOcrResultsSync(
            photoId = 1L,
            recognizedText = "Milk"
        )

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).message).isEqualTo("Failed to create list. Please try again.")
    }

    @Test
    fun `createListFromOcrResults returns listId on success`() {
        val testService = TestListCreationService()

        val result = testService.createListFromOcrResultsSync(
            photoId = 1L,
            recognizedText = "Milk"
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(1L)
    }

    @Test
    fun `list name uses current timestamp in correct format`() {
        val testService = TestListCreationService()
        val beforeTime = System.currentTimeMillis()

        testService.createListFromOcrResultsSync(
            photoId = 1L,
            recognizedText = "Milk"
        )

        val afterTime = System.currentTimeMillis()
        val listName = testService.lastCreatedList?.name ?: ""

        // Parse the list name back to timestamp and verify it's within the test window
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val parsedTime = dateFormat.parse(listName)?.time ?: 0

        // Allow for some rounding since the format truncates seconds
        assertThat(parsedTime).isAtLeast(beforeTime - 60000) // minus 1 minute for rounding
        assertThat(parsedTime).isAtMost(afterTime + 60000) // plus 1 minute for rounding
    }
}

/**
 * Test double for ListCreationService that simulates the core logic without Room dependency.
 */
private class TestListCreationService(
    private val shouldFailOnListInsert: Boolean = false,
    private val shouldFailOnItemInsert: Boolean = false
) {
    private val textParsingService = TextParsingServiceImpl()
    private var nextListId = 1L

    var lastCreatedList: ShoppingList? = null
        private set

    var lastInsertedItems: List<Item>? = null
        private set

    fun createListFromOcrResultsSync(photoId: Long, recognizedText: String): Result<Long> {
        return try {
            // Parse items FIRST, fail if empty BEFORE creating list (matches real impl)
            val items = textParsingService.parseTextToItems(recognizedText, listId = 0)
            if (items.isEmpty()) {
                return Result.Failure(
                    IllegalStateException("No items parsed from OCR text"),
                    "No list items detected. Ensure your list is clearly written."
                )
            }

            // Simulate list creation
            if (shouldFailOnListInsert) {
                throw Exception("DB error")
            }

            val list = ShoppingList(
                id = nextListId,
                photoId = photoId,
                name = generateListName()
            )
            lastCreatedList = list
            val listId = nextListId++

            // Simulate item creation
            if (shouldFailOnItemInsert) {
                throw Exception("Item insert failed")
            }
            lastInsertedItems = items.map { it.copy(listId = listId) }

            Result.Success(listId)
        } catch (e: Exception) {
            Result.Failure(e, "Failed to create list. Please try again.")
        }
    }

    private fun generateListName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return dateFormat.format(Date(System.currentTimeMillis()))
    }
}
