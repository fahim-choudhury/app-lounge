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

import android.os.Build
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.api.exodus.ExodusTrackerApi
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.ConnectException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RetrofitModule {

    /**
     * Provides an instance of Retrofit to work with CleanAPK API
     * @return instance of [CleanAPKInterface]
     */
    @Singleton
    @Provides
    fun provideCleanAPKInterface(okHttpClient: OkHttpClient): CleanAPKInterface {
        return Retrofit.Builder()
            .baseUrl(CleanAPKInterface.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(CleanAPKInterface::class.java)
    }

    @Singleton
    @Provides
    fun provideExodusApi(okHttpClient: OkHttpClient): ExodusTrackerApi {
        return Retrofit.Builder()
            .baseUrl(ExodusTrackerApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ExodusTrackerApi::class.java)
    }

    @Singleton
    @Provides
    fun provideInterceptor(): Interceptor {
        return Interceptor { chain ->
            val builder = chain.request().newBuilder()
            builder.header(
                "User-Agent",
                "Dalvik/2.1.0 (Linux; U; Android ${Build.VERSION.RELEASE}; ${Build.FINGERPRINT})"
            )
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
