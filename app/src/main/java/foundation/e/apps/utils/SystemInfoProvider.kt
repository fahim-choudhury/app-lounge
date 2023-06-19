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

package foundation.e.apps.utils

import android.annotation.SuppressLint
import android.os.Build
import foundation.e.apps.BuildConfig
import org.json.JSONObject

object SystemInfoProvider {

    const val KEY_LINEAGE_VERSION = "ro.lineage.version"

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String?): String? {
        var value: String? = null
        try {
            value = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java).invoke(null, key) as String
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return value
    }

    fun getAppBuildInfo(): String {
        val descriptionJson = JSONObject().apply {
            put("package", BuildConfig.APPLICATION_ID)
            put("version", BuildConfig.VERSION_NAME)
            put("device", Build.DEVICE)
            put("api", Build.VERSION.SDK_INT)
            put("os_version", getSystemProperty(KEY_LINEAGE_VERSION))
            put("build_id", BuildConfig.BUILD_ID)
        }
        return descriptionJson.toString()
    }
}
