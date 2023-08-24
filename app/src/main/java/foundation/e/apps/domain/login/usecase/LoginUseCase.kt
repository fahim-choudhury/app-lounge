package foundation.e.apps.domain.login.usecase

import android.content.Context
import app.lounge.storage.cache.configurations
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.utils.Resource
import timber.log.Timber
import java.lang.Exception
import java.util.Properties
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userLoginUseCase: UserLoginUseCase,
    private val gplayLoginUseCase: GplayLoginUseCase,
    private val properties: Properties
) {
    suspend fun getAuthObject(user: User?, email: String = "", oauthToken: String = ""): Resource<AuthObject>? {
        return try {
            val currentUser = user ?: User.valueOf(context.configurations.userType)

            when (currentUser) {
                User.ANONYMOUS -> userLoginUseCase.invoke(properties, "")
                User.GOOGLE -> gplayLoginUseCase.invoke(email, oauthToken)
                else -> null
            }
        } catch (e: Exception) {
            Timber.w(e)
            null
        }
    }
}