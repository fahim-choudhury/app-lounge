package foundation.e.apps.domain.login.usecase

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.domain.login.repository.GoogleLoginRepository
import foundation.e.apps.utils.Resource
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import javax.inject.Inject

class GplayLoginUseCase @Inject constructor(private val googleLoginRepository: GoogleLoginRepository) :
    BaseUseCase() {

    suspend operator fun invoke(
        email: String,
        oauthToken: String,
    ): Resource<AuthObject> = flow {
        try {
            emit(Resource.Loading())
            val authData = googleLoginRepository.getGoogleLoginAuthData(email, oauthToken)
            val authObject = AuthObject.GPlayAuth(ResultSupreme.Success(authData), User.ANONYMOUS) as AuthObject

            emit(Resource.Success(authObject))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: ""))
        }
    }.single()
}