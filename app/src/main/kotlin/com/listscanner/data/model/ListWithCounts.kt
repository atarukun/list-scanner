package com.listscanner.data.model

import androidx.room.Embedded
import com.listscanner.data.entity.ShoppingList

/**
 * Aggregated list data including item counts and photo path.
 * Used directly by Room DAO query with @Embedded for ShoppingList.
 */
data class ListWithCounts(
    @Embedded val list: ShoppingList,
    val itemCount: Int,
    val checkedCount: Int,
    val photoFilePath: String?
) {
    val uncheckedCount: Int get() = itemCount - checkedCount
}
