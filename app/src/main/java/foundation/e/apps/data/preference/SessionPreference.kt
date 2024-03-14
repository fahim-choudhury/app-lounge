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

package foundation.e.apps.data.preference

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.Constants.PREFERENCE_IGNORE_SESSION_REFRESH
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPreference @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferenceManager = PreferenceManager.getDefaultSharedPreferences(context)

    fun shouldIgnoreSessionRefresh(): Boolean {
        return preferenceManager.getBoolean(PREFERENCE_IGNORE_SESSION_REFRESH, false)
    }

    fun updateIgnoreSessionRefreshPreference(ignore: Boolean) {
        preferenceManager.edit().putBoolean(PREFERENCE_IGNORE_SESSION_REFRESH, ignore).apply()
    }
}
