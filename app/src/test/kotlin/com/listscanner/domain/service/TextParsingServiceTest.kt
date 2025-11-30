package com.listscanner.domain.service

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TextParsingServiceTest {

    private val service = TextParsingServiceImpl()

    @Test
    fun `parseTextToItems splits text by newlines`() {
        val text = "Milk\nEggs\nBread"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items).hasSize(3)
        assertThat(items[0].text).isEqualTo("Milk")
        assertThat(items[1].text).isEqualTo("Eggs")
        assertThat(items[2].text).isEqualTo("Bread")
    }

    @Test
    fun `parseTextToItems removes bullet prefixes`() {
        val text = "- Milk\n• Eggs\n* Bread"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items).hasSize(3)
        assertThat(items.map { it.text }).containsExactly("Milk", "Eggs", "Bread")
    }

    @Test
    fun `parseTextToItems removes numbered prefixes`() {
        val text = "1. Milk\n2) Eggs\n3. Bread"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items.map { it.text }).containsExactly("Milk", "Eggs", "Bread")
    }

    @Test
    fun `parseTextToItems handles mixed format bullets numbers and plain`() {
        val text = "- Milk\n1. Eggs\nBread\n• Cheese\n2) Butter"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items).hasSize(5)
        assertThat(items.map { it.text }).containsExactly("Milk", "Eggs", "Bread", "Cheese", "Butter")
    }

    @Test
    fun `parseTextToItems filters empty lines`() {
        val text = "Milk\n\nEggs\n   \nBread"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items).hasSize(3)
    }

    @Test
    fun `parseTextToItems filters whitespace-only lines`() {
        val text = "Milk\n   \n\t\nEggs"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items).hasSize(2)
        assertThat(items.map { it.text }).containsExactly("Milk", "Eggs")
    }

    @Test
    fun `parseTextToItems filters short items under 2 chars`() {
        val text = "Milk\na\nEggs\nx\nBread"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items).hasSize(3)
        assertThat(items.map { it.text }).containsExactly("Milk", "Eggs", "Bread")
    }

    @Test
    fun `parseTextToItems truncates items over 200 chars`() {
        val longText = "A".repeat(250)

        val items = service.parseTextToItems(longText, listId = 1L)

        assertThat(items).hasSize(1)
        assertThat(items[0].text).hasLength(200)
    }

    @Test
    fun `parseTextToItems assigns sequential positions starting at 0`() {
        val text = "Milk\nEggs\nBread"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items[0].position).isEqualTo(0)
        assertThat(items[1].position).isEqualTo(1)
        assertThat(items[2].position).isEqualTo(2)
    }

    @Test
    fun `parseTextToItems sets listId on all items`() {
        val text = "Milk\nEggs"

        val items = service.parseTextToItems(text, listId = 42L)

        assertThat(items.all { it.listId == 42L }).isTrue()
    }

    @Test
    fun `parseTextToItems sets isChecked false on all items`() {
        val text = "Milk\nEggs"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items.all { !it.isChecked }).isTrue()
    }

    @Test
    fun `parseTextToItems handles Windows line endings`() {
        val text = "Milk\r\nEggs\r\nBread"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items).hasSize(3)
    }

    @Test
    fun `parseTextToItems handles single line without newlines`() {
        val text = "Just one item"

        val items = service.parseTextToItems(text, listId = 1L)

        assertThat(items).hasSize(1)
        assertThat(items[0].text).isEqualTo("Just one item")
    }

    @Test
    fun `parseTextToItems returns empty list for empty input`() {
        val items = service.parseTextToItems("", listId = 1L)

        assertThat(items).isEmpty()
    }

    @Test
    fun `parseTextToItems returns empty list for whitespace-only input`() {
        val items = service.parseTextToItems("   \n\t\n   ", listId = 1L)

        assertThat(items).isEmpty()
    }
}
