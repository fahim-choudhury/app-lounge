package foundation.e.apps.domain.login.repository

import android.content.Context
import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.model.AuthDataResponse
import app.lounge.networking.NetworkFetching
import app.lounge.storage.cache.configurations
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LoginRepositoryImpl @Inject constructor(
    private val networkFetching: NetworkFetching,
    @ApplicationContext val applicationContext: Context
): LoginRepository {

    override suspend fun anonymousUser(authDataRequestBody: AnonymousAuthDataRequestBody): AuthDataResponse {
        return networkFetching.requestAuthData(
            anonymousAuthDataRequestBody = authDataRequestBody
        ).also {
            applicationContext.configurations.authData = it.toString()
        }
    }

}
