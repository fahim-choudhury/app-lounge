package app.lounge.users.anonymous

import app.lounge.extension.toByteArray
import app.lounge.networking.FetchError
import app.lounge.networking.RetrofitFetching
import app.lounge.networking.appLounge
import app.lounge.networking.fetch
import com.aurora.gplayapi.data.providers.HeaderProvider
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

internal class RetrofitAnonymousAPI(
    baseURL: String,
    anonymousUserEndpointFollowsRedirects: Boolean,
    callTimeoutInSeconds: Long,
): AnonymousAPI, RetrofitFetching {

    private val anonymousUserEndPoint: AnonymousUserEndPointEndpoint = Retrofit.Builder().appLounge(
        baseURL = baseURL,
        shouldFollowRedirects = anonymousUserEndpointFollowsRedirects,
        callTimeoutInSeconds = callTimeoutInSeconds
    ).create(AnonymousUserEndPointEndpoint::class.java)

    interface AnonymousUserEndPointEndpoint {

        @POST(AnonymousAPI.Path.authData)
        fun authDataRequest(
            @HeaderMap headers: Map<String, String>,
            @Body requestBody: RequestBody
        ): Call<AuthDataResponse>

        @POST(AnonymousAPI.Path.sync)
        fun loginUser(
            @HeaderMap headers: Map<String, String>
        ): Call<LoginResponse>
    }

    override fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody,
        success: (AuthDataResponse) -> Unit,
        failure: (FetchError) -> Unit
    ) {
        val requestBody: RequestBody =
            anonymousAuthDataRequestBody.properties.toByteArray().let { result ->
                result.toRequestBody(
                    contentType = "application/json".toMediaTypeOrNull(),
                    offset = 0,
                    byteCount = result.size
                )
        }

        fetch(
            endpoint = anonymousUserEndPoint.authDataRequest(
                requestBody = requestBody,
                headers = AnonymousAPI.Header.authData {
                    anonymousAuthDataRequestBody.userAgent
                }
            ),
            success = success,
            failure = failure
        )
    }

    override fun performUserLogin(
        anonymousLoginRequestBody: AnonymousLoginRequestBody,
        success: (LoginResponse) -> Unit,
        failure: (FetchError) -> Unit
    ) {
        fetch(
            endpoint = anonymousUserEndPoint.loginUser(
                headers = anonymousLoginRequestBody.header
            ),
            success = success,
            failure = failure
        )
    }
}