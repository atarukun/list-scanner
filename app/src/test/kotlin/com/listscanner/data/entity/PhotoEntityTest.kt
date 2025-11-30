package com.listscanner.data.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhotoEntityTest {

    @Test
    fun `photo created with default values has correct defaults`() {
        val beforeCreation = System.currentTimeMillis()
        val photo = Photo(filePath = "/test/path.jpg")
        val afterCreation = System.currentTimeMillis()

        assertEquals(0L, photo.id)
        assertEquals("/test/path.jpg", photo.filePath)
        assertEquals(OcrStatus.PENDING, photo.ocrStatus)
        assertTrue(photo.timestamp in beforeCreation..afterCreation)
    }

    @Test
    fun `photo created with explicit values preserves them`() {
        val photo = Photo(
            id = 5L,
            filePath = "/custom/path.jpg",
            timestamp = 1234567890L,
            ocrStatus = OcrStatus.COMPLETED
        )

        assertEquals(5L, photo.id)
        assertEquals("/custom/path.jpg", photo.filePath)
        assertEquals(1234567890L, photo.timestamp)
        assertEquals(OcrStatus.COMPLETED, photo.ocrStatus)
    }

    @Test
    fun `photo copy with modified status works correctly`() {
        val original = Photo(filePath = "/test/path.jpg")
        val modified = original.copy(ocrStatus = OcrStatus.PROCESSING)

        assertEquals(OcrStatus.PENDING, original.ocrStatus)
        assertEquals(OcrStatus.PROCESSING, modified.ocrStatus)
        assertEquals(original.filePath, modified.filePath)
    }
}
