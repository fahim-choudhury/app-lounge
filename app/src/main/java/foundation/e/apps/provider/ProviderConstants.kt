package foundation.e.apps.provider

import foundation.e.apps.BuildConfig

class ProviderConstants {
    companion object {
        const val PACKAGE_NAME = "package_name"
        const val AGE_RATING = "age_rating"
        const val LOGIN_TYPE = "login_type"

        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider"
        const val PATH_LOGIN_TYPE = "login_type"
        const val PATH_AGE_RATINGS = "age_ratings"
    }
}