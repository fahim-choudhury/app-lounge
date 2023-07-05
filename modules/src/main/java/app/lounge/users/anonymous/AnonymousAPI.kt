package app.lounge.users.anonymous

import app.lounge.networking.FetchError
import com.aurora.gplayapi.GooglePlayApi
import com.aurora.gplayapi.data.providers.HeaderProvider
import java.util.Properties

interface AnonymousAPI {

    companion object {
        const val tokenBaseURL: String = "https://eu.gtoken.ecloud.global"
        const val loginBaseURL: String = GooglePlayApi.URL_BASE

        fun create(baseURL: String = tokenBaseURL, callTimeoutInSeconds: Long = 10) : AnonymousAPI {
            return RetrofitAnonymousAPI(
                baseURL = baseURL,
                anonymousUserEndpointFollowsRedirects = true,
                callTimeoutInSeconds = callTimeoutInSeconds
            )
        }
    }

    fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody,
        success : (AuthDataResponse) -> Unit,
        failure : (FetchError) -> Unit
    )

    fun requestAuthDataValidation(
        anonymousAuthDataValidationRequestBody: AnonymousAuthDataValidationRequestBody,
        success : (AuthDataValidationResponse) -> Unit,
        failure : (FetchError) -> Unit
    )

    object Header {
        val authData: (() -> String) -> Map<String, String> = {
            mapOf(Pair("User-Agent", it.invoke()))
        }
    }

    object Path {
        const val authData = "/"
        const val sync = "/fdfe/apps/contentSync"
    }

}

/** AnonymousAuthDataRequestBody */
data class AnonymousAuthDataRequestBody(
    val properties: Properties,
    val userAgent: String
)

/** AnonymousLoginRequestBody */
data class AnonymousAuthDataValidationRequestBody(
    val authDataResponse: AuthDataResponse,
) {
    val header = HeaderProvider.getDefaultHeaders(authDataResponse)
}