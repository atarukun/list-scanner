package com.listscanner.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyConsentRepositoryTest {

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: PrivacyConsentRepositoryImpl

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir.toFile(), "test_prefs_${System.nanoTime()}.preferences_pb") }
        )
        repository = PrivacyConsentRepositoryImpl(testDataStore)
    }

    @Test
    fun `hasUserConsented returns false when no consent stored`() = runTest {
        val result = repository.hasUserConsented()
        assertThat(result).isFalse()
    }

    @Test
    fun `hasUserConsented returns true after setUserConsent true called`() = runTest {
        repository.setUserConsent(true)
        val result = repository.hasUserConsented()
        assertThat(result).isTrue()
    }

    @Test
    fun `setUserConsent false clears consent`() = runTest {
        repository.setUserConsent(true)
        assertThat(repository.hasUserConsented()).isTrue()

        repository.setUserConsent(false)
        assertThat(repository.hasUserConsented()).isFalse()
    }

    @Test
    fun `observeConsentState emits correct values on change`() = runTest {
        // Initial state should be false
        assertThat(repository.observeConsentState().first()).isFalse()

        // After setting consent to true
        repository.setUserConsent(true)
        assertThat(repository.observeConsentState().first()).isTrue()

        // After setting consent back to false
        repository.setUserConsent(false)
        assertThat(repository.observeConsentState().first()).isFalse()
    }
}
