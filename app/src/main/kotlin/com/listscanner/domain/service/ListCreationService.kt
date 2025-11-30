package com.listscanner.domain.service

import com.listscanner.domain.Result

interface ListCreationService {
    suspend fun createListFromOcrResults(photoId: Long, recognizedText: String): Result<Long>
}
