package foundation.e.apps.domain.login.repository

import app.lounge.model.AnonymousAuthDataRequestBody
import com.aurora.gplayapi.data.models.AuthData


interface LoginRepository {

    suspend fun anonymousUser(
        authDataRequestBody: AnonymousAuthDataRequestBody
    ): AuthData

}