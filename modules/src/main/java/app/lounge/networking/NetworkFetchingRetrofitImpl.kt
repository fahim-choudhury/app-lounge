package app.lounge.networking

import app.lounge.extension.toByteArray
import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.model.AnonymousAuthDataValidationRequestBody
import app.lounge.model.AuthDataResponse
import app.lounge.model.AuthDataValidationResponse
import com.aurora.gplayapi.GooglePlayApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

interface NetworkFetchingRetrofitAPI {

    companion object {
        const val tokenBaseURL: String = "https://eu.gtoken.ecloud.global"
        const val googlePlayBaseURL: String = GooglePlayApi.URL_BASE
    }

    @POST(Path.authData)
    suspend fun authDataRequest(
        @HeaderMap headers: Map<String, String>,
        @Body requestBody: RequestBody
    ): AuthDataResponse

    @POST(Path.sync)
    suspend fun validateAuthentication(
        @HeaderMap headers: Map<String, String>
    ): AuthDataValidationResponse



    object Header {
        val authData: (() -> String) -> Map<String, String> = {
            mapOf(Pair("User-Agent", it.invoke()))
        }
    }

    private object Path {
        const val authData = "/"
        const val sync = "/fdfe/apps/contentSync"
    }

}

@Singleton
class NetworkFetchingRetrofitImpl @Inject constructor(
    val eCloud: Retrofit,
    val google: Retrofit,
) : NetworkFetching  {

    private val eCloudNetworkFetchingRetrofitAPI = eCloud.create(
        NetworkFetchingRetrofitAPI::class.java
    )

    private val googleNetworkFetchingRetrofitAPI = google.create(
        NetworkFetchingRetrofitAPI::class.java
    )

    override suspend fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody
    ): AuthDataResponse {
        val requestBody: RequestBody =
            anonymousAuthDataRequestBody.properties.toByteArray().let { result ->
                result.toRequestBody(
                    contentType = "application/json".toMediaTypeOrNull(),
                    offset = 0,
                    byteCount = result.size
                )
            }
        return eCloudNetworkFetchingRetrofitAPI.authDataRequest(
            requestBody = requestBody,
            headers = NetworkFetchingRetrofitAPI.Header.authData {
                anonymousAuthDataRequestBody.userAgent
            }
        )
    }

    override suspend fun requestAuthDataValidation(
        anonymousAuthDataValidationRequestBody: AnonymousAuthDataValidationRequestBody
    ): AuthDataValidationResponse {
        return googleNetworkFetchingRetrofitAPI.validateAuthentication(
            headers = anonymousAuthDataValidationRequestBody.header
        )
    }
}