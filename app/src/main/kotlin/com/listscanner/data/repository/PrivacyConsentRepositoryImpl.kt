package com.listscanner.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PrivacyConsentRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : PrivacyConsentRepository {

    private object PreferencesKeys {
        val PRIVACY_CONSENT_GIVEN = booleanPreferencesKey("privacy_consent_given")
    }

    override suspend fun hasUserConsented(): Boolean {
        return dataStore.data.first()[PreferencesKeys.PRIVACY_CONSENT_GIVEN] ?: false
    }

    override suspend fun setUserConsent(consented: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRIVACY_CONSENT_GIVEN] = consented
        }
    }

    override fun observeConsentState(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[PreferencesKeys.PRIVACY_CONSENT_GIVEN] ?: false
        }
    }
}
