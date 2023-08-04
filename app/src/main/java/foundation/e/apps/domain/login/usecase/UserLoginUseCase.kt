package foundation.e.apps.domain.login.usecase

import app.lounge.model.AnonymousAuthDataRequestBody
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.domain.login.repository.LoginRepository
import foundation.e.apps.utils.Resource
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import retrofit2.HttpException
import java.io.IOException
import java.util.Properties
import javax.inject.Inject

class UserLoginUseCase @Inject constructor(
    private val loginRepository: LoginRepository,
) {

    suspend operator fun invoke(
        properties: Properties,
        userAgent: String
    ): Resource<AuthData> = flow {
        try {
            emit(Resource.Loading())
            val userResponse = loginRepository.anonymousUser(
                authDataRequestBody = AnonymousAuthDataRequestBody(
                    properties = properties,
                    userAgent = userAgent
                )
            )
            emit(Resource.Success(userResponse))
        } catch(e: Exception) {
            emit(Resource.Error(e.localizedMessage))
        }
    }.single()
}