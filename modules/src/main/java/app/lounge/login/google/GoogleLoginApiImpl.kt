package app.lounge.login.google

import app.lounge.networking.GplayHttpClient
import app.lounge.networking.fetchGooglePlayApi
import app.lounge.networking.NetworkResult
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.helpers.AuthValidator
import com.aurora.gplayapi.network.IHttpClient
import java.util.Locale
import javax.inject.Inject

class GoogleLoginApiImpl @Inject constructor(
    private val gplayHttpClient: IHttpClient,
) : GoogleLoginApi {

    companion object {
        private const val TOKEN_AUTH_URL = "https://android.clients.google.com/auth"
        private const val BUILD_VERSION_SDK = 28
        private const val PLAY_SERVICES_VERSION_CODE = 19629032
    }

    override suspend fun fetchAASToken(
        email: String,
        oauthToken: String
    ): NetworkResult<PlayResponse> {
        return fetchGooglePlayApi {
            fetchAASTokenPlayResponse(email, oauthToken)
        }
    }

    override suspend fun validate(authData: AuthData): NetworkResult<Boolean> {
        return fetchGooglePlayApi {
            val authValidator = AuthValidator(authData).using(gplayHttpClient)
            authValidator.isValid()
        }
    }

    private fun fetchAASTokenPlayResponse(email: String?, oAuthToken: String?): PlayResponse {
        if (email == null || oAuthToken == null)
            return PlayResponse()

        val params: MutableMap<String, Any> = hashMapOf()
        params["lang"] = Locale.getDefault().toString().replace("_", "-")
        params["google_play_services_version"] = PLAY_SERVICES_VERSION_CODE
        params["sdk_version"] = BUILD_VERSION_SDK
        params["device_country"] = Locale.getDefault().country.lowercase(Locale.US)
        params["Email"] = email
        params["service"] = "ac2dm"
        params["get_accountid"] = 1
        params["ACCESS_TOKEN"] = 1
        params["callerPkg"] = "com.google.android.gms"
        params["add_account"] = 1
        params["Token"] = oAuthToken
        params["callerSig"] = "38918a453d07199354f8b19af05ec6562ced5788"

        val body = params.map { "${it.key}=${it.value}" }.joinToString(separator = "&")
        val header = mapOf(
            "app" to "com.google.android.gms",
            "User-Agent" to "",
            "Content-Type" to "application/x-www-form-urlencoded"
        )

        /*
         * Returning PlayResponse instead of map so that we can get the network response code.
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5709
         */
        return gplayHttpClient.post(TOKEN_AUTH_URL, header, body.toByteArray())
    }

}