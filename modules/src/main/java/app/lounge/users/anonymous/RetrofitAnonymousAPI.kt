package app.lounge.users.anonymous

import app.lounge.extension.toByteArray
import app.lounge.networking.FetchError
import app.lounge.networking.RetrofitFetching
import app.lounge.networking.appLounge
import app.lounge.networking.fetch
import com.aurora.gplayapi.data.models.AuthData
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.HeaderMap
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

        @POST
        fun authDataRequest(
            @Url url: String = "https://eu.gtoken.ecloud.global",
            @HeaderMap headers: Map<String, String>,
            @Body requestBody: RequestBody
        ): Call<AuthDataResponse>
    }

    override fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody,
        success: (AuthDataResponse) -> Unit,
        failure: (FetchError) -> Unit
    ) {
        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            anonymousAuthDataRequestBody.properties.toByteArray()
        )

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

}

typealias AuthDataResponse = AuthData