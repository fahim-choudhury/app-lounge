/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.data.cleanapk

import android.os.Build
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.cleanapk.data.app.Application
import foundation.e.apps.data.ecloud.EcloudApiInterface
import foundation.e.apps.data.exodus.ExodusTrackerApi
import foundation.e.apps.data.fdroid.FdroidApiInterface
import foundation.e.apps.data.gitlab.SystemAppsUpdatesApi
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.net.ConnectException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RetrofitModule {

    private const val HTTP_TIMEOUT_IN_SECOND = 10L

    /**
     * Provides an instance of Retrofit to work with CleanAPK API
     * @return instance of [CleanApkRetrofit]
     */
    @Singleton
    @Provides
    fun provideCleanAPKInterface(okHttpClient: OkHttpClient, moshi: Moshi): CleanApkRetrofit {
        return Retrofit.Builder()
            .baseUrl(CleanApkRetrofit.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CleanApkRetrofit::class.java)
    }

    /**
     * Provides an instance of Retrofit to work with CleanAPK API
     * @return instance of [CleanApkAppDetailsRetrofit]
     */
    @Singleton
    @Provides
    fun provideCleanAPKDetailApi(
        okHttpClient: OkHttpClient,
        @Named("gsonCustomAdapter") gson: Gson
    ): CleanApkAppDetailsRetrofit {
        return Retrofit.Builder()
            .baseUrl(CleanApkRetrofit.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(CleanApkAppDetailsRetrofit::class.java)
    }

    @Singleton
    @Provides
    fun provideExodusApi(okHttpClient: OkHttpClient, moshi: Moshi): ExodusTrackerApi {
        return Retrofit.Builder()
            .baseUrl(ExodusTrackerApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ExodusTrackerApi::class.java)
    }

    /**
     * The fdroid api returns results in .yaml format.
     * Hence we need a yaml convertor.
     * Convertor is being provided by [getYamlFactory].
     */
    @Singleton
    @Provides
    fun provideFdroidApi(
        okHttpClient: OkHttpClient,
        @Named("yamlFactory") yamlFactory: JacksonConverterFactory
    ): FdroidApiInterface {
        return Retrofit.Builder()
            .baseUrl(FdroidApiInterface.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(yamlFactory)
            .build()
            .create(FdroidApiInterface::class.java)
    }

    @Singleton
    @Provides
    fun provideSystemAppsUpdatesApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): SystemAppsUpdatesApi {
        return Retrofit.Builder()
            .baseUrl(SystemAppsUpdatesApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SystemAppsUpdatesApi::class.java)
    }

    @Singleton
    @Provides
    fun provideEcloudApi(okHttpClient: OkHttpClient, moshi: Moshi): EcloudApiInterface {
        return Retrofit.Builder()
            .baseUrl(EcloudApiInterface.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(EcloudApiInterface::class.java)
    }

    @Singleton
    @Provides
    fun getMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Singleton
    @Provides
    @Named("gsonCustomAdapter")
    fun getGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(Application::class.java, ApplicationDeserializer())
            .enableComplexMapKeySerialization()
            .create()
    }

    /**
     * Used in above [provideFdroidApi].
     * Reference: https://stackoverflow.com/a/69859687
     */
    @Singleton
    @Provides
    @Named("yamlFactory")
    fun getYamlFactory(): JacksonConverterFactory {
        return JacksonConverterFactory.create(ObjectMapper(YAMLFactory()))
    }

    @Singleton
    @Provides
    fun provideInterceptor(): Interceptor {
        return Interceptor { chain ->
            val builder = chain.request().newBuilder()
            builder.header(
                "User-Agent",
                "Dalvik/2.1.0 (Linux; U; Android ${Build.VERSION.RELEASE};)"
            ).header("Accept-Language", Locale.getDefault().language)

            return@Interceptor chain.proceed(builder.build())
        }
    }

    @Singleton
    @Provides
    fun provideOkHttpClient(cache: Cache, interceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .callTimeout(HTTP_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)
            .cache(cache)
            .build()
    }
}
