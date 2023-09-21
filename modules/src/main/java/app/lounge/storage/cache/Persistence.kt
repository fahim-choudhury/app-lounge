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

package app.lounge.storage.cache

import android.content.Context
import kotlin.reflect.KProperty


val Context.configurations: PersistentConfiguration get() = PersistentConfiguration(context = this)

internal enum class PersistenceKey {
    updateInstallAuto,
    updateCheckIntervals,
    updateAppsFromOtherStores,
    showAllApplications,
    showPWAApplications,
    showFOSSApplications,
    // OLD datastore
    authData,
    email,
    oauthtoken,
    userType,
    tocStatus,
    tosversion
}

class PersistentConfiguration(context: Context) {
    var updateInstallAuto by context.persistent(PersistenceKey.updateInstallAuto, false)
    var updateCheckIntervals by context.persistent(PersistenceKey.updateCheckIntervals, 24)
    var updateAppsFromOtherStores by context.persistent(PersistenceKey.updateAppsFromOtherStores, false)
    var showAllApplications by context.persistent(PersistenceKey.showAllApplications, true)
    var showPWAApplications by context.persistent(PersistenceKey.showPWAApplications, true)
    var showFOSSApplications by context.persistent(PersistenceKey.showFOSSApplications, true)
    var authData by context.persistent(PersistenceKey.authData, "")
    var email by context.persistent(PersistenceKey.email, "")
    var oauthtoken by context.persistent(PersistenceKey.oauthtoken, "")
    var userType by context.persistent(PersistenceKey.userType, "")
    var tocStatus by context.persistent(PersistenceKey.tocStatus, false)
    var tosversion by context.persistent(PersistenceKey.tosversion, "")
}

internal  class PersistentItem<T>(
    context: Context,
    val key: PersistenceKey,
    var defaultValue: T
) {

    private val sharedPref =
        context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
    private val sharedPrefKey = "${context.packageName}." + key.name


    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return try {
            when (property.returnType.classifier) {
                Int::class -> sharedPref.getInt(sharedPrefKey, defaultValue as Int)
                Long::class -> sharedPref.getLong(sharedPrefKey, defaultValue as Long)
                Boolean::class -> sharedPref.getBoolean(sharedPrefKey, defaultValue as Boolean)
                String::class -> sharedPref.getString(sharedPrefKey, defaultValue as String)
                else -> IllegalArgumentException(
                    "TODO: Missing accessor for type -- ${property.returnType.classifier}"
                )
            } as T
        } catch (e: ClassCastException) {
            defaultValue
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        sharedPref.edit().apply {
            when (value) {
                is Int -> putInt(sharedPrefKey, value)
                is Long -> putLong(sharedPrefKey, value)
                is Boolean -> putBoolean(sharedPrefKey, value)
                is String -> putString(sharedPrefKey, value)
                else -> IllegalArgumentException(
                    "TODO: Missing setter for type -- ${property.returnType.classifier}"
                )
            }
            apply()
        }
    }
}

internal fun <T> Context.persistent(key: PersistenceKey, defaultValue: T) : PersistentItem<T> {
    return PersistentItem(context = this, key = key, defaultValue = defaultValue)
}