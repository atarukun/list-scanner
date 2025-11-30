package com.listscanner.domain.service

import androidx.room.withTransaction
import com.listscanner.data.ListScannerDatabase
import com.listscanner.data.entity.ShoppingList
import com.listscanner.domain.Result
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListCreationServiceImpl(
    private val database: ListScannerDatabase,
    private val textParsingService: TextParsingService
) : ListCreationService {

    override suspend fun createListFromOcrResults(photoId: Long, recognizedText: String): Result<Long> {
        return try {
            // Parse items FIRST, fail if empty BEFORE creating list (AC 2)
            val items = textParsingService.parseTextToItems(recognizedText, listId = 0)
            if (items.isEmpty()) {
                return Result.Failure(
                    IllegalStateException("No items parsed from OCR text"),
                    "No list items detected. Ensure your list is clearly written."
                )
            }

            val listId = database.withTransaction {
                val list = ShoppingList(
                    photoId = photoId,
                    name = generateListName()
                )
                val insertedListId = database.listDao().insert(list)
                val itemsWithListId = items.map { it.copy(listId = insertedListId) }
                database.itemDao().insertAll(itemsWithListId)
                insertedListId
            }
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
