package com.listscanner.data.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemEntityTest {

    @Test
    fun `item created with required values has correct defaults`() {
        val item = Item(listId = 1L, text = "Milk", position = 0)

        assertEquals(0L, item.id)
        assertEquals(1L, item.listId)
        assertEquals("Milk", item.text)
        assertFalse(item.isChecked)
        assertEquals(0, item.position)
    }

    @Test
    fun `item created with explicit values preserves them`() {
        val item = Item(
            id = 7L,
            listId = 2L,
            text = "Eggs",
            isChecked = true,
            position = 5
        )

        assertEquals(7L, item.id)
        assertEquals(2L, item.listId)
        assertEquals("Eggs", item.text)
        assertTrue(item.isChecked)
        assertEquals(5, item.position)
    }

    @Test
    fun `item copy with toggled isChecked works correctly`() {
        val original = Item(listId = 1L, text = "Bread", position = 2)
        val checked = original.copy(isChecked = true)

        assertFalse(original.isChecked)
        assertTrue(checked.isChecked)
        assertEquals(original.text, checked.text)
        assertEquals(original.position, checked.position)
    }

    @Test
    fun `item position can be updated via copy`() {
        val original = Item(listId = 1L, text = "Butter", position = 0)
        val moved = original.copy(position = 3)

        assertEquals(0, original.position)
        assertEquals(3, moved.position)
    }
}
