package com.listscanner.domain.service

import com.listscanner.data.entity.Item

interface TextParsingService {
    fun parseTextToItems(text: String, listId: Long): List<Item>
}
