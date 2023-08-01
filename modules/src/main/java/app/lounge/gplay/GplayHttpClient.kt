package app.lounge.gplay

import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.network.IHttpClient
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Named

class GplayHttpClient @Inject constructor(@Named("privateOkHttpClient") val okHttpClient: OkHttpClient): IHttpClient {

    companion object {
        private const val POST = "POST"
        private const val GET = "GET"
    }


    override fun get(url: String, headers: Map<String, String>): PlayResponse {
        return get(url, headers, mapOf())
    }

    override fun get(url: String, headers: Map<String, String>, paramString: String): PlayResponse {
        val request = Request.Builder()
            .url(url + paramString)
            .headers(headers.toHeaders())
            .method(GET, null)
            .build()
        return processRequest(request)
    }

    override fun get(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrlWithQueryParameters(url, params))
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

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse {
        TODO("Not yet implemented")
    }

    override fun post(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        TODO("Not yet implemented")
    }

    override fun postAuth(url: String, body: ByteArray): PlayResponse {
        TODO("Not yet implemented")
    }

    private fun buildUrlWithQueryParameters(url: String, params: Map<String, String>): HttpUrl {
        val urlBuilder = url.toHttpUrl().newBuilder()
        params.forEach {
            urlBuilder.addQueryParameter(it.key, it.value)
        }
        return urlBuilder.build()
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
            errorString = "${this@GplayHttpClient::class.java.simpleName}: ${e.localizedMessage}"
        }
    }

    private fun buildPlayResponse(response: Response): PlayResponse {
        return PlayResponse().apply {
            isSuccessful = response.isSuccessful
            code = response.code

            Timber.d("Url: ${response.request.url}\nStatus: $code")

            if (code == 401) {
                // TODO Handle the case of Unauthorized user
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