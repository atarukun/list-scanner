package com.listscanner.data

import androidx.room.TypeConverter
import com.listscanner.data.entity.OcrStatus

class Converters {
    @TypeConverter
    fun fromOcrStatus(status: OcrStatus): String = status.name

    @TypeConverter
    fun toOcrStatus(value: String): OcrStatus = OcrStatus.valueOf(value)
}
