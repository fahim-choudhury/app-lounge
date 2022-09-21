/*
 * Copyright (C) 2019-2022  E FOUNDATION
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

package foundation.e.apps.login

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.login.AuthObject.GPlayAuth
import foundation.e.apps.utils.enums.User

/**
 * Auth objects define which sources data is to be loaded from, for each source, also provides
 * a means of authentication to get the data.
 * Example, for Google Play, we have [GPlayAuth] which contains [AuthData].
 * For CleanApk, we don't need any authentication.
 *
 * In future, combination of sources is possible.
 *
 * https://gitlab.e.foundation/e/backlog/-/issues/5680
 */
sealed class AuthObject {

    abstract val result: ResultSupreme<*>

    class GPlayAuth(override val result: ResultSupreme<AuthData?>, val user: User) : AuthObject()
    class CleanApk(override val result: ResultSupreme<Unit>, val user: User) : AuthObject()
    // Add more auth types here
}
