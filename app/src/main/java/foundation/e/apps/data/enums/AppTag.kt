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

package foundation.e.apps.data.enums

/**
 * This sealed class is used for the tags shown in the categories screen,
 * the [displayTag] holds the tag in the user device specific locale.
 * (Example: [OpenSource.displayTag] for Deutsch language = "Quelloffen")
 *
 * Previously this was hard coded, which led to crashes due to changes in different locales.
 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5364
 */
sealed class AppTag(val displayTag: String) {
    class OpenSource(displayTag: String) : AppTag(displayTag)
    class PWA(displayTag: String) : AppTag(displayTag)
    class GPlay(displayTag: String = "") : AppTag(displayTag)

    /**
     * In many places in the code, checks are for hard coded string "Open Source".
     * This method allows for all those check to work without modification.
     */
    fun getOperationalTag(): String {
        return if (this is OpenSource) "Open Source"
        else this::class.java.simpleName
    }
}
