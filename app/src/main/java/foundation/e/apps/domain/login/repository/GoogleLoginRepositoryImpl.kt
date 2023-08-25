package foundation.e.apps.domain.login.repository

import android.content.Context
import app.lounge.login.google.GoogleLoginApi
import app.lounge.networking.NetworkResult
import app.lounge.storage.cache.configurations
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.helpers.AuthHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.enums.User
import timber.log.Timber
import java.lang.Exception
import java.util.Properties
import javax.inject.Inject

class GoogleLoginRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleLoginApi: GoogleLoginApi,
    private val properties: Properties
) : GoogleLoginRepository {

    override suspend fun getGoogleLoginAuthData(email: String, oauthToken: String?): AuthData? {
        context.configurations.email = email
        context.configurations.userType = User.GOOGLE.name
        oauthToken?.let {
            context.configurations.oauthtoken = oauthToken
        }

        val aasToken = context.configurations.aasToken
        if (oauthToken.isNullOrEmpty() && aasToken.isNotEmpty()) {
            return AuthHelper.build(email, aasToken, properties)
        }

        return fetchAuthData(oauthToken, email)
    }

    private suspend fun fetchAuthData(oauthToken: String?, email: String): AuthData? {
        oauthToken?.let {
            val result = googleLoginApi.getAuthTokenPlayResponse(email, oauthToken)
            return when (result) {
                is NetworkResult.Success -> handleAuthTokenPlayResponseSuccess(result, email)
                is NetworkResult.Error -> throw Exception(result.errorMessage, result.exception)
            }
        }

        return null
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
            context.configurations.aasToken = token
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