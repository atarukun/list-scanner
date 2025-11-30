package com.listscanner.data.repository

interface UsageTrackingRepository {
    suspend fun incrementUsage()
    suspend fun getWeeklyUsage(): Int
    suspend fun shouldShowCostWarning(): Boolean
    suspend fun markWarningShown()
    suspend fun resetIfNewWeek()
}
