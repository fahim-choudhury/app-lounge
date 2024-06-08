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

import foundation.e.apps.BuildConfig

object Constants {
    const val timeoutDurationInMillis: Long = 10000

    const val PREFERENCE_SHOW_FOSS = "showFOSSApplications"
    const val PREFERENCE_SHOW_PWA = "showPWAApplications"
    const val PREFERENCE_SHOW_GPLAY = "showAllApplications"

    const val ACTION_AUTHDATA_DUMP = "foundation.e.apps.action.DUMP_GACCOUNT_INFO"
    const val TAG_AUTHDATA_DUMP = "AUTHDATA_DUMP"

    const val ACTION_DUMP_APP_INSTALL_STATE = "foundation.e.apps.action.APP_INSTALL_STATE"
    const val TAG_APP_INSTALL_STATE = "APP_INSTALL_STATE"

    const val ACTION_PARENTAL_CONTROL_APP_LOUNGE_LOGIN =
        "${BuildConfig.PACKAGE_NAME_PARENTAL_CONTROL}.action.APP_LOUNGE_LOGIN"
}
