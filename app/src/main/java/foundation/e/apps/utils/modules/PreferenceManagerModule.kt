/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
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

package foundation.e.apps.utils.modules

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.OpenForTesting
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class PreferenceManagerModule @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val preferenceManager = PreferenceManager.getDefaultSharedPreferences(context)

    fun preferredApplicationType(): String {
        val showFOSSApplications = preferenceManager.getBoolean("showFOSSApplications", false)
        val showPWAApplications = preferenceManager.getBoolean("showPWAApplications", false)

        return when {
            showFOSSApplications -> "open"
            showPWAApplications -> "pwa"
            else -> "any"
        }
    }

    fun isOpenSourceSelected() = preferenceManager.getBoolean("showFOSSApplications", false)
    fun isPWASelected() = preferenceManager.getBoolean("showPWAApplications", false)
    fun isGplaySelected() = preferenceManager.getBoolean("showAllApplications", false)

    fun autoUpdatePreferred(): Boolean {
        return preferenceManager.getBoolean("updateInstallAuto", false)
    }
}
