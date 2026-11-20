package com.example.data.remote

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkModule {
    @Volatile
    private var okHttpClient: OkHttpClient? = null

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun getOkHttpClient(context: Context): OkHttpClient {
        return okHttpClient ?: synchronized(this) {
            okHttpClient ?: buildOkHttpClient(context.applicationContext).also { okHttpClient = it }
        }
    }

    private fun buildOkHttpClient(appContext: Context): OkHttpClient {
        val cacheDir = File(appContext.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 25L * 1024L * 1024L) // 25 MB HTTP metadata cache
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .cache(cache)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "StreamMusicAndroid/1.0")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .build()
    }

    fun createAudiusService(context: Context): AudiusApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.audius.co/")
            .client(getOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AudiusApiService::class.java)
    }

    fun createDeezerService(context: Context): DeezerApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(getOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DeezerApiService::class.java)
    }

    fun createLrclibService(context: Context): LrclibApiService {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(getOkHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LrclibApiService::class.java)
    }
}
