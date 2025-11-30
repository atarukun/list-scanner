package com.listscanner.device

import kotlinx.coroutines.flow.Flow

interface NetworkConnectivityService {
    fun isNetworkAvailable(): Boolean
    fun observeNetworkState(): Flow<Boolean>
}
