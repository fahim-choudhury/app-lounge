package foundation.e.apps.domain.login.usecase

import android.content.Context
import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.storage.cache.configurations
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.domain.login.repository.GoogleLoginRepository
import foundation.e.apps.domain.login.repository.GoogleLoginRepositoryImpl
import foundation.e.apps.domain.login.repository.LoginRepositoryImpl
import foundation.e.apps.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.lang.Exception
import java.util.Properties
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleLoginRepository: GoogleLoginRepositoryImpl,
    private val loginRepositoryImpl: LoginRepositoryImpl,
    private val properties: Properties
) {
    suspend fun getAuthObject(
        user: User?,
        email: String = "",
        oauthToken: String = ""
    ): Flow<Resource<AuthObject>> {
        return flow {
            try {
                val currentUser = user ?: User.valueOf(context.configurations.userType)
                val currentEmail = email.ifEmpty { context.configurations.email }

                if (currentUser == User.GOOGLE && oauthToken.isNotEmpty()) {
                    fetchGplayAuthObject(currentEmail, oauthToken, currentUser)?.let {
                        emit(Resource.Success(it))
                        return@flow
                    }
                }

                val currentToken = context.configurations.aasToken

                if (currentUser == User.ANONYMOUS && currentToken.isEmpty()) {
                    loginRepositoryImpl.anonymousUser(authDataRequestBody = AnonymousAuthDataRequestBody(
                        properties = properties,
                        userAgent = ""
                    )
                    )
                }

                val authData = AuthHelper.build(currentEmail, currentToken, properties)

                if (currentUser == User.GOOGLE || currentUser == User.ANONYMOUS) {
                    emit(
                        Resource.Success(
                            AuthObject.GPlayAuth(
                                ResultSupreme.Success(authData),
                                currentUser
                            )
                        )
                    )
                } else {
                    emit(Resource.Error("No User Available!"))
                }
            } catch (e: Exception) {
                Timber.w(e)
                emit(
                    Resource.Error(
                        e.localizedMessage ?: context.getString(R.string.unknown_error)
                    )
                )
            }
        }
    }

    private suspend fun fetchGplayAuthObject(
        currentEmail: String,
        oauthToken: String,
        user: User
    ): AuthObject? {
        var authObject: AuthObject? = null
        googleLoginRepository.getGoogleLoginAuthData(currentEmail, oauthToken)?.let {
            authObject = AuthObject.GPlayAuth(ResultSupreme.Success(it), user)
        }
        return authObject
    }
}