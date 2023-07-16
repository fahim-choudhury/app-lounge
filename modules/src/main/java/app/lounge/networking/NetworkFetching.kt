package app.lounge.networking

import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.model.AnonymousAuthDataValidationRequestBody
import app.lounge.model.AuthDataResponse
import app.lounge.model.AuthDataValidationResponse

interface NetworkFetching {
    suspend  fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody
    ) : AuthDataResponse

    suspend fun requestAuthDataValidation(
        anonymousAuthDataValidationRequestBody: AnonymousAuthDataValidationRequestBody
    ) : AuthDataValidationResponse
}

