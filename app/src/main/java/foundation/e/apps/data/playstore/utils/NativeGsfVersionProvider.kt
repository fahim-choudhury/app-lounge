/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
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

package foundation.e.apps.data.playstore.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat

class NativeGsfVersionProvider(context: Context) {
    private var gsfVersionCode = 0
    private val packageManager = context.packageManager

    init {
        try {
            val gsfPkgInfo = packageManager.getPackageInfo(GOOGLE_SERVICES_PACKAGE_ID, 0)
            gsfVersionCode = PackageInfoCompat.getLongVersionCode(gsfPkgInfo).toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            // com.google.android.gms not found
        }
    }

    fun getGsfVersionCode(defaultIfNotFound: Boolean): Int {
        return if (defaultIfNotFound && gsfVersionCode < GOOGLE_SERVICES_VERSION_CODE)
            GOOGLE_SERVICES_VERSION_CODE
        else
            gsfVersionCode
    }

    fun getVendingVersionCode(): Int {
        return GOOGLE_VENDING_VERSION_CODE
    }

    fun getVendingVersionString(): String {
        return GOOGLE_VENDING_VERSION_STRING
    }

    companion object {
        private const val GOOGLE_SERVICES_PACKAGE_ID = "com.google.android.gms"
        private const val GOOGLE_SERVICES_VERSION_CODE = 203019037
        private const val GOOGLE_VENDING_VERSION_CODE = 82151710
        private const val GOOGLE_VENDING_VERSION_STRING = "21.5.17-21 [0] [PR] 326734551"
    }
}
