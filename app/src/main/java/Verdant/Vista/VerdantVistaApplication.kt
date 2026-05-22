package Verdant.Vista

import android.app.Application
import Verdant.Vista.data.api.INaturalistService
import Verdant.Vista.data.api.INaturalistV2Service
import Verdant.Vista.data.api.WikipediaService
import Verdant.Vista.data.db.AppDatabase
import Verdant.Vista.data.repository.ObservationRepository
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class VerdantVistaApplication : Application(), ImageLoaderFactory {

    lateinit var repository: ObservationRepository
        private set

    private val okHttpClient: OkHttpClient by lazy {
        // Create a cache for API responses (JSON data)
        // 10MB is plenty for thousands of species descriptions
        val apiCache = Cache(File(cacheDir, "api_cache"), 10 * 1024 * 1024)

        OkHttpClient.Builder()
            .cache(apiCache)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "VerdantVista/1.0 (Android; https://github.com/VerdantVista)")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        val iNaturalistRetrofit = Retrofit.Builder()
            .baseUrl("https://api.inaturalist.org/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val iNaturalistService = iNaturalistRetrofit.create(INaturalistService::class.java)

        val iNaturalistV2Retrofit = Retrofit.Builder()
            .baseUrl("https://api.inaturalist.org/v2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val iNaturalistV2Service = iNaturalistV2Retrofit.create(INaturalistV2Service::class.java)

        val wikiRetrofit = Retrofit.Builder()
            .baseUrl("https://en.wikipedia.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val wikipediaService = wikiRetrofit.create(WikipediaService::class.java)

        val database = AppDatabase.getDatabase(this)
        repository = ObservationRepository(iNaturalistService, iNaturalistV2Service, wikipediaService, database.favoriteDao(), database.discoveryDao(), this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250 * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
