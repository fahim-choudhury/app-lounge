package app.lounge.login.google

import app.lounge.gplay.GplayHttpClient
import com.aurora.gplayapi.data.models.PlayResponse
import java.util.Locale
import javax.inject.Inject

class AuthTokenFetchApi @Inject constructor(private val gplayHttpClient: GplayHttpClient) {
    companion object {
        private const val TOKEN_AUTH_URL = "https://android.clients.google.com/auth"
        private const val BUILD_VERSION_SDK = 28
        private const val PLAY_SERVICES_VERSION_CODE = 19629032
    }

    fun getAuthTokenPlayResponse(email: String?, oAuthToken: String?): PlayResponse {
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