package com.listscanner.data.repository

import kotlinx.coroutines.flow.Flow

interface PrivacyConsentRepository {
    suspend fun hasUserConsented(): Boolean
    suspend fun setUserConsent(consented: Boolean)
    fun observeConsentState(): Flow<Boolean>
}
