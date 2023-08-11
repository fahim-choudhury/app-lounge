package foundation.e.apps.domain.login

import com.aurora.gplayapi.data.models.AuthData

interface GoogleLoginRepository {
    suspend fun getGoogleLoginAuthData(email: String, oauthToken: String): AuthData?
    suspend fun validate(): Boolean
}