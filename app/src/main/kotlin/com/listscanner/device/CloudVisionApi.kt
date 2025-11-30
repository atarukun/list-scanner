package com.listscanner.device

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface CloudVisionApi {
    @POST("v1/images:annotate")
    suspend fun annotateImage(
        @Query("key") apiKey: String,
        @Body request: CloudVisionRequest
    ): CloudVisionResponse
}
