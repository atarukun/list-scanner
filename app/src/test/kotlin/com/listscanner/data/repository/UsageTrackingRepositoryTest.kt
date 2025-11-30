package com.listscanner.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class UsageTrackingRepositoryTest {

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: UsageTrackingRepositoryImpl

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir.toFile(), "test_usage_${System.nanoTime()}.preferences_pb") }
        )
        repository = UsageTrackingRepositoryImpl(testDataStore)
    }

    @Test
    fun `getWeeklyUsage returns 0 when no usage stored`() = runTest {
        val result = repository.getWeeklyUsage()
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `incrementUsage increases weekly count by 1`() = runTest {
        repository.incrementUsage()
        val result = repository.getWeeklyUsage()
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `getWeeklyUsage returns correct count after multiple increments`() = runTest {
        repeat(5) { repository.incrementUsage() }
        val result = repository.getWeeklyUsage()
        assertThat(result).isEqualTo(5)
    }

    @Test
    fun `shouldShowCostWarning returns false below threshold`() = runTest {
        repeat(100) { repository.incrementUsage() }
        assertThat(repository.shouldShowCostWarning()).isFalse()
    }

    @Test
    fun `shouldShowCostWarning returns true at threshold`() = runTest {
        repeat(UsageTrackingRepositoryImpl.COST_WARNING_THRESHOLD) { repository.incrementUsage() }
        assertThat(repository.shouldShowCostWarning()).isTrue()
    }

    @Test
    fun `shouldShowCostWarning returns false after markWarningShown`() = runTest {
        repeat(UsageTrackingRepositoryImpl.COST_WARNING_THRESHOLD) { repository.incrementUsage() }
        assertThat(repository.shouldShowCostWarning()).isTrue()

        repository.markWarningShown()
        assertThat(repository.shouldShowCostWarning()).isFalse()
    }

    @Test
    fun `shouldShowCostWarning returns true above threshold when warning not shown`() = runTest {
        repeat(UsageTrackingRepositoryImpl.COST_WARNING_THRESHOLD + 100) { repository.incrementUsage() }
        assertThat(repository.shouldShowCostWarning()).isTrue()
    }
}
