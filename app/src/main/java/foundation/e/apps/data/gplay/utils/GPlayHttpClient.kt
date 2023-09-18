/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
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

package foundation.e.apps.data.gplay.utils

import androidx.annotation.VisibleForTesting
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.network.IHttpClient
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.utils.SystemInfoProvider
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GPlayHttpClient @Inject constructor(
    private val cache: Cache
) : IHttpClient {

    private val POST = "POST"
    private val GET = "GET"

    companion object {
        private const val TAG = "GPlayHttpClient"
        private const val HTTP_TIMEOUT_IN_SECOND = 10L
        private const val SEARCH = "search"
        private const val SEARCH_SUGGEST = "searchSuggest"
        private const val STATUS_CODE_OK = 200
        private const val STATUS_CODE_UNAUTHORIZED = 401
        private const val STATUS_CODE_TOO_MANY_REQUESTS = 429
        const val STATUS_CODE_TIMEOUT = 408
    }

    @VisibleForTesting
    var okHttpClient = OkHttpClient().newBuilder()
        .retryOnConnectionFailure(false)
        .callTimeout(HTTP_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cache(cache)
        .build()

    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, requestBody: RequestBody): PlayResponse {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toHeaders())
            .method(POST, requestBody)
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun post(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .method(POST, "".toRequestBody(null))
            .build()
        return processRequest(request)
    }

    override fun postAuth(url: String, body: ByteArray): PlayResponse {
        val requestBody = body.toRequestBody("application/json".toMediaType(), 0, body.size)
        val headers = mapOf(
            "User-Agent" to SystemInfoProvider.getAppBuildInfo()
        )
        val request = Request.Builder()
            .headers(headers.toHeaders())
            .url(url)
            .method(POST, requestBody)
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse {
        val requestBody = body.toRequestBody(
            "application/x-protobuf".toMediaType(),
            0,
            body.size
        )
        return post(url, headers, requestBody)
    }

    @Throws(IOException::class)
    override fun get(url: String, headers: Map<String, String>): PlayResponse {
        return get(url, headers, mapOf())
    }

    @Throws(IOException::class)
    override fun get(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .method(GET, null)
            .build()
        return processRequest(request)
    }

    override fun getAuth(url: String): PlayResponse {
        val request = Request.Builder()
            .url(url)
            .method(GET, null)
            .build()
        Timber.d("get auth request", request.toString())
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun get(
        url: String,
        headers: Map<String, String>,
        paramString: String
    ): PlayResponse {
        val request = Request.Builder()
            .url(url + paramString)
            .headers(headers.toHeaders())
            .method(GET, null)
            .build()
        return processRequest(request)
    }

    private fun processRequest(request: Request): PlayResponse {
        var response: Response? = null
        return try {
            val call = okHttpClient.newCall(request)
            response = call.execute()
            buildPlayResponse(response)
        } catch (e: GplayHttpRequestException) {
            throw e
        } catch (e: Exception) {
            val status = if (e is SocketTimeoutException) STATUS_CODE_TIMEOUT else -1
            throw GplayHttpRequestException(status, e.localizedMessage ?: "")
        } finally {
            response?.close()
        }
    }

    private fun buildUrl(url: String, params: Map<String, String>): HttpUrl {
        val urlBuilder = url.toHttpUrl().newBuilder()
        params.forEach {
            urlBuilder.addQueryParameter(it.key, it.value)
        }
        return urlBuilder.build()
    }

    private fun buildPlayResponse(response: Response): PlayResponse {
        return PlayResponse().apply {
            isSuccessful = response.isSuccessful
            code = response.code
            Timber.d("$TAG: Url: ${response.request.url}\nStatus: $code")

            when (code) {
                STATUS_CODE_UNAUTHORIZED -> MainScope().launch {
                    EventBus.invokeEvent(
                        AppEvent.InvalidAuthEvent(AuthObject.GPlayAuth::class.java.simpleName)
                    )
                }

                STATUS_CODE_TOO_MANY_REQUESTS -> MainScope().launch {
                    cache.evictAll()
                    if (response.request.url.toString().contains(SEARCH_SUGGEST)) {
                        return@launch
                    }

                    EventBus.invokeEvent(
                        AppEvent.TooManyRequests()
                    )
                }
            }

            if (code !in listOf(STATUS_CODE_OK, STATUS_CODE_UNAUTHORIZED)) {
                throw GplayHttpRequestException(code, response.message)
            }

            if (response.body != null) {
                responseBytes = response.body!!.bytes()
            }

            if (!isSuccessful) {
                errorString = response.message
            }
        }
    }
}

class GplayHttpRequestException(val status: Int, message: String) : Exception(message)
