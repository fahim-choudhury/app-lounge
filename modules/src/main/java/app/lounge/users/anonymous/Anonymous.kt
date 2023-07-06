package app.lounge.users.anonymous

import app.lounge.networking.FetchError
import com.aurora.gplayapi.data.models.AuthData
import okhttp3.ResponseBody

interface Anonymous {

    val api: AnonymousAPI

    companion object {

        fun anonymousRequestFor(
            api: AnonymousAPI
        ) : Anonymous {
            return object : Anonymous {
                override val api: AnonymousAPI = api
            }
        }
    }

    // pass input from this function
    fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody,
        success : (AuthDataResponse) -> Unit,
        failure : (FetchError) -> Unit
    ) {
        api.requestAuthData(
            anonymousAuthDataRequestBody = anonymousAuthDataRequestBody,
            success = success,
            failure = failure
        )
    }

    fun requestAuthDataValidation(
        anonymousAuthDataValidationRequestBody: AnonymousAuthDataValidationRequestBody,
        success : (AuthDataValidationResponse) -> Unit,
        failure : (FetchError) -> Unit
    ) {
        api.requestAuthDataValidation(
            anonymousAuthDataValidationRequestBody = anonymousAuthDataValidationRequestBody,
            success = success,
            failure = failure
        )
    }
}

typealias AuthDataResponse = AuthData
typealias AuthDataValidationResponse = ResponseBody