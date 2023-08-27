package foundation.e.apps.domain.login.usecase

import android.content.Context
import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.storage.cache.configurations
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.domain.login.repository.GoogleLoginRepositoryImpl
import foundation.e.apps.domain.login.repository.LoginRepositoryImpl
import foundation.e.apps.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.util.Properties
import javax.inject.Inject
import kotlin.Exception

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
    ): Flow<Resource<out AuthObject>> {
        return flow {
            try {
                emit(Resource.Loading())
                val currentUser =
                    user ?: User.valueOf(context.configurations.userType.ifEmpty { "UNAVAILABLE" })
                val currentAuthData = getAuthData()
                Timber.d("currentAuthData: $(currentAuthData != null) currentUser: $currentUser")

                if (currentAuthData != null && currentUser != User.UNAVAILABLE) {
                    Timber.d("User is already available!")
                    emit(
                        createResourceSuccess(currentAuthData, currentUser)
                    )
                    return@flow
                }

                if (currentUser == User.GOOGLE) {
                    val currentEmail = email.ifEmpty { context.configurations.email }
                    fetchGplayAuthObject(currentEmail, oauthToken, currentUser)?.let {
                        emit(Resource.Success(it))
                    }
                    return@flow
                }

                if (currentUser == User.ANONYMOUS) {
                    val authData = loginRepositoryImpl.anonymousUser(
                        authDataRequestBody = AnonymousAuthDataRequestBody(
                            properties = properties,
                            userAgent = ""
                        )
                    )
                    emit(createResourceSuccess(authData, currentUser))
                    return@flow
                }

                emit(Resource.Error("User is not available!"))

            } catch (e: Exception) {
                Timber.w(e)
                emit(
                    Resource.Error(
                        e.localizedMessage ?: context.getString(R.string.unknown_error)
                    )
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun getAuthData(): AuthData? {
        return try {
            Gson().fromJson(context.configurations.authData, AuthData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun createResourceSuccess(
        authData: AuthData,
        currentUser: User
    ): Resource.Success<AuthObject> =
        Resource.Success(
            AuthObject.GPlayAuth(
                ResultSupreme.Success(authData),
                currentUser
            )
        )

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