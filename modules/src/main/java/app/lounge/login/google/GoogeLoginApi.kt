package app.lounge.login.google

import app.lounge.networking.NetworkResult
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse

interface GoogleLoginApi {
    suspend fun fetchAASToken(email: String, oauthToken: String): NetworkResult<PlayResponse>
    suspend fun validate(authData: AuthData): NetworkResult<Boolean>
}