package com.listscanner.device

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.listscanner.BuildConfig
import com.listscanner.di.DatabaseModule
import com.listscanner.domain.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Integration test for Cloud Vision API.
 *
 * NOTE: This test requires:
 * 1. A valid API key in local.properties (CLOUD_VISION_API_KEY)
 * 2. Network access
 * 3. A connected Android device or emulator
 *
 * Run with:
 * JAVA_HOME=/home/atarukun/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest --tests "com.listscanner.device.CloudVisionIntegrationTest"
 */
@RunWith(AndroidJUnit4::class)
class CloudVisionIntegrationTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var context: Context
    private lateinit var service: CloudVisionService

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Skip tests if no API key is configured
        assumeTrue(
            "CLOUD_VISION_API_KEY not configured in local.properties",
            BuildConfig.CLOUD_VISION_API_KEY.isNotBlank()
        )

        val okHttpClient = DatabaseModule.provideOkHttpClient()
        val retrofit = DatabaseModule.provideRetrofit(okHttpClient)
        val api = DatabaseModule.provideCloudVisionApi(retrofit)
        service = CloudVisionServiceImpl(
            api = api,
            apiKey = BuildConfig.CLOUD_VISION_API_KEY
        )
    }

    @Test
    fun recognizeText_withSampleHandwrittenImage_returnsExpectedText() = runBlocking {
        val testFile = copyAssetToFile("test_list.jpg")

        val result = service.recognizeText(testFile.absolutePath)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val recognizedText = (result as Result.Success).data
        assertThat(recognizedText).isNotEmpty()

        // Verify at least one expected item is recognized
        val expectedWords = listOf("Milk", "Eggs", "Bread", "Butter")
        val containsExpectedWord = expectedWords.any { word ->
            recognizedText.contains(word, ignoreCase = true)
        }
        assertThat(containsExpectedWord)
            .withFailMessage("Expected OCR to recognize at least one of: $expectedWords, but got: $recognizedText")
            .isTrue()

        testFile.delete()
    }

    @Test
    fun recognizeText_completesWithinTimeLimit() = runBlocking {
        val testFile = copyAssetToFile("test_list.jpg")
        val startTime = System.currentTimeMillis()

        service.recognizeText(testFile.absolutePath)

        val elapsed = System.currentTimeMillis() - startTime
        // AC: Recognition completes in <5 seconds including network latency
        assertThat(elapsed).isLessThan(5000L)

        testFile.delete()
    }

    @Test
    fun recognizeText_withNonExistentFile_returnsFailure() = runBlocking {
        val result = service.recognizeText("/non/existent/path.jpg")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.message).contains("not found")
    }

    private fun copyAssetToFile(assetName: String): File {
        val inputStream = context.assets.open(assetName)
        val outputFile = File(context.cacheDir, assetName)
        inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outputFile
    }
}
