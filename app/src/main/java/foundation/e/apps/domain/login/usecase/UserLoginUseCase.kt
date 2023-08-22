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
import foundation.e.apps.domain.login.repository.LoginRepository
import foundation.e.apps.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class UserLoginUseCase @Inject constructor(
    private val loginRepository: LoginRepository,
) {

    fun anonymousUser(): Flow<Resource<AuthData>> = flow {
        try {
            emit(Resource.Loading())
            val userResponse = loginRepository.anonymousUser()
            emit(Resource.Success(userResponse))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage))
        }
    }
}
