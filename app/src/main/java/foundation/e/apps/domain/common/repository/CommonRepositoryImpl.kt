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

package foundation.e.apps.domain.common.repository

import android.content.Context
import app.lounge.storage.cache.configurations
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.enums.User
import foundation.e.apps.utils.toAuthData
import javax.inject.Inject

class CommonRepositoryImpl @Inject constructor(
    @ApplicationContext val applicationContext: Context,
) : CommonRepository {

    override fun currentUser(): User {
        return applicationContext.configurations.userType.takeIf { it.isNotEmpty() }
            ?.let { User.getUser(it) }
            ?: run { User.UNAVAILABLE }
    }
    override fun resetCachedData() {
        applicationContext.configurations.apply {
            authData = ""
            userType = User.UNAVAILABLE.name
            email = ""
            oauthtoken = ""
            // TODO: reset access token for Google login. It is not defined yet.
        }
    }

    override fun cacheAuthData(): AuthData =
        applicationContext.configurations.authData.let { data ->
            if (data.isEmpty()) throw Exception("Auth Data not available")
            return data.toAuthData()
        }
}
