package com.listscanner.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.listscanner.BuildConfig
import com.listscanner.data.ListScannerDatabase
import com.listscanner.data.dao.ItemDao
import com.listscanner.data.dao.ListDao
import com.listscanner.data.dao.PhotoDao
import com.listscanner.data.repository.ItemRepositoryImpl
import com.listscanner.data.repository.ListRepositoryImpl
import com.listscanner.data.repository.PhotoRepositoryImpl
import com.listscanner.data.repository.PrivacyConsentRepository
import com.listscanner.data.repository.PrivacyConsentRepositoryImpl
import com.listscanner.data.repository.UsageTrackingRepository
import com.listscanner.data.repository.UsageTrackingRepositoryImpl
import com.listscanner.device.CloudVisionApi
import com.listscanner.device.CloudVisionService
import com.listscanner.device.CloudVisionServiceImpl
import com.listscanner.device.ImageCropService
import com.listscanner.device.ImageCropServiceImpl
import com.listscanner.device.NetworkConnectivityService
import com.listscanner.device.NetworkConnectivityServiceImpl
import com.listscanner.domain.repository.ItemRepository
import com.listscanner.domain.repository.ListRepository
import com.listscanner.domain.repository.PhotoRepository
import com.listscanner.domain.service.ListCreationService
import com.listscanner.domain.service.ListCreationServiceImpl
import com.listscanner.domain.service.TextParsingService
import com.listscanner.domain.service.TextParsingServiceImpl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "list_scanner_settings")

object DatabaseModule {
    @Volatile
    private var INSTANCE: ListScannerDatabase? = null

    @Volatile
    private var textParsingService: TextParsingService? = null

    @Volatile
    private var listCreationService: ListCreationService? = null

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var cloudVisionApi: CloudVisionApi? = null

    @Volatile
    private var cloudVisionService: CloudVisionService? = null

    @Volatile
    private var networkConnectivityService: NetworkConnectivityService? = null

    @Volatile
    private var imageCropService: ImageCropService? = null

    @Volatile
    private var privacyConsentRepository: PrivacyConsentRepository? = null

    @Volatile
    private var usageTrackingRepository: UsageTrackingRepository? = null

    private const val CLOUD_VISION_BASE_URL = "https://vision.googleapis.com/"

    fun provideDatabase(context: Context): ListScannerDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                ListScannerDatabase::class.java,
                ListScannerDatabase.DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
            INSTANCE = instance
            instance
        }
    }

    fun providePhotoDao(database: ListScannerDatabase): PhotoDao = database.photoDao()
    fun provideListDao(database: ListScannerDatabase): ListDao = database.listDao()
    fun provideItemDao(database: ListScannerDatabase): ItemDao = database.itemDao()

    fun providePhotoRepository(photoDao: PhotoDao): PhotoRepository =
        PhotoRepositoryImpl(photoDao)

    fun provideListRepository(listDao: ListDao, photoDao: PhotoDao, database: ListScannerDatabase): ListRepository =
        ListRepositoryImpl(listDao, photoDao, database)

    fun provideItemRepository(itemDao: ItemDao, database: ListScannerDatabase): ItemRepository =
        ItemRepositoryImpl(itemDao, database)

    fun provideTextParsingService(): TextParsingService {
        return textParsingService ?: synchronized(this) {
            textParsingService ?: TextParsingServiceImpl().also {
                textParsingService = it
            }
        }
    }

    fun provideListCreationService(
        database: ListScannerDatabase,
        textParsingService: TextParsingService
    ): ListCreationService {
        return listCreationService ?: synchronized(this) {
            listCreationService ?: ListCreationServiceImpl(
                database,
                textParsingService
            ).also {
                listCreationService = it
            }
        }
    }

    fun provideOkHttpClient(): OkHttpClient {
        return okHttpClient ?: synchronized(this) {
            okHttpClient ?: run {
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .addInterceptor(loggingInterceptor)
                    .build()
                    .also { okHttpClient = it }
            }
        }
    }

    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return retrofit ?: synchronized(this) {
            retrofit ?: Retrofit.Builder()
                .baseUrl(CLOUD_VISION_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .also { retrofit = it }
        }
    }

    fun provideCloudVisionApi(retrofit: Retrofit): CloudVisionApi {
        return cloudVisionApi ?: synchronized(this) {
            cloudVisionApi ?: retrofit.create(CloudVisionApi::class.java)
                .also { cloudVisionApi = it }
        }
    }

    fun provideCloudVisionService(api: CloudVisionApi): CloudVisionService {
        return cloudVisionService ?: synchronized(this) {
            cloudVisionService ?: CloudVisionServiceImpl(
                api = api,
                apiKey = BuildConfig.CLOUD_VISION_API_KEY
            ).also {
                cloudVisionService = it
            }
        }
    }

    fun provideNetworkConnectivityService(context: Context): NetworkConnectivityService {
        return networkConnectivityService ?: synchronized(this) {
            networkConnectivityService ?: NetworkConnectivityServiceImpl(context.applicationContext).also {
                networkConnectivityService = it
            }
        }
    }

    fun provideImageCropService(): ImageCropService {
        return imageCropService ?: synchronized(this) {
            imageCropService ?: ImageCropServiceImpl().also {
                imageCropService = it
            }
        }
    }

    fun provideDataStore(context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    fun providePrivacyConsentRepository(context: Context): PrivacyConsentRepository {
        return privacyConsentRepository ?: synchronized(this) {
            privacyConsentRepository ?: PrivacyConsentRepositoryImpl(
                provideDataStore(context)
            ).also {
                privacyConsentRepository = it
            }
        }
    }

    fun provideUsageTrackingRepository(context: Context): UsageTrackingRepository {
        return usageTrackingRepository ?: synchronized(this) {
            usageTrackingRepository ?: UsageTrackingRepositoryImpl(
                provideDataStore(context)
            ).also {
                usageTrackingRepository = it
            }
        }
    }
}
