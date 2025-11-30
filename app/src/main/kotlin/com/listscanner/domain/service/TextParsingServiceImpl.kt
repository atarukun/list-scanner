package com.listscanner.domain.service

import com.listscanner.data.entity.Item

class TextParsingServiceImpl : TextParsingService {
    companion object {
        private const val MIN_ITEM_LENGTH = 2
        private const val MAX_ITEM_LENGTH = 200
        private val BULLET_PATTERN = Regex("""^\s*(?:[-•*]|\d+[.)])\s*""")
    }

    override fun parseTextToItems(text: String, listId: Long): List<Item> {
        return text
            .replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }
            .map { BULLET_PATTERN.replace(it, "") }
            .filter { it.length >= MIN_ITEM_LENGTH }
            .map { if (it.length > MAX_ITEM_LENGTH) it.take(MAX_ITEM_LENGTH) else it }
            .mapIndexed { index, itemText ->
                Item(
                    listId = listId,
                    text = itemText,
                    position = index
                )
            }
    }
}
