package app.lounge.users.anonymous

import app.lounge.networking.RetrofitFetching
import app.lounge.networking.appLounge
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST

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
            @HeaderMap headers: Map<String, String> = AnonymousAPI.Header.authData,
            @Body requestBody: RequestBody?
        ): Call<Unit>
    }

    override fun performLogin(success: () -> Unit, failure: () -> Unit) {
        /*fetch(
            endpoint = anonymousUserEndPoint.authDataRequest(requestBody = null),
            processing = ,
        )*/
    }

}
