package foundation.e.apps.domain.login.repository

import android.content.Context
import app.lounge.login.anonymous.AnonymousUser
import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.networking.NetworkResult
import app.lounge.storage.cache.configurations
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LoginRepositoryImpl @Inject constructor(
    private val networkFetching: AnonymousUser,
    @ApplicationContext val applicationContext: Context
): LoginRepository {

    override suspend fun anonymousUser(authDataRequestBody: AnonymousAuthDataRequestBody): AuthData {
        val result = networkFetching.requestAuthData(
            anonymousAuthDataRequestBody = authDataRequestBody
        )

        when(result) {
            is NetworkResult.Error ->
                throw Exception(result.errorMessage, result.exception)
            is NetworkResult.Success -> {
                applicationContext.configurations.authData = result.data.toString()
                return result.data
            }
        }
    }

}
