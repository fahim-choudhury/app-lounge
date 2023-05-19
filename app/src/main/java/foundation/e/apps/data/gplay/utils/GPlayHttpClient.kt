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

import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.network.IHttpClient
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import foundation.e.apps.utils.modules.CommonUtilsFunctions
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
import java.net.UnknownHostException
import javax.inject.Inject

class GPlayHttpClient @Inject constructor(
    cache: Cache,
) : IHttpClient {

    private val POST = "POST"
    private val GET = "GET"

    companion object {
        private const val TAG = "GPlayHttpClient"
    }

    private val okHttpClient = OkHttpClient().newBuilder()
        .retryOnConnectionFailure(false)
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
            "User-Agent" to CommonUtilsFunctions.getAppBuildInfo()
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
        return try {
            val call = okHttpClient.newCall(request)
            buildPlayResponse(call.execute())
        } catch (e: Exception) {
            when (e) {
                is UnknownHostException,
                is SocketTimeoutException -> handleExceptionOnGooglePlayRequest(e)
                else -> handleExceptionOnGooglePlayRequest(e)
            }
        }
    }

    private fun handleExceptionOnGooglePlayRequest(e: Exception): PlayResponse {
        Timber.e("processRequest: ${e.localizedMessage}")
        return PlayResponse().apply {
            errorString = "${this@GPlayHttpClient::class.java.simpleName}: ${e.localizedMessage}"
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

            if (code == 401) {
                MainScope().launch {
                    EventBus.invokeEvent(
                        AppEvent.InvalidAuthEvent(AuthObject.GPlayAuth::class.java.simpleName)
                    )
                }
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
