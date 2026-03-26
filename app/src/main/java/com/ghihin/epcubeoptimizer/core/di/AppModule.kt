package com.ghihin.epcubeoptimizer.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ghihin.epcubeoptimizer.BuildConfig
import com.ghihin.epcubeoptimizer.core.network.OpenWeatherMapApi
import com.ghihin.epcubeoptimizer.data.repository.ScheduleRepositoryImpl
import com.ghihin.epcubeoptimizer.data.repository.WeatherRepositoryImpl
import com.ghihin.epcubeoptimizer.domain.repository.ScheduleRepository
import com.ghihin.epcubeoptimizer.domain.repository.WeatherRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_schedule"
)

/** アプリ全体のシングルトン依存関係を提供する Hilt モジュール。 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenWeatherMapApi(retrofit: Retrofit): OpenWeatherMapApi =
        retrofit.create(OpenWeatherMapApi::class.java)

    @Provides
    @Named("apiKey")
    fun provideApiKey(): String = BuildConfig.OPEN_WEATHER_MAP_API_KEY

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    fun provideZoneId(): java.time.ZoneId = java.time.ZoneId.systemDefault()
}

/** Repository インターフェースと実装クラスをバインドする Hilt モジュール。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository
}
