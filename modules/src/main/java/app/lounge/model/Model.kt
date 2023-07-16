package app.lounge.model

import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.providers.HeaderProvider
import okhttp3.ResponseBody
import java.util.Properties


/** AnonymousAuthDataRequestBody */
data class AnonymousAuthDataRequestBody(
    val properties: Properties,
    val userAgent: String
)

/** AnonymousLoginRequestBody */
data class AnonymousAuthDataValidationRequestBody(
    val authDataResponse: AuthDataResponse,
) {
    val header = HeaderProvider.getDefaultHeaders(authDataResponse)
}

typealias AuthDataResponse = AuthData

typealias AuthDataValidationResponse = ResponseBody