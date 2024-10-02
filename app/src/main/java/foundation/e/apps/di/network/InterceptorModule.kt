/*
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.di.network

import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.BuildConfig
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class InterceptorModule {

    companion object {
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"
        const val ERROR_RESPONSE_CODE = 999 // Arbitrary value, not to mix with HTTP status code
        const val ERROR_RESPONSE_MESSAGE = "IOException occurred."
        val HEADER_USER_AGENT_VALUE =
            "Dalvik/2.1.0 (Linux; U; Android ${Build.VERSION.RELEASE};)"
    }

    @Singleton
    @Provides
    fun provideInterceptor(): Interceptor {
        return Interceptor { chain ->
            val builder =
                chain
                    .request()
                    .newBuilder()
                    .header(
                        HEADER_USER_AGENT,
                        HEADER_USER_AGENT_VALUE
                    )
                    .header(HEADER_ACCEPT_LANGUAGE, Locale.getDefault().language)

            val response = try {
                chain.proceed(builder.build())
            } catch (ioException: IOException) {
                Timber.e(ioException)
                return@Interceptor createResponseForIOException(chain.request())
            }

            return@Interceptor response
        }
    }

    private fun createResponseForIOException(request: Request) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(ERROR_RESPONSE_CODE)
        .message(ERROR_RESPONSE_MESSAGE)
        .body(ERROR_RESPONSE_MESSAGE.toResponseBody())
        .build()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = when {
                BuildConfig.DEBUG -> HttpLoggingInterceptor.Level.HEADERS
                else -> HttpLoggingInterceptor.Level.NONE
            }
        }
    }
}
