package foundation.e.apps.domain.login

import app.lounge.login.google.GoogleLoginApi
import app.lounge.networking.NetworkResult
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.helpers.AuthHelper
import timber.log.Timber
import java.lang.Exception
import java.util.Properties
import javax.inject.Inject

class GoogleLoginRepositoryImpl @Inject constructor(
    private val googleLoginApi: GoogleLoginApi,
    private val properties: Properties
) : GoogleLoginRepository {

    override suspend fun getGoogleLoginAuthData(email: String, oauthToken: String): AuthData? {
        val result = googleLoginApi.getAuthTokenPlayResponse(email, oauthToken)
        return when (result) {
            is NetworkResult.Success -> handleAuthTokenPlayResponseSuccess(result, email)
            is NetworkResult.Error -> throw Exception(result.errorMessage, result.exception)
        }
    }

    private fun handleAuthTokenPlayResponseSuccess(
        result: NetworkResult.Success<PlayResponse>,
        email: String
    ): AuthData? {
        if (result.data.isSuccessful) {
            val parsedResult =
                AuthTokenPlayResponseParser.parseResponse(String(result.data.responseBytes))
            val token = parsedResult["Token"] ?: ""
            Timber.d("Parsed token: $token")
            //TODO save token in the preferences
            return AuthHelper.build(email, token, properties)
        }
        return null
    }

    override suspend fun validate(): Boolean {
        val authData = AuthHelper.build("", "") // TODO authdata will be fetched from preferences
        val result = googleLoginApi.validate(authData)
        return result is NetworkResult.Success && result.data
    }
}