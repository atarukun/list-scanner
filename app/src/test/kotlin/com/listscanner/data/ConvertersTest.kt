package com.listscanner.data

import com.listscanner.data.entity.OcrStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ConvertersTest {

    private val converters = Converters()

    @ParameterizedTest
    @EnumSource(OcrStatus::class)
    fun `fromOcrStatus converts all enum values to string`(status: OcrStatus) {
        val result = converters.fromOcrStatus(status)
        assertEquals(status.name, result)
    }

    @ParameterizedTest
    @EnumSource(OcrStatus::class)
    fun `toOcrStatus converts all string values back to enum`(status: OcrStatus) {
        val result = converters.toOcrStatus(status.name)
        assertEquals(status, result)
    }

    @Test
    fun `round trip conversion preserves value`() {
        val original = OcrStatus.PROCESSING
        val asString = converters.fromOcrStatus(original)
        val backToEnum = converters.toOcrStatus(asString)
        assertEquals(original, backToEnum)
    }
}
