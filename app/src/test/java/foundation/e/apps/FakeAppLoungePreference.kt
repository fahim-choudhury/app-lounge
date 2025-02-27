/*
 *  Copyright (C) 2022  ECORP
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps

import android.content.Context
import foundation.e.apps.data.preference.AppLoungePreference

class FakeAppLoungePreference(context: Context) : AppLoungePreference(context) {
    var isPWASelectedFake = false
    var isOpenSourceelectedFake = false
    var isGplaySelectedFake = false
    var shouldUpdateFromOtherStores = true

    override fun isPWASelected(): Boolean {
        return isPWASelectedFake
    }

    override fun isOpenSourceSelected(): Boolean {
        return isOpenSourceelectedFake
    }

    override fun isGplaySelected(): Boolean {
        return isGplaySelectedFake
    }

    override fun preferredApplicationType(): String {
        return when {
            isOpenSourceelectedFake -> "open"
            isPWASelectedFake -> "pwa"
            else -> "any"
        }
    }

    override fun shouldUpdateAppsFromOtherStores(): Boolean {
        return shouldUpdateFromOtherStores
    }
}
