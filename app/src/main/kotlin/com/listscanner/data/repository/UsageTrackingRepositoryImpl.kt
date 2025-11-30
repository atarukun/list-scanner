package com.listscanner.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.Calendar

class UsageTrackingRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : UsageTrackingRepository {

    private object PreferencesKeys {
        val WEEKLY_USAGE_COUNT = intPreferencesKey("weekly_usage_count")
        val WEEK_START_TIMESTAMP = longPreferencesKey("week_start_timestamp")
        val WARNING_SHOWN_FOR_THRESHOLD = booleanPreferencesKey("warning_shown_for_threshold")
    }

    companion object {
        const val COST_WARNING_THRESHOLD = 667 // ~$1 at $1.50/1000
    }

    override suspend fun incrementUsage() {
        resetIfNewWeek()
        dataStore.edit { preferences ->
            val currentCount = preferences[PreferencesKeys.WEEKLY_USAGE_COUNT] ?: 0
            preferences[PreferencesKeys.WEEKLY_USAGE_COUNT] = currentCount + 1
        }
    }

    override suspend fun getWeeklyUsage(): Int {
        resetIfNewWeek()
        return dataStore.data.first()[PreferencesKeys.WEEKLY_USAGE_COUNT] ?: 0
    }

    override suspend fun shouldShowCostWarning(): Boolean {
        val usage = getWeeklyUsage()
        val warningShown = dataStore.data.first()[PreferencesKeys.WARNING_SHOWN_FOR_THRESHOLD] ?: false
        return usage >= COST_WARNING_THRESHOLD && !warningShown
    }

    override suspend fun markWarningShown() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WARNING_SHOWN_FOR_THRESHOLD] = true
        }
    }

    override suspend fun resetIfNewWeek() {
        val currentWeekStart = getStartOfCurrentWeek()
        val storedWeekStart = dataStore.data.first()[PreferencesKeys.WEEK_START_TIMESTAMP] ?: 0L

        if (storedWeekStart < currentWeekStart) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.WEEKLY_USAGE_COUNT] = 0
                preferences[PreferencesKeys.WEEK_START_TIMESTAMP] = currentWeekStart
                preferences[PreferencesKeys.WARNING_SHOWN_FOR_THRESHOLD] = false
            }
        }
    }

    private fun getStartOfCurrentWeek(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
