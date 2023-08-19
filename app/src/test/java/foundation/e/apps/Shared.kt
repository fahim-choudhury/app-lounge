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

package foundation.e.apps

import app.lounge.model.AnonymousAuthDataRequestBody
import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.utils.SystemInfoProvider
import java.util.Properties

const val testEmailAddress: String = "eOS@murena.io"
const val loginFailureMessage = "Fail to login"
val testFailureException: Exception = Exception(loginFailureMessage)

val testAnonymousRequestData = AnonymousAuthDataRequestBody(
    properties = Properties(),
    userAgent = SystemInfoProvider.getAppBuildInfo()
)

val testAnonymousResponseData = AuthData(testEmailAddress, "")
