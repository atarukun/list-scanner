package com.listscanner.data.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShoppingListEntityTest {

    @Test
    fun `shopping list created with default values has correct defaults`() {
        val beforeCreation = System.currentTimeMillis()
        val list = ShoppingList(photoId = null, name = "Groceries")
        val afterCreation = System.currentTimeMillis()

        assertEquals(0L, list.id)
        assertNull(list.photoId)
        assertEquals("Groceries", list.name)
        assertTrue(list.createdDate in beforeCreation..afterCreation)
    }

    @Test
    fun `shopping list created with photo reference preserves it`() {
        val list = ShoppingList(
            id = 3L,
            photoId = 10L,
            name = "Hardware Store",
            createdDate = 9876543210L
        )

        assertEquals(3L, list.id)
        assertEquals(10L, list.photoId)
        assertEquals("Hardware Store", list.name)
        assertEquals(9876543210L, list.createdDate)
    }

    @Test
    fun `shopping list photoId can be nullable`() {
        val listWithPhoto = ShoppingList(photoId = 5L, name = "With Photo")
        val listWithoutPhoto = ShoppingList(photoId = null, name = "No Photo")

        assertEquals(5L, listWithPhoto.photoId)
        assertNull(listWithoutPhoto.photoId)
    }
}
