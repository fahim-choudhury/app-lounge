package app.lounge.users.anonymous

import app.lounge.networking.FetchError

/**
 * Implement API related to Anonymous login flow only.
 * 1. Login api for Anonymous users.
 * 2. Parsing Anonymous user data. For now use typealias object, later we will refactor with generic type
 * 3. Add unit test cases for the api functions.
 * */

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
}