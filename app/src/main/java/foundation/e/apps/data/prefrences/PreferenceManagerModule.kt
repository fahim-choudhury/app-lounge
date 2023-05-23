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

package foundation.e.apps.data.prefrences

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.OpenForTesting
import foundation.e.apps.R
import foundation.e.apps.data.Constants.PREFERENCE_SHOW_FOSS
import foundation.e.apps.data.Constants.PREFERENCE_SHOW_GPLAY
import foundation.e.apps.data.Constants.PREFERENCE_SHOW_PWA
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class PreferenceManagerModule @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val preferenceManager = PreferenceManager.getDefaultSharedPreferences(context)

    fun preferredApplicationType(): String {
        val showFOSSApplications = preferenceManager.getBoolean(PREFERENCE_SHOW_FOSS, false)
        val showPWAApplications = preferenceManager.getBoolean(PREFERENCE_SHOW_PWA, false)

        return when {
            showFOSSApplications -> "open"
            showPWAApplications -> "pwa"
            else -> "any"
        }
    }

    fun isOpenSourceSelected() = preferenceManager.getBoolean(PREFERENCE_SHOW_FOSS, true)
    fun isPWASelected() = preferenceManager.getBoolean(PREFERENCE_SHOW_PWA, true)
    fun isGplaySelected() = preferenceManager.getBoolean(PREFERENCE_SHOW_GPLAY, true)

    fun autoUpdatePreferred(): Boolean {
        return preferenceManager.getBoolean("updateInstallAuto", false)
    }

    fun getUpdateInterval() = preferenceManager.getString(
        context.getString(R.string.update_check_intervals),
        context.getString(R.string.preference_update_interval_default)
    )!!.toLong()

    fun shouldUpdateAppsFromOtherStores() = preferenceManager.getBoolean(
        context.getString(R.string.update_apps_from_other_stores),
        true
    )
}
