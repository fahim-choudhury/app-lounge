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
