package foundation.e.apps.domain.login.repository

import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.model.AuthDataResponse
import app.lounge.networking.NetworkFetching
import javax.inject.Inject

class LoginRepositoryImpl @Inject constructor(
    private val networkFetching: NetworkFetching
): LoginRepository {

    override suspend fun anonymousUser(authDataRequestBody: AnonymousAuthDataRequestBody): AuthDataResponse {
        return networkFetching.requestAuthData(
            anonymousAuthDataRequestBody = authDataRequestBody
        )
    }

}
