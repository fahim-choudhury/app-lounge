/*
 * Copyright (C) 2021-2024 MURENA SAS
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
 *
 */

package foundation.e.apps.data

object Constants {
    const val timeoutDurationInMillis: Long = 10000

    const val PREFERENCE_SHOW_FOSS = "showFOSSApplications"
    const val PREFERENCE_SHOW_PWA = "showPWAApplications"
    const val PREFERENCE_SHOW_GPLAY = "showAllApplications"

    const val ACTION_AUTHDATA_DUMP = "foundation.e.apps.action.DUMP_GACCOUNT_INFO"
    const val TAG_AUTHDATA_DUMP = "AUTHDATA_DUMP"

    const val PREFERENCE_IGNORE_SESSION_REFRESH = "ignoreSessionRefresh"
}
