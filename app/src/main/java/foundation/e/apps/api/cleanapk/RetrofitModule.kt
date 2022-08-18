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

package foundation.e.apps.api.cleanapk

import android.util.Log
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
import foundation.e.apps.api.cleanapk.data.app.Application
import foundation.e.apps.api.ecloud.EcloudApiInterface
import foundation.e.apps.api.exodus.ExodusTrackerApi
import foundation.e.apps.api.fdroid.FdroidApiInterface
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
import java.net.ConnectException
import java.util.Locale
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RetrofitModule {

    const val FAKE_ANDROID_VERSION = 7

    /**
     * Provides an instance of Retrofit to work with CleanAPK API
     * @return instance of [CleanAPKInterface]
     */
    @Singleton
    @Provides
    fun provideCleanAPKInterface(okHttpClient: OkHttpClient, moshi: Moshi): CleanAPKInterface {
        return Retrofit.Builder()
            .baseUrl(CleanAPKInterface.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CleanAPKInterface::class.java)
    }

    /**
     * Provides an instance of Retrofit to work with CleanAPK API
     * @return instance of [CleanApkAppDetailApi]
     */
    @Singleton
    @Provides
    fun provideCleanAPKDetailApi(
        okHttpClient: OkHttpClient,
        @Named("gsonCustomAdapter") gson: Gson
    ): CleanApkAppDetailApi {
        return Retrofit.Builder()
            .baseUrl(CleanAPKInterface.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(CleanApkAppDetailApi::class.java)
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
                "Dalvik/2.1.0 (Linux; U; Android $FAKE_ANDROID_VERSION;)"
            ).header("Accept-Language", Locale.getDefault().language)
            try {
                return@Interceptor chain.proceed(builder.build())
            } catch (e: ConnectException) {
                return@Interceptor buildErrorResponse(e, chain)
            } catch (e: Exception) {
                return@Interceptor buildErrorResponse(e, chain)
            }
        }
    }

    private fun buildErrorResponse(
        e: Exception,
        chain: Interceptor.Chain
    ): Response {
        Log.e("Retrofit", "buildErrorResponse: ${e.localizedMessage}")
        return Response.Builder()
            .code(999)
            .message(e.localizedMessage ?: "Unknown error")
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .body("{}".toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
    }

    @Singleton
    @Provides
    fun provideOkHttpClient(cache: Cache, interceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .cache(cache)
            .build()
    }
}
