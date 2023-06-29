package app.lounge.users.anonymous

import app.lounge.BuildConfig

interface AnonymousAPI {

    companion object {
        private const val tokenBaseURL: String = "https://eu.gtoken.ecloud.global"
        private const val loginBaseURL: String = "GooglePlayApi.URL_SYNC"

        fun create(baseURL: String = tokenBaseURL) : AnonymousAPI {
            return RetrofitAnonymousAPI(
                baseURL = baseURL,
                anonymousUserEndpointFollowsRedirects = true,
                callTimeoutInSeconds = 30
            )
        }
    }

    fun performLogin(
        success : () -> Unit,
        failure : () -> Unit
    )

    object Header {
        val authData: Map<String, String> get() {
            return mapOf(
                Pair("User-Agent", BuildConfig.BUILD_TYPE), // CommonUtilsFunctions.getAppBuildInfo()
            )
        }
    }

}