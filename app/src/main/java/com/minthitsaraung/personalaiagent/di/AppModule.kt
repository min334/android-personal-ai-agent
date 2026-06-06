package com.minthitsaraung.personalaiagent.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.minthitsaraung.personalaiagent.BuildConfig
import com.minthitsaraung.personalaiagent.data.remote.OpenRouterApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * AppModule — Hilt DI module (application-scoped singletons).
 * ──────────────────────────────────────────────────────────────────────────────
 * Provides: OkHttpClient → Gson → Retrofit → OpenRouterApiService → Context.
 *
 * ─── OpenRouter endpoint ──────────────────────────────────────────────────────
 * Base URL: https://openrouter.ai/api/v1/
 * The API key is NOT injected here as an OkHttp interceptor, because the user
 * can update it at runtime in Settings. Instead the key is read from
 * SecureStorageManager on every call and passed as a per-request @Header
 * in OpenRouterApiService. This means the Retrofit singleton never needs
 * to be recreated after a key change.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─── Base URL ─────────────────────────────────────────────────────────────
    // Defined here as a constant so it is easy to find and update.
    // Trailing slash is required by Retrofit.
    private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/"

    // ─── Networking ───────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)   // OpenRouter can be slow on large models
            .writeTimeout(30, TimeUnit.SECONDS)

        // Log full request/response bodies in DEBUG builds only.
        // WARNING: Do not enable in release — logs would contain the API key.
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()   // Tolerate minor JSON deviations from some models
        .create()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(OPENROUTER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideOpenRouterApiService(retrofit: Retrofit): OpenRouterApiService =
        retrofit.create(OpenRouterApiService::class.java)

    // ─── Application Context ──────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context
}
