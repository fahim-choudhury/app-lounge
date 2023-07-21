package foundation.e.apps.domain.login.repository

import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.model.AuthDataResponse


interface LoginRepository {

    suspend fun anonymousUser(
        authDataRequestBody: AnonymousAuthDataRequestBody
    ): AuthDataResponse

}