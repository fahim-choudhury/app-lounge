/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.domain.login.usecase

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.domain.common.repository.CommonRepository
import foundation.e.apps.domain.login.repository.LoginRepository
import foundation.e.apps.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class UserLoginUseCase @Inject constructor(
    private val loginRepository: LoginRepository,
    private val commonRepository: CommonRepository
) {

    fun anonymousUser(): Flow<Resource<AuthData>> = flow {
        try {
            emit(Resource.Loading())
            emit(Resource.Success(loginRepository.anonymousUser()))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage))
        }
    }

    fun googleUser(authData: AuthData, token: String): Flow<Resource<Unit>> = flow {
        try {
            emit(
                Resource.Success(
                    loginRepository.googleUser(
                        authData = authData,
                        oauthToken = token
                    )
                )
            )
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage))
        }
    }

    fun retrieveCachedAuthData(): Flow<Resource<AuthData>> = flow {
        try {
            emit(Resource.Loading())
            emit(Resource.Success(commonRepository.cacheAuthData()))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage))
        }
    }

    fun logoutUser() {
        commonRepository.resetCachedData()
    }

    fun currentUser() = commonRepository.currentUser()

    fun performAnonymousUserAuthentication(): Flow<Resource<AuthData>> = flow {
        anonymousUser().onEach { anonymousAuth ->
            // TODO -> If we are not using auth data then
            when (anonymousAuth) {
                is Resource.Error -> emit(Resource.Error(anonymousAuth.message ?: "An unexpected error occured"))
                is Resource.Loading -> emit(Resource.Loading())
                is Resource.Success -> {
                    retrieveCachedAuthData().onEach {
                        when (it) {
                            is Resource.Error -> {
                                emit(Resource.Error(anonymousAuth.message ?: "An unexpected error occured"))
                            }
                            is Resource.Loading -> emit(Resource.Loading())
                            is Resource.Success -> {
                                emit(Resource.Success(commonRepository.cacheAuthData()))
                            }
                        }
                    }.collect()
                }
            }
        }.collect()
    }
}
