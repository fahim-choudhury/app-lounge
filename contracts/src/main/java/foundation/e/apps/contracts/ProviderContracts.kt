/*
 *  Copyright MURENA SAS 2024
 *  Apps  Quickly and easily install Android apps onto your device!
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.contracts

object ProviderContracts {
    const val COLUMN_PACKAGE_NAME = "package_name"
    const val COLUMN_LOGIN_TYPE = "login_type"

    const val PATH_LOGIN_TYPE = "login_type"
    const val PATH_BLOCKLIST = "block_list"

    fun getAppLoungeProviderAuthority(isDebug: Boolean = false) =
        "foundation.e.apps${if (isDebug) ".debug" else ""}.provider"
}