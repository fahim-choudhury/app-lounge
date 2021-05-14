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

import android.os.Build
import android.util.Log

/**
 * Contains methods related to OS
 */
object OsInfo {
    /**
     * Provides the release type of the OS on which the app is running
     * @return [String] containing OS release type
     */
    fun getOSReleaseType(): String {
        val buildTags = Build.TAGS.split(",").toTypedArray()
        var osReleaseType = ""
        buildTags.forEach {
            if (it.contains("-release")) {
                osReleaseType = it.substringBefore("-release")
            }
        }
        Log.i("foundation.e.apps", "Release Type: $osReleaseType")
        return osReleaseType
    }

}