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


package app.lounge.networking

import app.lounge.model.AnonymousAuthDataRequestBody
import app.lounge.model.AnonymousAuthDataValidationRequestBody
import com.aurora.gplayapi.data.models.AuthData
import okhttp3.ResponseBody

interface AnonymousUser {
    suspend  fun requestAuthData(
        anonymousAuthDataRequestBody: AnonymousAuthDataRequestBody
    ) : NetworkResult<AuthData>

    suspend fun requestAuthDataValidation(
        anonymousAuthDataValidationRequestBody: AnonymousAuthDataValidationRequestBody
    ) : NetworkResult<ResponseBody>
}

