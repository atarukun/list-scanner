package com.listscanner.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for NetworkConnectivityServiceImpl.
 *
 * Note: Tests for observeNetworkState() Flow are in androidTest since they require
 * Android framework classes (NetworkRequest.Builder) that cannot be mocked in JVM tests.
 */
class NetworkConnectivityServiceTest {

    private lateinit var mockContext: Context
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var mockNetwork: Network
    private lateinit var mockNetworkCapabilities: NetworkCapabilities

    private lateinit var service: NetworkConnectivityServiceImpl

    @BeforeEach
    fun setup() {
        mockContext = mockk()
        mockConnectivityManager = mockk(relaxed = true)
        mockNetwork = mockk()
        mockNetworkCapabilities = mockk()

        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isNetworkAvailable returns true when connected with validated internet`() {
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true

        service = NetworkConnectivityServiceImpl(mockContext)
        val result = service.isNetworkAvailable()

        assertThat(result).isTrue()
    }

    @Test
    fun `isNetworkAvailable returns false when no active network`() {
        every { mockConnectivityManager.activeNetwork } returns null

        service = NetworkConnectivityServiceImpl(mockContext)
        val result = service.isNetworkAvailable()

        assertThat(result).isFalse()
    }

    @Test
    fun `isNetworkAvailable returns false when no network capabilities`() {
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns null

        service = NetworkConnectivityServiceImpl(mockContext)
        val result = service.isNetworkAvailable()

        assertThat(result).isFalse()
    }

    @Test
    fun `isNetworkAvailable returns false when internet capability missing`() {
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true

        service = NetworkConnectivityServiceImpl(mockContext)
        val result = service.isNetworkAvailable()

        assertThat(result).isFalse()
    }

    @Test
    fun `isNetworkAvailable returns false when validation capability missing`() {
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false

        service = NetworkConnectivityServiceImpl(mockContext)
        val result = service.isNetworkAvailable()

        assertThat(result).isFalse()
    }
}
