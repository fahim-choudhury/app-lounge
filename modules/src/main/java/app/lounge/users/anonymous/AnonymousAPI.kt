package app.lounge.users.anonymous

import app.lounge.BuildConfig
import app.lounge.networking.FetchError
import com.google.gson.Gson
import java.util.Properties

interface AnonymousAPI {

    companion object {
        const val tokenBaseURL: String = "https://eu.gtoken.ecloud.global"
        const val loginBaseURL: String = "GooglePlayApi.URL_SYNC"

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
