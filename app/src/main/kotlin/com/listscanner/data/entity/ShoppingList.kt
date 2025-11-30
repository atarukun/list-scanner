package com.listscanner.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lists",
    foreignKeys = [
        ForeignKey(
            entity = Photo::class,
            parentColumns = ["id"],
            childColumns = ["photo_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("photo_id"),
        Index("created_date")
    ]
)
data class ShoppingList(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "photo_id")
    val photoId: Long?,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_date")
    val createdDate: Long = System.currentTimeMillis()
)
