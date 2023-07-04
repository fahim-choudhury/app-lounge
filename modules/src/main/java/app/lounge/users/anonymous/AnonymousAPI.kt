package app.lounge.users.anonymous

import app.lounge.networking.FetchError
import com.aurora.gplayapi.GooglePlayApi
import com.aurora.gplayapi.data.providers.HeaderProvider
import java.util.Properties

interface AnonymousAPI {

    companion object {
        const val tokenBaseURL: String = "https://eu.gtoken.ecloud.global"
        const val loginBaseURL: String = GooglePlayApi.URL_SYNC+"/"

        fun create(baseURL: String = tokenBaseURL) : AnonymousAPI {
            return RetrofitAnonymousAPI(
                baseURL = baseURL,
                anonymousUserEndpointFollowsRedirects = true,
                callTimeoutInSeconds = 30
            )
        }
    }

    fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody,
        success : (AuthDataResponse) -> Unit,
        failure : (FetchError) -> Unit
    )

    fun performUserLogin(
        anonymousLoginRequestBody: AnonymousLoginRequestBody,
        success : (LoginResponse) -> Unit,
        failure : (FetchError) -> Unit
    )

    object Header {
        val authData: (() -> String) -> Map<String, String> = {
            mapOf(Pair("User-Agent", it.invoke()))
        }
    }

}

/** AnonymousAuthDataRequestBody */
data class AnonymousAuthDataRequestBody(
    val properties: Properties,
    val userAgent: String
)

/** AnonymousLoginRequestBody */
data class AnonymousLoginRequestBody(
    val authDataResponse: AuthDataResponse,
) {
    val header = HeaderProvider.getAuthHeaders(authDataResponse)
}