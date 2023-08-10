package app.lounge.login.google

import app.lounge.gplay.GplayHttpClient
import app.lounge.networking.fetchPlayResponse
import app.lounge.networking.NetworkResult
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.helpers.AuthValidator
import javax.inject.Inject

class GoogleLoginApiImpl @Inject constructor(
    private val gplayHttpClient: GplayHttpClient,
    private val authTokenFetchApi: AuthTokenFetchApi
) : GoogleLoginApi {

    override suspend fun getAuthTokenPlayResponse(
        email: String,
        oauthToken: String
    ): NetworkResult<PlayResponse> {
        return fetchPlayResponse {
            authTokenFetchApi.getAuthTokenPlayResponse(email, oauthToken)
        }
    }

    override suspend fun validate(authData: AuthData): NetworkResult<Boolean> {
        return fetchPlayResponse {
            val authValidator = AuthValidator(authData).using(gplayHttpClient)
            authValidator.isValid()
        }
    }

}