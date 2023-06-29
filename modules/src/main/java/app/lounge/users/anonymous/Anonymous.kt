package app.lounge.users.anonymous

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
            api: AnonymousAPI = AnonymousAPI.create()
        ) : Anonymous {
            return object : Anonymous {
                override val api: AnonymousAPI = api
            }
        }
    }

    // pass input from this function
    fun login(
        success : () -> Unit,
        failure : () -> Unit
    ) {
        api.performLogin(
            success = success,
            failure = failure
        )
    }
}