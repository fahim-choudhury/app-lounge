/*
 * Copyright (C) 2019-2021  E FOUNDATION
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

package foundation.e.apps.utils

import android.content.Context
import android.content.SharedPreferences
import foundation.e.apps.MainActivity

class PreferenceStorage(val context: Context) {

    private val sharedPref: SharedPreferences = context.getSharedPreferences(MainActivity.sharedPrefFile, Context.MODE_PRIVATE)

    /**
     * Saves the given string into Shared Preferences
     * @param KEY_NAME Name of the key, must be a [String]
     * @param text, must be a [String]
     */
    fun save(KEY_NAME: String, text: String) {
        with(sharedPref.edit()) {
            putString(KEY_NAME, text)
            apply()
        }
    }

    /**
     * Saves the given string into Shared Preferences
     * @param KEY_NAME Name of the key, must be a [String]
     * @param value, must be a [Int]
     */
    fun save(KEY_NAME: String, value: Int) {
        with(sharedPref.edit()) {
            putInt(KEY_NAME, value)
            apply()
        }
    }

    /**
     * Saves the given string into Shared Preferences
     * @param KEY_NAME Name of the key, must be a [String]
     * @param status, must be a [Boolean]
     */
    fun save(KEY_NAME: String, status: Boolean) {
        with(sharedPref.edit()) {
            putBoolean(KEY_NAME, status)
            apply()
        }
    }

    /**
     * Returns a value for the given key
     * @param KEY_NAME Name of the key, must be a [String]
     * @return Stored [String] value, can be null if the key doesn't exists
     */
    fun getStringValue(KEY_NAME: String) = sharedPref.getString(KEY_NAME, null)

    /**
     * Returns a value for the given key
     * @param KEY_NAME Name of the key, must be a [String]
     * @return Stored [Int] value, can be 0 if the key doesn't exists
     */
    fun getInt(KEY_NAME: String) = sharedPref.getInt(KEY_NAME, 0)

    /**
     * Returns a value for the given key
     * @param KEY_NAME Name of the key, must be a [String]
     * @return Stored [Boolean] value, can be [defaultValue] if the key doesn't exists
     */
    fun getBoolean(KEY_NAME: String, defaultValue: Boolean) = sharedPref.getBoolean(KEY_NAME, defaultValue)
}