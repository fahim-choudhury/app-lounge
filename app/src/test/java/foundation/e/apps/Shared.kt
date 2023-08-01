package foundation.e.apps

import app.lounge.model.AnonymousAuthDataRequestBody
import com.aurora.gplayapi.data.models.AuthData
import java.util.Properties


val testAnonymousRequestBodyData = AnonymousAuthDataRequestBody(
    properties = Properties(),
    userAgent = "testUserAgent"
)

val testAnonymousResponseData = AuthData("eOS@murena.io", "")

const val loginFailureMessage = "Fail to login"