package foundation.e.apps.domain.login.usecase

import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.model.AuthDataResponse
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
    ): Resource<AuthDataResponse> = flow {
        try {
            emit(Resource.Loading())
            val userResponse: AuthDataResponse = loginRepository.anonymousUser(
                authDataRequestBody = AnonymousAuthDataRequestBody(
                    properties = properties,
                    userAgent = userAgent
                )
            )
            emit(Resource.Success(userResponse))
        } catch(e: HttpException) {
            emit(Resource.Error(e.localizedMessage ?: "An unexpected error occured"))
        } catch(e: IOException) {
            emit(Resource.Error("Couldn't reach server. Check your internet connection."))
        }
    }.single()
}