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
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

interface UserAuthenticationRetrofitAPI {

    companion object {
        const val tokenBaseURL: String = "https://eu.gtoken.ecloud.global"
        const val googlePlayBaseURL: String = GooglePlayApi.URL_BASE
    }

    @POST(Path.authData)
    suspend fun authDataRequest(
        @HeaderMap headers: Map<String, String>,
        @Body requestBody: RequestBody
    ): Response<AuthDataResponse>

    @POST(Path.sync)
    suspend fun validateAuthentication(
        @HeaderMap headers: Map<String, String>
    ): Response<AuthDataValidationResponse>



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
class UserAuthenticationRetrofitImpl @Inject constructor(
    retrofit: Retrofit
) : UserAuthentication  {

    private val userAuthenticationRetrofitAPI = retrofit.create(
        UserAuthenticationRetrofitAPI::class.java
    )

    override suspend fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody
    ): NetworkResult<AuthDataResponse> {
        val requestBody: RequestBody =
            anonymousAuthDataRequestBody.properties.toByteArray().let { result ->
                result.toRequestBody(
                    contentType = "application/json".toMediaTypeOrNull(),
                    offset = 0,
                    byteCount = result.size
                )
            }
        return fetch {
            userAuthenticationRetrofitAPI.authDataRequest(
                requestBody = requestBody,
                headers = UserAuthenticationRetrofitAPI.Header.authData {
                    anonymousAuthDataRequestBody.userAgent
                }
            )
        }
    }

    override suspend fun requestAuthDataValidation(
        anonymousAuthDataValidationRequestBody: AnonymousAuthDataValidationRequestBody
    ): NetworkResult<AuthDataValidationResponse> {
        return fetch {
            userAuthenticationRetrofitAPI.validateAuthentication(
                headers = anonymousAuthDataValidationRequestBody.header
            )
        }
    }

}