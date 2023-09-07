package foundation.e.apps.domain.login.usecase

import android.content.Context
import app.lounge.storage.cache.configurations
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.login.AuthObject
import javax.inject.Inject

class NoGoogleModeUseCase @Inject constructor(@ApplicationContext private val context: Context) {
    fun performNoGoogleLogin(): AuthObject {
        context.configurations.userType = User.NO_GOOGLE.toString()
        context.configurations.showAllApplications = false
        context.configurations.showFOSSApplications = true
        context.configurations.showPWAApplications = true
        return getAuthObject()
    }

    private fun getAuthObject(): AuthObject.CleanApk {
        return AuthObject.CleanApk(
            ResultSupreme.Success(Unit),
            User.NO_GOOGLE
        )
    }
}
